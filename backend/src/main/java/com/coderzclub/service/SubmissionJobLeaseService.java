package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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

        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(jobId));
        query.addCriteria(Criteria.where("status").in(Arrays.asList(
            SubmissionJob.JobStatus.QUEUED,
            SubmissionJob.JobStatus.RETRYING
        )));
        query.addCriteria(new Criteria().orOperator(
            Criteria.where("lockedUntil").is(null),
            Criteria.where("lockedUntil").lte(now)
        ));
        query.addCriteria(new Criteria().orOperator(
            Criteria.where("nextRetryAt").is(null),
            Criteria.where("nextRetryAt").lte(now)
        ));

        Update update = new Update();
        update.set("status", SubmissionJob.JobStatus.RUNNING);
        update.set("lockedBy", workerId);
        update.set("lockedUntil", leaseUntil);
        update.set("heartbeatAt", now);
        update.set("startedAt", now);
        update.set("nextRetryAt", null);
        update.inc("attemptCount", 1);

        SubmissionJob job = mongoTemplate.findAndModify(query, update, SubmissionJob.class);
        if (job == null) {
            return Optional.empty();
        }

        logger.debug("Claimed submission job {} by worker {} leaseUntil={}", jobId, workerId, leaseUntil);
        return Optional.of(job);
    }

    public void heartbeat(String jobId, String workerId, long leaseDurationSeconds) {
        Date now = new Date();
        Date leaseUntil = new Date(now.getTime() + leaseDurationSeconds * 1000);
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(jobId));
        query.addCriteria(Criteria.where("status").is(SubmissionJob.JobStatus.RUNNING));
        query.addCriteria(Criteria.where("lockedBy").is(workerId));

        Update update = new Update();
        update.set("heartbeatAt", now);
        update.set("lockedUntil", leaseUntil);

        mongoTemplate.updateFirst(query, update, SubmissionJob.class);
        logger.debug("Heartbeat job {} by worker {} leaseRenewedUntil={}", jobId, workerId, leaseUntil);
    }
}
