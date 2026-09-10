package com.coderzclub.controller;

import com.coderzclub.model.Problem;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserRepository;
//import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.service.SubmissionJobService;

import com.coderzclub.service.SubmissionLimitService;
import com.coderzclub.service.SubmissionValidator;
//import com.coderzclub.model.Problem;
import com.coderzclub.dto.CreateSubmissionJobRequest;
import com.coderzclub.dto.SubmissionJobResponse;
import com.coderzclub.dto.TestResultResponse;

import com.coderzclub.service.SubmissionLimitDecision;
import com.coderzclub.service.SubmissionValidationService;
import com.coderzclub.service.SubmissionJobEventService;
import com.coderzclub.service.SubmissionQueueAdmissionService;
import com.coderzclub.service.SubmissionJobAccessService;
import com.coderzclub.service.SubmissionJobSseTicketService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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



    @Autowired
    private SubmissionValidationService validationService;

    @Autowired
    private SubmissionJobEventService eventService;

    @Autowired
    private SubmissionQueueAdmissionService queueAdmissionService;

    @Autowired
    private SubmissionJobAccessService jobAccessService;

    @Autowired
    private SubmissionJobSseTicketService sseTicketService;


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

            SubmissionQueueAdmissionService.Admission admission = queueAdmissionService.check();
            if (!admission.allowed()) {
                return ResponseEntity.status(admission.reason().equals("QUEUE_UNAVAILABLE")
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                        "error", "Submission service is busy. Please retry later.",
                        "reason", admission.reason(),
                        "retryAfterSeconds", 30));
            }

            SubmissionLimitDecision decision = submissionLimitService.tryAcquireSubmissionSlot(user.getId(), request.getProblemId());
            if (!decision.isAllowed()) {
                switch (decision.getReason()) {
                    case "COOLDOWN" ->
                        {return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                            "error", "Please wait before submitting again.",
                            "retryAfterSeconds", submissionLimitService.getCooldownSeconds(user.getId())
                        ));}
                    case "DAILY_LIMIT" ->
                        {return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                            "error", "Daily submission limit exceeded."
                        ));}
                    case "PROBLEM_LIMIT" ->
                        {return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                            "error", "Daily submission limit for this problem exceeded."
                        ));}
                    case "REDIS_UNAVAILABLE" ->
                        {return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                            "error", "Rate limit service unavailable. Please try again later."
                        ));}
                    default ->
                        {return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                            "error", "Submission limit exceeded."
                        ));}
                }
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

            validationService.validateSubmissionRequest(request.getProblemId(), request.getCode(), request.getLanguageId());

            // Resolve problem test cases server-side so hidden data never travels in the public submission payload.
            SubmissionJob job = jobService.createJob(
                user.getId(),
                request.getProblemId(),
                request.getCode(),
                request.getLanguage(),
                request.getLanguageId(),

                problem.getTestcaseVersion(),
                totalTests,
                problem.getExecutionMode()

                //publicTests,
                //hiddenTests

            );

            // Note: the submission attempt is already acquired atomically above.
            // If job creation fails after acquisition, the attempt is counted and not compensated.

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
                List<com.coderzclub.model.SubmissionTestResult> storedResults = jobService.getResults(job);
                List<TestResultResponse> sanitized = new java.util.ArrayList<>();
                for (int i = 0; i < (storedResults != null ? storedResults.size() : 0); i++) {
                    com.coderzclub.model.SubmissionTestResult r = storedResults.get(i);
                    if (r.getTestcaseType() == com.coderzclub.model.SubmissionTestResult.TestcaseType.PUBLIC) {
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

    @GetMapping(value = "/{jobId}/events", produces = "text/event-stream")
    public ResponseEntity<SseEmitter> streamJobEvents(@PathVariable String jobId,
                                                      @RequestParam String ticket,
                                                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        Optional<SubmissionJob> jobOpt = jobService.getJob(jobId);
        if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();
        if (sseTicketService.consume(ticket, jobId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        SseEmitter emitter = eventService.register(jobId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-cache, no-transform")
            .header("X-Accel-Buffering", "no")
            .header("Connection", "keep-alive").body(emitter);
    }

    private boolean canViewJob(Authentication authentication, SubmissionJob job) {
        return jobAccessService.canView(authentication, job);
    }

    @PostMapping("/{jobId}/events/ticket")
    public ResponseEntity<?> createSseTicket(@PathVariable String jobId, Authentication authentication) {
        Optional<SubmissionJob> jobOpt = jobService.getJob(jobId);
        if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();
        if (!canViewJob(authentication, jobOpt.get())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        Optional<User> user = userRepository.findByUsername(authentication.getName());
        if (user.isEmpty()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        SubmissionJobSseTicketService.Ticket ticket = sseTicketService.create(jobId, user.get().getId());
        return ResponseEntity.ok(Map.of("ticket", ticket.value(), "expiresInSeconds", ticket.expiresInSeconds()));
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