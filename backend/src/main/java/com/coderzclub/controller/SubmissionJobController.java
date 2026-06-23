package com.coderzclub.controller;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.service.SubmissionJobService;
import com.coderzclub.service.SubmissionLimitService;
import com.coderzclub.service.SubmissionValidator;
import com.coderzclub.model.Problem;
import com.coderzclub.dto.CreateSubmissionJobRequest;
import com.coderzclub.dto.SubmissionJobResponse;
import com.coderzclub.dto.TestResultResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
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
    private SubmissionLimitService submissionLimitService;

    @Autowired
    private SubmissionValidator submissionValidator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    /**
     * Create a new submission job with strict validation
     */
    @PostMapping
    public ResponseEntity<?> createJob(@Valid @RequestBody CreateSubmissionJobRequest request) {
        try {
            // Step 1: Validate code size and content
            try {
                submissionValidator.validateCode(request.getCode());
            } catch (IllegalArgumentException e) {
                logger.warn("Code validation failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }

            // Step 2: Validate language ID
            try {
                submissionValidator.validateLanguageId(request.getLanguageId());
            } catch (IllegalArgumentException e) {
                logger.warn("Language validation failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }

            // Step 3: Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();

            Optional<User> userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            if (!submissionLimitService.canSubmitNow(user.getId())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                        "error", "Please wait before submitting again.",
                        "retryAfterSeconds", submissionLimitService.getCooldownSeconds(user.getId())
                    ));
            }

            if (submissionLimitService.hasExceededDailyLimit(user.getId())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Daily submission limit exceeded."));
            }

            if (submissionLimitService.hasExceededProblemLimit(user.getId(), request.getProblemId())) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Daily submission limit for this problem exceeded."));
            }

            // Step 4: Load problem and validate it exists
            Optional<Problem> problemOpt = problemRepository.findById(request.getProblemId());
            if (problemOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Problem not found"));
            }
            Problem problem = problemOpt.get();

            int totalTests = (problem.getPublicTestCases() != null ? problem.getPublicTestCases().size() : 0)
                + (problem.getHiddenTestCases() != null ? problem.getHiddenTestCases().size() : 0);

            logger.info("submission_request_received user={} problemId={} language={} languageId={} totalTests={} codeLength={}",
                username, request.getProblemId(), request.getLanguage(), request.getLanguageId(),
                totalTests, request.getCode() != null ? request.getCode().length() : 0);

            // Step 5: Validate problem testcases against limits
            try {
                submissionValidator.validateProblemTestCases(problem);
            } catch (IllegalArgumentException e) {
                logger.error("Problem testcases validation failed: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Problem configuration error: " + e.getMessage()));
            }

            // Step 6: Create job using server-side testcases
            SubmissionJob job = jobService.createJob(
                user.getId(),
                request.getProblemId(),
                request.getCode(),
                request.getLanguage(),
                request.getLanguageId(),
                problem.getPublicTestCases(),
                problem.getHiddenTestCases()
            );

            // Record the submission attempt in rate limiter (Redis)
            try {
                submissionLimitService.recordSubmissionAttempt(user.getId(), request.getProblemId());
            } catch (Exception e) {
                logger.warn("Failed to record submission attempt: {}", e.getMessage());
            }

            SubmissionJobResponse resp = new SubmissionJobResponse();
            resp.setJobId(job.getId());
            resp.setStatus(job.getStatus().toString());
            resp.setCreatedAt(job.getCreatedAt());

            logger.info("Created submission job {} for user {} problemId={} totalTests={}",
                job.getId(), username, request.getProblemId(), job.getTotalTests());

            return ResponseEntity.accepted().body(resp);

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

            SubmissionJobResponse resp = new SubmissionJobResponse();
            resp.setJobId(job.getId());
            resp.setStatus(job.getStatus().toString());
            resp.setCreatedAt(job.getCreatedAt());
            resp.setStartedAt(job.getStartedAt());
            resp.setCompletedAt(job.getCompletedAt());
            resp.setProgress(Map.of("completed", job.getCompletedTests(), "total", job.getTotalTests()));

            if (job.getStatus() == SubmissionJob.JobStatus.COMPLETED) {
                resp.setResult(job.getFinalResult());
                resp.setRuntime(job.getTotalRuntime());
                resp.setMemory(job.getTotalMemory());

                // Sanitize test results: do not leak hidden test input/expected output
                List<SubmissionJob.TestResult> storedResults = job.getTestResults();
                int publicCount = job.getPublicTestCases() != null ? job.getPublicTestCases().size() : 0;
                List<TestResultResponse> sanitized = new java.util.ArrayList<>();
                for (int i = 0; i < (storedResults != null ? storedResults.size() : 0); i++) {
                    SubmissionJob.TestResult r = storedResults.get(i);
                    if (i < publicCount) {
                        TestResultResponse tr = new TestResultResponse();
                        tr.setInput(r.getInput());
                        tr.setExpectedOutput(r.getExpectedOutput());
                        tr.setActualOutput(r.getActualOutput());
                        tr.setPassed(r.isPassed());
                        tr.setRuntime(r.getRuntime());
                        tr.setMemory(r.getMemory());
                        tr.setErrorType(r.getErrorType());
                        tr.setErrorMessage(r.getErrorMessage());
                        sanitized.add(tr);
                    } else {
                        // Hidden test: never expose input or expected output
                        TestResultResponse tr = new TestResultResponse();
                        tr.setType("hidden");
                        tr.setStatus(r.isPassed() ? "PASSED" : "FAILED");
                        tr.setPassed(r.isPassed());
                        tr.setRuntime(r.getRuntime());
                        tr.setMemory(r.getMemory());
                        if (!r.isPassed()) tr.setMessage("Failed on hidden testcase");
                        sanitized.add(tr);
                    }
                }

                resp.setTestResults(sanitized);
            } else if (job.getStatus() == SubmissionJob.JobStatus.FAILED) {
                resp.setError(job.getErrorMessage());
            }

            return ResponseEntity.ok(resp);

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

            List<Map<String, Object>> summaries = new java.util.ArrayList<>();
            for (SubmissionJob job : jobs) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("jobId", job.getId());
                summary.put("problemId", job.getProblemId());
                summary.put("status", job.getStatus().toString());
                summary.put("createdAt", job.getCreatedAt());
                summary.put("startedAt", job.getStartedAt());
                summary.put("completedAt", job.getCompletedAt());
                summary.put("result", job.getFinalResult());
                summary.put("completedTests", job.getCompletedTests());
                summary.put("totalTests", job.getTotalTests());
                summaries.add(summary);
            }

            return ResponseEntity.ok(Map.of("jobs", summaries));

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

    // Request DTO for creating jobs moved to com.coderzclub.dto.CreateSubmissionJobRequest
}