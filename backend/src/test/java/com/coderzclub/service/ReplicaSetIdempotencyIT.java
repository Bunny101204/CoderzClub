package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.MutationIdempotencyRecord;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionOutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;

/**
 * Runs only when CODERZCLUB_REPLICA_SET=1 and a local single-node replica set
 * is listening on 127.0.0.1:27018. The default suite stays on standalone MongoDB.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CODERZCLUB_REPLICA_SET", matches = "1")
class ReplicaSetIdempotencyIT {
    @DynamicPropertySource
    static void replicaSet(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () ->
            "mongodb://127.0.0.1:27018/coderzclub_rs?replicaSet=rs0&retryWrites=true&directConnection=false");
    }

    @SpyBean
    private MutationIdempotencyService idempotency;
    @Autowired
    private SubmissionJobService jobService;
    @Autowired
    private MongoTemplate mongoTemplate;
    @SpyBean
    private SubmissionOutboxService outbox;

    private final String userId = "rs-" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        reset(outbox, idempotency);
        List<String> eventIds = new ArrayList<>();
        for (SubmissionJob job : mongoTemplate.find(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class)) {
            eventIds.add(job.getId());
        }
        if (!eventIds.isEmpty()) {
            mongoTemplate.remove(Query.query(Criteria.where("aggregateId").in(eventIds)), SubmissionOutboxEvent.class);
        }
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), MutationIdempotencyRecord.class);
    }

    @Test
    void helloAsFirstTransactionCommandReproducesCode263() {
        var manager = new org.springframework.data.mongodb.MongoTransactionManager(mongoTemplate.getMongoDatabaseFactory());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        RuntimeException failure = assertThrows(RuntimeException.class,
            () -> transaction.execute(status -> mongoTemplate.executeCommand(new org.bson.Document("hello", 1))));
        Throwable cause = failure;
        while (cause != null && !(cause instanceof com.mongodb.MongoException)) cause = cause.getCause();
        assertTrue(cause instanceof com.mongodb.MongoException, failure.toString());
        assertEquals(263, ((com.mongodb.MongoException) cause).getCode());
    }

    @Test
    void committedFirstKeyAllowsALegitimateSequentialSubmissionWithAnotherKey() {
        assertTrue(idempotency.begin(userId, "submission-job", "sequential01", "sequential").owned());
        SubmissionJob first = jobService.createJob(userId, "sequential", "print(1)", "Python", 71, "v1", 0,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", "sequential01");
        // Simulate a lost completion response: begin resolves the durable job itself.
        assertTrue(idempotency.begin(userId, "submission-job", "sequential02", "sequential").owned());
        SubmissionJob second = jobService.createJob(userId, "sequential", "print(2)", "Python", 71, "v1", 0,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", "sequential02");
        org.junit.jupiter.api.Assertions.assertNotEquals(first.getId(), second.getId());
        assertEquals(2, mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class));
        assertEquals(first.getId(), idempotency.begin(userId, "submission-job", "sequential01", "sequential").resourceId());
        assertEquals(1, events(first.getId()));
        assertEquals(1, events(second.getId()));
    }

    @Test
    void coldUnkeyedAdmissionDoesNotStartTransactionWithHello() {
        ReflectionTestUtils.setField(jobService, "replicaSet", null);
        SubmissionJob job = jobService.createJob(userId, "cold", "print(1)", "Python", 71,
            "v1", 0, ExecutionMode.STANDARD_PER_CASE);
        assertEquals(1, events(job.getId()));
    }

    @Test
    void outboxFailureRollsBackJobAndClaimRenewalThenSameKeyCanRetry() {
        String key = "rollback001";
        IdempotencyClaim claim = idempotency.begin(userId, "submission-job", key, "rollback");
        var before = mongoTemplate.findById(claim.recordId(), MutationIdempotencyRecord.class).getCreatedAt();
        java.util.concurrent.atomic.AtomicReference<String> rolledBackJob = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(call -> {
            rolledBackJob.set(call.getArgument(0));
            call.callRealMethod();
            throw new IllegalStateException("outbox failed after its write");
        }).when(outbox).createJobEvent(anyString());
        assertThrows(IllegalStateException.class, () -> jobService.createJob(
            userId, "rollback", "print(1)", "Python", 71, "v1", 0,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", key));
        assertEquals(0, mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class));
        assertEquals(0, events(rolledBackJob.get()));
        assertEquals(before, mongoTemplate.findById(claim.recordId(), MutationIdempotencyRecord.class).getCreatedAt());
        reset(outbox);
        SubmissionJob retried = jobService.createJob(userId, "rollback", "print(1)", "Python", 71, "v1", 0,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", key);
        assertEquals(1, events(retried.getId()));
        assertEquals(retried.getId(), idempotency.begin(userId, "submission-job", key, "rollback").resourceId());
    }

    @Test
    void transientFailureAfterOutboxWriteRetriesTheEntireTransaction() {
        String key = "transient001";
        assertTrue(idempotency.begin(userId, "submission-job", key, "transient").owned());
        List<String> attemptedJobs = new ArrayList<>();
        doAnswer(call -> {
            attemptedJobs.add(call.getArgument(0));
            Object event = call.callRealMethod();
            if (attemptedJobs.size() == 1) {
                var conflict = new com.mongodb.MongoException(112, "injected transient conflict after persistence");
                conflict.addLabel(com.mongodb.MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                throw conflict;
            }
            return event;
        }).when(outbox).createJobEvent(anyString());
        SubmissionJob job = jobService.createJob(userId, "transient", "print(1)", "Python", 71, "v1", 0,
            ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", key);
        assertEquals(2, attemptedJobs.size());
        assertEquals(0, events(attemptedJobs.get(0)));
        assertEquals(job.getId(), attemptedJobs.get(1));
        assertEquals(1, events(job.getId()));
        assertEquals(1, mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class));
    }

    @Test
    void concurrentUniqueIndexConflictKeepsOneJobAndOneOutboxEvent() throws Exception {
        String key = "attemptrs01";
        assertTrue(idempotency.begin(userId, "submission-job", key, "problem-rs").owned());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return jobService.createJob(
                            userId, "problem-rs", "print(1)", "Python", 71, "v1", 0,
                            ExecutionMode.STANDARD_PER_CASE, null, null, null, null,
                            "submission-job", key
                        ).getId();
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<String> future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }
            List<SubmissionJob> jobs = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId).and("idempotencyKey").is(key)),
                SubmissionJob.class
            );
            assertEquals(1, jobs.size());
            assertEquals(Set.of(jobs.get(0).getId()), ids);
            assertEquals(1, events(jobs.get(0).getId()));

            SubmissionJob retried = jobService.createJob(
                userId, "problem-rs", "print(1)", "Python", 71, "v1", 0,
                ExecutionMode.STANDARD_PER_CASE, null, null, null, null,
                "submission-job", key
            );
            assertEquals(jobs.get(0).getId(), retried.getId());
            assertEquals(1, events(jobs.get(0).getId()));

            IdempotencyClaim replay = idempotency.begin(userId, "submission-job", key, "problem-rs");
            assertTrue(replay.replay());
            assertEquals(jobs.get(0).getId(), replay.resourceId());
            assertEquals(1, events(jobs.get(0).getId()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void staleClaimTakeoverFencesTheOriginalTransaction() throws Exception {
        IdempotencyClaim claim = idempotency.begin(userId, "submission-job", "attemptcommit", "problem-commit");
        assertTrue(claim.owned());
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(claim.recordId())),
            new org.springframework.data.mongodb.core.query.Update()
                .set("createdAt", java.util.Date.from(java.time.Instant.now().minusSeconds(46))),
            MutationIdempotencyRecord.class
        );
        // Pause after the original transaction's first read, before claim renewal.
        // A start-only barrier permits the original to commit before begin() reads it;
        // that is two legitimate sequential keys, not a stale-owner race.
        CountDownLatch originalRead = new CountDownLatch(1);
        CountDownLatch takeover = new CountDownLatch(1);
        doAnswer(call -> {
            originalRead.countDown();
            assertTrue(takeover.await(10, TimeUnit.SECONDS));
            return call.callRealMethod();
        }).when(idempotency).prepareCommit(eq(userId), eq("submission-job"), eq("attemptcommit"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> original = pool.submit(() -> {
                var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> jobService.createJob(userId, "problem-commit", "print(1)", "Python", 71, "v1", 0,
                        ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", "attemptcommit"));
                assertEquals(409, error.getStatusCode().value());
                return "fenced";
            });
            Future<String> other = pool.submit(() -> {
                assertTrue(originalRead.await(10, TimeUnit.SECONDS));
                try {
                    IdempotencyClaim second = idempotency.begin(userId, "submission-job", "attemptother1", "problem-commit");
                    assertTrue(second.owned());
                } finally {
                    takeover.countDown();
                }
                return jobService.createJob(userId, "problem-commit", "print(2)", "Python", 71, "v1", 0,
                    ExecutionMode.STANDARD_PER_CASE, null, null, null, null, "submission-job", "attemptother1").getId();
            });
            assertEquals("fenced", original.get(30, TimeUnit.SECONDS));
            String winner = other.get(30, TimeUnit.SECONDS);
            List<SubmissionJob> jobs = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId).and("problemId").is("problem-commit")), SubmissionJob.class);
            assertEquals(1, jobs.size());
            assertEquals(winner, jobs.get(0).getId());
            assertEquals(1, events(winner));
        } finally {
            takeover.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void ensureIndexesIsIdempotentAndAConflictingDefinitionStopsStartup() {
        idempotency.ensureIndexes();
        idempotency.ensureIndexes();
        List<String> names = mongoTemplate.indexOps(MutationIdempotencyRecord.class).getIndexInfo()
            .stream().map(IndexInfo::getName).toList();
        assertTrue(names.contains(MutationIdempotencyService.OPERATION_INDEX));
        assertTrue(names.contains(MutationIdempotencyService.OPEN_SUBJECT_INDEX));
        assertTrue(names.contains(MutationIdempotencyService.TTL_INDEX));
        assertTrue(mongoTemplate.indexOps(SubmissionJob.class).getIndexInfo().stream()
            .map(IndexInfo::getName).toList().contains(MutationIdempotencyService.JOB_INDEX));

        mongoTemplate.indexOps(MutationIdempotencyRecord.class).dropIndex(MutationIdempotencyService.OPERATION_INDEX);
        mongoTemplate.indexOps(MutationIdempotencyRecord.class).ensureIndex(
            new Index().on("scope", Sort.Direction.ASC).named(MutationIdempotencyService.OPERATION_INDEX));
        assertThrows(IllegalStateException.class, idempotency::ensureIndexes);
        mongoTemplate.indexOps(MutationIdempotencyRecord.class).dropIndex(MutationIdempotencyService.OPERATION_INDEX);
        idempotency.ensureIndexes();
    }

    private long events(String jobId) {
        return mongoTemplate.count(
            Query.query(Criteria.where("aggregateId").is(jobId).and("eventType").is("SubmissionJobCreated")),
            SubmissionOutboxEvent.class
        );
    }
}
