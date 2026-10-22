package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestRunJob;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import com.coderzclub.service.ContestProblemSnapshotService;
import com.coderzclub.service.ContestRunLeaseService;
import com.coderzclub.service.ContestRunMetrics;
import com.coderzclub.service.ExecutionOutcome;
import com.coderzclub.service.IncompatibleExecutionException;
import com.coderzclub.service.Judge0ExecutionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Profile("worker")
public class ContestRunWorker {
    private static final Logger logger = LoggerFactory.getLogger(ContestRunWorker.class);
    static final String RUN_EXPIRED = "RUN_EXPIRED";
    static final String RUN_EXPIRED_MESSAGE = "Run waited too long. Please run again.";

    private final ContestRunConsumer consumer;
    private final ContestRunProperties properties;
    private final Clock clock;
    private final MongoTemplate mongoTemplate;
    private final ContestRunLeaseService leaseService;
    private final ContestProblemSnapshotService snapshotService;
    private final Judge0ExecutionService executionService;
    private final ContestRunMetrics metrics;
    private Semaphore executionPermits;
    private ScheduledExecutorService heartbeatExecutor;

    public ContestRunWorker(
        ContestRunConsumer consumer,
        ContestRunProperties properties,
        Clock clock,
        MongoTemplate mongoTemplate,
        ContestRunLeaseService leaseService,
        ContestProblemSnapshotService snapshotService,
        Judge0ExecutionService executionService,
        ContestRunMetrics metrics
    ) {
        this.consumer = consumer;
        this.properties = properties;
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
        this.leaseService = leaseService;
        this.snapshotService = snapshotService;
        this.executionService = executionService;
        this.metrics = metrics;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            logger.info("Contest run worker disabled by configuration");
            return;
        }
        int concurrency = Math.max(1, properties.getQueue().getConcurrency());
        executionPermits = new Semaphore(concurrency);
        heartbeatExecutor = Executors.newScheduledThreadPool(concurrency);
        consumer.start(this::handleMessage, concurrency);
        logger.info("Started contest run worker concurrency={} prefetch={}",
            concurrency, properties.getQueue().getPrefetch());
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.stop();
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    SubmissionQueueConsumer.MessageDisposition handleMessage(String runId) {
        if (runId == null || runId.isBlank()) {
            return SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER;
        }
        ContestRunJob existing = mongoTemplate.findById(runId, ContestRunJob.class);
        if (existing == null) {
            logger.warn("contest_run_unknown_job");
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }
        if (existing.isTerminal()) {
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }
        if (queueExpired(existing)) {
            return expireWaitingRun(runId);
        }
        return process(runId);
    }

    /**
     * Queue-age expiry for a run that is still waiting. A live execution lease is left alone.
     */
    private SubmissionQueueConsumer.MessageDisposition expireWaitingRun(String runId) {
        if (leaseService.failIfQueueExpired(runId, RUN_EXPIRED, RUN_EXPIRED_MESSAGE)) {
            metrics.failed("queue_expired");
            logger.info("contest_run_queue_expired runId={}", runId);
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }
        ContestRunJob current = mongoTemplate.findById(runId, ContestRunJob.class);
        if (current == null || current.isTerminal()) {
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }
        if (current.getStatus() == ContestRunJob.Status.QUEUED) {
            logger.warn("contest_run_queue_expire_retry runId={}", runId);
            return SubmissionQueueConsumer.MessageDisposition.RETRY;
        }
        logger.info("contest_run_queue_age_elapsed_while_running runId={}", runId);
        return SubmissionQueueConsumer.MessageDisposition.ACK;
    }

    private boolean queueExpired(ContestRunJob job) {
        if (job.getCreatedAt() == null) {
            return false;
        }
        long maxAgeMillis = properties.getQueue().getAdmissionMaxOldestAgeSeconds() * 1000L;
        long ageMillis = clock.millis() - job.getCreatedAt().getTime();
        return ageMillis > maxAgeMillis;
    }

