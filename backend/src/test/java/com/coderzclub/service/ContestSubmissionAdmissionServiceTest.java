package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestSubmissionAdmissionServiceTest {
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");
    private static final Instant LIVE = Instant.parse("2026-06-01T14:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T15:00:00Z");

    private MongoTemplate mongo;
    private ContestProperties properties;
    private SubmissionValidator submissionValidator;
    private SubmissionValidationService validationService;
    private ExecutionCompatibilityService compatibilityService;
    private SubmissionLimitService submissionLimitService;
    private SubmissionQueueAdmissionService queueAdmissionService;
    private SubmissionJobService jobService;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        properties = new ContestProperties();
        submissionValidator = mock(SubmissionValidator.class);
        validationService = mock(SubmissionValidationService.class);
        compatibilityService = mock(ExecutionCompatibilityService.class);
        submissionLimitService = mock(SubmissionLimitService.class);
        queueAdmissionService = mock(SubmissionQueueAdmissionService.class);
        jobService = mock(SubmissionJobService.class);
    }

    @Test
    void missingContestIsNotFound() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        assertNotFound(service, user("u1"), "missing", "p1", request());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
        verify(mongo, never()).find(any(Query.class), eq(BatchMember.class));
    }

    @Test
    void unpublishedContestIsNotFound() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        when(mongo.findById("c1", Contest.class)).thenReturn(unpublished());
        assertNotFound(service, user("u1"), "c1", "p1", request());
        verify(mongo, never()).find(any(Query.class), eq(BatchMember.class));
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void outsiderCannotSubmit() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        assertNotFound(service, user("outsider"), "c1", "p1", request());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
    }

    @Test
    void activeBatchMemberCanSubmitAtLive() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        SubmissionJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("job-1", job.getId());
        assertEquals("c1", job.getContestId());
        assertEquals(LIVE, job.getContestSubmittedAt());
        verify(submissionLimitService).tryAcquireSubmissionSlot("u1", "p1");
        verify(queueAdmissionService).check();
        verify(jobService).createJob(eq("u1"), eq("p1"), eq("print(1)"), eq("Python"), eq(71),
            eq("v1"), eq(1), eq(ExecutionMode.STANDARD_PER_CASE), any(), eq("c1"), eq(LIVE), eq("snap-p1"));
    }

    @Test
    void memberOfSecondAssignedBatchCanSubmit() {
        Contest contest = liveContest();
        contest.setBatchIds(List.of("b1", "b2"));
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(contest, publicProblem("p1"));
        stubActiveMember("b2", "u1");
        SubmissionJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("c1", job.getContestId());
    }

    @Test
    void inactiveBatchMemberCannotSubmitWhileLive() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        stubInactiveMember("b1", "u1");
        assertNotFound(service, user("u1"), "c1", "p1", request());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void allUsersObeysFeatureFlag() {
        Contest contest = liveContest();
        contest.setAudienceType(ContestAudienceType.ALL_USERS);
        contest.setBatchIds(List.of());
        properties.setGlobalEnabled(false);
        ContestSubmissionAdmissionService disabled = serviceAt(LIVE);
        stubContest(contest);
        assertNotFound(disabled, user("u1"), "c1", "p1", request());

        properties.setGlobalEnabled(true);
        ContestSubmissionAdmissionService enabled = serviceAt(LIVE);
        stubHappyPath(contest, publicProblem("p1"));
        SubmissionJob job = enabled.admit(user("u1"), "c1", "p1", request());
        assertEquals("c1", job.getContestId());
    }

    @Test
    void upcomingEligibleIsConflictNotLive() {
        ContestSubmissionAdmissionService service = serviceAt(START.minusSeconds(1));
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("CONTEST_NOT_LIVE", ex.getError());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void exactStartIsAccepted() {
        ContestSubmissionAdmissionService service = serviceAt(START);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        SubmissionJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals(START, job.getContestSubmittedAt());
    }

    @Test
    void exactEndIsRejected() {
        ContestSubmissionAdmissionService service = serviceAt(END);
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("CONTEST_ENDED", ex.getError());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void endedEligibleIsConflictEnded() {
        ContestSubmissionAdmissionService service = serviceAt(END.plusSeconds(1));
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("CONTEST_ENDED", ex.getError());
    }

    @Test
    void problemOutsideContestIsNotFound() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        assertNotFound(service, user("u1"), "c1", "other", request());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void contestOnlyProblemIsAdmittedThroughContestEndpoint() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        Problem secret = publicProblem("p1");
        secret.setAvailability(ProblemAvailability.CONTEST_ONLY);
        stubHappyPath(liveContest(), secret);
        stubActiveMember("b1", "u1");
        SubmissionJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("c1", job.getContestId());
    }

    @Test
    void numericProblemIdResolvesCanonically() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        Problem problem = publicProblem("p1");
        problem.setNumericId(42);
        stubHappyPath(liveContest(), problem);
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem("c1", problem, LIVE);
        snapshot.setId("snap-p1");
        when(mongo.find(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(List.of(snapshot));
        stubActiveMember("b1", "u1");
        service.admit(user("u1"), "c1", "42", request());
        verify(jobService).createJob(eq("u1"), eq("p1"), any(), any(), any(), any(), anyInt(), any(), any(), eq("c1"), eq(LIVE), eq("snap-p1"));
    }

    @Test
    void authenticatedUserIdIsUsedNotClientIdentity() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        service.admit(user("u1"), "c1", "p1", request());
        verify(jobService).createJob(eq("u1"), eq("p1"), any(), any(), any(), any(), anyInt(), any(), any(), eq("c1"), eq(LIVE), eq("snap-p1"));
    }

    @Test
    void hiddenTestsComeFromSnapshotNotMutableProblem() throws Exception {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        Problem problem = publicProblem("p1");
        problem.setHiddenTestCases(List.of(new TestCase("mutated-in", "mutated-out", null)));
        stubHappyPath(liveContest(), problem);
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem("c1", publicProblem("p1"), LIVE);
        snapshot.setId("snap-p1");
        snapshot.setHiddenTestCases(List.of(new TestCase("secret-in", "secret-out", null)));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(snapshot);
        stubActiveMember("b1", "u1");
        service.admit(user("u1"), "c1", "p1", request());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionJob.TestCase>> hidden = ArgumentCaptor.forClass(List.class);
        verify(compatibilityService).validate(eq(ExecutionMode.STANDARD_PER_CASE), eq(71), eq("print(1)"),
            any(), hidden.capture(), eq("v1"));
        assertEquals(1, hidden.getValue().size());
        assertEquals("secret-in", hidden.getValue().get(0).getInput());
        assertEquals("secret-out", hidden.getValue().get(0).getExpectedOutput());
    }

    @Test
    void cooldownRejectsWith429AndDoesNotCreateJob() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected("COOLDOWN"));
        when(submissionLimitService.getCooldownSeconds("u1")).thenReturn(12L);
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("Please wait before submitting again.", ex.getBody().get("error"));
        assertEquals(12L, ex.getBody().get("retryAfterSeconds"));
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void dailyLimitRejectsWith429() {
        assertLimitRejected("DAILY_LIMIT", "Daily submission limit exceeded.");
    }

    @Test
    void problemLimitRejectsWith429() {
        assertLimitRejected("PROBLEM_LIMIT", "Daily submission limit for this problem exceeded.");
    }

    @Test
    void redisUnavailableRejectsWith503() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected("REDIS_UNAVAILABLE"));
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void queueUnavailableRejectsWith503WithoutConsumingRateLimitSlot() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(queueAdmissionService.check())
            .thenReturn(new SubmissionQueueAdmissionService.Admission(false, -1, -1, "QUEUE_UNAVAILABLE"));
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void queueBusyRejectsWith429WithoutConsumingRateLimitSlot() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(queueAdmissionService.check())
            .thenReturn(new SubmissionQueueAdmissionService.Admission(false, 50, 1, "QUEUE_DEPTH"));
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidCodeSurfacesExistingValidation() {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem("c1", publicProblem("p1"), LIVE);
        snapshot.setId("snap-p1");
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(snapshot);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("code exceeds maximum length"))
            .when(submissionValidator).validateCode(any());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("code exceeds maximum length", ex.getMessage());
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
    }

    private void assertLimitRejected(String reason, String error) {
        ContestSubmissionAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected(reason));
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals(error, ex.getBody().get("error"));
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    private void assertNotFound(ContestSubmissionAdmissionService service, User user, String contestId,
                                String problemId, ContestSubmissionJobRequest request) {
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user, contestId, problemId, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("Contest not found", ex.getError());
    }

    private ContestSubmissionAdmissionService serviceAt(Instant now) {
        ContestAccessService access = new ContestAccessService(mongo, properties, Clock.fixed(now, ZoneOffset.UTC));
        return new ContestSubmissionAdmissionService(
            Clock.fixed(now, ZoneOffset.UTC),
            mongo,
            access,
            new ContestProblemSnapshotService(mongo, Clock.fixed(now, ZoneOffset.UTC)),
            submissionValidator,
            validationService,
            compatibilityService,
            submissionLimitService,
            queueAdmissionService,
            jobService
        );
    }

    private void stubHappyPath(Contest contest, Problem problem) {
        stubContest(contest);
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem(contest.getId(), problem, LIVE);
        snapshot.setId("snap-" + problem.getId());
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(snapshot);
        when(submissionLimitService.tryAcquireSubmissionSlot(any(), any())).thenReturn(SubmissionLimitDecision.allowed());
        when(queueAdmissionService.check()).thenReturn(new SubmissionQueueAdmissionService.Admission(true, 0, 0, "none"));
        when(jobService.createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                SubmissionJob job = new SubmissionJob();
                job.setId("job-1");
                job.setStatus(SubmissionJob.JobStatus.QUEUED);
                job.setUserId(invocation.getArgument(0));
                job.setProblemId(invocation.getArgument(1));
                job.setContestId(invocation.getArgument(9));
                job.setContestSubmittedAt(invocation.getArgument(10));
                job.setContestProblemSnapshotId(invocation.getArgument(11));
                return job;
            });
    }

    private void stubContest(Contest contest) {
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
    }

    private void stubActiveMember(String batchId, String userId) {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member(batchId, userId)));
        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setActive(true);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
    }

    private void stubInactiveMember(String batchId, String userId) {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member(batchId, userId)));
        Batch batch = new Batch();
        batch.setId(batchId);
        batch.setActive(false);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
    }

    private static Contest liveContest() {
        Contest contest = new Contest();
        contest.setId("c1");
        contest.setPublished(true);
        contest.setAudienceType(ContestAudienceType.BATCHES);
        contest.setBatchIds(List.of("b1"));
        contest.setProblemIds(List.of("p1"));
        contest.setStartAt(START);
        contest.setEndAt(END);
        return contest;
    }

    private static Contest unpublished() {
        Contest contest = liveContest();
        contest.setPublished(false);
        return contest;
    }

    private static Problem publicProblem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setAvailability(ProblemAvailability.PUBLIC);
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(List.of(new TestCase("1", "1", null)));
        problem.setHiddenTestCases(List.of());
        return problem;
    }

    private static ContestSubmissionJobRequest request() {
        ContestSubmissionJobRequest request = new ContestSubmissionJobRequest();
        request.setCode("print(1)");
        request.setLanguage("Python");
        request.setLanguageId(71);
        return request;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static BatchMember member(String batchId, String userId) {
        BatchMember member = new BatchMember();
        member.setBatchId(batchId);
        member.setUserId(userId);
        return member;
    }
}
