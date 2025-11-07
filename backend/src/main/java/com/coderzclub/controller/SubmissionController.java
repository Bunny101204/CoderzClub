package com.coderzclub.controller;

import com.coderzclub.model.Submission;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProblemRepository problemRepository;

    @PostMapping
    public ResponseEntity<?> submitSolution(@RequestBody SubmissionRequest request) {
        try {
            // Get current user from JWT
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            Optional<Problem> problemOpt = problemRepository.findById(request.getProblemId());
            if (!problemOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Problem not found");
            }
            
            Problem problem = problemOpt.get();
            
            // Create submission
            Submission submission = Submission.builder()
                .userId(user.getId())
                .problemId(request.getProblemId())
                .code(request.getCode())
                .language(request.getLanguage())
                .result(request.getResult())
                .output(request.getOutput())
                .build();
                
            submission = submissionRepository.save(submission);
            
            // Update user stats if solution is correct
            if ("ACCEPTED".equals(request.getResult())) {
                updateUserStats(user, problem);
            }
            
            return ResponseEntity.ok(submission);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error saving submission: " + e.getMessage());
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Submission>> getUserSubmissions(@PathVariable String userId) {
        List<Submission> submissions = submissionRepository.findByUserId(userId);
        return ResponseEntity.ok(submissions);
    }
    
    @GetMapping("/problem/{problemId}")
    public ResponseEntity<List<Submission>> getProblemSubmissions(@PathVariable String problemId) {
        List<Submission> submissions = submissionRepository.findByProblemId(problemId);
        return ResponseEntity.ok(submissions);
    }
    
    @GetMapping("/my-submissions")
    public ResponseEntity<List<Submission>> getMySubmissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            return ResponseEntity.badRequest().body(null);
        }
        
        List<Submission> submissions = submissionRepository.findByUserId(userOpt.get().getId());
        return ResponseEntity.ok(submissions);
    }
    
    private void updateUserStats(User user, Problem problem) {
        // Check if user already solved this problem
        if (user.getSolvedProblemIds() != null && 
            user.getSolvedProblemIds().contains(problem.getId())) {
            return; // Already solved
        }
        
        // Add problem to solved list
        if (user.getSolvedProblemIds() == null) {
            user.setSolvedProblemIds(new java.util.ArrayList<>());
        }
        user.getSolvedProblemIds().add(problem.getId());
        
        // Update stats
        user.setProblemsSolved(user.getProblemsSolved() + 1);
        user.setTotalPoints(user.getTotalPoints() + problem.getPoints());
        
        userRepository.save(user);
    }
    
    // DTO for submission request
    public static class SubmissionRequest {
        private String problemId;
        private String code;
        private String language;
        private String result;
        private String output;
        
        // Getters and setters
        public String getProblemId() { return problemId; }
        public void setProblemId(String problemId) { this.problemId = problemId; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
    }
}
