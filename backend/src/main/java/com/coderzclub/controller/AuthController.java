package com.coderzclub.controller;

import com.coderzclub.config.JwtUtil;
import com.coderzclub.dto.JwtResponse;
import com.coderzclub.dto.LoginRequest;
import com.coderzclub.dto.PasswordResetConfirmRequest;
import com.coderzclub.dto.PasswordResetRequest;
import com.coderzclub.dto.RegisterRequest;
import com.coderzclub.model.User;
import com.coderzclub.service.EmailService;
import com.coderzclub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            System.out.println("Registration attempt for username: " + req.getUsername() + ", email: " + req.getEmail());
            String role = req.getRole() != null ? req.getRole() : "user";
            User user = userService.registerUser(req.getUsername(), req.getEmail(), req.getPassword(), role);
            System.out.println("User registered successfully: " + user.getUsername() + ", email verified: " + user.isEmailVerified());
            emailService.sendVerificationEmail(user.getEmail(), user.getEmailVerificationToken());
            return ResponseEntity.ok(Map.of("message", "Registration successful. A verification email has been sent."));
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<?> confirmEmail(@RequestParam String token) {
        try {
            userService.verifyEmailToken(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        System.out.println("=== LOGIN DEBUG ===");
        System.out.println("Login attempt for identifier: " + req.getUsername());
        Optional<User> userOpt = userService.findByUsernameOrEmail(req.getUsername());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("User found: " + user.getUsername());
            if (!user.isEmailVerified()) {
                System.out.println("Login blocked: email not verified for " + user.getUsername());
                return ResponseEntity.status(401).body(Map.of("error", "Email is not verified. Please verify your email before logging in."));
            }
            
            boolean passwordMatch = userService.checkPassword(req.getPassword(), user.getPasswordHash());
            if (passwordMatch) {
                String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
                System.out.println("Generated token successfully");
                return ResponseEntity.ok(new JwtResponse(token, user.getRole()));
            } else {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
            }
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @PostMapping("/password-reset-request")
    public ResponseEntity<?> passwordResetRequest(@RequestBody PasswordResetRequest request) {
        try {
            User user = userService.createPasswordResetToken(request.getEmail());
            emailService.sendPasswordResetEmail(user.getEmail(), user.getPasswordResetToken());
            return ResponseEntity.ok(Map.of("message", "Password reset instructions have been sent if the email exists."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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

    @GetMapping("/validate-token")
    public ResponseEntity<?> validateToken(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "No token provided"));
            }
            
            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);
            Optional<User> userOpt = userService.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(401).body(Map.of("error", "User not found"));
            }
            
            if (!jwtUtil.isTokenValid(token, username)) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
            }
            
            return ResponseEntity.ok(Map.of("username", username, "role", role));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok("Backend is working!");
    }

    @PostMapping("/test-password")
    public ResponseEntity<?> testPassword(@RequestBody LoginRequest req) {
        String encoded = userService.getPasswordEncoder().encode(req.getPassword());
        boolean matches = userService.checkPassword(req.getPassword(), encoded);
        String encoded2 = userService.getPasswordEncoder().encode(req.getPassword());
        boolean matches2 = userService.checkPassword(req.getPassword(), encoded2);
        return ResponseEntity.ok(Map.of(
            "original", req.getPassword(),
            "encoded", encoded,
            "matches", matches,
            "encoded2", encoded2,
            "matches2", matches2
        ));
    }
}
