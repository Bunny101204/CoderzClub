package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAccessService;
import com.coderzclub.service.ProblemAccessService;
import com.coderzclub.service.SubmissionJobService;
import com.coderzclub.service.SubmissionLimitDecision;
import com.coderzclub.service.SubmissionLimitService;
import com.coderzclub.service.SubmissionQueueAdmissionService;
import com.coderzclub.service.SubmissionService;
import com.coderzclub.service.SubmissionValidationService;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.service.ExecutionCompatibilityService;
import com.coderzclub.service.IdempotencyClaim;
import com.coderzclub.service.SubmissionValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubmissionPracticeAdmissionTest {

    @Mock
    private SubmissionJobService jobService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private SubmissionValidator submissionValidator;
    @Mock
    private SubmissionQueueAdmissionService queueAdmissionService;
    @Mock
    private SubmissionLimitService submissionLimitService;
    @Mock
    private ContestAccessService contestAccessService;
    @Mock
    private SubmissionValidationService validationService;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private ExecutionCompatibilityService compatibilityService;

    private MockMvc jobsMvc;
    private MockMvc legacyMvc;

    @BeforeEach
    void setUp() {
        SubmissionJobController jobs = new SubmissionJobController();
        ReflectionTestUtils.setField(jobs, "jobService", jobService);
        ReflectionTestUtils.setField(jobs, "userRepository", userRepository);
        ReflectionTestUtils.setField(jobs, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(jobs, "submissionValidator", submissionValidator);
        ReflectionTestUtils.setField(jobs, "queueAdmissionService", queueAdmissionService);
        ReflectionTestUtils.setField(jobs, "submissionLimitService", submissionLimitService);
        ReflectionTestUtils.setField(jobs, "problemAccessService", new ProblemAccessService(contestAccessService));
        ReflectionTestUtils.setField(jobs, "validationService", validationService);
        ReflectionTestUtils.setField(jobs, "compatibilityService", compatibilityService);
        jobsMvc = MockMvcBuilders.standaloneSetup(jobs)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

        SubmissionController legacy = new SubmissionController();
        ReflectionTestUtils.setField(legacy, "userRepository", userRepository);
        ReflectionTestUtils.setField(legacy, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(legacy, "validationService", validationService);
        ReflectionTestUtils.setField(legacy, "problemAccessService", new ProblemAccessService(contestAccessService));
        ReflectionTestUtils.setField(legacy, "submissionService", submissionService);
        legacyMvc = MockMvcBuilders.standaloneSetup(legacy)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submissionJobCreateRejectsContestOnlyBeforeJobCreate() throws Exception {
        User student = user("u1");
        Problem secret = contestOnly("secret");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(student));
        when(queueAdmissionService.check()).thenReturn(new SubmissionQueueAdmissionService.Admission(true, 0, 0, "ok"));
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "secret")).thenReturn(SubmissionLimitDecision.allowed());
        when(problemRepository.findById("secret")).thenReturn(Optional.of(secret));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        jobsMvc.perform(post("/api/submission-jobs")
                .contentType("application/json")
                .content("{\"problemId\":\"secret\",\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isNotFound());

        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void practiceEndpointIgnoresSpoofedContestIdAndCreatesPracticeJob() throws Exception {
        User student = user("u1");
        Problem problem = new Problem();
        problem.setId("public-1");
        problem.setAvailability(ProblemAvailability.PUBLIC);
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(List.of());
        problem.setHiddenTestCases(List.of());
        SubmissionJob job = new SubmissionJob();
        job.setId("job-practice");
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setCreatedAt(new java.util.Date());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(student));
        when(queueAdmissionService.check()).thenReturn(new SubmissionQueueAdmissionService.Admission(true, 0, 0, "ok"));
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "public-1")).thenReturn(SubmissionLimitDecision.allowed());
        when(problemRepository.findById("public-1")).thenReturn(Optional.of(problem));
        when(jobService.createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(job);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        jobsMvc.perform(post("/api/submission-jobs")
                .contentType("application/json")
                .content("{\"problemId\":\"public-1\",\"code\":\"print(1)\",\"languageId\":71,\"contestId\":\"spoofed\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("job-practice"));

        verify(jobService).createJob(eq("u1"), eq("public-1"), eq("print(1)"), any(), eq(71), any(), anyInt(), any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void legacyPracticeSubmitRejectsContestOnly() throws Exception {
        User student = user("u1");
        Problem secret = contestOnly("secret");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(student));
        when(problemRepository.findById("secret")).thenReturn(Optional.of(secret));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        legacyMvc.perform(post("/api/submissions")
                .contentType("application/json")
                .content("{\"problemId\":\"secret\",\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isNotFound());
        verify(submissionService, never()).applyAcceptedSubmission(any(), any(), any());
    }

    @Test
    void replayReturnsTheOriginalJobWithoutAnotherRateLimitSlot() throws Exception {
        MockMvc mvc = controllerWith(IdempotencyClaim.replay("job-original"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("u1")));
        SubmissionJob job = new SubmissionJob();
        job.setId("job-original");
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setCreatedAt(new java.util.Date());
        when(jobService.getJob("job-original")).thenReturn(Optional.of(job));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mvc.perform(post("/api/submission-jobs")
                .header("Idempotency-Key", "attempt01")
                .contentType("application/json")
                .content("{\"problemId\":\"public-1\",\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("job-original"));

        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void inProgressConflictNamesTheOwnerOperation() throws Exception {
        MockMvc mvc = controllerWith(IdempotencyClaim.inProgress(null, "attempt-owner"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("u1")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mvc.perform(post("/api/submission-jobs")
                .header("Idempotency-Key", "attempt-other")
                .contentType("application/json")
                .content("{\"problemId\":\"public-1\",\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.operationKey").value("attempt-owner"));

        verify(submissionLimitService, never()).tryAcquireSubmissionSlot(any(), any());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void conflictUsesTheAuthenticatedUserAndRequestProblem() throws Exception {
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenScope = new AtomicReference<>();
        AtomicReference<String> seenSubject = new AtomicReference<>();
        SubmissionJobController jobs = new SubmissionJobController();
        ReflectionTestUtils.setField(jobs, "jobService", jobService);
        ReflectionTestUtils.setField(jobs, "userRepository", userRepository);
        ReflectionTestUtils.setField(jobs, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(jobs, "submissionValidator", submissionValidator);
        ReflectionTestUtils.setField(jobs, "queueAdmissionService", queueAdmissionService);
        ReflectionTestUtils.setField(jobs, "submissionLimitService", submissionLimitService);
        ReflectionTestUtils.setField(jobs, "problemAccessService", new ProblemAccessService(contestAccessService));
        ReflectionTestUtils.setField(jobs, "validationService", validationService);
        ReflectionTestUtils.setField(jobs, "compatibilityService", compatibilityService);
        ReflectionTestUtils.setField(jobs, "idempotency", new com.coderzclub.service.IdempotencyOperations() {
            @Override
            public com.coderzclub.service.IdempotencyClaim begin(String userId, String scope, String operationKey, String subjectId) {
                seenUser.set(userId);
                seenScope.set(scope);
                seenSubject.set(subjectId);
                return com.coderzclub.service.IdempotencyClaim.inProgress(null, "attempt-owner");
            }
            @Override
            public void complete(com.coderzclub.service.IdempotencyClaim owned, String resourceId) {
            }
            @Override
            public void abandon(com.coderzclub.service.IdempotencyClaim owned) {
            }
            @Override
            public boolean stillHeld(com.coderzclub.service.IdempotencyClaim owned) {
                return false;
            }
            @Override
            public boolean renew(String userId, String scope, String operationKey) {
                return false;
            }
            @Override
            public boolean prepareCommit(String userId, String scope, String operationKey) {
                return false;
            }
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(jobs).setControllerAdvice(new ApiExceptionHandler()).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("u1")));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mvc.perform(post("/api/submission-jobs")
                .header("Idempotency-Key", "attempt-other")
                .contentType("application/json")
                .content("{\"problemId\":\"public-1\",\"code\":\"print(1)\",\"languageId\":71,\"userId\":\"victim-user\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.operationKey").value("attempt-owner"));

        org.junit.jupiter.api.Assertions.assertEquals("u1", seenUser.get());
        org.junit.jupiter.api.Assertions.assertEquals("submission-job", seenScope.get());
        org.junit.jupiter.api.Assertions.assertEquals("public-1", seenSubject.get());
        verify(jobService, never()).createJob(any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    private MockMvc controllerWith(com.coderzclub.service.IdempotencyClaim claim) {
        SubmissionJobController jobs = new SubmissionJobController();
        ReflectionTestUtils.setField(jobs, "jobService", jobService);
        ReflectionTestUtils.setField(jobs, "userRepository", userRepository);
        ReflectionTestUtils.setField(jobs, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(jobs, "submissionValidator", submissionValidator);
        ReflectionTestUtils.setField(jobs, "queueAdmissionService", queueAdmissionService);
        ReflectionTestUtils.setField(jobs, "submissionLimitService", submissionLimitService);
        ReflectionTestUtils.setField(jobs, "problemAccessService", new ProblemAccessService(contestAccessService));
        ReflectionTestUtils.setField(jobs, "validationService", validationService);
        ReflectionTestUtils.setField(jobs, "compatibilityService", compatibilityService);
        ReflectionTestUtils.setField(jobs, "idempotency", new com.coderzclub.service.IdempotencyOperations() {
            @Override
            public com.coderzclub.service.IdempotencyClaim begin(String userId, String scope, String operationKey, String subjectId) {
                return claim;
            }
            @Override
            public void complete(com.coderzclub.service.IdempotencyClaim owned, String resourceId) {
            }
            @Override
            public void abandon(com.coderzclub.service.IdempotencyClaim owned) {
            }
            @Override
            public boolean stillHeld(com.coderzclub.service.IdempotencyClaim owned) {
                return false;
            }
            @Override
            public boolean renew(String userId, String scope, String operationKey) {
                return true;
            }
            @Override
            public boolean prepareCommit(String userId, String scope, String operationKey) {
                return true;
            }
        });
        return MockMvcBuilders.standaloneSetup(jobs)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    private static Problem contestOnly(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle("Secret");
        problem.setAvailability(ProblemAvailability.CONTEST_ONLY);
        return problem;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("alice");
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
