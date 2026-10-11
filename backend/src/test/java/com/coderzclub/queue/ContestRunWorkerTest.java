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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRunWorkerTest {
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
        worker = new ContestRunWorker(
            consumer,
            new ContestRunProperties(),
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
    void workerDoesNotDependOnScoringOrLiveProblem() {
        java.util.Arrays.stream(ContestRunWorker.class.getDeclaredFields()).forEach(field -> {
            String name = field.getType().getName();
            assertTrue(!name.contains("ProblemRepository"), name);
            assertTrue(!name.contains("SubmissionService"), name);
            assertTrue(!name.contains("ContestScoringService"), name);
            assertTrue(!name.contains("ContestLeaderboard"), name);
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
