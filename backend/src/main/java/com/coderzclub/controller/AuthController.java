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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {
    @Autowired
    private UserService userService;
    
    @Autowired
    private EmailService emailService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${app.frontend.url:${app.backend.url:http://localhost:8080}}")
    private String frontendUrl;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req,
                                      @RequestHeader(value = "X-Client-Origin", required = false) String clientOrigin) {
        try {
            String role = req.getRole() != null ? req.getRole() : "user";
            User user = userService.registerUser(req.getUsername(), req.getEmail(), req.getPassword(), role);

            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerificationToken(), clientOrigin);
                return ResponseEntity.ok(Map.of(
                    "message", "Registration successful. A verification email has been sent.",
                    "emailSent", true
                ));
            } catch (Exception emailEx) {
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
    public ResponseEntity<?> confirmEmail(@RequestParam String token, HttpServletRequest request) {
        try {
            userService.verifyEmailToken(token);
            // Redirect to frontend auth page with success message
            return ResponseEntity.status(302).header("Location", "/auth?verified=true").build();
        } catch (Exception e) {
            try {
                String encodedError = java.net.URLEncoder.encode(e.getMessage(), "UTF-8");
                return ResponseEntity.status(302).header("Location", "/auth?error=" + encodedError).build();
            } catch (Exception encodeEx) {
                return ResponseEntity.status(302).header("Location", "/auth?error=Verification%20failed").build();
            }
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
                String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
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
            return ResponseEntity.ok(Map.of("username", username, "role", role == null ? "USER" : role));
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
