package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.model.UserSolvedProblem;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.UserSolvedProblemRepository;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestSubmissionDurabilityTest {
    private static final Instant SUBMITTED_AT = Instant.parse("2026-06-01T14:59:59.900Z");

    @Test
    void contestJobPersistsContextBeforeOutboxJobId() {
        SubmissionJobRepository jobs = mock(SubmissionJobRepository.class);
        SubmissionOutboxService outbox = mock(SubmissionOutboxService.class);
        SubmissionJobEventService events = mock(SubmissionJobEventService.class);
        when(jobs.save(any(SubmissionJob.class))).thenAnswer(invocation -> {
            SubmissionJob job = invocation.getArgument(0);
            job.setId("job-contest");
            return job;
        });

        SubmissionJobService service = new SubmissionJobService();
        ReflectionTestUtils.setField(service, "jobRepository", jobs);
        ReflectionTestUtils.setField(service, "outboxService", outbox);
        ReflectionTestUtils.setField(service, "eventService", events);
        ReflectionTestUtils.setField(service, "workerProperties", new WorkerProperties());
        ReflectionTestUtils.setField(service, "resultRepository", mock(SubmissionTestResultRepository.class));

        ArgumentCaptor<SubmissionJob> saved = ArgumentCaptor.forClass(SubmissionJob.class);
        service.createJob("user", "problem", "code", "java", 62, "v1", 1,
            ExecutionMode.STANDARD_PER_CASE, 12, "contest-1", SUBMITTED_AT, "snap-1");

        var order = inOrder(jobs, outbox);
        order.verify(jobs).save(saved.capture());
        order.verify(outbox).createJobEvent("job-contest");
        assertEquals("contest-1", saved.getValue().getContestId());
        assertEquals(SUBMITTED_AT, saved.getValue().getContestSubmittedAt());
        assertEquals("snap-1", saved.getValue().getContestProblemSnapshotId());
    }

    @Test
    void practiceJobLeavesContestContextNull() {
        SubmissionJobRepository jobs = mock(SubmissionJobRepository.class);
        SubmissionOutboxService outbox = mock(SubmissionOutboxService.class);
        when(jobs.save(any(SubmissionJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SubmissionJobService service = new SubmissionJobService();
        ReflectionTestUtils.setField(service, "jobRepository", jobs);
        ReflectionTestUtils.setField(service, "outboxService", outbox);
        ReflectionTestUtils.setField(service, "eventService", mock(SubmissionJobEventService.class));
        ReflectionTestUtils.setField(service, "workerProperties", new WorkerProperties());
        ReflectionTestUtils.setField(service, "resultRepository", mock(SubmissionTestResultRepository.class));

        service.createJob("user", "problem", "code", "java", 62, "v1", 1,
            ExecutionMode.STANDARD_PER_CASE, 12);

        ArgumentCaptor<SubmissionJob> saved = ArgumentCaptor.forClass(SubmissionJob.class);
        verify(jobs).save(saved.capture());
        assertNull(saved.getValue().getContestId());
        assertNull(saved.getValue().getContestSubmittedAt());
        assertNull(saved.getValue().getContestProblemSnapshotId());
        verify(outbox).createJobEvent(any());
    }

    @Test
    void acceptedContestJobDoesNotAwardPracticeRewards() {
        SubmissionService submissionService = submissionService();
        SubmissionJob job = contestJob();
        stubMaterialization(submissionService, job, "ACCEPTED");

        Submission created = submissionService.createSubmissionFromJob(job);

        assertEquals("contest-1", created.getContestId());
        assertEquals(SUBMITTED_AT, created.getContestSubmittedAt());
        assertEquals("snap-1", created.getContestProblemSnapshotId());
        assertEquals("job-contest", created.getSubmissionJobId());
        UserSolvedProblemRepository solved = (UserSolvedProblemRepository) ReflectionTestUtils.getField(submissionService, "userSolvedProblemRepository");
        LeaderboardService leaderboard = (LeaderboardService) ReflectionTestUtils.getField(submissionService, "leaderboardService");
        UserService userService = (UserService) ReflectionTestUtils.getField(submissionService, "userService");
        verify(solved, never()).insert(any(UserSolvedProblem.class));
        verify(leaderboard, never()).update(any());
        verify(userService, never()).updateUserStreak(any());
        verify(userService, never()).recordFinalSubmission(any());
        ContestScoringService scoring = (ContestScoringService) ReflectionTestUtils.getField(submissionService, "contestScoringService");
        verify(scoring).apply(created);
        assertEquals(com.coderzclub.model.ContestScoringStatus.PENDING, created.getContestScoringStatus());
    }

    @Test
    void acceptedPracticeJobStillAwardsExistingRewards() {
        SubmissionService submissionService = submissionService();
        SubmissionJob job = practiceJob();
        stubMaterialization(submissionService, job, "ACCEPTED");
        UserSolvedProblemRepository solved = (UserSolvedProblemRepository) ReflectionTestUtils.getField(submissionService, "userSolvedProblemRepository");
        when(solved.insert(any(UserSolvedProblem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Submission created = submissionService.createSubmissionFromJob(job);

        assertNull(created.getContestId());
        verify(solved).insert(any(UserSolvedProblem.class));
        LeaderboardService leaderboard = (LeaderboardService) ReflectionTestUtils.getField(submissionService, "leaderboardService");
        UserService userService = (UserService) ReflectionTestUtils.getField(submissionService, "userService");
        verify(leaderboard).update(any());
        verify(userService).updateUserStreak("user-1");
        verify(userService).recordFinalSubmission(any());
        ContestScoringService scoring = (ContestScoringService) ReflectionTestUtils.getField(submissionService, "contestScoringService");
        verify(scoring, never()).apply(any());
        assertNull(created.getContestScoringStatus());
    }

    @Test
    void contestMaterializationRemainsIdempotentOnSubmissionJobId() {
        SubmissionService submissionService = submissionService();
        SubmissionJob job = contestJob();
        Submission existing = Submission.builder()
            .id("sub-1")
            .submissionJobId("job-contest")
            .contestId("contest-1")
            .contestSubmittedAt(SUBMITTED_AT)
            .result("ACCEPTED")
            .build();
        SubmissionRepository submissions = (SubmissionRepository) ReflectionTestUtils.getField(submissionService, "submissionRepository");
        when(submissions.findBySubmissionJobId("job-contest")).thenReturn(Optional.of(existing));

        Submission reused = submissionService.createSubmissionFromJob(job);

        assertEquals("sub-1", reused.getId());
        assertEquals("contest-1", reused.getContestId());
        verify(submissions, never()).save(any());
        UserSolvedProblemRepository solved = (UserSolvedProblemRepository) ReflectionTestUtils.getField(submissionService, "userSolvedProblemRepository");
        verify(solved, never()).insert(any(UserSolvedProblem.class));
        ContestScoringService scoring = (ContestScoringService) ReflectionTestUtils.getField(submissionService, "contestScoringService");
        verify(scoring).apply(existing);
    }

    @Test
    void retentionCopiesContestContext() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        SubmissionRetentionService retention = new SubmissionRetentionService(mongo, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(retention, "enabled", true);
        ReflectionTestUtils.setField(retention, "dryRun", false);
        ReflectionTestUtils.setField(retention, "jobDays", 7);
        ReflectionTestUtils.setField(retention, "summaryDays", 365);
        SubmissionJob job = contestJob();
        job.setStatus(SubmissionJob.JobStatus.COMPLETED);
        job.setCompletedAt(new java.util.Date(System.currentTimeMillis() - 10L * 86400000L));
        when(mongo.count(any(Query.class), eq(SubmissionJob.class))).thenReturn(1L);
        when(mongo.find(any(Query.class), eq(SubmissionJob.class))).thenReturn(List.of(job));

        retention.cleanup();

        ArgumentCaptor<com.coderzclub.model.SubmissionJobSummary> summary =
            ArgumentCaptor.forClass(com.coderzclub.model.SubmissionJobSummary.class);
        verify(mongo).save(summary.capture());
        assertEquals("contest-1", summary.getValue().getContestId());
        assertEquals(SUBMITTED_AT, summary.getValue().getContestSubmittedAt());
        assertEquals("snap-1", summary.getValue().getContestProblemSnapshotId());
    }

    private static SubmissionService submissionService() {
        SubmissionService service = new SubmissionService();
        ReflectionTestUtils.setField(service, "submissionRepository", mock(SubmissionRepository.class));
        ReflectionTestUtils.setField(service, "resultRepository", mock(SubmissionTestResultRepository.class));
        ReflectionTestUtils.setField(service, "leaderboardService", mock(LeaderboardService.class));
        ReflectionTestUtils.setField(service, "userRepository", mock(UserRepository.class));
        ReflectionTestUtils.setField(service, "problemRepository", mock(ProblemRepository.class));
        ReflectionTestUtils.setField(service, "userSolvedProblemRepository", mock(UserSolvedProblemRepository.class));
        ReflectionTestUtils.setField(service, "mongoTemplate", mock(MongoTemplate.class));
        ReflectionTestUtils.setField(service, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(service, "operationalMetrics", new OperationalMetrics(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(service, "contestScoringService", mock(ContestScoringService.class));
        return service;
    }

    private static void stubMaterialization(SubmissionService service, SubmissionJob job, String result) {
        job.setFinalResult(result);
        SubmissionRepository submissions = (SubmissionRepository) ReflectionTestUtils.getField(service, "submissionRepository");
        UserRepository users = (UserRepository) ReflectionTestUtils.getField(service, "userRepository");
        ProblemRepository problems = (ProblemRepository) ReflectionTestUtils.getField(service, "problemRepository");
        SubmissionTestResultRepository results = (SubmissionTestResultRepository) ReflectionTestUtils.getField(service, "resultRepository");
        when(submissions.findBySubmissionJobId(job.getId())).thenReturn(Optional.empty());
        User user = new User();
        user.setId("user-1");
        when(users.findById("user-1")).thenReturn(Optional.of(user));
        Problem problem = new Problem();
        problem.setId("problem-1");
        problem.setPoints(10);
        when(problems.findById("problem-1")).thenReturn(Optional.of(problem));
        when(results.findByJobIdOrderByTestcaseIndexAsc(job.getId())).thenReturn(List.of());
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            saved.setId("sub-1");
            return saved;
        });
        MongoTemplate mongo = (MongoTemplate) ReflectionTestUtils.getField(service, "mongoTemplate");
        when(mongo.updateFirst(any(), any(), any(Class.class))).thenReturn(UpdateResult.acknowledged(1, 1L, null));
    }

    private static SubmissionJob contestJob() {
        SubmissionJob job = practiceJob();
        job.setId("job-contest");
        job.setContestId("contest-1");
        job.setContestSubmittedAt(SUBMITTED_AT);
        job.setContestProblemSnapshotId("snap-1");
        return job;
    }

    private static SubmissionJob practiceJob() {
        SubmissionJob job = new SubmissionJob();
        job.setId("job-practice");
        job.setUserId("user-1");
        job.setProblemId("problem-1");
        job.setCode("print(1)");
        job.setLanguage("PYTHON");
        job.setFinalResult("ACCEPTED");
        job.setTotalTests(1);
        return job;
    }
}
