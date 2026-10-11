package com.coderzclub.service;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestRunJob;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestRunQueueAdmissionServiceTest {
    @Test
    void disabledRejectsWithoutUsingBroker() {
        ContestRunProperties properties = new ContestRunProperties();
        properties.setEnabled(false);
        ContestRunQueueAdmissionService service = new ContestRunQueueAdmissionService(mock(MongoTemplate.class), properties);
        ContestRunQueueAdmissionService.Admission admission = service.check();
        assertFalse(admission.allowed());
        assertEquals("DISABLED", admission.reason());
    }

    @Test
    void rabbitDepthRejects() {
        ContestRunProperties properties = new ContestRunProperties();
        properties.getQueue().setAdmissionMaxDepth(2);
        MongoTemplate mongo = mock(MongoTemplate.class);
        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueInfo(properties.getQueue().getName()))
            .thenReturn(new QueueInformation(properties.getQueue().getName(), 2, 0));
        when(mongo.findOne(any(), eq(ContestRunJob.class))).thenReturn(null);
        ContestRunQueueAdmissionService service = new ContestRunQueueAdmissionService(mongo, properties);
        ReflectionTestUtils.setField(service, "rabbitAdmin", admin);
        ContestRunQueueAdmissionService.Admission admission = service.check();
        assertFalse(admission.allowed());
        assertEquals("QUEUE_DEPTH", admission.reason());
    }

    @Test
    void emptyQueueAllowed() {
        ContestRunProperties properties = new ContestRunProperties();
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(any(), eq(ContestRunJob.class))).thenReturn(0L);
        when(mongo.findOne(any(), eq(ContestRunJob.class))).thenReturn(null);
        ContestRunQueueAdmissionService service = new ContestRunQueueAdmissionService(mongo, properties);
        assertTrue(service.check().allowed());
    }

    @Test
    void oldQueuedJobRejects() {
        ContestRunProperties properties = new ContestRunProperties();
        properties.getQueue().setAdmissionMaxOldestAgeSeconds(10);
        MongoTemplate mongo = mock(MongoTemplate.class);
        ContestRunJob oldest = new ContestRunJob();
        oldest.setCreatedAt(new Date(System.currentTimeMillis() - 60_000));
        when(mongo.findOne(any(), eq(ContestRunJob.class))).thenReturn(oldest);
        ContestRunQueueAdmissionService service = new ContestRunQueueAdmissionService(mongo, properties);
        ContestRunQueueAdmissionService.Admission admission = service.check();
        assertFalse(admission.allowed());
        assertEquals("QUEUE_AGE", admission.reason());
    }
}
