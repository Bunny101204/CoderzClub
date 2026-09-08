package com.coderzclub.controller;

import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserStatsRepository;
import com.coderzclub.model.UserStats;
import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.service.LeaderboardService;
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
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserStatsRepository userStatsRepository;

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
            
            UserStats stored = userStatsRepository.findById(user.getId()).orElse(null);
            long totalSubmissions = stored == null ? 0 : stored.getTotalSubmissions();
            long acceptedSubmissions = stored == null ? 0 : stored.getAcceptedSubmissions();
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalProblemsSolved", stored == null ? user.getProblemsSolved() : stored.getProblemsSolved());
            stats.put("totalPoints", stored == null ? user.getTotalPoints() : stored.getTotalPoints());
            stats.put("currentStreak", stored == null ? user.getCurrentStreak() : stored.getCurrentStreak());
            stats.put("longestStreak", stored == null ? user.getLongestStreak() : stored.getLongestStreak());
            stats.put("totalSubmissions", totalSubmissions);
            stats.put("acceptedSubmissions", acceptedSubmissions);
            stats.put("successRate", totalSubmissions > 0 ? (double)acceptedSubmissions / totalSubmissions : 0.0);
            
            return ResponseEntity.ok(stats);
            
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
