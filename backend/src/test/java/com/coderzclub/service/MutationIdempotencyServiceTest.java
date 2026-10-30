package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.MutationIdempotencyRecord;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionOutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MutationIdempotencyServiceTest {
    @Autowired
    private MutationIdempotencyService service;
    @Autowired
    private SubmissionJobService jobService;
    @Autowired
    private MongoTemplate mongoTemplate;

    private final String userId = "idem-" + UUID.randomUUID();

    @AfterEach
    void cleanup() {
        List<String> eventIds = new ArrayList<>();
        eventIds.add("job-original");
        eventIds.add("job-retry");
        eventIds.add("job-other");
        for (SubmissionJob job : mongoTemplate.find(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class)) {
            eventIds.add(job.getId());
        }
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").in(eventIds)), SubmissionOutboxEvent.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), SubmissionJob.class);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(userId)), MutationIdempotencyRecord.class);
    }

    @Test
    void sameKeyConcurrentlyOwnsOneRecordAndReplaysTheOriginalJob() throws Exception {
        String key = "attempt01";
        List<IdempotencyClaim> claims = race(() -> service.begin(userId, "submission-job", key, "problem-1"));
        long owned = claims.stream().filter(IdempotencyClaim::owned).count();
        long waiting = claims.stream().filter(claim -> claim.inProgress() || claim.replay()).count();
        assertEquals(1, owned);
        assertEquals(1, waiting);

        IdempotencyClaim winner = claims.stream().filter(IdempotencyClaim::owned).findFirst().orElseThrow();
        service.complete(winner, "job-original");

        IdempotencyClaim replay = service.begin(userId, "submission-job", key, "problem-1");
        assertTrue(replay.replay());
        assertEquals("job-original", replay.resourceId());

        IdempotencyClaim otherKey = service.begin(userId, "submission-job", "attempt02", "problem-1");
        assertTrue(otherKey.owned());
        service.abandon(otherKey);
        assertNotEquals("job-original", otherKey.recordId());
    }

    @Test
    void overlappingDifferentKeysForOneProblemDoNotBothOwnTheAttempt() throws Exception {
        List<IdempotencyClaim> claims = raceTwo(
            () -> service.begin(userId, "submission-job", "attempt03", "problem-2"),
            () -> service.begin(userId, "submission-job", "attempt04", "problem-2")
        );
        long owned = claims.stream().filter(IdempotencyClaim::owned).count();
        assertEquals(1, owned);
        claims.stream().filter(IdempotencyClaim::owned).forEach(service::abandon);
    }

    @Test
    void abandonedAttemptCanBeRetriedAndADifferentProblemIsIndependent() {
        IdempotencyClaim failed = service.begin(userId, "submission-job", "attempt05", "problem-3");
        assertTrue(failed.owned());
        service.abandon(failed);

        IdempotencyClaim retry = service.begin(userId, "submission-job", "attempt05", "problem-3");
        assertTrue(retry.owned());
        service.complete(retry, "job-retry");

        IdempotencyClaim otherProblem = service.begin(userId, "submission-job", "attempt06", "problem-4");
        assertTrue(otherProblem.owned());
        service.complete(otherProblem, "job-other");

        IdempotencyClaim sameCodeNewAttempt = service.begin(userId, "submission-job", "attempt07", "problem-3");
        assertTrue(sameCodeNewAttempt.owned());
        service.abandon(sameCodeNewAttempt);
    }

    @Test
    void operationKeyIsNotDisclosedAcrossUsersScopesOrProblems() {
        IdempotencyClaim owner = service.begin(userId, "submission-job", "attemptowner", "problem-scope");
        assertTrue(owner.owned());
        SubmissionJob job = saveJob("problem-scope", "attemptowner");
        service.complete(owner, job.getId());

        String otherUser = userId + "-other";
        IdempotencyClaim guessedKey = service.begin(otherUser, "submission-job", "attemptowner", "problem-scope");
        assertTrue(guessedKey.owned());
        assertTrue(guessedKey.operationKey() == null);
        service.abandon(guessedKey);

        IdempotencyClaim otherProblem = service.begin(userId, "submission-job", "attemptprob2", "problem-other");
        assertTrue(otherProblem.owned());
        assertTrue(otherProblem.operationKey() == null);
        service.abandon(otherProblem);

        IdempotencyClaim otherScope = service.begin(userId, "contest-submission", "attemptcontest", "problem-scope");
        assertTrue(otherScope.owned());
        service.abandon(otherScope);

        IdempotencyClaim replay = service.begin(userId, "submission-job", "attemptowner", "problem-scope");
        assertTrue(replay.replay());
        assertEquals(job.getId(), replay.resourceId());
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(otherUser)), MutationIdempotencyRecord.class);
    }

    @Test
    void openClaimKeyIsVisibleOnlyToTheSameUserAndSubject() {
        IdempotencyClaim owner = service.begin(userId, "submission-job", "attemptopen1", "problem-open");
        assertTrue(owner.owned());

        String otherUser = userId + "-intruder";
        IdempotencyClaim intruder = service.begin(otherUser, "submission-job", "attemptopen2", "problem-open");
        assertTrue(intruder.owned());
        assertTrue(intruder.operationKey() == null);
        service.abandon(intruder);

        IdempotencyClaim sameSubject = service.begin(userId, "submission-job", "attemptopen3", "problem-open");
        assertTrue(sameSubject.inProgress());
        assertEquals("attemptopen1", sameSubject.operationKey());

        IdempotencyClaim otherScope = service.begin(userId, "contest-submission", "attemptopen4", "c1:problem-open");
        assertTrue(otherScope.owned());
        assertTrue(otherScope.operationKey() == null);
        service.abandon(otherScope);
        service.abandon(owner);
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(otherUser)), MutationIdempotencyRecord.class);
    }

    @Test
    void renewKeepsAStaleClaimFromBeingReplacedWhileTheJobIsSaved() {
        IdempotencyClaim claim = service.begin(userId, "submission-job", "attemptrenew", "problem-renew");
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(claim.recordId())),
            new Update().set("createdAt", Date.from(Instant.now().minus(Duration.ofSeconds(46)))),
            MutationIdempotencyRecord.class
        );
        assertTrue(service.renew(userId, "submission-job", "attemptrenew"));
        IdempotencyClaim intruder = service.begin(userId, "submission-job", "attemptintrude", "problem-renew");
        assertTrue(intruder.inProgress());
        assertEquals("attemptrenew", intruder.operationKey());
        SubmissionJob job = saveJob("problem-renew", "attemptrenew");
        assertEquals(1, mongoTemplate.count(Query.query(Criteria.where("userId").is(userId).and("idempotencyKey").is("attemptrenew")), SubmissionJob.class));
        IdempotencyClaim replay = service.begin(userId, "submission-job", "attemptrenew", "problem-renew");
        assertTrue(replay.replay());
        assertEquals(job.getId(), replay.resourceId());
        assertEquals(1, events(job.getId()));
    }

    @Test
    void deletedClaimStillReplaysTheSavedJob() {
        SubmissionJob job = saveJob("problem-orphan-claim", "attemptorphan");
        IdempotencyClaim replay = service.begin(userId, "submission-job", "attemptorphan", "problem-orphan-claim");
        assertTrue(replay.replay());
        assertEquals(job.getId(), replay.resourceId());
        assertEquals(1, mongoTemplate.count(
            Query.query(Criteria.where("userId").is(userId).and("idempotencyKey").is("attemptorphan")),
            SubmissionJob.class));
        assertEquals(1, events(job.getId()));
    }

    @Test
    void savedJobWithAnOpenClaimIsRecoveredAndDoesNotPublishASecondEvent() {
        IdempotencyClaim claim = service.begin(userId, "submission-job", "attemptsave1", "problem-saved");
        assertTrue(claim.owned());
        SubmissionJob job = saveJob("problem-saved", "attemptsave1");
        assertFalse(service.stillHeld(claim));

        IdempotencyClaim recovered = service.begin(userId, "submission-job", "attemptsave1", "problem-saved");
        assertTrue(recovered.replay());
        assertEquals(job.getId(), recovered.resourceId());
        assertEquals(1, events(job.getId()));

        IdempotencyClaim again = service.begin(userId, "submission-job", "attemptsave1", "problem-saved");
        assertTrue(again.replay());
        assertEquals(job.getId(), again.resourceId());
        assertEquals(1, events(job.getId()));

        MutationIdempotencyRecord linked = mongoTemplate.findOne(
            Query.query(Criteria.where("userId").is(userId).and("operationKey").is("attemptsave1")),
            MutationIdempotencyRecord.class
        );
        assertEquals(job.getId(), linked.getResourceId());
        assertFalse(linked.isOpen());
    }

    @Test
    void abandonAfterTheJobIsSavedLinksTheOriginalJobInsteadOfAllowingAnother() {
        IdempotencyClaim claim = service.begin(userId, "submission-job", "attemptsave2", "problem-abandon");
        SubmissionJob job = saveJob("problem-abandon", "attemptsave2");
        service.abandon(claim);

        IdempotencyClaim recovered = service.begin(userId, "submission-job", "attemptsave2", "problem-abandon");
        assertTrue(recovered.replay());
        assertEquals(job.getId(), recovered.resourceId());
        assertEquals(1, events(job.getId()));
    }

    @Test
    void openClaimWithoutAJobBlocksOnlyForTheLeaseThenANewAttemptIsAllowed() {
        IdempotencyClaim claim = service.begin(userId, "submission-job", "attemptfresh", "problem-lease");
        assertTrue(claim.owned());
        assertTrue(service.stillHeld(claim));

        IdempotencyClaim same = service.begin(userId, "submission-job", "attemptfresh", "problem-lease");
        assertTrue(same.inProgress());
        assertEquals("attemptfresh", same.operationKey());

        IdempotencyClaim other = service.begin(userId, "submission-job", "attemptother", "problem-lease");
        assertTrue(other.inProgress());
        assertEquals("attemptfresh", other.operationKey());
        assertEquals(0, mongoTemplate.count(Query.query(Criteria.where("userId").is(userId).and("problemId").is("problem-lease")), SubmissionJob.class));

        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(claim.recordId())),
            new Update().set("createdAt", Date.from(Instant.now().minus(Duration.ofSeconds(46)))),
            MutationIdempotencyRecord.class
        );

        IdempotencyClaim retried = service.begin(userId, "submission-job", "attemptafter", "problem-lease");
        assertTrue(retried.owned());
        service.abandon(retried);

        IdempotencyClaim originalAgain = service.begin(userId, "submission-job", "attemptfresh", "problem-lease");
        assertTrue(originalAgain.owned());
        service.abandon(originalAgain);
    }

    @Test
    void jobRowWithoutAnOutboxOrResourceLinkIsReconciledOnce() {
        IdempotencyClaim claim = service.begin(userId, "submission-job", "attemptdirect", "problem-direct");
        assertTrue(claim.owned());
        SubmissionJob job = new SubmissionJob();
        job.setUserId(userId);
        job.setProblemId("problem-direct");
        job.setCode("print(1)");
        job.setLanguage("Python");
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setIdempotencyScope("submission-job");
        job.setIdempotencyKey("attemptdirect");
        job = mongoTemplate.insert(job);

        IdempotencyClaim recovered = service.begin(userId, "submission-job", "attemptdirect", "problem-direct");
        assertTrue(recovered.replay());
        assertEquals(job.getId(), recovered.resourceId());
        assertEquals(1, events(job.getId()));
        assertFalse(service.stillHeld(claim));

        IdempotencyClaim refresh = service.begin(userId, "submission-job", "attemptdirect", "problem-direct");
        assertTrue(refresh.replay());
        assertEquals(1, events(job.getId()));
    }

    @Test
    void missingQueueEventIsRestoredOnceAndASettledJobIsNotRequeued() {
        SubmissionJob job = saveJob("problem-queue", "attemptqueue");
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").is(job.getId())), SubmissionOutboxEvent.class);
        assertEquals(0, events(job.getId()));

        jobService.recoverQueuedPublish(job);
        assertEquals(1, events(job.getId()));
        jobService.recoverQueuedPublish(job);
        assertEquals(1, events(job.getId()));

        job.setStatus(SubmissionJob.JobStatus.COMPLETED);
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").is(job.getId())), SubmissionOutboxEvent.class);
        jobService.recoverQueuedPublish(job);
        assertEquals(0, events(job.getId()));

        SubmissionJob plain = savePlainJob();
        mongoTemplate.remove(Query.query(Criteria.where("aggregateId").is(plain.getId())), SubmissionOutboxEvent.class);
        jobService.recoverQueuedPublish(plain);
        assertEquals(0, events(plain.getId()));
    }

    @Test
    void duplicateKeyedCreateReturnsTheOriginalJobAndOneQueueEvent() {
        SubmissionJob first = saveJob("problem-dup", "attemptdup1");
        SubmissionJob second = saveJob("problem-dup", "attemptdup1");
        assertEquals(first.getId(), second.getId());
        assertEquals(1, events(first.getId()));

        IdempotencyClaim next = service.begin(userId, "submission-job", "attemptdup2", "problem-dup");
        assertTrue(next.owned());
        SubmissionJob separate = saveJob("problem-dup", "attemptdup2");
        service.complete(next, separate.getId());
        assertNotEquals(first.getId(), separate.getId());
        assertEquals(1, events(separate.getId()));
        assertEquals(1, events(first.getId()));
    }

    @Test
    void indexesAreEnsuredIdempotentlyAtStartup() {
        service.ensureIndexes();
        service.ensureIndexes();
        List<String> claimIndexes = mongoTemplate.indexOps(MutationIdempotencyRecord.class).getIndexInfo()
            .stream().map(IndexInfo::getName).toList();
        List<String> jobIndexes = mongoTemplate.indexOps(SubmissionJob.class).getIndexInfo()
            .stream().map(IndexInfo::getName).toList();
        assertTrue(claimIndexes.contains("idempotency_operation_unique"));
        assertTrue(claimIndexes.contains("idempotency_open_subject_unique"));
        assertTrue(claimIndexes.contains("mutation_idempotency_ttl"));
        assertTrue(jobIndexes.contains("submission_job_idempotency_unique"));
    }

    private SubmissionJob saveJob(String problemId, String operationKey) {
        return invokeCreate(problemId, operationKey);
    }

    private SubmissionJob savePlainJob() {
        try {
            return rawJobs().createJob(
                userId, "problem-plain", "print(1)", "Python", 71, "v1", 0,
                ExecutionMode.STANDARD_PER_CASE, null
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SubmissionJob invokeCreate(String problemId, String operationKey) {
        try {
            return rawJobs().createJob(
                userId,
                problemId,
                "print(1)",
                "Python",
                71,
                "v1",
                0,
                ExecutionMode.STANDARD_PER_CASE,
                null,
                null,
                null,
                null,
                "submission-job",
                operationKey
            );
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private SubmissionJobService rawJobs() throws Exception {
        if (jobService instanceof org.springframework.aop.framework.Advised advised) {
            return (SubmissionJobService) advised.getTargetSource().getTarget();
        }
        return jobService;
    }

    private long events(String jobId) {
        return mongoTemplate.count(
            Query.query(Criteria.where("aggregateId").is(jobId).and("eventType").is("SubmissionJobCreated")),
            SubmissionOutboxEvent.class
        );
    }

    private List<IdempotencyClaim> race(Callable<IdempotencyClaim> call) throws Exception {
        return raceTwo(call, call);
    }

    private List<IdempotencyClaim> raceTwo(Callable<IdempotencyClaim> left, Callable<IdempotencyClaim> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<IdempotencyClaim> first = pool.submit(left);
            Future<IdempotencyClaim> second = pool.submit(right);
            List<IdempotencyClaim> claims = new ArrayList<>();
            claims.add(first.get());
            claims.add(second.get());
            return claims;
        } finally {
            pool.shutdownNow();
        }
    }
}
