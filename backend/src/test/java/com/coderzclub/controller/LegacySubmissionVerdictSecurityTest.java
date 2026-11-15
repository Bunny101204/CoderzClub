package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAccessService;
import com.coderzclub.service.OrganizationAccessService;
import com.coderzclub.service.OrganizationResourceCompatibility;
import com.coderzclub.service.OrganizationVisibilityPolicy;
import com.coderzclub.service.ProblemAccessService;
import com.coderzclub.service.SubmissionLimitDecision;
import com.coderzclub.service.SubmissionLimitService;
import com.coderzclub.service.SubmissionService;
import com.coderzclub.service.SubmissionValidationService;
import com.coderzclub.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LegacySubmissionVerdictSecurityTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private SubmissionService submissionService;
    @Mock private SubmissionLimitService submissionLimitService;
    @Mock private SubmissionValidationService validationService;
    @Mock private UserService userService;
    @Mock private ContestAccessService contestAccessService;
    @Mock private OrganizationAccessService organizationAccess;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SubmissionController controller = new SubmissionController();
        ReflectionTestUtils.setField(controller, "submissionRepository", submissionRepository);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "problemRepository", problemRepository);
        ReflectionTestUtils.setField(controller, "submissionService", submissionService);
        ReflectionTestUtils.setField(controller, "submissionLimitService", submissionLimitService);
        ReflectionTestUtils.setField(controller, "validationService", validationService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "problemAccessService",
            new ProblemAccessService(
                contestAccessService,
                organizationAccess,
                new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility())));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forgedAcceptedVerdictDoesNotAwardRewards() throws Exception {
        User user = user("u1", "alice");
        Problem problem = publicProblem("public-1");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(problemRepository.findById("public-1")).thenReturn(Optional.of(problem));
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "public-1"))
            .thenReturn(SubmissionLimitDecision.allowed());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            saved.setId("sub-1");
            return saved;
        });

        mockMvc.perform(post("/api/submissions")
                .contentType("application/json")
                .content("""
                    {
                      "problemId":"public-1",
                      "code":"print(1)",
                      "language":"python",
                      "languageId":71,
                      "statusId":3,
                      "result":"ACCEPTED",
                      "passedTestCases":10,
                      "totalTestCases":10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scoring").value("REQUIRES_SUBMISSION_JOB"))
            .andExpect(jsonPath("$.submission.result").value("UNVERIFIED"))
            .andExpect(jsonPath("$.submission.verdict").value("UNVERIFIED"));

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        assertEquals("UNVERIFIED", captor.getValue().getResult());
        assertNotEquals("ACCEPTED", captor.getValue().getResult());
        verify(submissionService, never()).applyAcceptedSubmission(any(), any(), any());
        verify(userService).updateUserStreak("u1");
    }

    @Test
    void unauthorizedProblemAccessStillDenied() throws Exception {
        User user = user("u1", "alice");
        Problem secret = new Problem();
        secret.setId("secret");
        secret.setAvailability(ProblemAvailability.CONTEST_ONLY);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(problemRepository.findById("secret")).thenReturn(Optional.of(secret));

        mockMvc.perform(post("/api/submissions")
                .contentType("application/json")
                .content("""
                    {"problemId":"secret","code":"print(1)","languageId":71,"statusId":3,"result":"ACCEPTED"}
                    """))
            .andExpect(status().isNotFound());

        verify(submissionRepository, never()).save(any());
        verify(submissionService, never()).applyAcceptedSubmission(any(), any(), any());
    }

    @Test
    void duplicateForgedAcceptedPostsNeverAwardRewards() throws Exception {
        User user = user("u1", "alice");
        Problem problem = publicProblem("public-1");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(problemRepository.findById("public-1")).thenReturn(Optional.of(problem));
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "public-1"))
            .thenReturn(SubmissionLimitDecision.allowed());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("sub-" + System.nanoTime());
            }
            return saved;
        });

        String forged = """
            {"problemId":"public-1","code":"print(1)","language":"python","languageId":71,"statusId":3,"result":"ACCEPTED"}
            """;
        mockMvc.perform(post("/api/submissions").contentType("application/json").content(forged))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submission.result").value("UNVERIFIED"));
        mockMvc.perform(post("/api/submissions").contentType("application/json").content(forged))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.submission.result").value("UNVERIFIED"));

        verify(submissionRepository, org.mockito.Mockito.times(2)).save(any(Submission.class));
        verify(submissionService, never()).applyAcceptedSubmission(any(), any(), any());
    }

    @Test
    void orgPrivateForgedAcceptedStillDoesNotCallRewardPath() throws Exception {
        User user = user("u1", "alice");
        Problem problem = publicProblem("org-problem");
        problem.setOrganizationId("org-a");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(problemRepository.findById("org-problem")).thenReturn(Optional.of(problem));
        when(organizationAccess.findActiveMembership("org-a", "u1"))
            .thenReturn(Optional.of(new com.coderzclub.model.OrganizationMember()));
        when(organizationAccess.isActiveOrganizationId("org-a")).thenReturn(true);
        when(submissionLimitService.tryAcquireSubmissionSlot("u1", "org-problem"))
            .thenReturn(SubmissionLimitDecision.allowed());
        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            saved.setId("sub-org");
            return saved;
        });

        mockMvc.perform(post("/api/submissions")
                .contentType("application/json")
                .content("""
                    {"problemId":"org-problem","code":"print(1)","languageId":71,"statusId":3,"result":"ACCEPTED","passedTestCases":5}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scoring").value("REQUIRES_SUBMISSION_JOB"));

        verify(submissionService, never()).applyAcceptedSubmission(any(), any(), any());
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        return user;
    }

    private static Problem publicProblem(String id) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setAvailability(ProblemAvailability.PUBLIC);
        problem.setPoints(100);
        return problem;
    }
}
