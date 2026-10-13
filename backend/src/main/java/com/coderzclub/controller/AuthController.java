package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.dto.JwtResponse;
import com.coderzclub.dto.LoginRequest;
import com.coderzclub.dto.PasswordResetConfirmRequest;
import com.coderzclub.dto.PasswordResetRequest;
import com.coderzclub.dto.RegisterRequest;
import com.coderzclub.dto.ResendVerificationRequest;
import com.coderzclub.model.User;
import com.coderzclub.service.EmailService;
import com.coderzclub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;
    
    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin) {
        try {
            User user = userService.registerUser(req.getUsername(), req.getEmail(), req.getPassword());

            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerificationToken(), clientOrigin);
                return ResponseEntity.ok(Map.of(
                    "message", "Registration successful. A verification email has been sent.",
                    "emailSent", true
                ));
            } catch (Exception emailEx) {
                logger.warn("Verification email was not delivered for {}: {}", user.getUsername(), emailEx.getClass().getSimpleName());
                return ResponseEntity.ok(Map.of(
                    "message", "Registration successful, but the verification email could not be sent. Please contact support or try again later.",
                    "emailSent", false
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "Verification failed"
                : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req,
                                                @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin) {
        try {
            User user = userService.resendVerificationEmail(req.getUsernameOrEmail());
            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerificationToken(), clientOrigin);
                return ResponseEntity.ok(Map.of(
                    "message", "Verification email resent. Please check your inbox.",
                    "emailSent", true
                ));
            } catch (Exception emailEx) {
                logger.warn("Verification email was not delivered for {}: {}", user.getUsername(), emailEx.getClass().getSimpleName());
                return ResponseEntity.ok(Map.of(
                    "message", "User found, but the verification email could not be sent. Please contact support or try again later.",
                    "emailSent", false
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<User> userOpt = userService.findByUsernameOrEmail(req.getUsername());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isDeleted()) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }
            if (!user.isEmailVerified()) {
                return ResponseEntity.status(401).body(Map.of("error", "Email is not verified. Please verify your email before logging in."));
            }
            
            boolean passwordMatch = userService.checkPassword(req.getPassword(), user.getPasswordHash());
            if (passwordMatch) {
                String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
                return ResponseEntity.ok(new JwtResponse(token, user.getRole()));
            }
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/password-reset-request")
    public ResponseEntity<?> passwordResetRequest(@RequestBody PasswordResetRequest request) {
        String email = request == null ? null : request.getEmail();
        Optional<User> user = userService.createPasswordResetToken(email);
        if (user.isPresent()) {
            try {
                emailService.sendPasswordResetEmail(user.get().getEmail(), user.get().getPasswordResetToken());
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of(
            "message", "If an eligible account exists for that email, reset instructions have been sent."));
    }

    @PostMapping("/password-reset-confirm")
    public ResponseEntity<?> passwordResetConfirm(@RequestBody PasswordResetConfirmRequest request) {
        try {
            userService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            if (!userOpt.isPresent() || userOpt.get().isDeleted()) {
                return ResponseEntity.status(401).body(Map.of("error", "User not found"));
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
}
