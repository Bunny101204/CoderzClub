package com.coderzclub.queue;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionTestResult;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.Problem;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.service.Judge0ExecutionService;
import com.coderzclub.service.SubmissionJobLeaseService;
import com.coderzclub.service.SubmissionService;
import com.coderzclub.service.SubmissionJobEventService;
import com.coderzclub.service.OperationalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.MDC;

@Component
@Profile("worker")
public class SubmissionWorker {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionWorker.class);

    @Autowired
    private SubmissionQueueConsumer consumer;

    @Autowired
    private SubmissionJobRepository jobRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionTestResultRepository resultRepository;

    @Autowired
    private SubmissionJobLeaseService leaseService;

    @Autowired
    private Judge0ExecutionService executionService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired
    private SubmissionQueueProperties queueProperties;

    @Autowired
    private SubmissionJobEventService eventService;

    @Autowired
    private OperationalMetrics operationalMetrics;

    private ScheduledExecutorService heartbeatExecutor;

    @PostConstruct
    public void startWorkers() {
        if (!workerProperties.isEnabled()) {
            logger.info("Submission worker disabled by configuration");
            return;
        }

        int concurrency = Math.max(1, queueProperties.getConcurrency());
        heartbeatExecutor = Executors.newScheduledThreadPool(Math.max(1, concurrency));
        consumer.start(this::handleMessage, concurrency);
        logger.info("Started submission worker fleet with concurrency={}", concurrency);
    }

    private SubmissionQueueConsumer.MessageDisposition handleMessage(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER;
        }
        if (!jobRepository.existsById(jobId)) {
            logger.warn("Received submission message for unknown job {}; sending to DLQ", jobId);
            return SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER;
        }
        return processJob(jobId);
    }

    @PreDestroy
    public void stopWorkers() {
        if (consumer != null) consumer.stop();
        if (heartbeatExecutor != null) heartbeatExecutor.shutdownNow();
    }

    private SubmissionQueueConsumer.MessageDisposition processJob(String jobId) {
        String workerId = UUID.randomUUID().toString();
        MDC.put("jobId", jobId);
        MDC.put("workerId", workerId);
        Optional<SubmissionJob> jobOpt = leaseService.claimJob(jobId, workerId, workerProperties.getLeaseDurationSeconds());
        if (jobOpt.isEmpty()) {
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }

        SubmissionJob job = jobOpt.get();
        operationalMetrics.workerStarted();
        eventService.publish(job, SubmissionJob.JobStatus.RUNNING);
        long heartbeatInterval = Math.max(1, workerProperties.getLeaseDurationSeconds() / 2);
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    if (!leaseService.heartbeat(jobId, workerId, workerProperties.getLeaseDurationSeconds())) {
                        leaseLost.set(true);
                        logger.warn("Worker {} lost lease for job {}; stopping result processing", workerId, jobId);
                    }
                } catch (RuntimeException heartbeatFailure) {
                    leaseLost.set(true);
                    logger.warn("Heartbeat failed for job {}; stopping result processing", jobId, heartbeatFailure);
                }
            },
            heartbeatInterval,
            heartbeatInterval,
            TimeUnit.SECONDS
        );

        logger.info("Worker claimed job {} userId={} problemId={} attempt={} totalTests={} lockedBy={}",
            jobId, job.getUserId(), job.getProblemId(), job.getAttemptCount(), job.getTotalTests(), workerId);
        MDC.put("userId", String.valueOf(job.getUserId()));
        MDC.put("problemId", String.valueOf(job.getProblemId()));
        MDC.put("attemptCount", String.valueOf(job.getAttemptCount()));

        try {
            Problem problem = problemRepository.findById(job.getProblemId())
                .orElseThrow(() -> new IllegalStateException("Problem not found for submission job"));
            List<SubmissionJob.TestCase> publicTests = convert(problem.getPublicTestCases());
            List<SubmissionJob.TestCase> hiddenTests = convert(problem.getHiddenTestCases());
            List<SubmissionJob.TestResult> results = executionService.executeTestCases(
                job.getCode(), job.getLanguageId(), publicTests, hiddenTests);

            if (leaseLost.get() || !leaseService.isOwned(jobId, workerId)) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost before saving results for job {}; ignoring stale result", jobId);
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            saveResults(job.getId(), workerId, job.getAttemptCount(), results, publicTests.size(), job.getTotalTests());

            String finalResult = analyzeResults(results);
            Long maxRuntime = results.stream()
                .mapToLong(r -> r.getRuntime() != null ? r.getRuntime() : 0L)
                .max().orElse(0L);
            Long maxMemory = results.stream()
                .mapToLong(r -> r.getMemory() != null ? r.getMemory() : 0L)
                .max().orElse(0L);

            SubmissionJob completionPayload = new SubmissionJob();
            completionPayload.setFinalResult(finalResult);
            completionPayload.setTotalRuntime(maxRuntime);
            completionPayload.setTotalMemory(maxMemory);
            completionPayload.setCompletedTests(results.size());
            completionPayload.setCompletedAt(new Date());

            if (!leaseService.completeIfOwned(jobId, workerId, completionPayload)) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost before completing job {}; ignoring stale result", jobId);
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }

            job.setFinalResult(finalResult);
            job.setTotalRuntime(maxRuntime);
            job.setTotalMemory(maxMemory);
            job.setCompletedTests(results.size());
            job.setStatus(SubmissionJob.JobStatus.COMPLETED);
            job.setCompletedAt(completionPayload.getCompletedAt());
            eventService.publish(job, SubmissionJob.JobStatus.COMPLETED);
            operationalMetrics.verdict(finalResult);
            submissionService.createSubmissionFromJob(job);

            logger.info("Job {} completed result={} passedTests={}/{} runtimeMs={} memoryBytes={} attempts={}",
                jobId, finalResult,
                results.stream().filter(SubmissionJob.TestResult::isPassed).count(), results.size(),
                maxRuntime, maxMemory, job.getAttemptCount());

            return SubmissionQueueConsumer.MessageDisposition.ACK;
        } catch (Exception e) {
            logger.error("Job {} failed during execution", jobId, e);
            if (leaseLost.get()) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost while processing job {}; ignoring stale failure", jobId);
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                Date nextRetryAt = new Date(System.currentTimeMillis()
                    + workerProperties.retryDelayMillis(job.getAttemptCount()));
                if (!leaseService.retryIfOwned(jobId, workerId, e.getMessage(), nextRetryAt)) {
                    operationalMetrics.leaseLoss();
                    logger.warn("Lease ownership lost before retrying job {}; ignoring stale failure", jobId);
                    return SubmissionQueueConsumer.MessageDisposition.ACK;
                }
                operationalMetrics.retry();
                job.setLastError(e.getMessage());
                eventService.publish(job, SubmissionJob.JobStatus.RETRYING);
                logger.info("Job {} will retry at {} (attempt {}/{})", jobId, nextRetryAt, job.getAttemptCount(), job.getMaxAttempts());
                return SubmissionQueueConsumer.MessageDisposition.RETRY;
            } else {
                if (!leaseService.failIfOwned(jobId, workerId, e.getMessage())) {
                    logger.warn("Lease ownership lost before failing job {}; ignoring stale failure", jobId);
                    return SubmissionQueueConsumer.MessageDisposition.ACK;
                }
                job.setLastError(e.getMessage());
                eventService.publish(job, SubmissionJob.JobStatus.FAILED);
                logger.info("Job {} permanently failed after {} attempts", jobId, job.getAttemptCount());
                return SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER;
            }
        } finally {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(true);
            }
            operationalMetrics.workerStopped();
            MDC.remove("jobId");
            MDC.remove("workerId");
            MDC.remove("userId");
            MDC.remove("problemId");
            MDC.remove("attemptCount");
        }
    }

    private List<SubmissionJob.TestCase> convert(List<TestCase> testCases) {
        return testCases == null ? List.of() : testCases.stream()
            .map(test -> new SubmissionJob.TestCase(test.getInput(), test.getOutput(), test.getExplanation()))
            .toList();
    }

    void saveResults(String jobId, String workerId, Integer attemptCount,
                     List<SubmissionJob.TestResult> results, int publicCount, int totalTests) {
        for (int index = 0; index < results.size(); index++) {
            if (!leaseService.isOwned(jobId, workerId)) {
                throw new LeaseLostException();
            }
            SubmissionJob.TestResult source = results.get(index);
            SubmissionTestResult result = new SubmissionTestResult();
            result.setJobId(jobId);
            result.setAttemptCount(attemptCount);
            result.setTestcaseIndex(index);
            boolean publicTest = index < publicCount;
            result.setTestcaseType(publicTest ? SubmissionTestResult.TestcaseType.PUBLIC
                : SubmissionTestResult.TestcaseType.HIDDEN);
            result.setPassed(source.isPassed());
            result.setRuntime(source.getRuntime());
            result.setMemory(source.getMemory());
            result.setErrorType(source.getErrorType());
            result.setErrorMessage(safeError(source.getErrorMessage()));
            if (publicTest) {
                result.setInput(source.getInput());
                result.setExpectedOutput(source.getExpectedOutput());
                result.setActualOutput(source.getActualOutput());
            }
            resultRepository.save(result);
            if (!leaseService.updateProgressIfOwned(jobId, workerId, index + 1)) {
                throw new LeaseLostException();
            }
            eventService.publish(progressSnapshot(jobId, attemptCount, index + 1, totalTests));
        }
    }

    private SubmissionJob progressSnapshot(String jobId, Integer attemptCount, int completed, int totalTests) {
        SubmissionJob snapshot = new SubmissionJob();
        snapshot.setId(jobId);
        snapshot.setStatus(SubmissionJob.JobStatus.RUNNING);
        snapshot.setAttemptCount(attemptCount);
        snapshot.setCompletedTests(completed);
        snapshot.setTotalTests(totalTests);
        return snapshot;
    }

    private static class LeaseLostException extends RuntimeException {
    }

    private String safeError(String message) {
        if (message == null) return null;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
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
