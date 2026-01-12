package com.coderzclub.controller;

import com.coderzclub.model.Submission;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProblemRepository problemRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private com.coderzclub.service.SubmissionLimitService submissionLimitService;

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
            
            // Check submission limits
            if (!submissionLimitService.canSubmitNow(user.getId())) {
                long cooldown = submissionLimitService.getCooldownSeconds(user.getId());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "RATE_LIMIT_EXCEEDED");
                errorResponse.put("message", "Please wait before submitting again.");
                errorResponse.put("cooldownSeconds", cooldown);
                return ResponseEntity.status(429).body(errorResponse);
            }
            
            if (submissionLimitService.hasExceededDailyLimit(user.getId())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "DAILY_LIMIT_EXCEEDED");
                errorResponse.put("message", "You have exceeded your daily submission limit.");
                errorResponse.put("limit", 100);
                return ResponseEntity.status(429).body(errorResponse);
            }
            
            if (submissionLimitService.hasExceededProblemLimit(user.getId(), problem.getId())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "PROBLEM_LIMIT_EXCEEDED");
                errorResponse.put("message", "You have exceeded your submission limit for this problem today.");
                errorResponse.put("limit", 50);
                return ResponseEntity.status(429).body(errorResponse);
            }
            
            // Map Judge0 status to verdict
            String verdict = mapJudge0StatusToVerdict(request.getStatusId());
            
            // Create submission with enhanced fields
            Submission submission = Submission.builder()
                .userId(user.getId())
                .problemId(request.getProblemId())
                .code(request.getCode())
                .language(request.getLanguage())
                .result(request.getResult() != null ? request.getResult() : verdict)
                .output(request.getOutput())
                .runtime(request.getRuntime())
                .memory(request.getMemory())
                .errorMessage(request.getErrorMessage())
                .stderr(request.getStderr())
                .verdict(verdict)
                .passedTestCases(request.getPassedTestCases())
                .totalTestCases(request.getTotalTestCases())
                .executionDetails(request.getExecutionDetails())
                .build();
                
            submission = submissionRepository.save(submission);
            
            // Update user stats if solution is correct
            if ("ACCEPTED".equals(submission.getResult()) || "ACCEPTED".equals(verdict)) {
                updateUserStats(user, problem);
            }
            
            // Update streak for any submission (accepted or not)
            userService.updateUserStreak(user.getId());
            
            return ResponseEntity.ok(submission);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error saving submission: " + e.getMessage());
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserSubmissions(
        @PathVariable String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Submission> submissions = submissionRepository.findByUserId(userId, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("submissions", submissions.getContent());
            response.put("currentPage", submissions.getNumber());
            response.put("totalPages", submissions.getTotalPages());
            response.put("totalItems", submissions.getTotalElements());
            response.put("hasNext", submissions.hasNext());
            response.put("hasPrevious", submissions.hasPrevious());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching submissions: " + e.getMessage());
        }
    }
    
    @GetMapping("/problem/{problemId}")
    public ResponseEntity<?> getProblemSubmissions(
        @PathVariable String problemId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Submission> submissions = submissionRepository.findByProblemId(problemId, pageable);
            
            Map<String, Object> response = new HashMap<>();
            response.put("submissions", submissions.getContent());
            response.put("currentPage", submissions.getNumber());
            response.put("totalPages", submissions.getTotalPages());
            response.put("totalItems", submissions.getTotalElements());
            response.put("hasNext", submissions.hasNext());
            response.put("hasPrevious", submissions.hasPrevious());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching submissions: " + e.getMessage());
        }
    }
    
    @GetMapping("/my-submissions")
    public ResponseEntity<?> getMySubmissions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String problemId,
        @RequestParam(required = false) String result
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Submission> submissions;
            
            // Apply filters
            if (problemId != null && result != null) {
                submissions = submissionRepository.findByUserIdAndProblemIdAndResult(
                    userOpt.get().getId(), problemId, result, pageable);
            } else if (problemId != null) {
                submissions = submissionRepository.findByUserIdAndProblemId(
                    userOpt.get().getId(), problemId, pageable);
            } else if (result != null) {
                submissions = submissionRepository.findByUserIdAndResult(
                    userOpt.get().getId(), result, pageable);
            } else {
                submissions = submissionRepository.findByUserId(
                    userOpt.get().getId(), pageable);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("submissions", submissions.getContent());
            response.put("currentPage", submissions.getNumber());
            response.put("totalPages", submissions.getTotalPages());
            response.put("totalItems", submissions.getTotalElements());
            response.put("hasNext", submissions.hasNext());
            response.put("hasPrevious", submissions.hasPrevious());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching submissions: " + e.getMessage());
        }
    }
    
    @GetMapping("/limits")
    public ResponseEntity<?> getSubmissionLimits(@RequestParam(required = false) String problemId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            Optional<User> userOpt = userRepository.findByUsername(username);
            if (!userOpt.isPresent()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            Map<String, Object> limits = new HashMap<>();
            
            limits.put("remainingDaily", submissionLimitService.getRemainingDailySubmissions(user.getId()));
            limits.put("dailyLimit", 100);
            limits.put("canSubmitNow", submissionLimitService.canSubmitNow(user.getId()));
            limits.put("cooldownSeconds", submissionLimitService.getCooldownSeconds(user.getId()));
            
            if (problemId != null) {
                limits.put("remainingProblem", submissionLimitService.getRemainingProblemSubmissions(user.getId(), problemId));
                limits.put("problemLimit", 50);
                limits.put("hasExceededProblemLimit", submissionLimitService.hasExceededProblemLimit(user.getId(), problemId));
            }
            
            limits.put("hasExceededDailyLimit", submissionLimitService.hasExceededDailyLimit(user.getId()));
            
            return ResponseEntity.ok(limits);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching limits: " + e.getMessage());
        }
    }
    
    /**
     * Maps Judge0 status ID to human-readable verdict
     * Judge0 status IDs: https://ce.judge0.com/#statuses-and-languages-status-get
     */
    private String mapJudge0StatusToVerdict(Integer statusId) {
        if (statusId == null) {
            return "UNKNOWN";
        }
        
        switch (statusId) {
            case 1: return "IN_QUEUE";
            case 2: return "PROCESSING";
            case 3: return "ACCEPTED";
            case 4: return "WRONG_ANSWER";
            case 5: return "TIME_LIMIT_EXCEEDED";
            case 6: return "COMPILATION_ERROR";
            case 7: return "RUNTIME_ERROR_SIGSEGV";  // Segmentation fault
            case 8: return "RUNTIME_ERROR_SIGXFSZ";  // File size limit exceeded
            case 9: return "RUNTIME_ERROR_SIGFPE";    // Floating point exception
            case 10: return "RUNTIME_ERROR_SIGABRT"; // Abort signal
            case 11: return "RUNTIME_ERROR_NZEC";    // Non-zero exit code
            case 12: return "RUNTIME_ERROR_OTHER";    // Other runtime error
            case 13: return "INTERNAL_ERROR";
            case 14: return "EXEC_FORMAT_ERROR";
            default: return "UNKNOWN";
        }
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
        private Long runtime;
        private Long memory;
        private String errorMessage;
        private String stderr;
        private Integer statusId;  // Judge0 status ID
        private Integer passedTestCases;
        private Integer totalTestCases;
        private Map<String, Object> executionDetails;
        
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
        
        public Long getRuntime() { return runtime; }
        public void setRuntime(Long runtime) { this.runtime = runtime; }
        
        public Long getMemory() { return memory; }
        public void setMemory(Long memory) { this.memory = memory; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public String getStderr() { return stderr; }
        public void setStderr(String stderr) { this.stderr = stderr; }
        
        public Integer getStatusId() { return statusId; }
        public void setStatusId(Integer statusId) { this.statusId = statusId; }
        
        public Integer getPassedTestCases() { return passedTestCases; }
        public void setPassedTestCases(Integer passedTestCases) { this.passedTestCases = passedTestCases; }
        
        public Integer getTotalTestCases() { return totalTestCases; }
        public void setTotalTestCases(Integer totalTestCases) { this.totalTestCases = totalTestCases; }
        
        public Map<String, Object> getExecutionDetails() { return executionDetails; }
        public void setExecutionDetails(Map<String, Object> executionDetails) { this.executionDetails = executionDetails; }
    }
}
