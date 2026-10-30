package com.coderzclub.service;

import com.coderzclub.model.MutationIdempotencyRecord;
import com.coderzclub.model.SubmissionJob;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Stores one client attempt so a retry returns the original job instead of
 * publishing another one.
 *
 * The job document is the source of truth for "this attempt was saved".
 * If the idempotency row is still open after that save, the next request
 * links the row to that job and does not insert a second queue event.
 * An open row with no job only blocks other accepts for {@link #UNRESOLVED_LEASE}.
 * Completed rows stay for {@link #RETENTION_DAYS} so the same key can be replayed.
 *
 * This does not make judge execution exactly-once. A worker may still retry
 * the same saved job. Two different operation keys, after the first attempt
 * is linked, are two submissions even when the source code matches.
 */
@Service
public class MutationIdempotencyService implements IdempotencyOperations {
    public static final String HEADER = "Idempotency-Key";
    public static final Duration UNRESOLVED_LEASE = Duration.ofSeconds(45);
    private static final Logger logger = LoggerFactory.getLogger(MutationIdempotencyService.class);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{7,79}$");
    private static final int RETENTION_DAYS = 7;

    private final MongoTemplate mongoTemplate;
    private final SubmissionOutboxService outboxService;
    private final Clock clock;

    public MutationIdempotencyService(
        MongoTemplate mongoTemplate,
        SubmissionOutboxService outboxService,
        Clock clock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    /**
     * Idempotent startup initialization for the local and future process.
     * This is not a migration script and is not run against a remote database
     * from a release tool.
     */
    static final String OPERATION_INDEX = "idempotency_operation_unique";
    static final String OPEN_SUBJECT_INDEX = "idempotency_open_subject_unique";
    static final String TTL_INDEX = "mutation_idempotency_ttl";
    static final String JOB_INDEX = "submission_job_idempotency_unique";

    @PostConstruct
    void ensureIndexes() {
        ensureOne(MutationIdempotencyRecord.class, OPERATION_INDEX,
            new ExpectedIndex(new Document("userId", 1).append("scope", 1).append("operationKey", 1), true, false, null),
            () -> mongoTemplate.indexOps(MutationIdempotencyRecord.class).ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("scope", 1).append("operationKey", 1))
                .named(OPERATION_INDEX)
                .unique()));
        ensureOne(MutationIdempotencyRecord.class, OPEN_SUBJECT_INDEX,
            new ExpectedIndex(new Document("userId", 1).append("scope", 1).append("subjectId", 1), true, false,
                new Document("open", true)),
            () -> mongoTemplate.indexOps(MutationIdempotencyRecord.class).ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("scope", 1).append("subjectId", 1))
                .named(OPEN_SUBJECT_INDEX)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("open").is(true)))));
        ensureOne(MutationIdempotencyRecord.class, TTL_INDEX,
            new ExpectedIndex(new Document("expiresAt", 1), false, true, null),
            () -> mongoTemplate.indexOps(MutationIdempotencyRecord.class).ensureIndex(new Index()
                .on("expiresAt", Sort.Direction.ASC)
                .named(TTL_INDEX)
                .expire(0)));
        ensureOne(SubmissionJob.class, JOB_INDEX,
            new ExpectedIndex(new Document("userId", 1).append("idempotencyScope", 1).append("idempotencyKey", 1), true, false,
                new Document("idempotencyKey", new Document("$exists", true))),
            () -> mongoTemplate.indexOps(SubmissionJob.class).ensureIndex(new CompoundIndexDefinition(
                new Document("userId", 1).append("idempotencyScope", 1).append("idempotencyKey", 1))
                .named(JOB_INDEX)
                .unique()
                .partial(PartialIndexFilter.of(Criteria.where("idempotencyKey").exists(true)))));
    }

    private void ensureOne(Class<?> entity, String indexName, ExpectedIndex expected, Runnable create) {
        try {
            create.run();
        } catch (RuntimeException ex) {
            if (isIndexConflict(ex)) {
                throw new IllegalStateException(
                    "Idempotency index " + indexName + " conflicts with an existing index. Startup stops so two jobs cannot be accepted under a weaker definition.",
                    ex);
            }
            if (isUnauthorized(ex)) {
                if (definitionMatches(entity, indexName, expected)) {
                    logger.warn("idempotency_index_present_without_create_privilege index={}", indexName);
                    return;
                }
                throw new IllegalStateException(
                    "Idempotency index " + indexName + " is missing or conflicts and this database user cannot create indexes.",
                    ex);
            }
            throw ex;
        }
    }

    private boolean definitionMatches(Class<?> entity, String indexName, ExpectedIndex expected) {
        for (IndexInfo info : mongoTemplate.indexOps(entity).getIndexInfo()) {
            if (!indexName.equals(info.getName())) {
                continue;
            }
            if (info.isUnique() != expected.unique()) {
                return false;
            }
            boolean ttl = info.getExpireAfter().isPresent();
            if (ttl != expected.ttl()) {
                return false;
            }
            Document keys = new Document();
            for (org.springframework.data.mongodb.core.index.IndexField field : info.getIndexFields()) {
                int direction = field.getDirection() == Sort.Direction.DESC ? -1 : 1;
                keys.append(field.getKey(), direction);
            }
            if (!expected.keys().equals(keys)) {
                return false;
            }
            return samePartial(info.getPartialFilterExpression(), expected.partial());
        }
        return false;
    }

    private boolean samePartial(String actual, Document expected) {
        if (expected == null || expected.isEmpty()) {
            return actual == null || actual.isBlank();
        }
        if (actual == null || actual.isBlank()) {
            return false;
        }
        try {
            return Document.parse(actual).equals(expected);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private record ExpectedIndex(Document keys, boolean unique, boolean ttl, Document partial) {}

    @Override
    public IdempotencyClaim begin(String userId, String scope, String operationKey, String subjectId) {
        if (operationKey == null || operationKey.isBlank()) {
            return IdempotencyClaim.absent();
        }
        if (userId == null || userId.isBlank() || scope == null || scope.isBlank() || !KEY.matcher(operationKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key is invalid.");
        }
        String savedJobId = findJobId(userId, scope, operationKey);
        if (savedJobId != null) {
            MutationIdempotencyRecord existing = findByKey(userId, scope, operationKey);
            if (existing != null) {
                link(existing.getId(), savedJobId);
            }
            outboxService.ensureJobEvent(savedJobId);
            return IdempotencyClaim.replay(savedJobId);
        }
        MutationIdempotencyRecord byKey = findByKey(userId, scope, operationKey);
        if (byKey != null) {
            IdempotencyClaim resolved = resolve(byKey, true);
            if (resolved != null) {
                return resolved;
            }
        }
        boolean trackSubject = subjectId != null && !subjectId.isBlank();
        if (trackSubject) {
            MutationIdempotencyRecord open = findOpen(userId, scope, subjectId);
            if (open != null) {
                IdempotencyClaim resolved = resolve(open, operationKey.equals(open.getOperationKey()));
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        MutationIdempotencyRecord record = new MutationIdempotencyRecord();
        record.setUserId(userId);
        record.setScope(scope);
        record.setOperationKey(operationKey);
        record.setSubjectId(trackSubject ? subjectId : "");
        record.setOpen(trackSubject);
        record.setCreatedAt(Date.from(Instant.now(clock)));
        record.setExpiresAt(Date.from(Instant.now(clock).plus(RETENTION_DAYS, ChronoUnit.DAYS)));
        try {
            mongoTemplate.insert(record);
            return IdempotencyClaim.owned(record.getId());
        } catch (RuntimeException ex) {
            if (!isDuplicate(ex)) {
                throw ex;
            }
            MutationIdempotencyRecord raced = findByKey(userId, scope, operationKey);
            if (raced != null) {
                IdempotencyClaim resolved = resolve(raced, true);
                if (resolved != null) {
                    return resolved;
                }
            }
            if (trackSubject) {
                MutationIdempotencyRecord open = findOpen(userId, scope, subjectId);
                if (open != null) {
                    IdempotencyClaim resolved = resolve(open, false);
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
            return IdempotencyClaim.inProgress(null, operationKey);
        }
    }

    @Override
    public void complete(IdempotencyClaim claim, String resourceId) {
        if (claim == null || !claim.owned() || claim.recordId() == null || resourceId == null || resourceId.isBlank()) {
            return;
        }
        link(claim.recordId(), resourceId);
        outboxService.ensureJobEvent(resourceId);
    }

    @Override
    public void abandon(IdempotencyClaim claim) {
        if (claim == null || !claim.owned() || claim.recordId() == null) {
            return;
        }
        MutationIdempotencyRecord record = mongoTemplate.findById(claim.recordId(), MutationIdempotencyRecord.class);
        if (record == null) {
            return;
        }
        String jobId = findJobId(record.getUserId(), record.getScope(), record.getOperationKey());
        if (jobId != null) {
            link(record.getId(), jobId);
            outboxService.ensureJobEvent(jobId);
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(record.getId())), MutationIdempotencyRecord.class);
    }

    @Override
    public boolean stillHeld(IdempotencyClaim claim) {
        if (claim == null || !claim.owned() || claim.recordId() == null) {
            return false;
        }
        MutationIdempotencyRecord record = mongoTemplate.findById(claim.recordId(), MutationIdempotencyRecord.class);
        return record != null && record.isOpen()
            && (record.getResourceId() == null || record.getResourceId().isBlank())
            && findJobId(record.getUserId(), record.getScope(), record.getOperationKey()) == null;
    }

    /**
     * @param sameOperation true when the caller is retrying this record's key
     * @return a claim to return, or null when the caller may start a new record
     */
    private IdempotencyClaim resolve(MutationIdempotencyRecord record, boolean sameOperation) {
        String jobId = record.getResourceId() != null && !record.getResourceId().isBlank()
            ? record.getResourceId()
            : findJobId(record.getUserId(), record.getScope(), record.getOperationKey());
        if (jobId != null) {
            link(record.getId(), jobId);
            outboxService.ensureJobEvent(jobId);
            if (sameOperation) {
                return IdempotencyClaim.replay(jobId);
            }
            return null;
        }
        if (!record.isOpen()) {
            if (!sameOperation) {
                return null;
            }
            var reopened = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(record.getId()).and("open").is(false)),
                new Update().set("open", true).set("createdAt", Date.from(Instant.now(clock))),
                MutationIdempotencyRecord.class
            );
            if (reopened.getModifiedCount() > 0) {
                return IdempotencyClaim.owned(record.getId());
            }
            return IdempotencyClaim.inProgress(null, record.getOperationKey());
        }
        if (isFresh(record)) {
            return IdempotencyClaim.inProgress(null, record.getOperationKey());
        }
        var released = mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(record.getId())
                .and("open").is(true)
                .and("createdAt").is(record.getCreatedAt())),
            new Update().set("open", false),
            MutationIdempotencyRecord.class
        );
        if (released.getModifiedCount() == 0) {
            MutationIdempotencyRecord current = mongoTemplate.findById(record.getId(), MutationIdempotencyRecord.class);
            if (current == null) {
                return null;
            }
            boolean sameInstant = current.getCreatedAt() != null && record.getCreatedAt() != null
                && current.getCreatedAt().getTime() == record.getCreatedAt().getTime();
            if (sameInstant) {
                return IdempotencyClaim.inProgress(null, current.getOperationKey());
            }
            return resolve(current, sameOperation);
        }
        logger.info("idempotency_stale_claim_released userId={} scope={} operationKey={}",
            record.getUserId(), record.getScope(), record.getOperationKey());
        return null;
    }

    @Override
    public boolean prepareCommit(String userId, String scope, String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            return true;
        }
        MutationIdempotencyRecord row = findByKey(userId, scope, operationKey);
        if (row == null) {
            return true;
        }
        if (!row.isOpen()) {
            return false;
        }
        var updated = mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(row.getId())
                .and("open").is(true)
                .and("createdAt").is(row.getCreatedAt())),
            new Update().set("createdAt", Date.from(Instant.now(clock))),
            MutationIdempotencyRecord.class
        );
        return updated.getModifiedCount() > 0;
    }

    @Override
    public boolean renew(String userId, String scope, String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            return true;
        }
        var updated = mongoTemplate.updateFirst(
            Query.query(Criteria.where("userId").is(userId)
                .and("scope").is(scope)
                .and("operationKey").is(operationKey)
                .and("open").is(true)),
            new Update().set("createdAt", Date.from(Instant.now(clock))),
            MutationIdempotencyRecord.class
        );
        return updated.getModifiedCount() > 0 || updated.getMatchedCount() > 0;
    }

    private boolean isFresh(MutationIdempotencyRecord record) {
        if (record.getCreatedAt() == null) {
            return false;
        }
        return record.getCreatedAt().toInstant().plus(UNRESOLVED_LEASE).isAfter(Instant.now(clock));
    }

    private void link(String recordId, String resourceId) {
        try {
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(recordId)),
                new Update().set("resourceId", resourceId).set("open", false),
                MutationIdempotencyRecord.class
            );
        } catch (RuntimeException ex) {
            logger.warn("idempotency_link_failed recordId={} resourceId={}", recordId, resourceId);
        }
    }

    private String findJobId(String userId, String scope, String operationKey) {
        if (userId == null || scope == null || operationKey == null) {
            return null;
        }
        SubmissionJob job = mongoTemplate.findOne(
            Query.query(Criteria.where("userId").is(userId)
                .and("idempotencyScope").is(scope)
                .and("idempotencyKey").is(operationKey)),
            SubmissionJob.class
        );
        return job == null ? null : job.getId();
    }

    private MutationIdempotencyRecord findByKey(String userId, String scope, String operationKey) {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("userId").is(userId).and("scope").is(scope).and("operationKey").is(operationKey)),
            MutationIdempotencyRecord.class
        );
    }

    private MutationIdempotencyRecord findOpen(String userId, String scope, String subjectId) {
        return mongoTemplate.findOne(
            Query.query(Criteria.where("userId").is(userId).and("scope").is(scope).and("subjectId").is(subjectId).and("open").is(true)),
            MutationIdempotencyRecord.class
        );
    }

    private boolean isDuplicate(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            String name = current.getClass().getName();
            if (name.contains("DuplicateKey") || name.contains("MongoWriteException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isUnauthorized(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getName().contains("MongoCommandException")
                && String.valueOf(current.getMessage()).contains("Unauthorized")) {
                return true;
            }
            String message = String.valueOf(current.getMessage());
            if (message.contains("not authorized") || message.contains("code 13")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isIndexConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = String.valueOf(current.getMessage());
            if (message.contains("IndexOptionsConflict")
                || message.contains("IndexKeySpecsConflict")
                || message.contains("already exists with a different")
                || message.contains("code 85")
                || message.contains("code 86")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
