package com.coderzclub.service;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestRunJob;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContestRunQueueAdmissionService {
    @Autowired(required = false)
    private RabbitAdmin rabbitAdmin;
    private final MongoTemplate mongoTemplate;
    private final ContestRunProperties properties;

    public ContestRunQueueAdmissionService(MongoTemplate mongoTemplate, ContestRunProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    /**
     * Advisory pre-publish check. Depth is a separate read — the Rabbit management/passive
     * declare count when available, otherwise a Mongo count of QUEUED and RUNNING jobs —
     * and it is not atomic with publish. Several API instances can all observe depth below
     * the limit and publish. {@code x-max-length} with {@code reject-publish} is the hard bound.
     */
    public Admission check() {
        if (!properties.isEnabled()) {
            return new Admission(false, -1, -1, "DISABLED");
        }
        try {
            ContestRunProperties.Queue queue = properties.getQueue();
            int depth = mongoQueuedDepth();
            if (rabbitAdmin != null) {
                QueueInformation info = rabbitAdmin.getQueueInfo(queue.getName());
                if (info != null) {
                    depth = info.getMessageCount();
                }
            }
            Query query = Query.query(Criteria.where("status").is(ContestRunJob.Status.QUEUED));
            query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
            ContestRunJob oldest = mongoTemplate.findOne(query, ContestRunJob.class);
            long age = oldest == null || oldest.getCreatedAt() == null
                ? 0
                : Math.max(0, (System.currentTimeMillis() - oldest.getCreatedAt().getTime()) / 1000);
            if (depth >= queue.getAdmissionMaxDepth()) {
                return new Admission(false, depth, age, "QUEUE_DEPTH");
            }
            if (age >= queue.getAdmissionMaxOldestAgeSeconds()) {
                return new Admission(false, depth, age, "QUEUE_AGE");
            }
            return new Admission(true, depth, age, "none");
        } catch (Exception ex) {
            return new Admission(false, -1, -1, "QUEUE_UNAVAILABLE");
        }
    }

    private int mongoQueuedDepth() {
        Query query = Query.query(Criteria.where("status").in(List.of(
            ContestRunJob.Status.QUEUED, ContestRunJob.Status.RUNNING)));
        return (int) mongoTemplate.count(query, ContestRunJob.class);
    }

    public record Admission(boolean allowed, int depth, long oldestAgeSeconds, String reason) {}
}
