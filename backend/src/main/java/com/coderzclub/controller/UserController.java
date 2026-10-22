package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.AccountDeletionRequest;
import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.dto.UserProfileStatsResponse;
import com.coderzclub.dto.UserProfileResponse;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.AccountDeletionService;
import com.coderzclub.service.LeaderboardService;
import com.coderzclub.service.UserDataExportService;
import com.coderzclub.service.UserProgressService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private UserDataExportService userDataExportService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            if (user.isDeleted()) {
                return ResponseEntity.status(401).body(Map.of("error", "Account is no longer available"));
            }
            return ResponseEntity.ok(UserProfileResponse.from(user));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching profile");
        }
    }
    
    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats() {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
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
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Integer limit) {
        try {
            int requestedSize = limit == null ? size : limit;
            Map<String, Object> body = new java.util.LinkedHashMap<>(leaderboardService.page(page, requestedSize));
            currentUser()
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> {
                    java.util.Map<String, Object> viewer = new java.util.LinkedHashMap<>();
                    viewer.put("username", user.getUsername());
                    viewer.put("rank", leaderboardService.rank(user.getId()));
                    viewer.put("totalPoints", user.getTotalPoints());
                    viewer.put("problemsSolved", user.getProblemsSolved());
                    @SuppressWarnings("unchecked")
                    java.util.List<LeaderboardEntry> users = (java.util.List<LeaderboardEntry>) body.get("users");
                    viewer.put("onPage", users != null && users.stream().anyMatch(entry -> user.getId().equals(entry.getId())));
                    body.put("viewer", viewer);
                });
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("error", "Leaderboard is temporarily unavailable"));
        }
    }
    
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest request) {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            
            // Update allowed fields
            if (request.getBio() != null) user.setBio(request.getBio());
            if (request.getLocation() != null) user.setLocation(request.getLocation());
            if (request.getWebsite() != null) user.setWebsite(request.getWebsite());
            
            userRepository.save(user);
            return ResponseEntity.ok(UserProfileResponse.from(user));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating profile");
        }
    }

    @GetMapping("/me/export")
    public ResponseEntity<?> exportMyData() {
        User user = currentUser().orElse(null);
        if (user == null || user.isDeleted()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"coderzclub-data-export.json\"")
            .body(userDataExportService.export(user));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMyAccount(@RequestBody(required = false) AccountDeletionRequest body) {
        User user = currentUser().orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        String confirmation = body == null ? null : body.getConfirmation();
        return ResponseEntity.ok(accountDeletionService.deleteSelf(user, confirmation));
    }

    private Optional<User> currentUser() {
        return AuthenticatedUser.current(userRepository);
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
