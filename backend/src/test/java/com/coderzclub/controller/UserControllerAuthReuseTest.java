package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.UserProfileStatsResponse;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.AccountDeletionService;
import com.coderzclub.service.LeaderboardService;
import com.coderzclub.service.UserDataExportService;
import com.coderzclub.service.UserProgressService;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerAuthReuseTest {
    @Mock private UserRepository userRepository;
    @Mock private UserProgressService userProgressService;
    @Mock private LeaderboardService leaderboardService;
    @Mock private AccountDeletionService accountDeletionService;
    @Mock private UserDataExportService userDataExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "userProgressService", userProgressService);
        ReflectionTestUtils.setField(controller, "leaderboardService", leaderboardService);
        ReflectionTestUtils.setField(controller, "accountDeletionService", accountDeletionService);
        ReflectionTestUtils.setField(controller, "userDataExportService", userDataExportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        authenticate("alice");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileReusesRequestUser() throws Exception {
        User user = user("u1", "alice");
        mockMvc.perform(get("/api/users/profile").requestAttr(AuthenticatedUser.ATTRIBUTE, user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"));
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void statsReusesRequestUser() throws Exception {
        User user = user("u1", "alice");
        UserProfileStatsResponse stats = new UserProfileStatsResponse();
        stats.setUniqueProblemsSolved(2);
        stats.setTotalPoints(40);
        stats.setAcceptedJudgedSubmissions(1);
        stats.setCompletedStudentSubmissions(4);
        stats.setSuccessRate(0.25);
        when(userProgressService.profileStats(user)).thenReturn(stats);
        mockMvc.perform(get("/api/users/stats").requestAttr(AuthenticatedUser.ATTRIBUTE, user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uniqueProblemsSolved").value(2))
            .andExpect(jsonPath("$.successRate").value(0.25));
        verify(userRepository, never()).findByUsername(anyString());
        verify(userProgressService).profileStats(user);
    }

    @Test
    void leaderboardViewerReusesRequestUser() throws Exception {
        User user = user("u1", "alice");
        user.setTotalPoints(40);
        user.setProblemsSolved(2);
        when(leaderboardService.page(0, 20)).thenReturn(new LinkedHashMap<>(Map.of("users", List.of())));
        when(leaderboardService.rank("u1")).thenReturn(5L);
        mockMvc.perform(get("/api/users/leaderboard").requestAttr(AuthenticatedUser.ATTRIBUTE, user))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewer.username").value("alice"))
            .andExpect(jsonPath("$.viewer.rank").value(5));
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void profileFallbackLooksUpUsernameWhenRequestUserAbsent() throws Exception {
        User user = user("u1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        mockMvc.perform(get("/api/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"));
        verify(userRepository).findByUsername("alice");
    }

    @Test
    void unauthenticatedLeaderboardDoesNotTrustRequestUserAttribute() throws Exception {
        SecurityContextHolder.clearContext();
        User spoofed = user("u1", "alice");
        when(leaderboardService.page(0, 20)).thenReturn(new LinkedHashMap<>(Map.of("users", List.of())));
        mockMvc.perform(get("/api/users/leaderboard").requestAttr(AuthenticatedUser.ATTRIBUTE, spoofed))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewer").doesNotExist());
        verify(userRepository, never()).findByUsername(anyString());
        verify(leaderboardService, never()).rank(anyString());
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            username, "n", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