    SubmissionQueueConsumer.MessageDisposition process(String runId) {
        String workerId = "contest-run-" + UUID.randomUUID();
        Optional<ContestRunJob> claimed = leaseService.claim(runId, workerId);
        if (claimed.isEmpty()) {
            ContestRunJob current = mongoTemplate.findById(runId, ContestRunJob.class);
            if (current == null || current.isTerminal()) {
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }
        ContestRunJob job = claimed.get();
        metrics.started();
        ScheduledFuture<?> heartbeat = null;
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        if (heartbeatExecutor != null) {
            long interval = Math.max(15, properties.getLeaseDurationSeconds() / 4);
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (!leaseService.heartbeat(runId, workerId)) {
                    leaseLost.set(true);
                }
            }, interval, interval, TimeUnit.SECONDS);
        }
        try {
            ContestProblemSnapshot snapshot = snapshotService.findById(job.getContestProblemSnapshotId());
            if (snapshot == null) {
                logger.warn("contest_run_snapshot_missing");
                leaseService.failIfOwned(runId, workerId, "SNAPSHOT_MISSING", "Contest problem snapshot is unavailable");
                metrics.failed("SNAPSHOT_MISSING");
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            List<SubmissionJob.TestCase> publicTests = convert(snapshot.getPublicTestCases());
            if (publicTests.isEmpty()) {
                leaseService.failIfOwned(runId, workerId, "NO_PUBLIC_TESTS", "No public tests are available for Run");
                metrics.failed("NO_PUBLIC_TESTS");
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            acquirePermit();
            ExecutionOutcome outcome;
            try {
                outcome = executionService.executeTestCases(
                    job.getCode(),
                    job.getLanguageId(),
                    publicTests,
                    List.of(),
                    snapshot.getExecutionMode(),
                    snapshot.getTestcaseVersion()
                );
            } finally {
                releasePermit();
            }
            if (leaseLost.get() || !leaseService.isOwned(runId, workerId)) {
                logger.warn("contest_run_lease_lost_before_complete");
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            List<ContestRunJob.PublicTestResult> results = mapPublic(outcome.getResults());
            if (!leaseService.completeIfOwned(runId, workerId, results, publicTests.size())) {
                logger.warn("contest_run_complete_not_owned");
                return SubmissionQueueConsumer.MessageDisposition.ACK;
            }
            metrics.completed();
            logger.info("contest_run_completed publicTests={}", publicTests.size());
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        } catch (IncompatibleExecutionException incompatible) {
            leaseService.failIfOwned(runId, workerId, "INCOMPATIBLE", incompatible.getMessage());
            metrics.failed("INCOMPATIBLE");
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        } catch (Exception e) {
            int attempts = job.getAttemptCount() == null ? 1 : job.getAttemptCount();
            int max = job.getMaxAttempts() == null ? properties.getMaxAttempts() : job.getMaxAttempts();
            if (attempts < max) {
                if (!leaseService.releaseToQueuedIfOwned(runId, workerId)) {
                    logger.warn("contest_run_retry_not_owned attempt={}", attempts);
                    return SubmissionQueueConsumer.MessageDisposition.ACK;
                }
                logger.warn("contest_run_retryable_failure attempt={}", attempts);
                return SubmissionQueueConsumer.MessageDisposition.RETRY;
            }
            leaseService.failIfOwned(runId, workerId, "EXECUTION_FAILED", "Run failed");
            metrics.failed("max_attempts");
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(true);
            }
        }
    }

    @Scheduled(fixedDelayString = "${contest.run.repair-fixed-delay-ms:15000}")
    public void recoverExpiredLeases() {
        if (!properties.isEnabled()) {
            return;
        }
        for (ContestRunJob job : leaseService.findExpiredRunning(25)) {
            if (queueExpired(job)) {
                if (leaseService.failIfQueueExpired(job.getId(), RUN_EXPIRED, RUN_EXPIRED_MESSAGE)) {
                    metrics.failed("queue_expired");
                    logger.info("contest_run_queue_expired runId={}", job.getId());
                }
                continue;
            }
            int attempts = job.getAttemptCount() == null ? 0 : job.getAttemptCount();
            int max = job.getMaxAttempts() == null ? properties.getMaxAttempts() : job.getMaxAttempts();
            if (attempts >= max) {
                leaseService.failExpired(job.getId(), "LEASE_EXPIRED", "Run timed out");
                metrics.failed("LEASE_EXPIRED");
            }
        }
    }

    private void acquirePermit() throws InterruptedException {
        if (executionPermits != null) {
            executionPermits.acquire();
        }
    }

    private void releasePermit() {
        if (executionPermits != null) {
            executionPermits.release();
        }
    }

    private static List<SubmissionJob.TestCase> convert(List<TestCase> testCases) {
        if (testCases == null) {
            return List.of();
        }
        return testCases.stream()
            .map(test -> new SubmissionJob.TestCase(test.getInput(), test.getOutput(), test.getExplanation()))
            .toList();
    }

    static List<ContestRunJob.PublicTestResult> mapPublic(List<SubmissionJob.TestResult> source) {
        if (source == null) {
            return List.of();
        }
        List<ContestRunJob.PublicTestResult> results = new ArrayList<>();
        for (SubmissionJob.TestResult item : source) {
            ContestRunJob.PublicTestResult mapped = new ContestRunJob.PublicTestResult();
            mapped.setInput(item.getInput());
            mapped.setExpectedOutput(item.getExpectedOutput());
            mapped.setActualOutput(item.getActualOutput());
            mapped.setPassed(item.isPassed());
            mapped.setRuntime(item.getRuntime());
            mapped.setMemory(item.getMemory());
            mapped.setErrorType(item.getErrorType());
            mapped.setErrorMessage(item.getErrorMessage());
            results.add(mapped);
        }
        return results;
    }
}
