package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.dto.ContestRunRequest;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import com.coderzclub.queue.ContestRunPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRunAdmissionServiceTest {
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");
    private static final Instant LIVE = Instant.parse("2026-06-01T14:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T15:00:00Z");

    private MongoTemplate mongo;
    private ContestProperties contestProperties;
    private ContestRunProperties runProperties;
    private SubmissionValidator submissionValidator;
    private SubmissionValidationService validationService;
    private ExecutionCompatibilityService compatibilityService;
    private ContestRunQueueAdmissionService queueAdmissionService;
    private SubmissionLimitService submissionLimitService;
    private ContestRunPublisher publisher;
    private ContestRunMetrics metrics;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        contestProperties = new ContestProperties();
        runProperties = new ContestRunProperties();
        submissionValidator = mock(SubmissionValidator.class);
        validationService = mock(SubmissionValidationService.class);
        compatibilityService = mock(ExecutionCompatibilityService.class);
        queueAdmissionService = mock(ContestRunQueueAdmissionService.class);
        submissionLimitService = mock(SubmissionLimitService.class);
        publisher = mock(ContestRunPublisher.class);
        metrics = new ContestRunMetrics(new SimpleMeterRegistry());
    }

    @Test
    void unpublishedContestIsNotFound() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        when(mongo.findById("c1", Contest.class)).thenReturn(unpublished());
        assertNotFound(service, user("u1"), "c1", "p1", request());
        verify(publisher, never()).publishRun(any());
        verify(submissionLimitService, never()).tryAcquireRunSlot(any(), any());
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
    }

    @Test
    void outsiderCannotRun() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        assertNotFound(service, user("outsider"), "c1", "p1", request());
        verify(publisher, never()).publishRun(any());
    }

    @Test
    void inactiveBatchRejected() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        stubInactiveMember("b1", "u1");
        assertNotFound(service, user("u1"), "c1", "p1", request());
        verify(publisher, never()).publishRun(any());
    }

    @Test
    void allUsersObeysFeatureFlag() {
        Contest contest = liveContest();
        contest.setAudienceType(ContestAudienceType.ALL_USERS);
        contest.setBatchIds(List.of());
        contestProperties.setGlobalEnabled(false);
        ContestRunAdmissionService disabled = serviceAt(LIVE);
        stubContest(contest);
        assertNotFound(disabled, user("u1"), "c1", "p1", request());

        contestProperties.setGlobalEnabled(true);
        ContestRunAdmissionService enabled = serviceAt(LIVE);
        stubHappyPath(contest, publicProblem("p1"));
        ContestRunJob job = enabled.admit(user("u1"), "c1", "p1", request());
        assertEquals("run-1", job.getId());
    }

    @Test
    void upcomingRejected() {
        ContestRunAdmissionService service = serviceAt(START.minusSeconds(1));
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("CONTEST_NOT_LIVE", ex.getError());
        verify(publisher, never()).publishRun(any());
    }

    @Test
    void exactStartAccepted() {
        ContestRunAdmissionService service = serviceAt(START);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        ContestRunJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("run-1", job.getId());
        assertEquals(ContestRunJob.Status.QUEUED, job.getStatus());
        verify(publisher).publishRun("run-1");
    }

    @Test
    void liveAcceptedForContestOnlyProblem() {
        Problem problem = publicProblem("p1");
        problem.setAvailability(ProblemAvailability.CONTEST_ONLY);
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), problem);
        stubActiveMember("b1", "u1");
        ContestRunJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("snap-p1", job.getContestProblemSnapshotId());
        verify(submissionLimitService).tryAcquireRunSlot("u1", "p1");
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
    }

    @Test
    void exactEndRejected() {
        ContestRunAdmissionService service = serviceAt(END);
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("CONTEST_ENDED", ex.getError());
        verify(publisher, never()).publishRun(any());
        verify(submissionLimitService, never()).tryAcquireRunSlot(any(), any());
    }

    @Test
    void endedRejected() {
        ContestRunAdmissionService service = serviceAt(END.plusSeconds(1));
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("CONTEST_ENDED", ex.getError());
    }

    @Test
    void problemNotInContestIsNotFound() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubContest(liveContest());
        stubActiveMember("b1", "u1");
        assertNotFound(service, user("u1"), "c1", "other", request());
        verify(publisher, never()).publishRun(any());
    }

    @Test
    void queueBusyDoesNotAcquireRunSlotOrPublish() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(queueAdmissionService.check())
            .thenReturn(new ContestRunQueueAdmissionService.Admission(false, 50, 1, "QUEUE_DEPTH"));
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("Run is busy. Please retry.", ex.getBody().get("error"));
        verify(submissionLimitService, never()).tryAcquireRunSlot(any(), any());
        verify(publisher, never()).publishRun(any());
        verify(mongo, never()).save(any(ContestRunJob.class));
    }

    @Test
    void runRateLimitRejectedDoesNotPublish() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(submissionLimitService.tryAcquireRunSlot("u1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected("COOLDOWN"));
        assertThrows(RunLimitExceededException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        verify(publisher, never()).publishRun(any());
        verify(mongo, never()).save(any(ContestRunJob.class));
        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
    }

    @Test
    void redisUnavailableIsFailClosed() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        when(submissionLimitService.tryAcquireRunSlot("u1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected("REDIS_UNAVAILABLE"));
        RunLimitExceededException ex = assertThrows(RunLimitExceededException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals("REDIS_UNAVAILABLE", ex.getDecision().getReason());
        verify(publisher, never()).publishRun(any());
    }

    @Test
    void allowedQueueAndRateCreatesJobAndPublishes() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        ContestRunJob job = service.admit(user("u1"), "c1", "p1", request());
        assertEquals("run-1", job.getId());
        verify(queueAdmissionService).check();
        verify(submissionLimitService).tryAcquireRunSlot("u1", "p1");
        verify(publisher).publishRun("run-1");
        verify(mongo).save(any(ContestRunJob.class));
    }

    @Test
    void publishFailureDeletesJobAndReturnsUnavailable() {
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), publicProblem("p1"));
        stubActiveMember("b1", "u1");
        org.mockito.Mockito.doThrow(new IllegalStateException("broker down")).when(publisher).publishRun("run-1");
        SubmissionAdmissionHttpException ex = assertThrows(SubmissionAdmissionHttpException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verify(mongo).remove(any(ContestRunJob.class));
    }

    @Test
    void missingPublicTestsRejected() {
        Problem problem = publicProblem("p1");
        problem.setPublicTestCases(List.of());
        ContestRunAdmissionService service = serviceAt(LIVE);
        stubHappyPath(liveContest(), problem);
        stubActiveMember("b1", "u1");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.admit(user("u1"), "c1", "p1", request()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(publisher, never()).publishRun(any());
    }

    private void assertNotFound(ContestRunAdmissionService service, User user, String contestId,
                                String problemId, ContestRunRequest request) {
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.admit(user, contestId, problemId, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    private ContestRunAdmissionService serviceAt(Instant now) {
        ContestAccessService access = new ContestAccessService(mongo, contestProperties, Clock.fixed(now, ZoneOffset.UTC));
        return new ContestRunAdmissionService(
            Clock.fixed(now, ZoneOffset.UTC),
            mongo,
            access,
            new ContestProblemSnapshotService(mongo, Clock.fixed(now, ZoneOffset.UTC)),
            submissionValidator,
            validationService,
            compatibilityService,
            queueAdmissionService,
            submissionLimitService,
            publisher,
            runProperties,
            metrics
        );
    }

    private void stubHappyPath(Contest contest, Problem problem) {
        stubContest(contest);
        ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem(contest.getId(), problem, LIVE);
        snapshot.setId("snap-" + problem.getId());
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(snapshot);
        when(queueAdmissionService.check()).thenReturn(new ContestRunQueueAdmissionService.Admission(true, 0, 0, "none"));
        when(submissionLimitService.tryAcquireRunSlot(any(), any())).thenReturn(SubmissionLimitDecision.allowed());
        when(mongo.save(any(ContestRunJob.class))).thenAnswer(invocation -> {
            ContestRunJob job = invocation.getArgument(0);
            job.setId("run-1");
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
        problem.setHiddenTestCases(List.of(new TestCase("hidden", "hidden", null)));
        return problem;
    }

    private static ContestRunRequest request() {
        ContestRunRequest request = new ContestRunRequest();
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
