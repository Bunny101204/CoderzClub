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
import com.coderzclub.service.ExecutionOutcome;
import com.coderzclub.service.ExecutionVerdictMapper;
import com.coderzclub.service.IncompatibleExecutionException;
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
        if (workerProperties.getConcurrency() != concurrency) {
            logger.warn("worker.concurrency={} is unused; Rabbit listener concurrency is submission.queue.concurrency={}",
                workerProperties.getConcurrency(), concurrency);
        }
        heartbeatExecutor = Executors.newScheduledThreadPool(Math.max(1, concurrency));
        consumer.start(this::handleMessage, concurrency);
        logger.info("Started submission worker fleet with concurrency={} prefetch={} "
                + "(submission.queue.concurrency is canonical; worker.concurrency does not control the listener)",
            concurrency, queueProperties.getPrefetch());
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

    SubmissionQueueConsumer.MessageDisposition processJob(String jobId) {
        String workerId = UUID.randomUUID().toString();
        MDC.put("jobId", jobId);
        MDC.put("workerId", workerId);
        Optional<SubmissionJob> jobOpt = leaseService.claimJob(jobId, workerId, workerProperties.getLeaseDurationSeconds());
        if (jobOpt.isEmpty()) {
            operationalMetrics.duplicateClaim();
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }

        SubmissionJob job = jobOpt.get();
        operationalMetrics.workerStarted();
        eventService.publish(job, SubmissionJob.JobStatus.RUNNING);
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        long heartbeatInterval = Math.max(1, workerProperties.getHeartbeatIntervalSeconds());
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        ScheduledFuture<?> heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(
            () -> {
                try {
                    if (!leaseService.heartbeat(jobId, workerId, workerProperties.getLeaseDurationSeconds())) {
                        leaseLost.set(true);
                        operationalMetrics.heartbeatFailure();
                        logger.warn("Worker {} lost lease for job {}; in-flight wait=true Judge0 execution cannot be cancelled",
                            workerId, jobId);
                    }
                } catch (RuntimeException heartbeatFailure) {
                    leaseLost.set(true);
                    operationalMetrics.heartbeatFailure();
                    logger.warn("Heartbeat failed for job {}; in-flight wait=true Judge0 execution cannot be cancelled",
                        jobId, heartbeatFailure);
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
            ExecutionOutcome outcome = executionService.executeTestCases(
                job.getCode(), job.getLanguageId(), publicTests, hiddenTests, job.getExecutionMode(),
                job.getTestcaseVersion());
            List<SubmissionJob.TestResult> results = outcome.getResults();
            logger.info("job_execution_strategy jobId={} configured={} used={} logicalTestcases={} providerExecutions={} fallbackReason={}",
                jobId, outcome.getConfiguredMode(), outcome.getExecutionModeUsed(),
                outcome.getLogicalTestcases(), outcome.getProviderExecutions(), outcome.getFallbackReason());

            if (leaseLost.get() || !leaseService.isOwned(jobId, workerId)) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost before saving results for job {}; ignoring stale result. "
                    + "If a wait=true Judge0 POST was already sent, that provider execution cannot be cancelled.", jobId);
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
            java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("configuredExecutionMode", String.valueOf(outcome.getConfiguredMode()));
            metadata.put("executionModeUsed", String.valueOf(outcome.getExecutionModeUsed()));
            metadata.put("logicalTestcases", outcome.getLogicalTestcases());
            metadata.put("providerExecutions", outcome.getProviderExecutions());
            if (outcome.getFallbackReason() != null) {
                metadata.put("fallbackReason", outcome.getFallbackReason());
            }
            completionPayload.setMetadata(metadata);

            job.setFinalResult(finalResult);
            job.setTotalRuntime(maxRuntime);
            job.setTotalMemory(maxMemory);
            job.setCompletedTests(results.size());
            job.setCompletedAt(completionPayload.getCompletedAt());

            if (leaseLost.get() || !leaseService.persistJudgementIfOwned(jobId, workerId, completionPayload)) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost before persisting judgement for job {}; "
                    + "in-flight wait=true Judge0 execution cannot be cancelled", jobId);
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }

            com.coderzclub.model.Submission submission = submissionService.createSubmissionFromJob(job);
            if (submission != null) {
                completionPayload.setSubmissionId(submission.getId());
                job.setSubmissionId(submission.getId());
            }

            if (leaseLost.get() || !leaseService.completeIfOwned(jobId, workerId, completionPayload)) {
                operationalMetrics.leaseLoss();
                logger.warn("Lease ownership lost before completing job {}; submission already materialized if present. "
                    + "Recovery will finish COMPLETED without re-executing when judgement/submission exists.", jobId);
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }

            job.setStatus(SubmissionJob.JobStatus.COMPLETED);
            eventService.publish(job, SubmissionJob.JobStatus.COMPLETED);
            operationalMetrics.verdict(finalResult);

            logger.info("Job {} completed result={} passedTests={}/{} runtimeMs={} memoryBytes={} attempts={}",
                jobId, finalResult,
                results.stream().filter(SubmissionJob.TestResult::isPassed).count(), results.size(),
                maxRuntime, maxMemory, job.getAttemptCount());

            return SubmissionQueueConsumer.MessageDisposition.ACK;
        } catch (IncompatibleExecutionException incompatible) {
            logger.warn("Job {} incompatible execution configuration: {}", jobId, incompatible.getMessage());
            if (!leaseService.failIfOwned(jobId, workerId, incompatible.getMessage())) {
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            job.setLastError(incompatible.getMessage());
            eventService.publish(job, SubmissionJob.JobStatus.FAILED);
            return SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER;
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


    String analyzeResults(List<SubmissionJob.TestResult> results) {
        return ExecutionVerdictMapper.fromTestResults(results);
    }
}
