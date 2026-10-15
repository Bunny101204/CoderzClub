package com.coderzclub.controller;

import com.coderzclub.config.GlobalExceptionHandler;
import com.coderzclub.dto.ContestSubmissionJobRequest;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAdmissionException;
import com.coderzclub.service.ContestSubmissionAdmissionService;
import com.coderzclub.service.IncompatibleExecutionException;
import com.coderzclub.service.SubmissionAdmissionHttpException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContestSubmissionControllerTest {
    @Mock
    private ContestSubmissionAdmissionService admissionService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContestSubmissionController(admissionService, userRepository))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "alice", "n", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedSubmitIsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isUnauthorized());
        verify(admissionService, never()).admit(any(), any(), any(), any());
    }

    @Test
    void acceptedSubmitReturns202WithJobId() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setCreatedAt(new Date());
        job.setContestId("c1");
        job.setContestSubmittedAt(Instant.parse("2026-06-01T14:00:00Z"));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any())).thenReturn(job);

        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71,\"language\":\"Python\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.jobId").value("job-1"))
            .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void pathIdsAreAuthoritativeAndBodySpoofFieldsAreIgnored() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any())).thenReturn(job);

        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("""
                    {
                      "code":"print(1)",
                      "languageId":71,
                      "userId":"spoof-user",
                      "username":"eve",
                      "contestId":"other-contest",
                      "problemId":"other-problem",
                      "hiddenTestCases":[{"input":"hack","output":"hack"}],
                      "expectedOutput":"hack",
                      "testcaseVersion":"evil",
                      "executionMode":"FUNCTION_HARNESS_BATCH"
                    }
                    """))
            .andExpect(status().isAccepted());

        ArgumentCaptor<ContestSubmissionJobRequest> captor = ArgumentCaptor.forClass(ContestSubmissionJobRequest.class);
        verify(admissionService).admit(eq(alice), eq("c1"), eq("p1"), captor.capture());
        ContestSubmissionJobRequest body = captor.getValue();
        assertEquals("print(1)", body.getCode());
        assertEquals(71, body.getLanguageId());
        for (Class<?> type = ContestSubmissionJobRequest.class; type != null && type != Object.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                org.junit.jupiter.api.Assertions.assertFalse(
                    java.util.Set.of("userId", "username", "contestId", "problemId", "hiddenTestCases",
                        "expectedOutput", "testcaseVersion", "executionMode").contains(field.getName()),
                    field.getName());
            }
        }
    }

    @Test
    void upcomingReturnsStable409() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.notLive());
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONTEST_NOT_LIVE"));
    }

    @Test
    void admissionConflictBeatsGenericHandler() throws Exception {
        MockMvc ordered = MockMvcBuilders.standaloneSetup(new ContestSubmissionController(admissionService, userRepository))
            .setControllerAdvice(new GlobalExceptionHandler(), new ApiExceptionHandler())
            .build();
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.notLive());
        ordered.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONTEST_NOT_LIVE"));

        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.ended());
        ordered.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONTEST_ENDED"));
    }

    @Test
    void endedReturnsStable409() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.ended());
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("CONTEST_ENDED"));
    }

    @Test
    void ineligibleReturns404WithoutJavaNames() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.notFound());
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Contest not found"))
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void cooldownReturnsExisting429Body() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(new SubmissionAdmissionHttpException(HttpStatus.TOO_MANY_REQUESTS, Map.of(
                "error", "Please wait before submitting again.",
                "retryAfterSeconds", 9
            )));
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Please wait before submitting again."))
            .andExpect(jsonPath("$.retryAfterSeconds").value(9));
    }

    @Test
    void incompatibleExecutionReturns400() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(new IncompatibleExecutionException("Language 71 is not enabled"));
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Language 71 is not enabled"));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
