package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.queue.SubmissionQueuePublisher;
import com.coderzclub.repository.SubmissionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SubmissionJobService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobService.class);

    @Autowired
    private SubmissionJobRepository jobRepository;

    @Autowired
    private SubmissionQueuePublisher publisher;

    @Autowired
    private WorkerProperties workerProperties;

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
        job.setStatus(SubmissionJob.JobStatus.QUEUED);
        job.setTotalTests((publicTestCases != null ? publicTestCases.size() : 0) +
                         (hiddenTestCases != null ? hiddenTestCases.size() : 0));
        job.setAttemptCount(0);
        job.setMaxAttempts(workerProperties.getMaxAttempts());
        job.setNextRetryAt(null);
        job.setLastError(null);
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setHeartbeatAt(null);

        job = jobRepository.save(job);

        logger.info("submission_job_created id={} userId={} problemId={} language={} languageId={} totalTests={} codeLength={}",
            job.getId(), userId, problemId, language, languageId, job.getTotalTests(), code != null ? code.length() : 0);

        publisher.publishJob(job.getId());

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