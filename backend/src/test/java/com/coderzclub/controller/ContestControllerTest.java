package com.coderzclub.controller;

import com.coderzclub.dto.ContestProblemDetailResponse;
import com.coderzclub.dto.ContestUserResponse;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestLeaderboardService;
import com.coderzclub.service.ContestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContestControllerTest {
    @Mock
    private ContestService contestService;
    @Mock
    private ContestLeaderboardService leaderboardService;
    @Mock
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContestController(contestService, leaderboardService, userRepository))
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
    void listUsesAuthenticatedUserNotRequestUserId() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestService.listVisible(eq(alice), eq(0), eq(20)))
            .thenReturn(Map.of("contests", List.of(), "currentPage", 0, "totalItems", 0L));
        mockMvc.perform(get("/api/contests").param("userId", "someone-else"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(0));
        verify(contestService).listVisible(alice, 0, 20);
    }

    @Test
    void listForwardsPageAndRequestedSize() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestService.listVisible(eq(alice), eq(2), eq(999)))
            .thenReturn(Map.of("contests", List.of(), "currentPage", 2, "totalItems", 0L));
        mockMvc.perform(get("/api/contests").param("page", "2").param("size", "999"))
            .andExpect(status().isOk());
        verify(contestService).listVisible(alice, 2, 999);
    }

    @Test
    void ineligibleContestIsNotFound() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(contestService.getVisible(alice, "c1"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contest not found"));
        mockMvc.perform(get("/api/contests/c1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Contest not found"));
    }

    @Test
    void eligibleContestReturnsUserDto() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        ContestUserResponse body = new ContestUserResponse();
        body.setId("c1");
        body.setTitle("Weekly");
        body.setStatus(ContestStatus.UPCOMING);
        body.setProblemCount(3);
        when(contestService.getVisible(alice, "c1")).thenReturn(body);
        mockMvc.perform(get("/api/contests/c1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Weekly"))
            .andExpect(jsonPath("$.problemCount").value(3))
            .andExpect(jsonPath("$.problems").isEmpty());
    }

    @Test
    void contestProblemDetailDoesNotSerializeHiddenTests() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        ContestProblemDetailResponse body = new ContestProblemDetailResponse();
        when(contestService.getVisibleProblem(alice, "c1", "p1")).thenReturn(body);
        mockMvc.perform(get("/api/contests/c1/problems/p1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hiddenTestCases").doesNotExist());
        verify(contestService).getVisibleProblem(alice, "c1", "p1");
    }

    @Test
    void leaderboardForwardsPageAndSize() throws Exception {
        User alice = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(leaderboardService.page(alice, "c1", 1, 50)).thenReturn(Map.of(
            "entries", List.of(),
            "currentPage", 1,
            "totalPages", 0,
            "totalItems", 0L,
            "scoringMode", "POINTS_FIRST_ACCEPTED"
        ));
        mockMvc.perform(get("/api/contests/c1/leaderboard").param("page", "1").param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scoringMode").value("POINTS_FIRST_ACCEPTED"));
        verify(leaderboardService).page(alice, "c1", 1, 50);
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole("USER");
        return user;
    }
}
