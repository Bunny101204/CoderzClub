package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

@Service
public class SubmissionJobLeaseService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionJobLeaseService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    public Optional<SubmissionJob> claimJob(String jobId, String workerId, long leaseDurationSeconds) {
        Date now = new Date();
        Date leaseUntil = new Date(now.getTime() + leaseDurationSeconds * 1000);

        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("_id").is(jobId),
            Criteria.where("status").in(Arrays.asList(
                SubmissionJob.JobStatus.QUEUED,
                SubmissionJob.JobStatus.RETRYING
            )),
            new Criteria().orOperator(
                Criteria.where("lockedUntil").is(null),
                Criteria.where("lockedUntil").lte(now)
            ),
            new Criteria().orOperator(
                Criteria.where("nextRetryAt").is(null),
                Criteria.where("nextRetryAt").lte(now)
            )
        ));

        Update update = new Update();
        update.set("status", SubmissionJob.JobStatus.RUNNING);
        update.set("lockedBy", workerId);
        update.set("lockedUntil", leaseUntil);
        update.set("heartbeatAt", now);
        update.set("startedAt", now);
        update.set("nextRetryAt", null);
        update.inc("attemptCount", 1);

        SubmissionJob job = mongoTemplate.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), SubmissionJob.class);
        if (job == null) {
            return Optional.empty();
        }

        logger.debug("Claimed submission job {} by worker {} leaseUntil={}", jobId, workerId, leaseUntil);
        return Optional.of(job);
    }

    public boolean heartbeat(String jobId, String workerId, long leaseDurationSeconds) {
        Date now = new Date();
        Date leaseUntil = new Date(now.getTime() + leaseDurationSeconds * 1000);
        Query query = ownedRunningJob(jobId, workerId, now);

        Update update = new Update();
        update.set("heartbeatAt", now);
        update.set("lockedUntil", leaseUntil);

        boolean renewed = mongoTemplate.updateFirst(query, update, SubmissionJob.class).getModifiedCount() == 1;
        logger.debug("Heartbeat job {} by worker {} leaseRenewedUntil={}", jobId, workerId, leaseUntil);
        return renewed;
    }

    public boolean updateProgressIfOwned(String jobId, String workerId, int completedTests) {
        Query query = ownedRunningJob(jobId, workerId);
        Update update = new Update().set("completedTests", completedTests);
        return mongoTemplate.updateFirst(query, update, SubmissionJob.class).getModifiedCount() == 1;
    }

    public boolean completeIfOwned(String jobId, String workerId, SubmissionJob completionPayload) {
        Query query = ownedRunningJob(jobId, workerId);
        Update update = new Update()
            .set("status", SubmissionJob.JobStatus.COMPLETED)
            .set("testResults", completionPayload.getTestResults())
            .set("finalResult", completionPayload.getFinalResult())
            .set("totalRuntime", completionPayload.getTotalRuntime())
            .set("totalMemory", completionPayload.getTotalMemory())
            .set("completedTests", completionPayload.getCompletedTests())
            .set("completedAt", completionPayload.getCompletedAt())
            .set("lockedBy", null)
            .set("lockedUntil", null)
            .set("heartbeatAt", null)
            .set("nextRetryAt", null)
            .set("lastError", null);
        return mongoTemplate.updateFirst(query, update, SubmissionJob.class).getModifiedCount() == 1;
    }

    public boolean retryIfOwned(String jobId, String workerId, String error, Date nextRetryAt) {
        Query query = ownedRunningJob(jobId, workerId);
        Update update = new Update()
            .set("status", SubmissionJob.JobStatus.RETRYING)
            .set("lastError", error)
            .set("nextRetryAt", nextRetryAt)
            .set("lockedBy", null)
            .set("lockedUntil", null)
            .set("heartbeatAt", null);
        return mongoTemplate.updateFirst(query, update, SubmissionJob.class).getModifiedCount() == 1;
    }

    public boolean failIfOwned(String jobId, String workerId, String error) {
        Query query = ownedRunningJob(jobId, workerId);
        Update update = new Update()
            .set("status", SubmissionJob.JobStatus.FAILED)
            .set("lastError", error)
            .set("completedAt", new Date())
            .set("lockedBy", null)
            .set("lockedUntil", null)
            .set("heartbeatAt", null)
            .set("nextRetryAt", null);
        return mongoTemplate.updateFirst(query, update, SubmissionJob.class).getModifiedCount() == 1;
    }

    private Query ownedRunningJob(String jobId, String workerId) {
        return ownedRunningJob(jobId, workerId, new Date());
    }

    public boolean isOwned(String jobId, String workerId) {
        return mongoTemplate.exists(ownedRunningJob(jobId, workerId), SubmissionJob.class);
    }

    private Query ownedRunningJob(String jobId, String workerId, Date now) {
        return Query.query(Criteria.where("_id").is(jobId)
            .and("status").is(SubmissionJob.JobStatus.RUNNING)
            .and("lockedBy").is(workerId)
            .and("lockedUntil").gt(now));
    }
}
