package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestRunJob;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class RabbitContestRunQueueMetrics {
    private final RabbitAdmin rabbitAdmin;
    private final ContestRunProperties properties;
    private final MongoTemplate mongoTemplate;

    public RabbitContestRunQueueMetrics(RabbitAdmin rabbitAdmin, ContestRunProperties properties,
                                        MongoTemplate mongoTemplate, MeterRegistry meterRegistry) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
        this.mongoTemplate = mongoTemplate;
        Gauge.builder("contest.run.queue.depth", this, RabbitContestRunQueueMetrics::queueDepth)
            .description("Messages ready in the contest-run RabbitMQ queue")
            .register(meterRegistry);
        Gauge.builder("contest.run.queue.oldest_message_age_seconds", this,
                RabbitContestRunQueueMetrics::oldestMessageAgeSeconds)
            .description("Age of the oldest queued contest Run job")
            .register(meterRegistry);
    }

    public double queueDepth() {
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getQueue().getName());
        return info == null ? 0 : info.getMessageCount();
    }

    public double oldestMessageAgeSeconds() {
        Query query = Query.query(Criteria.where("status").is(ContestRunJob.Status.QUEUED));
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"));
        ContestRunJob oldest = mongoTemplate.findOne(query, ContestRunJob.class);
        return oldest == null || oldest.getCreatedAt() == null
            ? 0 : Math.max(0, (System.currentTimeMillis() - oldest.getCreatedAt().getTime()) / 1000.0);
    }
}
