package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.service.OperationalMetrics;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitContestRunConsumerTest {
    @Test
    void emptyPayloadGoesToDlq() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitContestRunConsumer consumer = new RabbitContestRunConsumer(
            mock(ConnectionFactory.class), template, new ContestRunProperties(),
            new OperationalMetrics(new SimpleMeterRegistry()));
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        Channel channel = mock(Channel.class);
        consumer.handle(new Message(new byte[0], properties), channel, runId -> {
            throw new AssertionError("empty payload must not reach handler");
        });
        verify(channel).basicReject(9L, false);
    }

    @Test
    void ackDispositionAcknowledges() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitContestRunConsumer consumer = new RabbitContestRunConsumer(
            mock(ConnectionFactory.class), template, new ContestRunProperties(),
            new OperationalMetrics(new SimpleMeterRegistry()));
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(10L);
        Channel channel = mock(Channel.class);
        consumer.handle(new Message("run-1".getBytes(), properties), channel,
            runId -> SubmissionQueueConsumer.MessageDisposition.ACK);
        verify(channel).basicAck(10L, false);
    }
}
