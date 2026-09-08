package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.SubmissionJob;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

@Component
@Profile("!local")
public class RabbitSubmissionQueueMetrics {
    private static final Logger logger = LoggerFactory.getLogger(RabbitSubmissionQueueMetrics.class);

    private final RabbitAdmin rabbitAdmin;
    private final SubmissionQueueProperties properties;
    private final MongoTemplate mongoTemplate;

    public RabbitSubmissionQueueMetrics(RabbitAdmin rabbitAdmin, SubmissionQueueProperties properties,
                                        MongoTemplate mongoTemplate, MeterRegistry meterRegistry) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
        this.mongoTemplate = mongoTemplate;
        Gauge.builder("submission.queue.depth", this, RabbitSubmissionQueueMetrics::queueDepth)
            .description("Messages ready in the RabbitMQ submission queue")
            .register(meterRegistry);
        Gauge.builder("submission.queue.oldest_message_age_seconds", this,
                RabbitSubmissionQueueMetrics::oldestMessageAgeSeconds)
            .description("Age of the oldest queued submission job")
            .register(meterRegistry);
        Gauge.builder("submission.queue.dlq_depth", this, RabbitSubmissionQueueMetrics::deadLetterDepth)
            .description("Messages waiting in the submission dead-letter queue")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${submission.queue.metrics-interval-ms:30000}")
    public void reportDeadLetters() {
        double depth = deadLetterDepth();
        if (depth > 0) {
            logger.warn("Submission queue DLQ contains {} message(s)", depth);
        }
    }

    public double queueDepth() {
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getName());
        return info == null ? 0 : info.getMessageCount();
    }

    public double deadLetterDepth() {
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getDlqName());
        return info == null ? 0 : info.getMessageCount();
    }

    public double oldestMessageAgeSeconds() {
        Query query = Query.query(Criteria.where("status").in(List.of(
            SubmissionJob.JobStatus.QUEUED, SubmissionJob.JobStatus.PENDING,
            SubmissionJob.JobStatus.RETRYING)));
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
        SubmissionJob oldest = mongoTemplate.findOne(query, SubmissionJob.class);
        return oldest == null || oldest.getCreatedAt() == null
            ? 0 : Math.max(0, (System.currentTimeMillis() - oldest.getCreatedAt().getTime()) / 1000.0);
    }
}