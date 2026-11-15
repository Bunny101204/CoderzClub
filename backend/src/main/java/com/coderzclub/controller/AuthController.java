package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.dto.JwtResponse;
import com.coderzclub.dto.LoginRequest;
import com.coderzclub.dto.PasswordResetConfirmRequest;
import com.coderzclub.dto.PasswordResetRequest;
import com.coderzclub.dto.RegisterRequest;
import com.coderzclub.dto.ResendVerificationRequest;
import com.coderzclub.model.User;
import com.coderzclub.service.AuthEmailRateLimiter;
import com.coderzclub.service.EmailBrandContext;
import com.coderzclub.service.EmailService;
import com.coderzclub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String PASSWORD_RESET_GENERIC =
        "If an eligible account exists for that email, reset instructions have been sent.";
    private static final String RESEND_GENERIC =
        "If an eligible unverified account exists, a verification email has been sent.";

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthEmailRateLimiter authEmailRateLimiter;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin,
                                      HttpServletRequest request) {
        try {
            authEmailRateLimiter.check(
                AuthEmailRateLimiter.Action.REGISTER,
                clientIp(request),
                req.getEmail()
            );
            User user = userService.registerUser(req.getUsername(), req.getEmail(), req.getPassword());

            try {
                emailService.sendVerificationEmail(
                    user.getEmail(),
                    user.getEmailVerificationToken(),
                    clientOrigin,
                    EmailBrandContext.platform()
                );
                return ResponseEntity.ok(Map.of(
                    "message", "Registration successful. A verification email has been sent.",
                    "emailSent", true
                ));
            } catch (Exception emailEx) {
                logger.warn("Verification email was not delivered type={}", emailEx.getClass().getSimpleName());
                return ResponseEntity.ok(Map.of(
                    "message", "Registration successful, but the verification email could not be sent. Please contact support or try again later.",
                    "emailSent", false
                ));
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e)));
        }
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<?> confirmEmail(@RequestParam String token) {
        try {
            userService.verifyEmailToken(token);
            return ResponseEntity.ok(Map.of(
                "verified", true,
                "message", "Email verified successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e, "Verification failed")));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req,
                                                @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin,
                                                HttpServletRequest request) {
        authEmailRateLimiter.check(
            AuthEmailRateLimiter.Action.RESEND_VERIFICATION,
            clientIp(request),
            req.getUsernameOrEmail()
        );
        try {
            User user = userService.resendVerificationEmail(req.getUsernameOrEmail());
            try {
                emailService.sendVerificationEmail(
                    user.getEmail(),
                    user.getEmailVerificationToken(),
                    clientOrigin,
                    EmailBrandContext.platform()
                );
            } catch (Exception emailEx) {
                logger.warn("Verification email was not delivered type={}", emailEx.getClass().getSimpleName());
            }
        } catch (Exception ignored) {
            // Uniform response prevents account enumeration.
        }
        return ResponseEntity.ok(Map.of(
            "message", RESEND_GENERIC,
            "emailSent", true
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<User> userOpt = userService.findByUsernameOrEmail(req.getUsername());

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isDeleted()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }
            boolean passwordMatch = userService.checkPassword(req.getPassword(), user.getPasswordHash());
            if (!passwordMatch) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }
            if (user.isInactive()) {
                return ResponseEntity.status(401).body(Map.of("error", "This account is inactive. Contact an administrator."));
            }
            if (!user.isEmailVerified()) {
                return ResponseEntity.status(401).body(Map.of(
                    "error", "Email is not verified. Please verify your email before logging in."));
            }
            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
            return ResponseEntity.ok(new JwtResponse(token, user.getRole()));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/password-reset-request")
    public ResponseEntity<?> passwordResetRequest(@RequestBody PasswordResetRequest requestBody,
                                                  @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin,
                                                  HttpServletRequest request) {
        String email = requestBody == null ? null : requestBody.getEmail();
        authEmailRateLimiter.check(
            AuthEmailRateLimiter.Action.PASSWORD_RESET,
            clientIp(request),
            email
        );
        Optional<User> user = userService.createPasswordResetToken(email);
        if (user.isPresent()) {
            try {
                emailService.sendPasswordResetEmail(
                    user.get().getEmail(),
                    user.get().getPasswordResetToken(),
                    clientOrigin,
                    EmailBrandContext.platform()
                );
            } catch (Exception emailEx) {
                logger.warn("Password reset email was not delivered type={}", emailEx.getClass().getSimpleName());
            }
        }
        return ResponseEntity.ok(Map.of("message", PASSWORD_RESET_GENERIC));
    }

    @PostMapping("/password-reset-confirm")
    public ResponseEntity<?> passwordResetConfirm(@RequestBody PasswordResetConfirmRequest request) {
        try {
            userService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", safeClientMessage(e, "Password reset failed")));
        }
    }

    @PostMapping("/validate-token")
    public ResponseEntity<?> validateToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "No token provided"));
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            Optional<User> userOpt = userService.findByUsername(username);
            if (!userOpt.isPresent() || !userOpt.get().canAuthenticate()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
            }

            if (!jwtUtil.isTokenValid(token, username)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
            }

            String role = userOpt.get().getRole();
            Map<String, String> body = new HashMap<>();
            body.put("username", username);
            body.put("role", role == null ? "USER" : role);
            if (userOpt.get().getId() != null && !userOpt.get().getId().isBlank()) {
                body.put("id", userOpt.get().getId());
            }
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("Backend is working!");
    }

    @PostMapping("/test-password")
    public ResponseEntity<?> testPassword() {
        return ResponseEntity.status(404).body(Map.of("error", "Not found"));
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded;
        }
        return request.getRemoteAddr();
    }

    private static String safeClientMessage(Exception e) {
        return safeClientMessage(e, "Request failed");
    }

    private static String safeClientMessage(Exception e, String fallback) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return fallback;
        }
        String message = e.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("smtp")
            || lower.contains("password")
            || lower.contains("mongodb")
            || lower.contains("jdbc")
            || lower.contains("exception")
            || lower.contains("stack")) {
            return fallback;
        }
        return message;
    }
}
