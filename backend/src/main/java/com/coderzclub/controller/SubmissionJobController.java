package com.coderzclub.controller;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.SubmissionJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/submission-jobs")
public class SubmissionJobController {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobController.class);

    @Autowired
    private SubmissionJobService jobService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Create a new submission job
     */
    @PostMapping
    public ResponseEntity<?> createJob(@RequestBody SubmissionJobRequest request) {
        try {
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            // Create job using actual user ID
            SubmissionJob job = jobService.createJob(
                user.getId(),
                request.getProblemId(),
                request.getCode(),
                request.getLanguage(),
                request.getLanguageId(),
                request.getPublicTestCases(),
                request.getHiddenTestCases()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", job.getId());
            response.put("status", job.getStatus().toString());
            response.put("createdAt", job.getCreatedAt());

            logger.info("Created submission job {} for user {}", job.getId(), username);

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            logger.error("Failed to create submission job", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to create submission job: " + e.getMessage()));
        }
    }

    /**
     * Get job status
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        try {
            Optional<SubmissionJob> jobOpt = jobService.getJob(jobId);
            if (!jobOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            SubmissionJob job = jobOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", job.getId());
            response.put("status", job.getStatus().toString());
            response.put("createdAt", job.getCreatedAt());
            response.put("startedAt", job.getStartedAt());
            response.put("completedAt", job.getCompletedAt());
            response.put("progress", Map.of(
                "completed", job.getCompletedTests(),
                "total", job.getTotalTests()
            ));

            if (job.getStatus() == SubmissionJob.JobStatus.COMPLETED) {
                response.put("result", job.getFinalResult());
                response.put("runtime", job.getTotalRuntime());
                response.put("memory", job.getTotalMemory());
                response.put("testResults", job.getTestResults());
            } else if (job.getStatus() == SubmissionJob.JobStatus.FAILED) {
                response.put("error", job.getErrorMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get job status", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get job status: " + e.getMessage()));
        }
    }

    /**
     * Get user's submission jobs
     */
    @GetMapping("/my-jobs")
    public ResponseEntity<?> getMyJobs(
        @RequestParam(defaultValue = "10") int limit
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            List<SubmissionJob> jobs = jobService.getUserJobs(username);
            // Limit results
            if (jobs.size() > limit) {
                jobs = jobs.subList(0, limit);
            }

            return ResponseEntity.ok(Map.of("jobs", jobs));

        } catch (Exception e) {
            logger.error("Failed to get user jobs", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get jobs: " + e.getMessage()));
        }
    }

    /**
     * Get queue statistics (for observability)
     */
    @GetMapping("/queue/stats")
    public ResponseEntity<?> getQueueStats() {
        try {
            SubmissionJobService.QueueStats stats = jobService.getQueueStats();

            Map<String, Object> response = new HashMap<>();
            response.put("pending", stats.getPending());
            response.put("running", stats.getRunning());
            response.put("completed", stats.getCompleted());
            response.put("failed", stats.getFailed());
            response.put("total", stats.getTotal());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to get queue stats", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to get queue stats: " + e.getMessage()));
        }
    }

    /**
     * Request DTO for creating jobs
     */
    public static class SubmissionJobRequest {
        private String problemId;
        private String code;
        private String language;
        private Integer languageId;
        private List<SubmissionJob.TestCase> publicTestCases;
        private List<SubmissionJob.TestCase> hiddenTestCases;

        // Getters and setters
        public String getProblemId() { return problemId; }
        public void setProblemId(String problemId) { this.problemId = problemId; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public Integer getLanguageId() { return languageId; }
        public void setLanguageId(Integer languageId) { this.languageId = languageId; }

        public List<SubmissionJob.TestCase> getPublicTestCases() { return publicTestCases; }
        public void setPublicTestCases(List<SubmissionJob.TestCase> publicTestCases) { this.publicTestCases = publicTestCases; }

        public List<SubmissionJob.TestCase> getHiddenTestCases() { return hiddenTestCases; }
        public void setHiddenTestCases(List<SubmissionJob.TestCase> hiddenTestCases) { this.hiddenTestCases = hiddenTestCases; }
    }
}