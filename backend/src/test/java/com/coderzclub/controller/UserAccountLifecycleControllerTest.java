package com.coderzclub.controller;

import com.coderzclub.dto.AccountDeletionRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserAccountLifecycleControllerTest {
    @Mock private UserRepository userRepository;
    @Mock private UserProgressService userProgressService;
    @Mock private LeaderboardService leaderboardService;
    @Mock private AccountDeletionService accountDeletionService;
    @Mock private UserDataExportService userDataExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "userProgressService", userProgressService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "leaderboardService", leaderboardService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "accountDeletionService", accountDeletionService);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "userDataExportService", userDataExportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedDeleteIsRejected() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isUnauthorized());
        verify(accountDeletionService, never()).deleteSelf(any(), any());
    }

    @Test
    void authenticatedUserDeletesSelfOnly() throws Exception {
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        authenticate("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(accountDeletionService.deleteSelf(eq(user), eq("DELETE"))).thenReturn(Map.of("deleted", true, "userId", "u1"));
        mockMvc.perform(delete("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));
        verify(accountDeletionService).deleteSelf(user, "DELETE");
    }

    @Test
    void profileOmitsPasswordHash() throws Exception {
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        user.setEmail("a@b.c");
        user.setPasswordHash("should-not-appear");
        user.setPasswordResetToken("token");
        authenticate("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        mockMvc.perform(get("/api/users/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.passwordResetToken").doesNotExist());
    }

    @Test
    void exportIsSelfOnly() throws Exception {
        User user = new User();
        user.setId("u1");
        user.setUsername("alice");
        authenticate("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userDataExportService.export(user)).thenReturn(Map.of("account", Map.of("username", "alice")));
        mockMvc.perform(get("/api/users/me/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.username").value("alice"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Content-Disposition", "attachment; filename=\"coderzclub-data-export.json\""));
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            username, "n", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
