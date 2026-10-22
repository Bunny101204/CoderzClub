package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.service.OperationalMetrics;
import org.slf4j.MDC;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Profile("worker & !local")
public class RabbitContestRunConsumer implements ContestRunConsumer {
    private final ConnectionFactory connectionFactory;
    private final RabbitTemplate rabbitTemplate;
    private final ContestRunProperties properties;
    private final OperationalMetrics metrics;
    private SimpleMessageListenerContainer container;

    public RabbitContestRunConsumer(ConnectionFactory connectionFactory, RabbitTemplate rabbitTemplate,
                                    ContestRunProperties properties, OperationalMetrics metrics) {
        this.connectionFactory = connectionFactory;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public synchronized void start(SubmissionQueueConsumer.MessageHandler handler, int concurrency) {
        if (container != null) {
            return;
        }
        ContestRunProperties.Queue queue = properties.getQueue();
        container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setPrefetchCount(Math.max(1, queue.getPrefetch()));
        container.setConcurrentConsumers(Math.max(1, concurrency));
        container.setMaxConcurrentConsumers(Math.max(1, concurrency));
        container.setMessageListener((ChannelAwareMessageListener) (message, channel) ->
            handle(message, channel, handler));
        container.start();
    }

    void handle(Message message, com.rabbitmq.client.Channel channel,
                SubmissionQueueConsumer.MessageHandler handler) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        ContestRunProperties.Queue queue = properties.getQueue();
        try {
            String runId = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            if (runId.isEmpty()) {
                metrics.queueConsumer("dead_letter");
                channel.basicReject(deliveryTag, false);
                return;
            }
            String correlationId = message.getMessageProperties().getHeader("X-Correlation-ID");
            try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId",
                correlationId == null ? "contest-run-" + runId : correlationId)) {
                SubmissionQueueConsumer.MessageDisposition disposition = handler.handle(runId);
                if (disposition == SubmissionQueueConsumer.MessageDisposition.RETRY) {
                    metrics.queueConsumer("retry");
                    if (correlationId == null) {
                        rabbitTemplate.convertAndSend(queue.getExchange(), queue.getRetryRoutingKey(), runId);
                    } else {
                        rabbitTemplate.convertAndSend(queue.getExchange(), queue.getRetryRoutingKey(), runId, retry -> {
                            retry.getMessageProperties().setHeader("X-Correlation-ID", correlationId);
                            return retry;
                        });
                    }
                    channel.basicAck(deliveryTag, false);
                } else if (disposition == SubmissionQueueConsumer.MessageDisposition.DEAD_LETTER) {
                    metrics.queueConsumer("dead_letter");
                    channel.basicReject(deliveryTag, false);
                } else {
                    metrics.queueConsumer("acked");
                    channel.basicAck(deliveryTag, false);
                }
            }
        } catch (Exception transientFailure) {
            metrics.queueConsumer("requeued_exception");
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (java.io.IOException acknowledgementFailure) {
                throw new IllegalStateException("Unable to requeue contest run message", acknowledgementFailure);
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
