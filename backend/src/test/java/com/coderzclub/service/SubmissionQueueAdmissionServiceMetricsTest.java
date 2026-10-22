package com.coderzclub.service;

import com.coderzclub.config.SubmissionQueueProperties;
import com.coderzclub.model.SubmissionJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionQueueAdmissionServiceMetricsTest {

    @Mock private RabbitAdmin rabbitAdmin;
    @Mock private MongoTemplate mongoTemplate;

    private SimpleMeterRegistry registry;
    private SubmissionQueueProperties properties;
    private SubmissionQueueAdmissionService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new SubmissionQueueProperties();
        service = new SubmissionQueueAdmissionService();
        ReflectionTestUtils.setField(service, "rabbitAdmin", rabbitAdmin);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "metrics", new OperationalMetrics(registry));
    }

    @Test
    void allowedAdmissionUnchangedAndTimersRecorded() {
        QueueInformation info = mock(QueueInformation.class);
        when(info.getMessageCount()).thenReturn(3);
        when(rabbitAdmin.getQueueInfo(properties.getName())).thenReturn(info);
        when(mongoTemplate.findOne(any(Query.class), eq(SubmissionJob.class))).thenReturn(null);

        SubmissionQueueAdmissionService.Admission admission = service.check();

        assertTrue(admission.allowed());
        assertEquals(3, admission.depth());
        assertEquals("none", admission.reason());
        assertEquals(1, registry.find("submission.admission").tag("outcome", "allowed").counter().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "rabbit_queue_info").timer().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "mongo_oldest_job").timer().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "total").timer().count());
    }

    @Test
    void rejectedAdmissionUnchangedWhenQueueTooDeep() {
        properties.setAdmissionMaxDepth(1);
        QueueInformation info = mock(QueueInformation.class);
        when(info.getMessageCount()).thenReturn(1);
        when(rabbitAdmin.getQueueInfo(properties.getName())).thenReturn(info);
        when(mongoTemplate.findOne(any(Query.class), eq(SubmissionJob.class))).thenReturn(null);

        SubmissionQueueAdmissionService.Admission admission = service.check();

        assertFalse(admission.allowed());
        assertEquals("QUEUE_DEPTH", admission.reason());
        assertEquals(1, registry.find("submission.admission").tag("outcome", "rejected").tag("reason", "QUEUE_DEPTH").counter().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "total").timer().count());
    }

    @Test
    void brokerExceptionStillReturnsQueueUnavailableAndRecordsTotal() {
        when(rabbitAdmin.getQueueInfo(properties.getName())).thenThrow(new RuntimeException("broker down"));

        SubmissionQueueAdmissionService.Admission admission = service.check();

        assertFalse(admission.allowed());
        assertEquals("QUEUE_UNAVAILABLE", admission.reason());
        assertEquals(-1, admission.depth());
        assertEquals(1, registry.find("submission.admission").tag("reason", "QUEUE_UNAVAILABLE").counter().count());
        assertEquals(1, registry.find("dependency.errors").counter().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "rabbit_queue_info").timer().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "total").timer().count());
        assertEquals(0, registry.find("submission.queue_admission.stage").tag("stage", "mongo_oldest_job").timer().count());
    }

    @Test
    void oldestJobAgeRejectionUnchanged() {
        properties.setAdmissionMaxOldestAgeSeconds(1);
        QueueInformation info = mock(QueueInformation.class);
        when(info.getMessageCount()).thenReturn(0);
        when(rabbitAdmin.getQueueInfo(properties.getName())).thenReturn(info);
        SubmissionJob oldest = new SubmissionJob();
        oldest.setCreatedAt(new Date(System.currentTimeMillis() - 120_000));
        when(mongoTemplate.findOne(any(Query.class), eq(SubmissionJob.class))).thenReturn(oldest);

        SubmissionQueueAdmissionService.Admission admission = service.check();

        assertFalse(admission.allowed());
        assertEquals("QUEUE_AGE", admission.reason());
    }
}
