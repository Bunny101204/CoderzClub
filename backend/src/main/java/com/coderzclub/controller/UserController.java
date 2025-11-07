package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private SubmissionRepository submissionRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            // Don't expose password hash
            user.setPasswordHash(null);
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching profile: " + e.getMessage());
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            
            // Get submission stats
            List<com.coderzclub.model.Submission> submissions = submissionRepository.findByUserId(user.getId());
            long totalSubmissions = submissions.size();
            long acceptedSubmissions = submissions.stream()
                .filter(s -> "ACCEPTED".equals(s.getResult()))
                .count();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalProblemsSolved", user.getProblemsSolved());
            stats.put("totalPoints", user.getTotalPoints());
            stats.put("currentStreak", user.getCurrentStreak());
            stats.put("longestStreak", user.getLongestStreak());
            stats.put("totalSubmissions", totalSubmissions);
            stats.put("acceptedSubmissions", acceptedSubmissions);
            stats.put("successRate", totalSubmissions > 0 ? (double)acceptedSubmissions / totalSubmissions : 0.0);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching stats: " + e.getMessage());
        }
    }
    
    @GetMapping("/leaderboard")
    public ResponseEntity<List<User>> getLeaderboard() {
        try {
            List<User> topUsers = userRepository.findAll()
                .stream()
                .sorted((a, b) -> Integer.compare(b.getTotalPoints(), a.getTotalPoints()))
                .limit(50)
                .collect(java.util.stream.Collectors.toList());
            
            // Remove sensitive data
            topUsers.forEach(user -> user.setPasswordHash(null));
            
            return ResponseEntity.ok(topUsers);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            
            // Update allowed fields
            if (request.getBio() != null) user.setBio(request.getBio());
            if (request.getLocation() != null) user.setLocation(request.getLocation());
            if (request.getWebsite() != null) user.setWebsite(request.getWebsite());
            
            userRepository.save(user);
            user.setPasswordHash(null);
            
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating profile: " + e.getMessage());
        }
    }
    
    // DTO for profile update request
    public static class ProfileUpdateRequest {
        private String bio;
        private String location;
        private String website;
        
        // Getters and setters
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
    }
}
