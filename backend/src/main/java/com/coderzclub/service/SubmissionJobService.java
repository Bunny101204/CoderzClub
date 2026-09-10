package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionTestResult;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionTestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Create a new submission job
     */
    @Transactional
    public SubmissionJob createJob(String userId, String problemId, String code, String language,
                                   Integer languageId, String testcaseVersion, int totalTests,
                                   ExecutionMode executionMode) {
        SubmissionJob job = new SubmissionJob();
        job.setUserId(userId);
        job.setProblemId(problemId);
        job.setCode(code);
        job.setLanguage(language);
        job.setLanguageId(languageId);
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

        job = jobRepository.save(job);
        eventService.publish(job, SubmissionJob.JobStatus.QUEUED);
        outboxService.createJobEvent(job.getId());

        logger.info("submission_job_created id={} userId={} problemId={} language={} languageId={} totalTests={} codeLength={}",
            job.getId(), userId, problemId, language, languageId, job.getTotalTests(), code != null ? code.length() : 0);

        return job;
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
}