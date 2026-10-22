package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes a contest run and waits for the correlated publisher confirm.
 * Spring AMQP 3.1 completes {@link CorrelationData#getFuture()} from the broker ack/nack.
 * {@code x-overflow=reject-publish} is a basic.nack with no cause. A nack that carries a
 * cause is a channel shutdown, which is broker unavailability rather than capacity.
 * Only {@code ack=true} is a successful publish.
 */
@Component
@Profile("!local")
public class RabbitContestRunPublisher implements ContestRunPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final ContestRunProperties properties;

    public RabbitContestRunPublisher(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin,
                                     ContestRunProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
    }

    @Override
    public void publishRun(String runId) {
        ContestRunProperties.Queue queue = properties.getQueue();
        if (!canAccept()) {
            throw new ContestRunPublishRejectedException();
        }
        CorrelationData correlation = new CorrelationData(runId == null ? UUID.randomUUID().toString() : runId);
        try {
            rabbitTemplate.convertAndSend(queue.getExchange(), queue.getRoutingKey(), runId, message -> {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                String correlationId = MDC.get("correlationId");
                if (correlationId != null) {
                    message.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
                }
                return message;
            }, correlation);
        } catch (RuntimeException sendFailure) {
            throw new IllegalStateException("Contest run publish was not confirmed", sendFailure);
        }
        CorrelationData.Confirm confirm = awaitConfirm(correlation);
        if (confirm != null && confirm.isAck()) {
            return;
        }
        if (isCapacityRejection(confirm)) {
            throw new ContestRunPublishRejectedException();
        }
        throw new IllegalStateException("Contest run publish was not confirmed");
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation) {
        try {
            return correlation.getFuture().get(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Contest run publish was not confirmed", interrupted);
        } catch (Exception waitFailure) {
            throw new IllegalStateException("Contest run publish was not confirmed", waitFailure);
        }
    }

    /**
     * Reject-publish nacks have a null cause. Channel shutdown nacks carry the shutdown text.
     * A literal {@code nack} cause is treated as capacity so a broker reason string is never required.
     */
    static boolean isCapacityRejection(CorrelationData.Confirm confirm) {
        if (confirm == null || confirm.isAck()) {
            return false;
        }
        String reason = confirm.getReason();
        return reason == null || reason.isBlank() || "nack".equalsIgnoreCase(reason.trim());
    }

    /**
     * Advisory snapshot. The broker max-length confirm is the distributed bound.
     */
    private boolean canAccept() {
        int maxDepth = properties.getQueue().getMaxDepth();
        if (maxDepth <= 0) {
            return true;
        }
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getQueue().getName());
        return info == null || info.getMessageCount() < maxDepth;
    }
}
