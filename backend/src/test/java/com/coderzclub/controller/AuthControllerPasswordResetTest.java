package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.model.User;
import com.coderzclub.service.EmailService;
import com.coderzclub.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordResetTest {
    private static final String GENERIC_MESSAGE =
        "If an eligible account exists for that email, reset instructions have been sent.";

    @Mock private UserService userService;
    @Mock private EmailService emailService;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthController authController;

    @Test
    void knownEmailIssuesTokenAndReturnsGenericMessage() throws Exception {
        User user = eligible("a@b.c", "reset-token");
        when(userService.createPasswordResetToken("a@b.c")).thenReturn(Optional.of(user));
        mockMvc().perform(post("/api/password-reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.c\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
        verify(userService).createPasswordResetToken("a@b.c");
        verify(emailService).sendPasswordResetEmail("a@b.c", "reset-token");
    }

    @Test
    void unknownEmailReturnsSameMessageAndSendsNoMail() throws Exception {
        when(userService.createPasswordResetToken("missing@example.com")).thenReturn(Optional.empty());
        mockMvc().perform(post("/api/password-reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void deletedAccountReturnsSameMessageAndSendsNoMail() throws Exception {
        when(userService.createPasswordResetToken("gone@example.com")).thenReturn(Optional.empty());
        mockMvc().perform(post("/api/password-reset-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"gone@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC_MESSAGE));
        verify(emailService, never()).sendPasswordResetEmail(eq("gone@example.com"), any());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    private static User eligible(String email, String token) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordResetToken(token);
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
