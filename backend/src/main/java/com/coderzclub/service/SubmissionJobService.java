package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionTestResult;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SubmissionJobService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobService.class);

    @Autowired
    private SubmissionJobRepository jobRepository;

    @Autowired
    private SubmissionTestResultRepository resultRepository;

    @Autowired
    private SubmissionOutboxService outboxService;

    @Autowired
    private SubmissionJobEventService eventService;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired(required = false)
    private IdempotencyOperations idempotency;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Autowired(required = false)
    private MongoTransactionManager transactionManager;

    private volatile Boolean replicaSet;

    /**
     * Create a new submission job
     */
    @Transactional
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode) {
        return createJob(userId, problemId, code, language, languageId, testcaseVersion, totalTests,
            executionMode, null);
    }

    @Transactional
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode, Integer codingDurationSeconds) {
        return createJob(userId, problemId, code, language, languageId, testcaseVersion, totalTests,
            executionMode, codingDurationSeconds, null, null, null);
    }

    @Transactional
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode, Integer codingDurationSeconds,
                                   String contestId, Instant contestSubmittedAt) {
        return createJob(userId, problemId, code, language, languageId, testcaseVersion, totalTests,
            executionMode, codingDurationSeconds, contestId, contestSubmittedAt, null);
    }

    /**
     * Durable job admission. Contest fields are set only by the contest submit path.
     */
    @Transactional
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode, Integer codingDurationSeconds,
                                   String contestId, Instant contestSubmittedAt,
                                   String contestProblemSnapshotId) {
        return createJob(userId, problemId, code, language, languageId, testcaseVersion, totalTests,
            executionMode, codingDurationSeconds, contestId, contestSubmittedAt, contestProblemSnapshotId,
            null, null);
    }

    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode, Integer codingDurationSeconds,
                                   String contestId, Instant contestSubmittedAt,
                                   String contestProblemSnapshotId,
                                   String idempotencyScope, String idempotencyKey) {
        RuntimeException lastConflict = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                SubmissionJob job = replicaSet()
                    ? new TransactionTemplate(transactionManager).execute(status -> writeJob(
                        userId, problemId, code, language, languageId, testcaseVersion, totalTests,
                        executionMode, codingDurationSeconds, contestId, contestSubmittedAt,
                        contestProblemSnapshotId, idempotencyScope, idempotencyKey))
                    : writeJob(userId, problemId, code, language, languageId, testcaseVersion, totalTests,
                        executionMode, codingDurationSeconds, contestId, contestSubmittedAt,
                        contestProblemSnapshotId, idempotencyScope, idempotencyKey);
                if (job != null) {
                    try {
                        eventService.publish(job, SubmissionJob.JobStatus.QUEUED);
                    } catch (RuntimeException publishError) {
                        logger.warn("submission_job_event_publish_failed id={}", job.getId());
                    }
                }
                return job;
            } catch (RuntimeException saveError) {
                SubmissionJob existing = existingIdempotentJob(userId, idempotencyScope, idempotencyKey, saveError);
                if (existing != null) {
                    outboxService.ensureJobEvent(existing.getId());
                    logger.info("submission_job_replayed id={} userId={} problemId={} idempotencyKey={}",
                        existing.getId(), userId, problemId, idempotencyKey);
                    return existing;
                }
                if (!isTransientTransaction(saveError) || attempt == 2) {
                    throw saveError;
                }
                lastConflict = saveError;
            }
        }
        throw lastConflict == null ? new IllegalStateException("Job was not saved") : lastConflict;
    }

    private SubmissionJob writeJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode, Integer codingDurationSeconds,
                                   String contestId, Instant contestSubmittedAt,
                                   String contestProblemSnapshotId,
                                   String idempotencyScope, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<SubmissionJob> already = jobRepository.findFirstByUserIdAndIdempotencyScopeAndIdempotencyKey(
                userId, idempotencyScope, idempotencyKey);
            if (already.isPresent()) {
                outboxService.ensureJobEvent(already.get().getId());
                return already.get();
            }
            if (idempotency != null && !idempotency.prepareCommit(userId, idempotencyScope, idempotencyKey)) {
                already = jobRepository.findFirstByUserIdAndIdempotencyScopeAndIdempotencyKey(
                    userId, idempotencyScope, idempotencyKey);
                if (already.isPresent()) {
                    outboxService.ensureJobEvent(already.get().getId());
                    return already.get();
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This attempt is no longer the active submission.");
            }
        }
        SubmissionJob job = new SubmissionJob();
        job.setUserId(userId);
        job.setProblemId(problemId);
        job.setCode(code);
        job.setLanguage(language);
        job.setLanguageId(languageId);
        job.setCodingDurationSeconds(codingDurationSeconds);
        job.setTestcaseVersion(testcaseVersion == null ? "v1" : testcaseVersion);
        job.setExecutionMode(executionMode == null ? ExecutionMode.STANDARD_PER_CASE : executionMode);
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setTotalTests(totalTests);
        job.setAttemptCount(0);
        job.setMaxAttempts(workerProperties.getMaxAttempts());
        job.setNextRetryAt(null);
        job.setLastError(null);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setHeartbeatAt(null);
        job.setContestId(contestId);
        job.setContestSubmittedAt(contestSubmittedAt);
        job.setContestProblemSnapshotId(contestProblemSnapshotId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            job.setIdempotencyScope(idempotencyScope);
            job.setIdempotencyKey(idempotencyKey);
        }

        job = jobRepository.save(job);
        try {
            outboxService.createJobEvent(job.getId());
        } catch (RuntimeException outboxError) {
            if (isDuplicate(outboxError)) {
                logger.warn("submission_job_outbox_already_present id={}", job.getId());
            } else {
                logger.warn("submission_job_outbox_failed id={} ; status refresh will publish one event", job.getId());
            }
        }

        logger.info("submission_job_created id={} userId={} problemId={} contestId={} language={} languageId={} totalTests={} codeLength={}",
            job.getId(), userId, problemId, contestId, language, languageId, job.getTotalTests(),
            code != null ? code.length() : 0);

        return job;
    }

    /**
     * If the queue event for a keyed job was not written, write it once.
     * Terminal jobs and jobs without an operation key are left alone.
     */
    public void recoverQueuedPublish(SubmissionJob job) {
        if (job == null || job.getIdempotencyKey() == null || job.getIdempotencyKey().isBlank()) {
            return;
        }
        if (job.getStatus() != SubmissionJob.JobStatus.QUEUED && job.getStatus() != SubmissionJob.JobStatus.PENDING) {
            return;
        }
        try {
            outboxService.ensureJobEvent(job.getId());
        } catch (RuntimeException ex) {
            logger.warn("submission_job_publish_recovery_failed id={}", job.getId());
        }
    }

    private SubmissionJob existingIdempotentJob(String userId, String scope, String key, RuntimeException error) {
        if (key == null || key.isBlank() || !isDuplicate(error)) {
            return null;
        }
        return jobRepository.findFirstByUserIdAndIdempotencyScopeAndIdempotencyKey(userId, scope, key).orElse(null);
    }

    private boolean isTransientTransaction(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = String.valueOf(current.getMessage());
            if (message.contains("WriteConflict") || message.contains("TransientTransactionError")
                || message.contains("code 112")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isDuplicate(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.springframework.dao.DuplicateKeyException
                || current instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
            if (current.getClass().getName().contains("DuplicateKey")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Get job by ID
     */
    public Optional<SubmissionJob> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    public List<SubmissionTestResult> getResults(String jobId) {
        return resultRepository.findByJobIdOrderByTestcaseIndexAsc(jobId);
    }

    public List<SubmissionTestResult> getResults(SubmissionJob job) {
        List<SubmissionTestResult> results = getResults(job.getId());
        if (!results.isEmpty() || job.getTestResults() == null) return results;
        int publicCount = job.getPublicTestCases() == null ? 0 : job.getPublicTestCases().size();
        for (int index = 0; index < job.getTestResults().size(); index++) {
            SubmissionJob.TestResult legacy = job.getTestResults().get(index);
            SubmissionTestResult result = new SubmissionTestResult();
            result.setJobId(job.getId());
            result.setTestcaseIndex(index);
            boolean publicTest = index < publicCount;
            result.setTestcaseType(publicTest ? SubmissionTestResult.TestcaseType.PUBLIC
                : SubmissionTestResult.TestcaseType.HIDDEN);
            result.setPassed(legacy.isPassed());
            result.setRuntime(legacy.getRuntime());
            result.setMemory(legacy.getMemory());
            result.setErrorType(legacy.getErrorType());
            result.setErrorMessage(legacy.getErrorMessage());
            if (publicTest) {
                result.setInput(legacy.getInput());
                result.setExpectedOutput(legacy.getExpectedOutput());
                result.setActualOutput(legacy.getActualOutput());
            }
            results.add(result);
        }
        return results;
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
        long queued = jobRepository.countByStatus(SubmissionJob.JobStatus.QUEUED)
            + jobRepository.countByStatus(SubmissionJob.JobStatus.PENDING)
            + jobRepository.countByStatus(SubmissionJob.JobStatus.RETRYING);
        long running = jobRepository.countByStatus(SubmissionJob.JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(SubmissionJob.JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(SubmissionJob.JobStatus.FAILED);

        return new QueueStats(queued, running, completed, failed);
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
        public long getQueued() { return pending; }
        public long getRunning() { return running; }
        public long getCompleted() { return completed; }
        public long getFailed() { return failed; }
        public long getTotal() { return pending + running + completed + failed; }
    }

    private boolean replicaSet() {
        if (mongoTemplate == null || transactionManager == null) {
            return false;
        }
        if (replicaSet == null) {
            Document hello = mongoTemplate.executeCommand("{ hello: 1 }");
            replicaSet = hello.get("setName") != null;
        }
        return replicaSet;
    }
}