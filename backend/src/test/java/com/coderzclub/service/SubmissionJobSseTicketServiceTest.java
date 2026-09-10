package com.coderzclub.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SubmissionJobSseTicketServiceTest {
    @SuppressWarnings("unchecked")
    @Test
    void ticketIsScopedAndConsumedOnlyOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("job-1|user-1", null);
        SubmissionJobSseTicketService service = new SubmissionJobSseTicketService(redis, 90);

        SubmissionJobSseTicketService.Ticket ticket = service.create("job-1", "user-1");
        Optional<String> first = service.consume(ticket.value(), "job-1");
        Optional<String> replay = service.consume(ticket.value(), "job-1");

        assertEquals(Optional.of("user-1"), first);
        assertTrue(replay.isEmpty());
        verify(values).set(anyString(), eq("job-1|user-1"), eq(Duration.ofSeconds(90)));
    }

    @SuppressWarnings("unchecked")
    @Test
    void ticketCannotBeUsedForAnotherJob() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete(anyString())).thenReturn("job-1|user-1");
        SubmissionJobSseTicketService service = new SubmissionJobSseTicketService(redis, 90);

        SubmissionJobSseTicketService.Ticket ticket = service.create("job-1", "user-1");

        assertTrue(service.consume(ticket.value(), "job-2").isEmpty());
    }
}