package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.repository.SubmissionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class SubmissionJobService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobService.class);

    @Autowired
    private SubmissionJobRepository jobRepository;

    @Autowired
    private Judge0ExecutionService executionService;

    @Autowired
    private SubmissionService submissionService;

    /**
     * Create a new submission job
     */
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, List<com.coderzclub.model.TestCase> publicTestCases,
                                   List<com.coderzclub.model.TestCase> hiddenTestCases) {
        SubmissionJob job = new SubmissionJob();
        job.setUserId(userId);
        job.setProblemId(problemId);
        job.setCode(code);
        job.setLanguage(language);
        job.setLanguageId(languageId);
        // Convert model.TestCase to SubmissionJob.TestCase
        java.util.List<SubmissionJob.TestCase> jobPublic = new java.util.ArrayList<>();
        if (publicTestCases != null) {
            for (com.coderzclub.model.TestCase tc : publicTestCases) {
                jobPublic.add(new SubmissionJob.TestCase(tc.getInput(), tc.getOutput(), tc.getExplanation()));
            }
        }

        java.util.List<SubmissionJob.TestCase> jobHidden = new java.util.ArrayList<>();
        if (hiddenTestCases != null) {
            for (com.coderzclub.model.TestCase tc : hiddenTestCases) {
                jobHidden.add(new SubmissionJob.TestCase(tc.getInput(), tc.getOutput(), tc.getExplanation()));
            }
        }

        job.setPublicTestCases(jobPublic);
        job.setHiddenTestCases(jobHidden);
        job.setStatus(SubmissionJob.JobStatus.PENDING);
        job.setTotalTests((publicTestCases != null ? publicTestCases.size() : 0) +
                         (hiddenTestCases != null ? hiddenTestCases.size() : 0));

        job = jobRepository.save(job);

        logger.info("submission_job_created id={} userId={} problemId={} language={} languageId={} totalTests={} codeLength={}",
            job.getId(), userId, problemId, language, languageId, job.getTotalTests(), code != null ? code.length() : 0);

        // Start processing asynchronously
        processJobAsync(job.getId());

        return job;
    }

    /**
     * Get job by ID
     */
    public Optional<SubmissionJob> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * Get jobs for user
     */
    public List<SubmissionJob> getUserJobs(String userId) {
        return jobRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get queue statistics for observability
     */
    public QueueStats getQueueStats() {
        long pending = jobRepository.countByStatus(SubmissionJob.JobStatus.PENDING);
        long running = jobRepository.countByStatus(SubmissionJob.JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(SubmissionJob.JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(SubmissionJob.JobStatus.FAILED);

        return new QueueStats(pending, running, completed, failed);
    }

    /**
     * Process job asynchronously
     */
    @Async
    public CompletableFuture<Void> processJobAsync(String jobId) {
        return CompletableFuture.runAsync(() -> {
            try {
                processJob(jobId);
            } catch (Exception e) {
                logger.error("Failed to process job {}", jobId, e);
                updateJobStatus(jobId, SubmissionJob.JobStatus.FAILED, e.getMessage());
            }
        });
    }

    /**
     * Process a submission job
     */
    private void processJob(String jobId) {
        Optional<SubmissionJob> jobOpt = jobRepository.findById(jobId);
        if (!jobOpt.isPresent()) {
            logger.warn("Job {} not found", jobId);
            return;
        }

        SubmissionJob job = jobOpt.get();

        // Update status to running
        job.setStatus(SubmissionJob.JobStatus.RUNNING);
        job.setStartedAt(new Date());
        jobRepository.save(job);

        logger.info("Processing submission job {} userId={} problemId={} language={} totalTests={}",
            jobId, job.getUserId(), job.getProblemId(), job.getLanguage(), job.getTotalTests());

        try {
            // Execute all test cases
            List<SubmissionJob.TestResult> results = executionService.executeTestCases(
                job.getCode(),
                job.getLanguageId(),
                job.getPublicTestCases(),
                job.getHiddenTestCases()
            );

            // Analyze results
            String finalResult = analyzeResults(results);
            Long maxRuntime = results.stream()
                .mapToLong(r -> r.getRuntime() != null ? r.getRuntime() : 0)
                .max().orElse(0);
            Long maxMemory = results.stream()
                .mapToLong(r -> r.getMemory() != null ? r.getMemory() : 0)
                .max().orElse(0);

        // Update job with results
            job.setTestResults(results);
            job.setFinalResult(finalResult);
            job.setTotalRuntime(maxRuntime);
            job.setTotalMemory(maxMemory);
            long passedTests = results.stream().filter(SubmissionJob.TestResult::isPassed).count();
            job.setCompletedTests(results.size());
            job.setStatus(SubmissionJob.JobStatus.COMPLETED);
            job.setCompletedAt(new Date());

            jobRepository.save(job);

            // Create final submission record
            submissionService.createSubmissionFromJob(job);

            logger.info("submission_job_completed jobId={} userId={} problemId={} result={} passedTests={}/{} runtimeMs={} memoryBytes={} processingTimeMs={} ",
                job.getId(), job.getUserId(), job.getProblemId(), finalResult,
                passedTests, results.size(), maxRuntime, maxMemory,
                job.getCompletedAt().getTime() - job.getStartedAt().getTime());

        } catch (Exception e) {
            logger.error("Job {} failed with error", job.getId(), e);

            // Observability: Log failure
            logger.error("submission_job_failed",
                "jobId", job.getId(),
                "userId", job.getUserId(),
                "problemId", job.getProblemId(),
                "error", e.getMessage(),
                "processingTimeMs", job.getStartedAt() != null ?
                    System.currentTimeMillis() - job.getStartedAt().getTime() : 0);

            job.setStatus(SubmissionJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(new Date());
            jobRepository.save(job);
        }
    }

    /**
     * Analyze test results to determine final verdict
     */
    private String analyzeResults(List<SubmissionJob.TestResult> results) {
        boolean allPassed = results.stream().allMatch(SubmissionJob.TestResult::isPassed);
        if (allPassed) {
            return "ACCEPTED";
        }

        // Check for specific failure types
        for (SubmissionJob.TestResult result : results) {
            if (!result.isPassed() && result.getErrorType() != null) {
                switch (result.getErrorType()) {
                    case "Compilation Error":
                        return "COMPILATION_ERROR";
                    case "Runtime Error":
                        return "RUNTIME_ERROR";
                    case "Time Limit Exceeded":
                        return "TIME_LIMIT_EXCEEDED";
                    case "Memory Limit Exceeded":
                        return "MEMORY_LIMIT_EXCEEDED";
                }
            }
        }

        return "WRONG_ANSWER";
    }

    /**
     * Update job status
     */
    private void updateJobStatus(String jobId, SubmissionJob.JobStatus status, String errorMessage) {
        Optional<SubmissionJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            SubmissionJob job = jobOpt.get();
            job.setStatus(status);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(new Date());
            jobRepository.save(job);
        }
    }

    /**
     * Queue statistics for observability
     */
    public static class QueueStats {
        private final long pending;
        private final long running;
        private final long completed;
        private final long failed;

        public QueueStats(long pending, long running, long completed, long failed) {
            this.pending = pending;
            this.running = running;
            this.completed = completed;
            this.failed = failed;
        }

        public long getPending() { return pending; }
        public long getRunning() { return running; }
        public long getCompleted() { return completed; }
        public long getFailed() { return failed; }
        public long getTotal() { return pending + running + completed + failed; }
    }
}