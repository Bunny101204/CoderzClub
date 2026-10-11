package com.coderzclub.queue;

import com.coderzclub.config.ContestRunProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitContestRunPublisherTest {
    @Test
    void publishesPersistentConfirmedMessage() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitAdmin admin = mock(RabbitAdmin.class);
        ContestRunProperties properties = new ContestRunProperties();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(any(), any(), any(), any(), any(CorrelationData.class));

        new RabbitContestRunPublisher(template, admin, properties).publishRun("run-1");

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(eq(properties.getQueue().getExchange()),
            eq(properties.getQueue().getRoutingKey()), eq("run-1"), processor.capture(), any(CorrelationData.class));
        Message message = processor.getValue().postProcessMessage(
            new Message("run-1".getBytes(), new org.springframework.amqp.core.MessageProperties()));
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void rejectsWhenQueueIsAtCapacity() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitAdmin admin = mock(RabbitAdmin.class);
        ContestRunProperties properties = new ContestRunProperties();
        properties.getQueue().setMaxDepth(1);
        doReturn(new org.springframework.amqp.core.QueueInformation(properties.getQueue().getName(), 1, 0))
            .when(admin).getQueueInfo(properties.getQueue().getName());
        assertThrows(IllegalStateException.class,
            () -> new RabbitContestRunPublisher(template, admin, properties).publishRun("run-1"));
    }
}
