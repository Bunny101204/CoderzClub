package com.coderzclub.service;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.SubmissionOutboxEvent;
import com.coderzclub.repository.SubmissionJobRepository;
import com.coderzclub.repository.SubmissionOutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxReliabilityTest {

    @Test
    void publisherFailureMarksEventFailedForRetry() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        SubmissionOutboxRepository repository = mock(SubmissionOutboxRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        SubmissionOutboxEvent event = event("event-1", "job-1");
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(SubmissionOutboxEvent.class)))
            .thenReturn(event, null);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(rabbit).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(mongo, repository, rabbit, new SubmissionQueueProperties(), new SimpleMeterRegistry())
            .publishDueEvents();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(SubmissionOutboxEvent.class));
        assertEquals(SubmissionOutboxEvent.Status.FAILED,
            update.getValue().getUpdateObject().get("$set", org.bson.Document.class).get("status"));
    }

    @Test
    void publisherConfirmMarksEventPublished() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        SubmissionOutboxRepository repository = mock(SubmissionOutboxRepository.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        SubmissionOutboxEvent event = event("event-2", "job-2");
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(SubmissionOutboxEvent.class)))
            .thenReturn(event, null);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        new OutboxPublisher(mongo, repository, rabbit, new SubmissionQueueProperties(), new SimpleMeterRegistry())
            .publishDueEvents();

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), update.capture(), eq(SubmissionOutboxEvent.class));
        assertEquals(SubmissionOutboxEvent.Status.PUBLISHED,
            update.getValue().getUpdateObject().get("$set", org.bson.Document.class).get("status"));
    }

    @Test
    void orphanQueuedJobCreatesRecoveryEvent() {
        SubmissionJobRepository jobs = mock(SubmissionJobRepository.class);
        SubmissionOutboxRepository outbox = mock(SubmissionOutboxRepository.class);
        SubmissionJob job = new SubmissionJob();
        job.setId("job-orphan");
        when(jobs.findByStatusOrderByCreatedAtAsc(SubmissionJob.JobStatus.QUEUED)).thenReturn(List.of(job));
        when(outbox.existsByAggregateIdAndEventType("job-orphan", "SubmissionJobCreated")).thenReturn(false);

        new SubmissionOutboxRepairService(jobs, outbox).repairOrphanedJobs();

        ArgumentCaptor<SubmissionOutboxEvent> event = ArgumentCaptor.forClass(SubmissionOutboxEvent.class);
        verify(outbox).save(event.capture());
        assertEquals("job-orphan", event.getValue().getPayload());
        assertEquals(SubmissionOutboxEvent.Status.PENDING, event.getValue().getStatus());
    }

    @Test
    void jobCreationPersistsOutboxAfterJobSave() {
        SubmissionJobRepository jobs = mock(SubmissionJobRepository.class);
        SubmissionOutboxService outbox = mock(SubmissionOutboxService.class);
        SubmissionJobEventService events = mock(SubmissionJobEventService.class);
        WorkerProperties properties = new WorkerProperties();
        SubmissionJob saved = new SubmissionJob();
        saved.setId("job-created");
        when(jobs.save(any(SubmissionJob.class))).thenReturn(saved);

        SubmissionJobService service = new SubmissionJobService();
        set(service, "jobRepository", jobs);
        set(service, "outboxService", outbox);
        set(service, "eventService", events);
        set(service, "workerProperties", properties);
        set(service, "resultRepository", mock(com.coderzclub.repository.SubmissionTestResultRepository.class));

        service.createJob("user", "problem", "code", "java", java.util.Objects.hash(1), "v1", 1);

        var order = inOrder(jobs, outbox);
        order.verify(jobs).save(any(SubmissionJob.class));
        order.verify(outbox).createJobEvent("job-created");
    }

    private static SubmissionOutboxEvent event(String id, String jobId) {
        SubmissionOutboxEvent event = new SubmissionOutboxEvent();
        event.setId(id);
        event.setAggregateId(jobId);
        event.setPayload(jobId);
        event.setStatus(SubmissionOutboxEvent.Status.PENDING);
        event.setNextAttemptAt(new Date());
        return event;
    }

    private static void set(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
