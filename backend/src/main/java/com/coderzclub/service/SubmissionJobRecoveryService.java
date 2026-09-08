package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.queue.SubmissionQueuePublisher;
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

    @Scheduled(fixedDelayString = "${worker.recoveryIntervalSeconds:30}000")
    public void recoverStuckJobs() {
        Date now = new Date();
        Query runningQuery = new Query();
        runningQuery.addCriteria(Criteria.where("status").is(SubmissionJob.JobStatus.RUNNING));
        runningQuery.addCriteria(Criteria.where("lockedUntil").lte(now));

        List<SubmissionJob> runningJobs = mongoTemplate.find(runningQuery, SubmissionJob.class);
        for (SubmissionJob job : runningJobs) {
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
}
