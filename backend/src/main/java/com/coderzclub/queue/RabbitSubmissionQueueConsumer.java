package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;

@Component
@Profile("worker")
public class RabbitSubmissionQueueConsumer implements SubmissionQueueConsumer {
    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final SubmissionQueueProperties properties;
    private SimpleMessageListenerContainer container;

    public RabbitSubmissionQueueConsumer(ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate,
                                         SubmissionQueueProperties properties) {
        this.connectionFactory = connectionFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Override
    public synchronized void start(MessageHandler handler, int concurrency) {
        if (container != null) return;
        container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(properties.getName());
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setPrefetchCount(Math.max(1, properties.getPrefetch()));
        container.setConcurrentConsumers(Math.max(1, concurrency));
        container.setMaxConcurrentConsumers(Math.max(1, concurrency));
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) -> handle(message, channel, handler));
        container.start();
    }

    void handle(Message message, com.rabbitmq.client.Channel channel, MessageHandler handler) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String jobId = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            if (jobId.isEmpty()) {
                channel.basicReject(deliveryTag, false);
                return;
            }
            String correlationId = message.getMessageProperties().getHeader("X-Correlation-ID");
            try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId",
                correlationId == null ? "queue-" + jobId : correlationId)) {
                MessageDisposition disposition = handler.handle(jobId);
                if (disposition == MessageDisposition.RETRY) {
                    if (correlationId == null) {
                        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRetryRoutingKey(), jobId);
                    } else {
                        rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRetryRoutingKey(), jobId, retry -> {
                            retry.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
                            return retry;
                        });
                    }
                    channel.basicAck(deliveryTag, false);
                } else if (disposition == MessageDisposition.DEAD_LETTER) {
                    channel.basicReject(deliveryTag, false);
                } else {
                    channel.basicAck(deliveryTag, false);
                }
            }
        } catch (Exception transientFailure) {
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (java.io.IOException acknowledgementFailure) {
                throw new IllegalStateException("Unable to requeue RabbitMQ message", acknowledgementFailure);
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}