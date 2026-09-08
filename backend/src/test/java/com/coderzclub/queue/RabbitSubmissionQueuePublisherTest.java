package com.coderzclub.queue;

import com.coderzclub.config.SubmissionQueueProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

class RabbitSubmissionQueuePublisherTest {

    @Test
    void publishesPersistentJobIdMessage() {
        RabbitTemplate template = org.mockito.Mockito.mock(RabbitTemplate.class);
        RabbitAdmin admin = org.mockito.Mockito.mock(RabbitAdmin.class);
        SubmissionQueueProperties properties = new SubmissionQueueProperties();
        RabbitSubmissionQueuePublisher publisher = new RabbitSubmissionQueuePublisher(template, admin, properties);

        publisher.publishJob("job-1");

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(template).convertAndSend(eq(properties.getExchange()), eq(properties.getRoutingKey()), eq("job-1"), processor.capture());
        Message message = processor.getValue().postProcessMessage(new Message("job-1".getBytes(), new org.springframework.amqp.core.MessageProperties()));
        assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
    }

    @Test
    void rejectsNewPublishWhenQueueIsAtCapacity() {
        RabbitTemplate template = org.mockito.Mockito.mock(RabbitTemplate.class);
        RabbitAdmin admin = org.mockito.Mockito.mock(RabbitAdmin.class);
        SubmissionQueueProperties properties = new SubmissionQueueProperties();
        properties.setMaxDepth(1);
        doReturn(new org.springframework.amqp.core.QueueInformation(properties.getName(), 1, 0))
            .when(admin).getQueueInfo(properties.getName());
        RabbitSubmissionQueuePublisher publisher = new RabbitSubmissionQueuePublisher(template, admin, properties);

        assertThrows(IllegalStateException.class, () -> publisher.publishJob("job-1"));
    }
}
