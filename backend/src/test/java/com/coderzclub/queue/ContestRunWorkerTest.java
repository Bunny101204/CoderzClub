package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.service.ContestProblemSnapshotService;
import com.coderzclub.service.ContestRunLeaseService;
import com.coderzclub.service.ContestRunMetrics;
import com.coderzclub.service.ExecutionOutcome;
import com.coderzclub.service.Judge0ExecutionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRunWorkerTest {
    private static final Instant T0 = Instant.parse("2026-06-01T14:00:00Z");

    private ContestRunConsumer consumer;
    private MongoTemplate mongo;
    private ContestRunLeaseService leaseService;
    private ContestProblemSnapshotService snapshotService;
    private Judge0ExecutionService executionService;
    private ContestRunWorker worker;

    @BeforeEach
    void setUp() {
        consumer = mock(ContestRunConsumer.class);
        mongo = mock(MongoTemplate.class);
        leaseService = mock(ContestRunLeaseService.class);
        snapshotService = mock(ContestProblemSnapshotService.class);
        executionService = mock(Judge0ExecutionService.class);
        worker = workerWith(Clock.systemUTC());
    }

    private ContestRunWorker workerWith(Clock clock) {
        ContestRunProperties properties = new ContestRunProperties();
        properties.getQueue().setAdmissionMaxOldestAgeSeconds(90);
        return new ContestRunWorker(
            consumer,
            properties,
            clock,
            mongo,
            leaseService,
            snapshotService,
            executionService,
            new ContestRunMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void executesPublicSnapshotTestsOnly() {
        ContestRunJob job = queuedJob();
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.of(running(job)));
        when(leaseService.isOwned(eq("run-1"), anyString())).thenReturn(true);
        when(leaseService.completeIfOwned(eq("run-1"), anyString(), anyList(), eq(1))).thenReturn(true);
        ContestProblemSnapshot snapshot = snapshotA();
        when(snapshotService.findById("snap-1")).thenReturn(snapshot);
        ExecutionOutcome outcome = new ExecutionOutcome();
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setInput("A");
        result.setExpectedOutput("A");
        result.setActualOutput("A");
        result.setPassed(true);
        outcome.setResults(List.of(result));
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any())).thenReturn(outcome);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        ArgumentCaptor<List<SubmissionJob.TestCase>> publicCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<SubmissionJob.TestCase>> hiddenCaptor = ArgumentCaptor.forClass(List.class);
        verify(executionService).executeTestCases(
            eq("print(1)"), eq(71), publicCaptor.capture(), hiddenCaptor.capture(),
            eq(ExecutionMode.STANDARD_PER_CASE), eq("snap-v"));
        assertEquals(1, publicCaptor.getValue().size());
        assertEquals("A", publicCaptor.getValue().get(0).getInput());
        assertTrue(hiddenCaptor.getValue().isEmpty());
        verify(snapshotService, never()).findById(eq(null));
    }

    @Test
    void missingSnapshotFailsWithoutExecuting() {
        ContestRunJob job = queuedJob();
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.of(running(job)));
        when(snapshotService.findById("snap-1")).thenReturn(null);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService).failIfOwned(eq("run-1"), anyString(), eq("SNAPSHOT_MISSING"), anyString());
    }

    @Test
    void terminalRedeliveryIsAckNoOp() {
        ContestRunJob job = queuedJob();
        job.setStatus(ContestRunJob.Status.COMPLETED);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));
        verify(leaseService, never()).claim(any(), any());
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
    }

    @Test
    void duplicateClaimIsAckWithoutSecondResults() {
        ContestRunJob job = queuedJob();
        job.setStatus(ContestRunJob.Status.RUNNING);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.empty());

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService, never()).completeIfOwned(any(), any(), any(), anyInt());
    }

    @Test
    void staleQueuedRunExpiresWithoutJudge0() {
        MutableClock clock = new MutableClock(T0.plusSeconds(91));
        worker = workerWith(clock);
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.failIfQueueExpired("run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE))
            .thenReturn(true);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService, never()).claim(any(), any());
        verify(leaseService).failIfQueueExpired(
            "run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE);
        assertEquals(Date.from(T0), job.getCreatedAt());
    }

    @Test
    void freshQueuedRunExecutesSnapshot() {
        worker = workerWith(new MutableClock(T0.plusSeconds(30)));
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.of(running(job)));
        when(leaseService.isOwned(eq("run-1"), anyString())).thenReturn(true);
        when(leaseService.completeIfOwned(eq("run-1"), anyString(), anyList(), eq(1))).thenReturn(true);
        when(snapshotService.findById("snap-1")).thenReturn(snapshotA());
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any())).thenReturn(passedOutcome());

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        verify(leaseService, never()).failIfQueueExpired(any(), any(), any());
        verify(leaseService).claim(eq("run-1"), anyString());
        verify(executionService).executeTestCases(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryAfterMaxQueueAgeExpiresWithoutAnotherExecution() {
        MutableClock clock = new MutableClock(T0.plusSeconds(30));
        worker = workerWith(clock);
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        job.setMaxAttempts(2);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.of(running(job)));
        when(snapshotService.findById("snap-1")).thenReturn(snapshotA());
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("temporary"));
        when(leaseService.releaseToQueuedIfOwned(eq("run-1"), anyString())).thenReturn(true);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.RETRY, worker.handleMessage("run-1"));
        assertEquals(Date.from(T0), job.getCreatedAt());
        verify(leaseService).releaseToQueuedIfOwned(eq("run-1"), anyString());

        clock.set(T0.plusSeconds(91));
        job.setStatus(ContestRunJob.Status.QUEUED);
        when(leaseService.failIfQueueExpired("run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE))
            .thenReturn(true);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        verify(executionService, times(1)).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService, times(1)).claim(any(), any());
        verify(leaseService).failIfQueueExpired(
            "run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE);
        assertEquals(Date.from(T0), job.getCreatedAt());
    }

    @Test
    void runThatStartedFreshCompletesAfterQueueAgeElapses() {
        MutableClock clock = new MutableClock(T0.plusSeconds(30));
        worker = workerWith(clock);
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.claim(eq("run-1"), anyString())).thenReturn(Optional.of(running(job)));
        when(leaseService.isOwned(eq("run-1"), anyString())).thenReturn(true);
        when(leaseService.completeIfOwned(eq("run-1"), anyString(), anyList(), eq(1))).thenReturn(true);
        when(snapshotService.findById("snap-1")).thenReturn(snapshotA());
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            clock.set(T0.plusSeconds(120));
            return passedOutcome();
        });

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        verify(leaseService, never()).failIfQueueExpired(any(), any(), any());
        verify(executionService).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService).completeIfOwned(eq("run-1"), anyString(), anyList(), eq(1));
    }

    @Test
    void laterDeliveryDoesNotFailAnActiveRun() {
        worker = workerWith(new MutableClock(T0.plusSeconds(91)));
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        job.setStatus(ContestRunJob.Status.RUNNING);
        when(mongo.findById("run-1", ContestRunJob.class)).thenReturn(job);
        when(leaseService.failIfQueueExpired(eq("run-1"), eq(ContestRunWorker.RUN_EXPIRED), anyString()))
            .thenReturn(false);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.handleMessage("run-1"));

        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(leaseService, never()).claim(any(), any());
        verify(leaseService, never()).failIfOwned(any(), any(), any(), any());
    }

    @Test
    void expiredLeaseRecoveryExpiresStaleRunWithoutExecution() {
        worker = workerWith(new MutableClock(T0.plusSeconds(91)));
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        job.setStatus(ContestRunJob.Status.RUNNING);
        when(leaseService.findExpiredRunning(25)).thenReturn(List.of(job));
        when(leaseService.failIfQueueExpired("run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE))
            .thenReturn(true);

        worker.recoverExpiredLeases();

        verify(leaseService).failIfQueueExpired(
            "run-1", ContestRunWorker.RUN_EXPIRED, ContestRunWorker.RUN_EXPIRED_MESSAGE);
        verify(leaseService, never()).failExpired(any(), any(), any());
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
    }

    @Test
    void expiredLeaseRecoveryKeepsFreshTimeoutBehavior() {
        worker = workerWith(new MutableClock(T0.plusSeconds(30)));
        ContestRunJob job = queuedJob();
        job.setCreatedAt(Date.from(T0));
        job.setStatus(ContestRunJob.Status.RUNNING);
        job.setAttemptCount(2);
        job.setMaxAttempts(2);
        when(leaseService.findExpiredRunning(25)).thenReturn(List.of(job));

        worker.recoverExpiredLeases();

        verify(leaseService, never()).failIfQueueExpired(any(), any(), any());
        verify(leaseService).failExpired(eq("run-1"), eq("LEASE_EXPIRED"), anyString());
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
    }

    @Test
    void workerDoesNotDependOnScoringOrLiveProblem() {
        java.util.Arrays.stream(ContestRunWorker.class.getDeclaredFields()).forEach(field -> {
            String name = field.getType().getName();
            assertTrue(!name.contains("ProblemRepository"), name);
            assertTrue(!name.contains("SubmissionService"), name);
            assertTrue(!name.contains("ContestScoringService"), name);
            assertTrue(!name.contains("ContestLeaderboard"), name);
            assertTrue(!name.contains("ContestAccessService"), name);
        });
    }

    private static ContestRunJob queuedJob() {
        ContestRunJob job = new ContestRunJob();
        job.setId("run-1");
        job.setCode("print(1)");
        job.setLanguageId(71);
        job.setStatus(ContestRunJob.Status.QUEUED);
        job.setContestProblemSnapshotId("snap-1");
        job.setAttemptCount(0);
        job.setMaxAttempts(2);
        return job;
    }

    private static ContestRunJob running(ContestRunJob job) {
        job.setStatus(ContestRunJob.Status.RUNNING);
        job.setAttemptCount(1);
        return job;
    }

    private static ExecutionOutcome passedOutcome() {
        ExecutionOutcome outcome = new ExecutionOutcome();
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setInput("A");
        result.setExpectedOutput("A");
        result.setActualOutput("A");
        result.setPassed(true);
        outcome.setResults(List.of(result));
        return outcome;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static ContestProblemSnapshot snapshotA() {
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setId("snap-1");
        snapshot.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        snapshot.setTestcaseVersion("snap-v");
        snapshot.setPublicTestCases(List.of(new TestCase("A", "A", null)));
        snapshot.setHiddenTestCases(List.of(new TestCase("HIDDEN", "HIDDEN", null)));
        return snapshot;
    }
}
