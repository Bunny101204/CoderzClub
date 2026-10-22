package com.coderzclub.controller;

import com.coderzclub.config.CorsOrigins;
import com.coderzclub.config.JwtFilter;
import com.coderzclub.config.SecurityConfig;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.coderzclub.config.GlobalExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = {AdminContestController.class, ContestController.class, ContestSubmissionController.class, ContestRunController.class},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = GlobalExceptionHandler.class)
)
@Import({SecurityConfig.class, CorsOrigins.class, ApiExceptionHandler.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "app.cors.allowed-origins=http://localhost:5173"
})
class ContestSecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContestService contestService;

    @MockBean
    private com.coderzclub.service.ContestLeaderboardService contestLeaderboardService;

    @MockBean
    private com.coderzclub.service.ContestSubmissionAdmissionService contestSubmissionAdmissionService;

    @MockBean
    private com.coderzclub.service.ContestRunAdmissionService contestRunAdmissionService;

    @MockBean
    private com.coderzclub.service.ContestRunQueryService contestRunQueryService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtFilter jwtFilter;

    @BeforeEach
    void passThroughJwtFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtFilter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void unauthenticatedAdminContestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/admin/contests"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestService, never()).adminList(anyInt(), anyInt());
    }

    @Test
    void roleUserCannotAccessAdminContestMutations() throws Exception {
        mockMvc.perform(post("/api/admin/contests")
                .with(user("alice").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Weekly\"}"))
            .andExpect(status().isForbidden());
        verify(contestService, never()).create(any(), any());
    }

    @Test
    void roleAdminCanReachAdminContestController() throws Exception {
        when(contestService.adminList(0, 20)).thenReturn(Map.of(
            "contests", List.of(),
            "currentPage", 0,
            "totalPages", 0,
            "totalItems", 0L
        ));
        mockMvc.perform(get("/api/admin/contests").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
        verify(contestService).adminList(0, 20);
    }

    @Test
    void authenticatedRoleUserCanReachUserContestList() throws Exception {
        User alice = dbUser("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestService.listVisible(eq(alice), eq(0), eq(20))).thenReturn(Map.of(
            "contests", List.of(),
            "currentPage", 0,
            "totalPages", 0,
            "totalItems", 0L
        ));
        mockMvc.perform(get("/api/contests").with(user("alice").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
        verify(contestService).listVisible(alice, 0, 20);
    }

    @Test
    void authenticatedUserCannotBypassBatchEligibility() throws Exception {
        User alice = dbUser("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestService.getVisible(alice, "secret-contest"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found"));
        mockMvc.perform(get("/api/contests/secret-contest")
                .with(user("alice").roles("USER"))
                .param("userId", "admin-id"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Contest not found"));
        verify(contestService).getVisible(alice, "secret-contest");
        verify(contestService, never()).adminGet(any());
    }

    @Test
    void unauthenticatedContestSubmitIsRejected() throws Exception {
        mockMvc.perform(post("/api/contests/c1/problems/p1/submission-jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestSubmissionAdmissionService, never()).admit(any(), any(), any(), any());
    }

    @Test
    void unauthenticatedContestProblemReadIsRejected() throws Exception {
        mockMvc.perform(get("/api/contests/c1/problems/p1"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestService, never()).getVisibleProblem(any(), any(), any());
    }

    @Test
    void unauthenticatedContestRunIsRejected() throws Exception {
        mockMvc.perform(post("/api/contests/c1/problems/p1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"print(1)\",\"languageId\":71}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestRunAdmissionService, never()).admit(any(), any(), any(), any());
    }

    @Test
    void unauthenticatedContestRunStatusIsRejected() throws Exception {
        mockMvc.perform(get("/api/contest-runs/run-1"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestRunQueryService, never()).getVisible(any(), any(), any());
    }

    @Test
    void unauthenticatedContestLeaderboardIsRejected() throws Exception {
        mockMvc.perform(get("/api/contests/c1/leaderboard"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status != 401 && status != 403) {
                    throw new AssertionError("expected 401 or 403 but was " + status);
                }
            });
        verify(contestLeaderboardService, never()).page(any(), any(), anyInt(), anyInt());
    }

    private static User dbUser(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
