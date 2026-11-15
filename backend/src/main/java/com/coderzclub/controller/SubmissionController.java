package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.model.Submission;
import com.coderzclub.repository.SubmissionRepository;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.model.User;
import com.coderzclub.model.Problem;
import com.coderzclub.service.SubmissionAccessService;
import com.coderzclub.service.SubmissionLimitDecision;
import com.coderzclub.service.SubmissionService;
import com.coderzclub.service.SubmissionValidationService;
import com.coderzclub.service.ProblemAccessService;
import com.coderzclub.service.UserProgressService;
import com.coderzclub.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubmissionController.class);
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ProblemRepository problemRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private com.coderzclub.service.SubmissionLimitService submissionLimitService;

    @Autowired
    private SubmissionValidationService validationService;

    @Autowired
    private UserProgressService userProgressService;

    @Autowired
    private ProblemAccessService problemAccessService;

    @Autowired
    private SubmissionAccessService submissionAccessService;

    /**
     * Legacy compatibility endpoint. Client-provided Judge0 verdicts, scores, and
     * execution results are never authoritative. Rewards and ACCEPTED status are
     * only applied through {@link SubmissionService#createSubmissionFromJob}.
     * Legitimate practice callers should use {@code POST /api/submission-jobs}.
     */
    @PostMapping
    public ResponseEntity<?> submitSolution(@RequestBody SubmissionRequest request) {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            
            User user = userOpt.get();
            Integer languageId = request.getLanguageId();
            validationService.validateSubmissionRequest(request.getProblemId(), request.getCode(), languageId);
            Optional<Problem> problemOpt = problemRepository.findById(request.getProblemId());
            if (!problemOpt.isPresent()) {
                return ResponseEntity.badRequest().body("Problem not found");
            }
            
            Problem problem = problemOpt.get();
            problemAccessService.requirePracticeExecute(user, problem);
            
            SubmissionLimitDecision decision = submissionLimitService.tryAcquireSubmissionSlot(user.getId(), problem.getId());
            if (!decision.isAllowed()) {
                Map<String, Object> errorResponse = new HashMap<>();
                switch (decision.getReason()) {
                    case "COOLDOWN" -> {
                        errorResponse.put("error", "RATE_LIMIT_EXCEEDED");
                        errorResponse.put("message", "Please wait before submitting again.");
                        errorResponse.put("cooldownSeconds", submissionLimitService.getCooldownSeconds(user.getId()));
                        return ResponseEntity.status(429).body(errorResponse);
                    }
                    case "DAILY_LIMIT" -> {
                        errorResponse.put("error", "DAILY_LIMIT_EXCEEDED");
                        errorResponse.put("message", "You have exceeded your daily submission limit.");
                        errorResponse.put("limit", 100);
                        return ResponseEntity.status(429).body(errorResponse);
                    }
                    case "PROBLEM_LIMIT" -> {
                        errorResponse.put("error", "PROBLEM_LIMIT_EXCEEDED");
                        errorResponse.put("message", "You have exceeded your submission limit for this problem today.");
                        errorResponse.put("limit", 50);
                        return ResponseEntity.status(429).body(errorResponse);
                    }
                    case "REDIS_UNAVAILABLE" -> {
                        errorResponse.put("error", "RATE_LIMIT_UNAVAILABLE");
                        errorResponse.put("message", "Rate limit service unavailable. Please try again later.");
                        return ResponseEntity.status(503).body(errorResponse);
                    }
                    default -> {
                        errorResponse.put("error", "RATE_LIMIT_EXCEEDED");
                        errorResponse.put("message", "Submission limit exceeded.");
                        return ResponseEntity.status(429).body(errorResponse);
                    }
                }
            }

            Map<String, Object> executionDetails = new HashMap<>();
            if (request.getExecutionDetails() != null) {
                executionDetails.put("clientReported", Map.copyOf(request.getExecutionDetails()));
            }
            Map<String, Object> clientClaim = new HashMap<>();
            if (request.getStatusId() != null) {
                clientClaim.put("statusId", request.getStatusId());
            }
            if (request.getResult() != null) {
                clientClaim.put("result", request.getResult());
            }
            if (request.getPassedTestCases() != null) {
                clientClaim.put("passedTestCases", request.getPassedTestCases());
            }
            if (request.getTotalTestCases() != null) {
                clientClaim.put("totalTestCases", request.getTotalTestCases());
            }
            if (!clientClaim.isEmpty()) {
                executionDetails.put("clientVerdictClaim", clientClaim);
            }
            executionDetails.put("scoringPathway", "REQUIRES_SUBMISSION_JOB");
            executionDetails.put("authoritative", Boolean.FALSE);

            // Never persist client ACCEPTED / Judge0 status as the server verdict.
            Submission submission = Submission.builder()
                .userId(user.getId())
                .problemId(request.getProblemId())
                .code(request.getCode())
                .language(request.getLanguage())
                .result("UNVERIFIED")
                .output(null)
                .runtime(null)
                .memory(null)
                .errorMessage(null)
                .stderr(null)
                .verdict("UNVERIFIED")
                .passedTestCases(null)
                .totalTestCases(null)
                .executionDetails(executionDetails.isEmpty() ? null : executionDetails)
                .build();
                
            submission = submissionRepository.save(submission);

            // Note: the rate limit token has already been acquired above.
            // If save fails after acquisition, the attempt is counted and not compensated.
            // Rewards are intentionally never applied from this legacy endpoint.

            userService.updateUserStreak(user.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("submission", submission);
            response.put("scoring", "REQUIRES_SUBMISSION_JOB");
            response.put("message",
                "Legacy submission recorded without a trusted verdict. Use POST /api/submission-jobs for judged scoring.");
            return ResponseEntity.ok(response);
            
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSubmissionById(@PathVariable String id) {
        try {
            Optional<User> current = currentUser();
            if (current.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            Optional<Submission> submissionOpt = submissionRepository.findById(id);
            if (submissionOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Submission not found"));
            }
            Submission submission = submissionOpt.get();
            if (!current.get().getId().equals(submission.getUserId())) {
                return ResponseEntity.status(404).body(Map.of("error", "Submission not found"));
            }
            submissionAccessService.requireViewOwnSubmission(current.get(), submission);
            return ResponseEntity.ok(submission);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserSubmissions(
        @PathVariable String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Optional<User> current = currentUser();
            if (current.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            if (!current.get().getId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot read another user's submissions"));
            }
            Pageable pageable = PageRequest.of(page, clampSize(size), Sort.by("createdAt").descending());
            return ResponseEntity.ok(visiblePagePayload(
                current.get(),
                submissionRepository.findByUserId(userId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }
    
    @GetMapping("/problem/{problemId}")
    public ResponseEntity<?> getProblemSubmissions(
        @PathVariable String problemId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        try {
            Optional<User> current = currentUser();
            if (current.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
            }
            if (!submissionAccessService.canListHistoryForProblem(current.get(), problemId)) {
                return ResponseEntity.ok(emptyPagePayload(page, clampSize(size)));
            }
            Pageable pageable = PageRequest.of(page, clampSize(size), Sort.by("createdAt").descending());
            Page<Submission> submissions = submissionRepository.findByUserIdAndProblemIdIn(
                current.get().getId(), problemAliases(problemId), pageable);
            return ResponseEntity.ok(visiblePagePayload(current.get(), submissions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }

    @GetMapping("/my-progress")
    public ResponseEntity<?> getMyProgress() {
        Optional<User> current = currentUser();
        if (current.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        return ResponseEntity.ok(Map.of("progress", userProgressService.progressForUser(current.get().getId())));
    }
    
    @GetMapping("/my-submissions")
    public ResponseEntity<?> getMySubmissions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String problemId,
        @RequestParam(required = false) String result
    ) {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("User not found");
            }
            User user = userOpt.get();
            if (problemId != null && !submissionAccessService.canListHistoryForProblem(user, problemId)) {
                return ResponseEntity.ok(emptyPagePayload(page, clampSize(size)));
            }
            
            Pageable pageable = PageRequest.of(page, clampSize(size), Sort.by("createdAt").descending());
            Page<Submission> submissions;
            String userId = user.getId();
            
            // Apply filters
            if (problemId != null && result != null) {
                submissions = submissionRepository.findByUserIdAndProblemIdAndResult(
                    userId, problemId, result, pageable);
            } else if (problemId != null) {
                submissions = submissionRepository.findByUserIdAndProblemIdIn(
                    userId, problemAliases(problemId), pageable);
            } else if (result != null) {
                submissions = submissionRepository.findByUserIdAndResult(
                    userId, result, pageable);
            } else {
                submissions = submissionRepository.findByUserId(userId, pageable);
            }
            
            return ResponseEntity.ok(visiblePagePayload(user, submissions));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }
    
    @GetMapping("/limits")
    public ResponseEntity<?> getSubmissionLimits(@RequestParam(required = false) String problemId) {
        try {
            Optional<User> userOpt = currentUser();
            if (userOpt.isEmpty()) {
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission request is invalid. Check your request and try again."));
        } catch (Exception e) {
            return unavailable(e);
        }
    }
    
    private ResponseEntity<?> unavailable(Exception error) {
        logger.error("Submission request failed", error);
        return ResponseEntity.status(503).body(Map.of(
            "error", "Submission service is temporarily unavailable. Please try again shortly.",
            "reason", "SUBMISSION_UNAVAILABLE"));
    }

    private Optional<User> currentUser() {
        return AuthenticatedUser.current(userRepository);
    }

    private int clampSize(int size) {
        return Math.min(50, Math.max(1, size));
    }

    private List<String> problemAliases(String problemId) {
        List<String> ids = new ArrayList<>();
        if (problemId != null && !problemId.isBlank()) {
            ids.add(problemId);
        }
        problemRepository.findById(problemId).ifPresent(problem -> {
            if (problem.getId() != null && !ids.contains(problem.getId())) {
                ids.add(problem.getId());
            }
            if (problem.getNumericId() != null) {
                String numeric = String.valueOf(problem.getNumericId());
                if (!ids.contains(numeric)) {
                    ids.add(numeric);
                }
            }
        });
        try {
            problemRepository.findByNumericId(Integer.valueOf(problemId)).ifPresent(problem -> {
                if (problem.getId() != null && !ids.contains(problem.getId())) {
                    ids.add(problem.getId());
                }
            });
        } catch (NumberFormatException ignored) {
        }
        return ids;
    }

    private Map<String, Object> visiblePagePayload(User viewer, Page<Submission> submissions) {
        List<Submission> visible = submissions.getContent().stream()
            .filter(submission -> submissionAccessService.canViewOwnSubmission(viewer, submission))
            .collect(Collectors.toList());
        long hidden = submissions.getNumberOfElements() - visible.size();
        Map<String, Object> response = new HashMap<>();
        response.put("submissions", visible);
        response.put("currentPage", submissions.getNumber());
        response.put("totalPages", submissions.getTotalPages());
        // totalItems is an upper bound when org-private rows are filtered out.
        response.put("totalItems", Math.max(0, submissions.getTotalElements() - hidden));
        response.put("hasNext", submissions.hasNext());
        response.put("hasPrevious", submissions.hasPrevious());
        return response;
    }

    private Map<String, Object> emptyPagePayload(int page, int size) {
        Map<String, Object> response = new HashMap<>();
        response.put("submissions", List.of());
        response.put("currentPage", page);
        response.put("totalPages", 0);
        response.put("totalItems", 0);
        response.put("hasNext", false);
        response.put("hasPrevious", page > 0);
        return response;
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
        private Integer languageId;
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

        public Integer getLanguageId() { return languageId; }
        public void setLanguageId(Integer languageId) { this.languageId = languageId; }
        
        public Integer getPassedTestCases() { return passedTestCases; }
        public void setPassedTestCases(Integer passedTestCases) { this.passedTestCases = passedTestCases; }
        
        public Integer getTotalTestCases() { return totalTestCases; }
        public void setTotalTestCases(Integer totalTestCases) { this.totalTestCases = totalTestCases; }
        
        public Map<String, Object> getExecutionDetails() { return executionDetails; }
        public void setExecutionDetails(Map<String, Object> executionDetails) { this.executionDetails = executionDetails; }
    }
}
