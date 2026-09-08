package com.coderzclub.service;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.SubmissionJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import java.util.List;

@Service
public class SubmissionQueueAdmissionService {
    @Autowired(required = false) private RabbitAdmin rabbitAdmin;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private SubmissionQueueProperties properties;
    @Autowired private OperationalMetrics metrics;

    public Admission check() {
        try {
            int depth = 0;
            if (rabbitAdmin != null) {
                QueueInformation info = rabbitAdmin.getQueueInfo(properties.getName());
                depth = info == null ? 0 : info.getMessageCount();
            }
            Query query = Query.query(Criteria.where("status").in(List.of(SubmissionJob.JobStatus.QUEUED, SubmissionJob.JobStatus.RETRYING, SubmissionJob.JobStatus.PENDING)));
            query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
            SubmissionJob oldest = mongoTemplate.findOne(query, SubmissionJob.class);
            long age = oldest == null || oldest.getCreatedAt() == null ? 0 : Math.max(0, (System.currentTimeMillis() - oldest.getCreatedAt().getTime()) / 1000);
            if (depth >= properties.getAdmissionMaxDepth()) return reject("QUEUE_DEPTH", depth, age);
            if (age >= properties.getAdmissionMaxOldestAgeSeconds()) return reject("QUEUE_AGE", depth, age);
            metrics.admission("allowed", "none");
            return new Admission(true, depth, age, "none");
        } catch (Exception ex) {
            metrics.admission("rejected", "QUEUE_UNAVAILABLE");
            metrics.dependencyError("broker");
            return new Admission(false, -1, -1, "QUEUE_UNAVAILABLE");
        }
    }
    private Admission reject(String reason, int depth, long age) { metrics.admission("rejected", reason); return new Admission(false, depth, age, reason); }
    public record Admission(boolean allowed, int depth, long oldestAgeSeconds, String reason) {}
}