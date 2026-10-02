package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.dto.UserProfileStatsResponse;
import com.coderzclub.service.LeaderboardService;
import com.coderzclub.service.UserProgressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private UserProgressService userProgressService;

    @Autowired
    private LeaderboardService leaderboardService;

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
            UserProfileStatsResponse stats = userProgressService.profileStats(user);
            Map<String, Object> body = new HashMap<>();
            body.put("totalProblemsSolved", stats.getUniqueProblemsSolved());
            body.put("uniqueProblemsSolved", stats.getUniqueProblemsSolved());
            body.put("totalPoints", stats.getTotalPoints());
            body.put("currentStreak", stats.getCurrentStreak());
            body.put("longestStreak", stats.getLongestStreak());
            body.put("totalSubmissions", stats.getCompletedStudentSubmissions());
            body.put("acceptedSubmissions", stats.getAcceptedJudgedSubmissions());
            body.put("acceptedJudgedSubmissions", stats.getAcceptedJudgedSubmissions());
            body.put("completedStudentSubmissions", stats.getCompletedStudentSubmissions());
            body.put("successRate", stats.getSuccessRate());
            body.put("successRateDefinition", stats.getSuccessRateDefinition());
            body.put("difficultySolved", stats.getDifficultySolved());
            body.put("activity", stats.getActivity());
            body.put("activityTimezone", stats.getActivityTimezone());
            return ResponseEntity.ok(body);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching stats: " + e.getMessage());
        }
    }
    
    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "false") boolean includeRank) {
        try {
            List<LeaderboardEntry> topUsers = leaderboardService.top(limit);
            if (!includeRank) return ResponseEntity.ok(topUsers);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth == null ? null : auth.getName();
            Long rank = username == null ? null : userRepository.findByUsername(username)
                .map(user -> leaderboardService.rank(user.getId())).orElse(null);
            return ResponseEntity.ok(Map.of("users", topUsers, "rank", rank == null ? 0 : rank));
            
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
