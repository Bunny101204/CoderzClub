package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitSubmissionQueueConsumerTest {

    @Test
    void invalidMessageIsRejectedToDeadLetterExchange() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitSubmissionQueueConsumer consumer = new RabbitSubmissionQueueConsumer(
            mock(ConnectionFactory.class), template, new SubmissionQueueProperties());
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(11L);

        consumer.handle(new Message(new byte[0], properties), mock(Channel.class), jobId -> {
            throw new AssertionError("invalid payload must not reach handler");
        });

        Channel channel = mock(Channel.class);
        consumer.handle(new Message(new byte[0], properties), channel, jobId -> SubmissionQueueConsumer.MessageDisposition.ACK);
        verify(channel).basicReject(11L, false);
    }

    @Test
    void retryPublishesToRetryRouteAndAcknowledgesOriginal() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        SubmissionQueueProperties config = new SubmissionQueueProperties();
        RabbitSubmissionQueueConsumer consumer = new RabbitSubmissionQueueConsumer(
            mock(ConnectionFactory.class), template, config);
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(12L);

        consumer.handle(new Message("job-1".getBytes(), properties), channel,
            jobId -> SubmissionQueueConsumer.MessageDisposition.RETRY);

        verify(template).convertAndSend(config.getExchange(), config.getRetryRoutingKey(), "job-1");
        verify(channel).basicAck(12L, false);
    }
}