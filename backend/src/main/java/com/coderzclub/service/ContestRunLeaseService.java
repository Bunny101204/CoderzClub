package com.coderzclub.service;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestRunJob;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ContestRunLeaseService {
    private final MongoTemplate mongoTemplate;
    private final ContestRunProperties properties;

    public ContestRunLeaseService(MongoTemplate mongoTemplate, ContestRunProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    public Optional<ContestRunJob> claim(String runId, String workerId) {
        Date now = new Date();
        Date leaseUntil = new Date(now.getTime() + properties.getLeaseDurationSeconds() * 1000);
        Date expiresAt = new Date(now.getTime() + ttlMillis());

        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("_id").is(runId),
            new Criteria().orOperator(
                Criteria.where("status").is(ContestRunJob.Status.QUEUED),
                new Criteria().andOperator(
                    Criteria.where("status").is(ContestRunJob.Status.RUNNING),
                    Criteria.where("leaseExpiresAt").lte(now)
                )
            ),
            new Criteria().orOperator(
                Criteria.where("attemptCount").is(null),
                Criteria.where("attemptCount").lt(properties.getMaxAttempts())
            )
        ));

        Update update = new Update()
            .set("status", ContestRunJob.Status.RUNNING)
            .set("lockedBy", workerId)
            .set("leaseExpiresAt", leaseUntil)
            .set("startedAt", now)
            .set("expiresAt", expiresAt)
            .set("publicResults", List.of())
            .inc("attemptCount", 1);

        ContestRunJob job = mongoTemplate.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), ContestRunJob.class);
        return Optional.ofNullable(job);
    }

    public boolean heartbeat(String runId, String workerId) {
        Date now = new Date();
        Date leaseUntil = new Date(now.getTime() + properties.getLeaseDurationSeconds() * 1000);
        Date expiresAt = new Date(now.getTime() + ttlMillis());
        Query query = ownedRunning(runId, workerId, now);
        Update update = new Update()
            .set("leaseExpiresAt", leaseUntil)
            .set("expiresAt", expiresAt);
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    public boolean completeIfOwned(String runId, String workerId, List<ContestRunJob.PublicTestResult> results,
                                   int totalTests) {
        Date now = new Date();
        Query query = ownedRunning(runId, workerId, now);
        int completed = results == null ? 0 : results.size();
        Update update = new Update()
            .set("status", ContestRunJob.Status.COMPLETED)
            .set("publicResults", results == null ? List.of() : results)
            .set("completedTests", completed)
            .set("totalTests", totalTests)
            .set("completedAt", now)
            .set("lockedBy", null)
            .set("leaseExpiresAt", null)
            .set("errorCode", null)
            .set("errorMessage", null)
            .set("expiresAt", new Date(now.getTime() + ttlMillis()));
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    public boolean failIfOwned(String runId, String workerId, String errorCode, String errorMessage) {
        Date now = new Date();
        Query query = ownedRunning(runId, workerId, now);
        Update update = new Update()
            .set("status", ContestRunJob.Status.FAILED)
            .set("errorCode", errorCode)
            .set("errorMessage", safeMessage(errorMessage))
            .set("completedAt", now)
            .set("lockedBy", null)
            .set("leaseExpiresAt", null)
            .set("expiresAt", new Date(now.getTime() + ttlMillis()));
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    /**
     * Fails a run that is still waiting: QUEUED, or RUNNING whose lease has already expired.
     * A live RUNNING lease and any terminal status are left unchanged.
     * {@code createdAt} is not modified.
     */
    public boolean failIfQueueExpired(String runId, String errorCode, String errorMessage) {
        Date now = new Date();
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("_id").is(runId),
            new Criteria().orOperator(
                Criteria.where("status").is(ContestRunJob.Status.QUEUED),
                new Criteria().andOperator(
                    Criteria.where("status").is(ContestRunJob.Status.RUNNING),
                    Criteria.where("leaseExpiresAt").lte(now)
                )
            )
        ));
        Update update = new Update()
            .set("status", ContestRunJob.Status.FAILED)
            .set("errorCode", errorCode)
            .set("errorMessage", safeMessage(errorMessage))
            .set("completedAt", now)
            .set("lockedBy", null)
            .set("leaseExpiresAt", null)
            .set("expiresAt", new Date(now.getTime() + ttlMillis()));
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    /**
     * Returns an owned run to QUEUED so a redelivery can expire or claim it.
     * Does not change {@code createdAt} or {@code attemptCount}.
     */
    public boolean releaseToQueuedIfOwned(String runId, String workerId) {
        Date now = new Date();
        Query query = Query.query(Criteria.where("_id").is(runId)
            .and("status").is(ContestRunJob.Status.RUNNING)
            .and("lockedBy").is(workerId)
            .and("leaseExpiresAt").gt(now));
        Update update = new Update()
            .set("status", ContestRunJob.Status.QUEUED)
            .set("lockedBy", null)
            .set("leaseExpiresAt", null)
            .set("startedAt", null);
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    public boolean failExpired(String runId, String errorCode, String errorMessage) {
        Date now = new Date();
        Query query = Query.query(Criteria.where("_id").is(runId)
            .and("status").is(ContestRunJob.Status.RUNNING)
            .and("leaseExpiresAt").lte(now));
        Update update = new Update()
            .set("status", ContestRunJob.Status.FAILED)
            .set("errorCode", errorCode)
            .set("errorMessage", safeMessage(errorMessage))
            .set("completedAt", now)
            .set("lockedBy", null)
            .set("leaseExpiresAt", null)
            .set("expiresAt", new Date(now.getTime() + ttlMillis()));
        return mongoTemplate.updateFirst(query, update, ContestRunJob.class).getModifiedCount() == 1;
    }

    public boolean isOwned(String runId, String workerId) {
        return mongoTemplate.exists(ownedRunning(runId, workerId, new Date()), ContestRunJob.class);
    }

    public List<ContestRunJob> findExpiredRunning(int limit) {
        Query query = Query.query(Criteria.where("status").is(ContestRunJob.Status.RUNNING)
            .and("leaseExpiresAt").lte(new Date()))
            .limit(Math.max(1, limit));
        return mongoTemplate.find(query, ContestRunJob.class);
    }

    private Query ownedRunning(String runId, String workerId, Date now) {
        return Query.query(Criteria.where("_id").is(runId)
            .and("status").is(ContestRunJob.Status.RUNNING)
            .and("lockedBy").is(workerId)
            .and("leaseExpiresAt").gt(now));
    }

    private long ttlMillis() {
        return Math.max(5, properties.getJobTtlMinutes()) * 60_000L;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Run failed";
        }
        String trimmed = message.replace('\n', ' ').replace('\t', ' ').trim();
        return trimmed.substring(0, Math.min(200, trimmed.length()));
    }
}
