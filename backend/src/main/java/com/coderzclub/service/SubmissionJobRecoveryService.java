package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.Submission;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.queue.SubmissionQueuePublisher;
import com.coderzclub.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import com.mongodb.client.result.UpdateResult;

@Service
@Profile("worker")
public class SubmissionJobRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobRecoveryService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private WorkerProperties workerProperties;

    @Autowired
    private SubmissionQueuePublisher publisher;

    @Autowired
    private SubmissionJobEventService eventService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionJobLeaseService leaseService;

    @Scheduled(fixedDelayString = "${worker.recoveryIntervalSeconds:30}000")
    public void recoverStuckJobs() {
        Date now = new Date();
        Query runningQuery = new Query();
        runningQuery.addCriteria(Criteria.where("status").is(SubmissionJob.JobStatus.RUNNING));
        runningQuery.addCriteria(Criteria.where("lockedUntil").lte(now));

        List<SubmissionJob> runningJobs = mongoTemplate.find(runningQuery, SubmissionJob.class);
        for (SubmissionJob job : runningJobs) {
            if (finishMaterializedJob(job, now)) {
                continue;
            }
            if (job.getAttemptCount() != null && job.getAttemptCount() >= job.getMaxAttempts()) {
                Update update = new Update();
                update.set("status", SubmissionJob.JobStatus.TIMEOUT);
                update.set("lastError", "Lease expired after max attempts");
                update.set("completedAt", now);
                update.set("lockedBy", null);
                update.set("lockedUntil", null);
                update.set("heartbeatAt", null);
                Query ownershipQuery = Query.query(Criteria.where("_id").is(job.getId())
                    .and("status").is(SubmissionJob.JobStatus.RUNNING)
                    .and("lockedBy").is(job.getLockedBy())
                    .and("lockedUntil").lte(now));
                UpdateResult result = mongoTemplate.updateFirst(ownershipQuery, update, SubmissionJob.class);
                if (result.getModifiedCount() == 1) {
                    job.setLastError("Lease expired after max attempts");
                    eventService.publish(job, SubmissionJob.JobStatus.TIMEOUT);
                    logger.warn("Job {} lease expired and max attempts reached; marking TIMEOUT", job.getId());
                }
            } else {
                Date retryAt = new Date(now.getTime()
                    + workerProperties.retryDelayMillis(job.getAttemptCount() == null ? 1 : job.getAttemptCount()));
                Update update = new Update();
                update.set("status", SubmissionJob.JobStatus.RETRYING);
                update.set("lockedBy", null);
                update.set("lockedUntil", null);
                update.set("nextRetryAt", retryAt);
                update.set("lastError", "Lease expired; retrying");
                update.set("heartbeatAt", null);
                Query ownershipQuery = Query.query(Criteria.where("_id").is(job.getId())
                    .and("status").is(SubmissionJob.JobStatus.RUNNING)
                    .and("lockedBy").is(job.getLockedBy())
                    .and("lockedUntil").lte(now));
                UpdateResult result = mongoTemplate.updateFirst(ownershipQuery, update, SubmissionJob.class);
                if (result.getModifiedCount() == 1) {
                    logger.warn("Job {} lease expired; moving to RETRYING until {}", job.getId(), retryAt);
                }
            }
        }

        Query retryingQuery = new Query();
        retryingQuery.addCriteria(Criteria.where("status").is(SubmissionJob.JobStatus.RETRYING));
        retryingQuery.addCriteria(Criteria.where("nextRetryAt").lte(now));

        List<SubmissionJob> retryingJobs = mongoTemplate.find(retryingQuery, SubmissionJob.class);
        for (SubmissionJob job : retryingJobs) {
            Update update = new Update();
            update.set("status", SubmissionJob.JobStatus.QUEUED);
            update.set("nextRetryAt", null);
            update.set("lastError", null);
            Query retryQuery = Query.query(Criteria.where("_id").is(job.getId())
                .and("status").is(SubmissionJob.JobStatus.RETRYING)
                .and("nextRetryAt").lte(now));
            UpdateResult result = mongoTemplate.updateFirst(retryQuery, update, SubmissionJob.class);
            if (result.getModifiedCount() == 1) {
                publisher.publishJob(job.getId());
                logger.info("Job {} retry window opened; published back to queue", job.getId());
            }
        }
    }

    @Scheduled(fixedDelayString = "${submission.materialization.repair-ms:30000}")
    public void repairCompletedJobsMissingSubmission() {
        Query missing = new Query();
        missing.addCriteria(Criteria.where("status").is(SubmissionJob.JobStatus.COMPLETED));
        missing.addCriteria(new Criteria().orOperator(
            Criteria.where("submissionId").is(null),
            Criteria.where("submissionId").exists(false)
        ));
        missing.limit(50);
        List<SubmissionJob> jobs = mongoTemplate.find(missing, SubmissionJob.class);
        for (SubmissionJob job : jobs) {
            Submission submission = submissionService.createSubmissionFromJob(job);
            if (submission != null) {
                leaseService.attachSubmissionIdIfCompleted(job.getId(), submission.getId());
                logger.info("Repaired missing submission {} for completed job {}", submission.getId(), job.getId());
            }
        }
    }

    private boolean finishMaterializedJob(SubmissionJob job, Date now) {
        Submission existing = submissionRepository.findBySubmissionJobId(job.getId()).orElse(null);
        boolean judged = job.getFinalResult() != null && !job.getFinalResult().isBlank();
        if (existing == null && !judged) {
            return false;
        }
        if (existing == null) {
            existing = submissionService.createSubmissionFromJob(job);
        }
        SubmissionJob payload = new SubmissionJob();
        payload.setFinalResult(job.getFinalResult() != null
            ? job.getFinalResult()
            : (existing == null ? "INTERNAL_ERROR" : existing.getResult()));
        payload.setTotalRuntime(job.getTotalRuntime() != null
            ? job.getTotalRuntime()
            : (existing == null ? null : existing.getRuntime()));
        payload.setTotalMemory(job.getTotalMemory() != null
            ? job.getTotalMemory()
            : (existing == null ? null : existing.getMemory()));
        payload.setCompletedTests(job.getCompletedTests());
        payload.setCompletedAt(now);
        payload.setMetadata(job.getMetadata());
        if (existing != null) {
            payload.setSubmissionId(existing.getId());
        }
        if (leaseService.completeExpiredRunningWithResult(job.getId(), payload)) {
            job.setStatus(SubmissionJob.JobStatus.COMPLETED);
            eventService.publish(job, SubmissionJob.JobStatus.COMPLETED);
            logger.warn("Job {} expired after judgement/materialization; completed without re-executing Judge0",
                job.getId());
            return true;
        }
        return submissionRepository.findBySubmissionJobId(job.getId()).isPresent();
    }
}
