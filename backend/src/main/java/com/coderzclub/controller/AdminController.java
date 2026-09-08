package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
            
            // Get counts without loading all data (more efficient)
            stats.put("totalUsers", userRepository.count());
            stats.put("totalProblems", problemRepository.count());
            stats.put("totalSubmissions", submissionRepository.count());
            
            // For statistics, we still need some data, but limit to recent entries
            List<Submission> recentSubmissions = submissionRepository.findAll(
                PageRequest.of(0, 1000, Sort.by("createdAt").descending())
            ).getContent();
            
            stats.put("submissionsByResult", recentSubmissions.stream()
                .collect(Collectors.groupingBy(Submission::getResult, Collectors.counting())));
            
            // Get first page of problems for difficulty stats (limit to 500)
            List<Problem> problems = problemRepository.findAll(
                PageRequest.of(0, 500)
            ).getContent();
            
            stats.put("problemsByDifficulty", problems.stream()
                .collect(Collectors.groupingBy(Problem::getDifficulty, Collectors.counting())));
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching dashboard stats: " + e.getMessage());
        }
    }
    
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("_id").ascending());
            Page<User> usersPage = userRepository.findAll(pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("users", usersPage.getContent().stream()
                .peek(user -> user.setPasswordHash(null))  // Remove sensitive data
                .collect(Collectors.toList()));
            response.put("currentPage", page);
            response.put("totalPages", usersPage.getTotalPages());
            response.put("totalItems", usersPage.getTotalElements());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching users: " + e.getMessage());
        }
    }
    
    @GetMapping("/submissions")
    public ResponseEntity<?> getAllSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Submission> submissionsPage = submissionRepository.findAll(pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("submissions", submissionsPage.getContent());
            response.put("currentPage", page);
            response.put("totalPages", submissionsPage.getTotalPages());
            response.put("totalItems", submissionsPage.getTotalElements());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching submissions: " + e.getMessage());
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
            // Limit to last 1000 submissions for performance
            List<Submission> recentSubmissions = submissionRepository.findAll(
                PageRequest.of(0, 1000, Sort.by("createdAt").descending())
            ).getContent();
            
            Map<String, Object> analytics = new HashMap<>();
            
            // Daily submissions for last 30 days
            Map<String, Long> dailySubmissions = recentSubmissions.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().after(new java.util.Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)))
                .collect(Collectors.groupingBy(
                    s -> new java.text.SimpleDateFormat("yyyy-MM-dd").format(s.getCreatedAt()),
                    Collectors.counting()
                ));
            
            analytics.put("dailySubmissions", dailySubmissions);
            
            // Language distribution
            Map<String, Long> languageDistribution = recentSubmissions.stream()
                .collect(Collectors.groupingBy(Submission::getLanguage, Collectors.counting()));
            
            analytics.put("languageDistribution", languageDistribution);
            
            // Success rate by language
            Map<String, Double> successRateByLanguage = recentSubmissions.stream()
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
