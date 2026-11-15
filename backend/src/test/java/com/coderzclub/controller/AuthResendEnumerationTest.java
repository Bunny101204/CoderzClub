package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.service.AuthEmailRateLimiter;
import com.coderzclub.service.EmailService;
import com.coderzclub.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthResendEnumerationTest {
    private static final String GENERIC =
        "If an eligible unverified account exists, a verification email has been sent.";

    @Mock private UserService userService;
    @Mock private EmailService emailService;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthController authController;

    @BeforeEach
    void wireRateLimiter() {
        ReflectionTestUtils.setField(authController, "authEmailRateLimiter", new AuthEmailRateLimiter(50, 60_000));
    }

    @Test
    void unknownUserReturnsGenericMessage() throws Exception {
        when(userService.resendVerificationEmail("missing@example.com"))
            .thenThrow(new RuntimeException("User not found"));
        mockMvc().perform(post("/api/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"missing@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value(GENERIC));
        verify(emailService, never()).sendVerificationEmail(any(), any(), any(), any());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(authController)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }
}
