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
            throw new IllegalStateException("Contest run queue is at capacity");
        }
        CorrelationData correlation = new CorrelationData(runId == null ? UUID.randomUUID().toString() : runId);
        rabbitTemplate.convertAndSend(queue.getExchange(), queue.getRoutingKey(), runId, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                message.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
            }
            return message;
        }, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "No publisher confirm" : String.valueOf(confirm.getReason()));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Contest run publish was not confirmed", e);
        }
    }

    private boolean canAccept() {
        int maxDepth = properties.getQueue().getMaxDepth();
        if (maxDepth <= 0) {
            return true;
        }
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getQueue().getName());
        return info == null || info.getMessageCount() < maxDepth;
    }
}
