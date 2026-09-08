package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

@Component
@Profile("!local")
public class RabbitSubmissionQueuePublisher implements SubmissionQueuePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;
    private final SubmissionQueueProperties properties;

    public RabbitSubmissionQueuePublisher(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin,
                                          SubmissionQueueProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
    }

    @Override
    public void publishJob(String jobId) {
        if (!canAccept()) {
            throw new IllegalStateException("Submission queue is at capacity");
        }
        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(), jobId, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) message.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
            return message;
        });
    }

    private boolean canAccept() {
        if (properties.getMaxDepth() <= 0) return true;
        QueueInformation info = rabbitAdmin.getQueueInfo(properties.getName());
        return info == null || info.getMessageCount() < properties.getMaxDepth();
    }
}