package com.coderzclub.service;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.repository.SubmissionOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("!local")
public class OutboxPublisher {
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    private final MongoTemplate mongoTemplate;
    private final SubmissionOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final SubmissionQueueProperties properties;
    private final MeterRegistry metrics;

    public OutboxPublisher(MongoTemplate mongoTemplate, SubmissionOutboxRepository repository,
                           RabbitTemplate rabbitTemplate, SubmissionQueueProperties properties,
                           MeterRegistry metrics) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${submission.outbox.poll-ms:1000}")
    public void publishDueEvents() {
        SubmissionOutboxEvent event;
        while ((event = claimNext()) != null) {
            publish(event);
        }
    }

    private SubmissionOutboxEvent claimNext() {
        Date now = new Date();
        Query query = Query.query(new Criteria().andOperator(
            Criteria.where("status").in(List.of(SubmissionOutboxEvent.Status.PENDING, SubmissionOutboxEvent.Status.FAILED)),
            new Criteria().orOperator(Criteria.where("nextAttemptAt").is(null), Criteria.where("nextAttemptAt").lte(now)),
            new Criteria().orOperator(Criteria.where("lockedUntil").is(null), Criteria.where("lockedUntil").lte(now))
        )).limit(1);
        Update update = new Update()
            .inc("attemptCount", 1)
            .set("lockedUntil", new Date(now.getTime() + 60_000));
        return mongoTemplate.findAndModify(query, update,
            FindAndModifyOptions.options().returnNew(true), SubmissionOutboxEvent.class);
    }

    private void publish(SubmissionOutboxEvent event) {
        try {
            CorrelationData correlation = new CorrelationData(event.getId());
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), event.getPayload(), message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                message.getMessageProperties().setHeader("X-Outbox-Event-ID", event.getId());
                return message;
            }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "No publisher confirm" : confirm.getReason());
            }
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(event.getId())),
                new Update().set("status", SubmissionOutboxEvent.Status.PUBLISHED)
                    .set("publishedAt", new Date()).set("lockedUntil", null).set("lastError", null),
                SubmissionOutboxEvent.class);
            counter("published");
            logger.info("submission_outbox_published eventId={} jobId={} attemptCount={}",
                event.getId(), event.getAggregateId(), event.getAttemptCount());
        } catch (Exception failure) {
            Date retryAt = new Date(System.currentTimeMillis() + backoffMillis(event.getAttemptCount()));
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(event.getId())),
                new Update().set("status", SubmissionOutboxEvent.Status.FAILED)
                    .set("nextAttemptAt", retryAt).set("lockedUntil", null)
                    .set("lastError", safeError(failure)), SubmissionOutboxEvent.class);
            counter("failed");
            logger.warn("submission_outbox_publish_failed eventId={} jobId={} attemptCount={} nextAttemptAt={} error={}",
                event.getId(), event.getAggregateId(), event.getAttemptCount(), retryAt, safeError(failure));
        }
    }

    private long backoffMillis(int attempt) {
        long base = Math.min(300_000L, 1_000L * (1L << Math.min(8, Math.max(0, attempt - 1))));
        return base + ThreadLocalRandom.current().nextLong(250L);
    }

    private String safeError(Exception failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message.substring(0, Math.min(500, message.length()));
    }

    private void counter(String outcome) {
        metrics.counter("submission.outbox.publish", "outcome", outcome).increment();
    }
}