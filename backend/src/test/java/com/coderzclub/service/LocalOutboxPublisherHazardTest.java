package com.coderzclub.service;

import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.queue.InMemorySubmissionQueuePublisher;
import com.coderzclub.repository.SubmissionOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalOutboxPublisherHazardTest {

    @Test
    void marksPublishedIntoProcessLocalQueueWithoutAtomicClaim() {
        InMemorySubmissionQueuePublisher publisher = new InMemorySubmissionQueuePublisher();
        SubmissionOutboxRepository repository = mock(SubmissionOutboxRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);
        SubmissionOutboxEvent event = new SubmissionOutboxEvent();
        event.setId("event-local");
        event.setPayload("job-local");
        event.setStatus(SubmissionOutboxEvent.Status.PENDING);
        event.setNextAttemptAt(new Date(0));
        event.setAttemptCount(0);
        when(repository.findAll()).thenReturn(List.of(event));

        new LocalOutboxPublisher(publisher, repository, metrics).publishDueEvents();

        assertEquals(SubmissionOutboxEvent.Status.PUBLISHED, event.getStatus());
        assertEquals(0, event.getAttemptCount());
        assertEquals("job-local", publisher.getQueue().poll());
        assertNull(publisher.getQueue().poll());
        verify(repository).save(event);
        assertEquals(1, registry.find("submission.outbox.publish")
            .tag("transport", "in_memory").tag("outcome", "published").counter().count());
    }
}
