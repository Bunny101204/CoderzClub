package com.coderzclub.controller;

import com.coderzclub.dto.ContestRunRequest;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestAdmissionException;
import com.coderzclub.service.ContestRunAdmissionService;
import com.coderzclub.service.ContestRunQueryService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContestRunControllerTest {
    @Mock private ContestRunAdmissionService admissionService;
    @Mock private ContestRunQueryService queryService;
    @Mock private UserRepository userRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ContestRunController(admissionService, queryService, userRepository))
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
    void unauthenticatedRunIsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(post("/api/contests/c1/problems/p1/runs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isUnauthorized());
        verify(admissionService, never()).admit(any(), any(), any(), any());
    }

    @Test
    void acceptedRunReturns202() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        ContestRunJob job = new ContestRunJob();
        job.setId("run-1");
        job.setStatus(ContestRunJob.Status.QUEUED);
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any())).thenReturn(job);

        mockMvc.perform(post("/api/contests/c1/problems/p1/runs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71,\"language\":\"Python\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void bodyCannotSpoofIdentity() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        ContestRunJob job = new ContestRunJob();
        job.setId("run-1");
        job.setStatus(ContestRunJob.Status.QUEUED);
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any())).thenReturn(job);

        mockMvc.perform(post("/api/contests/c1/problems/p1/runs")
                .contentType("application/json")
                .content("""
                    {
                      "code":"print(1)",
                      "languageId":71,
                      "userId":"spoof-user",
                      "contestId":"other-contest",
                      "problemId":"other-problem",
                      "snapshotId":"evil-snap",
                      "hiddenTestCases":[{"input":"hack","output":"hack"}],
                      "expectedOutput":"hack",
                      "executionMode":"FUNCTION_HARNESS_BATCH",
                      "testcaseVersion":"evil"
                    }
                    """))
            .andExpect(status().isAccepted());

        ArgumentCaptor<ContestRunRequest> captor = ArgumentCaptor.forClass(ContestRunRequest.class);
        verify(admissionService).admit(eq(alice), eq("c1"), eq("p1"), captor.capture());
        assertEquals("print(1)", captor.getValue().getCode());
        assertEquals(71, captor.getValue().getLanguageId());
    }

    @Test
    void outsiderAdmissionSurfaces404() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(admissionService.admit(eq(alice), eq("c1"), eq("p1"), any()))
            .thenThrow(ContestAdmissionException.notFound());
        mockMvc.perform(post("/api/contests/c1/problems/p1/runs")
                .contentType("application/json")
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedGetRunIsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/contest-runs/run-1"))
            .andExpect(status().isUnauthorized());
        verify(queryService, never()).getVisible(any(), any(), any());
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
