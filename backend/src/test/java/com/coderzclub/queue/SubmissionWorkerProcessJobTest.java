package com.coderzclub.queue;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.service.ContestProblemSnapshotService;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.service.ExecutionOutcome;
import com.coderzclub.service.Judge0ExecutionService;
import com.coderzclub.service.OperationalMetrics;
import com.coderzclub.service.SubmissionJobEventService;
import com.coderzclub.service.SubmissionJobLeaseService;
import com.coderzclub.service.SubmissionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubmissionWorkerProcessJobTest {

    @Mock private SubmissionQueueConsumer consumer;
    @Mock private SubmissionJobRepository jobRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private ContestProblemSnapshotService snapshotService;
    @Mock private SubmissionTestResultRepository resultRepository;
    @Mock private SubmissionJobLeaseService leaseService;
    @Mock private Judge0ExecutionService executionService;
    @Mock private SubmissionService submissionService;
    @Mock private WorkerProperties workerProperties;
    @Mock private com.coderzclub.config.SubmissionQueueProperties queueProperties;
    @Mock private SubmissionJobEventService eventService;
    @InjectMocks private SubmissionWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "operationalMetrics", new OperationalMetrics(new SimpleMeterRegistry()));
        when(workerProperties.getLeaseDurationSeconds()).thenReturn(180L);
        when(workerProperties.getHeartbeatIntervalSeconds()).thenReturn(1L);
    }

    @Test
    void heartbeatRunsDuringBlockingProviderCall() throws Exception {
        CountDownLatch executing = new CountDownLatch(1);
        stubOwnedJob();
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            executing.countDown();
            Thread.sleep(1600);
            return acceptedOutcome();
        });
        when(leaseService.heartbeat(anyString(), anyString(), anyLong())).thenReturn(true);
        when(leaseService.isOwned(anyString(), anyString())).thenReturn(true);
        when(leaseService.persistJudgementIfOwned(anyString(), anyString(), any())).thenReturn(true);
        when(leaseService.completeIfOwned(anyString(), anyString(), any())).thenReturn(true);
        when(leaseService.updateProgressIfOwned(anyString(), anyString(), anyInt())).thenReturn(true);
        when(submissionService.createSubmissionFromJob(any())).thenReturn(Submission.builder().id("sub-1").build());

        worker.processJob("job-1");

        assertTrue(executing.await(0, TimeUnit.MILLISECONDS));
        verify(leaseService, atLeastOnce()).heartbeat(eq("job-1"), anyString(), eq(180L));
        verify(snapshotService, never()).findById(any());
        verify(problemRepository).findById("problem-1");
    }

    @Test
    void lostLeaseCannotCompleteAsOwnerAfterMaterialization() {
        stubOwnedJob();
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any()))
            .thenReturn(acceptedOutcome());
        when(leaseService.isOwned(anyString(), anyString())).thenReturn(true);
        when(leaseService.updateProgressIfOwned(anyString(), anyString(), anyInt())).thenReturn(true);
        when(leaseService.persistJudgementIfOwned(anyString(), anyString(), any())).thenReturn(true);
        when(submissionService.createSubmissionFromJob(any())).thenReturn(Submission.builder().id("sub-1").build());
        when(leaseService.completeIfOwned(anyString(), anyString(), any())).thenReturn(false);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.processJob("job-1"));

        verify(submissionService).createSubmissionFromJob(any());
        verify(eventService, never()).publish(any(), eq(SubmissionJob.JobStatus.COMPLETED));
        verify(leaseService).completeIfOwned(eq("job-1"), anyString(), any());
    }

    @Test
    void duplicateClaimIsAcknowledgedWithoutExecution() {
        when(leaseService.claimJob(eq("job-1"), anyString(), anyLong())).thenReturn(Optional.empty());
        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.processJob("job-1"));
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contestJobExecutesSnapshotTestsNotMutableProblem() {
        SubmissionJob job = stubOwnedJob();
        job.setContestId("contest-1");
        job.setContestProblemSnapshotId("snap-1");
        job.setContestSubmittedAt(java.time.Instant.parse("2026-06-01T11:59:59.900Z"));
        job.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        job.setTestcaseVersion("v1");
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setId("snap-1");
        snapshot.setProblemId("problem-1");
        snapshot.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        snapshot.setTestcaseVersion("v1");
        snapshot.setPublicTestCases(List.of(new TestCase("A-in", "A-out", null)));
        snapshot.setHiddenTestCases(List.of(new TestCase("A-hidden-in", "A-hidden-out", null)));
        when(snapshotService.findById("snap-1")).thenReturn(snapshot);
        Problem mutated = new Problem();
        mutated.setId("problem-1");
        mutated.setPublicTestCases(List.of(new TestCase("B-in", "B-out", null)));
        mutated.setHiddenTestCases(List.of());
        when(problemRepository.findById("problem-1")).thenReturn(Optional.of(mutated));
        when(executionService.executeTestCases(any(), any(), any(), any(), any(), any()))
            .thenReturn(acceptedOutcome());
        when(leaseService.isOwned(anyString(), anyString())).thenReturn(true);
        when(leaseService.updateProgressIfOwned(anyString(), anyString(), anyInt())).thenReturn(true);
        when(leaseService.persistJudgementIfOwned(anyString(), anyString(), any())).thenReturn(true);
        when(submissionService.createSubmissionFromJob(any())).thenReturn(
            Submission.builder().id("sub-1").contestId("contest-1").contestProblemSnapshotId("snap-1").build());
        when(leaseService.completeIfOwned(anyString(), anyString(), any())).thenReturn(true);

        assertEquals(SubmissionQueueConsumer.MessageDisposition.ACK, worker.processJob("job-1"));

        org.mockito.ArgumentCaptor<List<SubmissionJob.TestCase>> publicTests =
            org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<SubmissionJob.TestCase>> hiddenTests =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(executionService).executeTestCases(any(), any(), publicTests.capture(), hiddenTests.capture(),
            eq(ExecutionMode.STANDARD_PER_CASE), eq("v1"));
        assertEquals("A-in", publicTests.getValue().get(0).getInput());
        assertEquals("A-out", publicTests.getValue().get(0).getExpectedOutput());
        assertEquals("A-hidden-in", hiddenTests.getValue().get(0).getInput());
        verify(problemRepository, never()).findById("problem-1");
    }

    @Test
    void missingContestSnapshotDoesNotFallBackToCurrentProblem() {
        SubmissionJob job = stubOwnedJob();
        job.setContestId("contest-1");
        job.setContestProblemSnapshotId("snap-missing");
        job.setMaxAttempts(1);
        when(snapshotService.findById("snap-missing")).thenReturn(null);
        when(leaseService.retryIfOwned(anyString(), anyString(), anyString(), any())).thenReturn(true);

        worker.processJob("job-1");

        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(problemRepository, never()).findById(anyString());
    }

    private SubmissionJob stubOwnedJob() {
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setUserId("user-1");
        job.setProblemId("problem-1");
        job.setAttemptCount(1);
        job.setMaxAttempts(3);
        job.setTotalTests(1);
        when(leaseService.claimJob(eq("job-1"), anyString(), anyLong())).thenReturn(Optional.of(job));
        Problem problem = new Problem();
        problem.setId("problem-1");
        problem.setPublicTestCases(List.of(new TestCase("1", "1", null)));
        problem.setHiddenTestCases(List.of());
        when(problemRepository.findById("problem-1")).thenReturn(Optional.of(problem));
        when(leaseService.heartbeat(anyString(), anyString(), anyLong())).thenReturn(true);
        return job;
    }

    private static ExecutionOutcome acceptedOutcome() {
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setPassed(true);
        result.setActualOutput("1");
        ExecutionOutcome outcome = new ExecutionOutcome();
        outcome.setResults(List.of(result));
        outcome.setLogicalTestcases(1);
        outcome.setProviderExecutions(1);
        return outcome;
    }
}
