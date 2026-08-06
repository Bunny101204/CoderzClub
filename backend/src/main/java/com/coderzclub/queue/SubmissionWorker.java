package com.coderzclub.queue;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.service.Judge0ExecutionService;
import com.coderzclub.service.SubmissionJobLeaseService;
import com.coderzclub.service.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SubmissionWorker {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionWorker.class);

    @Autowired
    private SubmissionQueuePublisher publisher;

    @Autowired
    private SubmissionJobRepository jobRepository;

    @Autowired
    private SubmissionJobLeaseService leaseService;

    @Autowired
    private Judge0ExecutionService executionService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private WorkerProperties workerProperties;

    private ExecutorService executor;
    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    public void startWorkers() {
        if (!workerProperties.isEnabled()) {
            logger.info("Submission worker disabled by configuration");
            return;
        }

        int concurrency = Math.max(1, workerProperties.getConcurrency());
        executor = Executors.newFixedThreadPool(concurrency);
        heartbeatExecutor = Executors.newScheduledThreadPool(Math.max(1, concurrency));
        for (int i = 0; i < concurrency; i++) {
            executor.submit(this::runWorkerLoop);
        }
        logger.info("Started submission worker fleet with concurrency={}", concurrency);
    }

    @Async
    public void runWorkerLoop() {
        if (!(publisher instanceof InMemorySubmissionQueuePublisher)) {
            logger.warn("Only in-memory submission queue publisher is supported by this worker implementation");
            return;
        }

        InMemorySubmissionQueuePublisher inMemoryPublisher = (InMemorySubmissionQueuePublisher) publisher;

        while (workerProperties.isEnabled()) {
            try {
                String jobId = inMemoryPublisher.getQueue().poll(workerProperties.getPollTimeoutSeconds(), TimeUnit.SECONDS);
                if (jobId == null) {
                    continue;
                }
                processJob(jobId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Submission worker interrupted", e);
                break;
            } catch (Exception e) {
                logger.error("Unexpected error in submission worker loop", e);
            }
        }
    }

    private void processJob(String jobId) {
        String workerId = UUID.randomUUID().toString();
        Optional<SubmissionJob> jobOpt = leaseService.claimJob(jobId, workerId, workerProperties.getLeaseDurationSeconds());
        if (jobOpt.isEmpty()) {
            return;
        }

        SubmissionJob job = jobOpt.get();
        long heartbeatInterval = Math.max(1, workerProperties.getLeaseDurationSeconds() / 2);
        ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
            () -> leaseService.heartbeat(jobId, workerId, workerProperties.getLeaseDurationSeconds()),
            heartbeatInterval,
            heartbeatInterval,
            TimeUnit.SECONDS
        );

        logger.info("Worker claimed job {} userId={} problemId={} attempt={} totalTests={} lockedBy={}",
            jobId, job.getUserId(), job.getProblemId(), job.getAttemptCount(), job.getTotalTests(), workerId);

        try {
            List<SubmissionJob.TestResult> results = executionService.executeTestCases(
                job.getCode(),
                job.getLanguageId(),
                job.getPublicTestCases(),
                job.getHiddenTestCases()
            );

            String finalResult = analyzeResults(results);
            Long maxRuntime = results.stream()
                .mapToLong(r -> r.getRuntime() != null ? r.getRuntime() : 0L)
                .max().orElse(0L);
            Long maxMemory = results.stream()
                .mapToLong(r -> r.getMemory() != null ? r.getMemory() : 0L)
                .max().orElse(0L);

            job.setTestResults(results);
            job.setFinalResult(finalResult);
            job.setTotalRuntime(maxRuntime);
            job.setTotalMemory(maxMemory);
            job.setCompletedTests(results.size());
            job.setStatus(SubmissionJob.JobStatus.COMPLETED);
            job.setCompletedAt(new Date());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setHeartbeatAt(null);
            job.setNextRetryAt(null);
            job.setLastError(null);
            jobRepository.save(job);

            submissionService.createSubmissionFromJob(job);

            logger.info("Job {} completed result={} passedTests={}/{} runtimeMs={} memoryBytes={} attempts={}",
                jobId, finalResult,
                results.stream().filter(SubmissionJob.TestResult::isPassed).count(), results.size(),
                maxRuntime, maxMemory, job.getAttemptCount());

        } catch (Exception e) {
            logger.error("Job {} failed during execution", jobId, e);
            job.setLastError(e.getMessage());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setHeartbeatAt(null);
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                job.setStatus(SubmissionJob.JobStatus.RETRYING);
                Date nextRetryAt = new Date(System.currentTimeMillis() + workerProperties.getRetryDelaySeconds() * 1000);
                job.setNextRetryAt(nextRetryAt);
                jobRepository.save(job);
                logger.info("Job {} will retry at {} (attempt {}/{})", jobId, nextRetryAt, job.getAttemptCount(), job.getMaxAttempts());
            } else {
                job.setStatus(SubmissionJob.JobStatus.FAILED);
                job.setCompletedAt(new Date());
                jobRepository.save(job);
                logger.info("Job {} permanently failed after {} attempts", jobId, job.getAttemptCount());
            }
        } finally {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
        }
    }

    private String analyzeResults(List<SubmissionJob.TestResult> results) {
        boolean allPassed = results.stream().allMatch(SubmissionJob.TestResult::isPassed);
        if (allPassed) {
            return "ACCEPTED";
        }
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
}
