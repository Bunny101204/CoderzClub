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

    @Autowired
    private MutationIdempotencyService idempotency;
    @Autowired
    private SubmissionJobService jobService;
    @Autowired
    private MongoTemplate mongoTemplate;

    private final String userId = "rs-" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
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
                    try {
                        return jobService.createJob(
                            userId, "problem-rs", "print(1)", "Python", 71, "v1", 0,
                            ExecutionMode.STANDARD_PER_CASE, null, null, null, null,
                            "submission-job", key
                        ).getId();
                    } catch (RuntimeException ex) {
                        return "error";
                    }
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<String> future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }
            ids.remove("error");
            List<SubmissionJob> jobs = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId).and("idempotencyKey").is(key)),
                SubmissionJob.class
            );
            assertEquals(1, jobs.size());
            assertTrue(ids.isEmpty() || ids.equals(Set.of(jobs.get(0).getId())));
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
    void staleClaimRecoveryCannotInsertASecondJobForTheCommittingKey() throws Exception {
        IdempotencyClaim claim = idempotency.begin(userId, "submission-job", "attemptcommit", "problem-commit");
        assertTrue(claim.owned());
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(claim.recordId())),
            new org.springframework.data.mongodb.core.query.Update()
                .set("createdAt", java.util.Date.from(java.time.Instant.now().minusSeconds(46))),
            MutationIdempotencyRecord.class
        );
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<String> original = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return jobService.createJob(
                        userId, "problem-commit", "print(1)", "Python", 71, "v1", 0,
                        ExecutionMode.STANDARD_PER_CASE, null, null, null, null,
                        "submission-job", "attemptcommit"
                    ).getId();
                } catch (RuntimeException ex) {
                    return "error";
                }
            });
            Future<String> other = pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                IdempotencyClaim second = idempotency.begin(userId, "submission-job", "attemptother1", "problem-commit");
                if (!second.owned()) {
                    return "blocked";
                }
                try {
                    return jobService.createJob(
                        userId, "problem-commit", "print(2)", "Python", 71, "v1", 0,
                        ExecutionMode.STANDARD_PER_CASE, null, null, null, null,
                        "submission-job", "attemptother1"
                    ).getId();
                } catch (RuntimeException ex) {
                    return "error";
                }
            });
            start.countDown();
            original.get(30, TimeUnit.SECONDS);
            other.get(30, TimeUnit.SECONDS);
            long jobs = mongoTemplate.count(
                Query.query(Criteria.where("userId").is(userId).and("problemId").is("problem-commit")),
                SubmissionJob.class
            );
            assertEquals(1, jobs);
        } finally {
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
