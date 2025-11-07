package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProblemRepository problemRepository;
    
    @Autowired
    private SubmissionRepository submissionRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // User stats
            List<User> allUsers = userRepository.findAll();
            stats.put("totalUsers", allUsers.size());
            stats.put("activeUsers", allUsers.stream()
                .filter(user -> user.getLastActiveDate() != null && 
                    user.getLastActiveDate().after(new java.util.Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)))
                .count());
            
            // Problem stats
            List<Problem> allProblems = problemRepository.findAll();
            stats.put("totalProblems", allProblems.size());
            stats.put("problemsByDifficulty", allProblems.stream()
                .collect(Collectors.groupingBy(Problem::getDifficulty, Collectors.counting())));
            
            // Submission stats
            List<Submission> allSubmissions = submissionRepository.findAll();
            stats.put("totalSubmissions", allSubmissions.size());
            stats.put("submissionsByResult", allSubmissions.stream()
                .collect(Collectors.groupingBy(Submission::getResult, Collectors.counting())));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching dashboard stats: " + e.getMessage());
        }
    }
    
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        try {
            List<User> users = userRepository.findAll();
            // Remove sensitive data
            users.forEach(user -> user.setPasswordHash(null));
            return ResponseEntity.ok(users);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @GetMapping("/submissions")
    public ResponseEntity<List<Submission>> getAllSubmissions() {
        try {
            List<Submission> submissions = submissionRepository.findAll();
            return ResponseEntity.ok(submissions);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        try {
            userRepository.deleteById(userId);
            return ResponseEntity.ok("User deleted successfully");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting user: " + e.getMessage());
        }
    }
    
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable String userId, @RequestBody RoleUpdateRequest request) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            user.setRole(request.getRole());
            userRepository.save(user);
            
            return ResponseEntity.ok("User role updated successfully");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating user role: " + e.getMessage());
        }
    }
    
    @GetMapping("/analytics/submissions")
    public ResponseEntity<?> getSubmissionAnalytics() {
        try {
            List<Submission> submissions = submissionRepository.findAll();
            
            Map<String, Object> analytics = new HashMap<>();
            
            // Daily submissions for last 30 days
            Map<String, Long> dailySubmissions = submissions.stream()
                .filter(s -> s.getCreatedAt().after(new java.util.Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)))
                .collect(Collectors.groupingBy(
                    s -> new java.text.SimpleDateFormat("yyyy-MM-dd").format(s.getCreatedAt()),
                    Collectors.counting()
                ));
            
            analytics.put("dailySubmissions", dailySubmissions);
            
            // Language distribution
            Map<String, Long> languageDistribution = submissions.stream()
                .collect(Collectors.groupingBy(Submission::getLanguage, Collectors.counting()));
            
            analytics.put("languageDistribution", languageDistribution);
            
            // Success rate by language
            Map<String, Double> successRateByLanguage = submissions.stream()
                .collect(Collectors.groupingBy(
                    Submission::getLanguage,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        list -> {
                            long total = list.size();
                            long accepted = list.stream().filter(s -> "ACCEPTED".equals(s.getResult())).count();
                            return total > 0 ? (double) accepted / total : 0.0;
                        }
                    )
                ));
            
            analytics.put("successRateByLanguage", successRateByLanguage);
            
            return ResponseEntity.ok(analytics);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching analytics: " + e.getMessage());
        }
    }
    
    // DTO for role update request
    public static class RoleUpdateRequest {
        private String role;
        
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
