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

import org.springframework.amqp.AmqpConnectException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void advisoryDepthRejectionDoesNotPublish() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitAdmin admin = mock(RabbitAdmin.class);
        ContestRunProperties properties = new ContestRunProperties();
        properties.getQueue().setMaxDepth(1);
        doReturn(new org.springframework.amqp.core.QueueInformation(properties.getQueue().getName(), 1, 0))
            .when(admin).getQueueInfo(properties.getQueue().getName());
        assertThrows(ContestRunPublishRejectedException.class,
            () -> new RabbitContestRunPublisher(template, admin, properties).publishRun("run-1"));
        verify(template, never()).convertAndSend(any(), any(), any(), any(), any(CorrelationData.class));
    }

    @Test
    void nullCauseNackIsCapacityRejection() {
        RabbitTemplate template = confirmingTemplate(new CorrelationData.Confirm(false, null));
        ContestRunPublishRejectedException rejected = assertThrows(ContestRunPublishRejectedException.class,
            () -> publisher(template).publishRun("run-1"));
        assertEquals("Contest run queue rejected publish", rejected.getMessage());
    }

    @Test
    void literalNackCauseIsCapacityRejection() {
        RabbitTemplate template = confirmingTemplate(new CorrelationData.Confirm(false, "nack"));
        assertThrows(ContestRunPublishRejectedException.class, () -> publisher(template).publishRun("run-1"));
    }

    @Test
    void shutdownNackIsUnavailableRatherThanCapacity() {
        RabbitTemplate template = confirmingTemplate(new CorrelationData.Confirm(false, "connection closed"));
        IllegalStateException unavailable = assertThrows(IllegalStateException.class,
            () -> publisher(template).publishRun("run-1"));
        assertEquals("Contest run publish was not confirmed", unavailable.getMessage());
    }

    @Test
    void brokerTransportFailureIsUnavailable() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("down")))
            .when(template).convertAndSend(any(), any(), any(), any(), any(CorrelationData.class));
        IllegalStateException unavailable = assertThrows(IllegalStateException.class,
            () -> publisher(template).publishRun("run-1"));
        assertEquals("Contest run publish was not confirmed", unavailable.getMessage());
    }

    /**
     * API depth checks are separate reads and cannot be the distributed bound.
     * Simultaneous publishes succeed only while the broker confirms them; a nack is a rejection.
     * Real x-max-length behavior is validated in the GCP contest-run load test, not this unit test.
     */
    @Test
    void concurrentConfirmsSurfaceNacksAsRejection() throws Exception {
        RabbitTemplate template = mock(RabbitTemplate.class);
        AtomicInteger accepted = new AtomicInteger();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (accepted.incrementAndGet() <= 2) {
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            } else {
                correlation.getFuture().complete(new CorrelationData.Confirm(false, null));
            }
            return null;
        }).when(template).convertAndSend(any(), any(), any(), any(), any(CorrelationData.class));
        RabbitContestRunPublisher publisher = publisher(template);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int id = i;
                futures.add(pool.submit(() -> {
                    try {
                        publisher.publishRun("run-" + id);
                        return "ACK";
                    } catch (ContestRunPublishRejectedException rejected) {
                        return "REJECT";
                    }
                }));
            }
            int acks = 0;
            int rejects = 0;
            for (Future<String> future : futures) {
                String outcome = future.get();
                if ("ACK".equals(outcome)) {
                    acks++;
                } else if ("REJECT".equals(outcome)) {
                    rejects++;
                } else {
                    throw new AssertionError(outcome);
                }
            }
            assertEquals(2, acks);
            assertEquals(6, rejects);
        } finally {
            pool.shutdownNow();
        }
    }

    private static RabbitContestRunPublisher publisher(RabbitTemplate template) {
        return new RabbitContestRunPublisher(template, mock(RabbitAdmin.class), new ContestRunProperties());
    }

    private static RabbitTemplate confirmingTemplate(CorrelationData.Confirm confirm) {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(confirm);
            return null;
        }).when(template).convertAndSend(any(), any(), any(), any(), any(CorrelationData.class));
        return template;
    }
}
