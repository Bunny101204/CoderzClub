package com.coderzclub.service;

import com.coderzclub.dto.SubmissionJobEvent;
import com.coderzclub.model.SubmissionJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionJobEventFanoutTest {
    @Test
    void terminalEventClosesConnectedClient() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SubmissionJobEventService service = new SubmissionJobEventService(
            mock(StringRedisTemplate.class), mock(RedisConnectionFactory.class), objectMapper, "events");
        SseEmitter emitter = service.register("job-1");

        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.COMPLETED);
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(objectMapper.writeValueAsBytes(SubmissionJobEvent.from(job)));

        service.onMessage(message, "events".getBytes());

        java.lang.reflect.Field field = SubmissionJobEventService.class.getDeclaredField("emitters");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ?> clients = (java.util.Map<String, ?>) field.get(service);
        assertTrue(clients.isEmpty());
    }
}
