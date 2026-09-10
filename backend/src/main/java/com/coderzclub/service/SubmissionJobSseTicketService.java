package com.coderzclub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubmissionJobSseTicketService {
    private static final String KEY_PREFIX = "submission:sse-ticket:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SubmissionJobSseTicketService(StringRedisTemplate redis,
                                         @Value("${submission.events.ticket-ttl-seconds:90}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(Math.max(60, Math.min(120, ttlSeconds)));
    }

    public Ticket create(String jobId, String userId) {
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue().set(key(ticket), jobId + "|" + userId, ttl);
        return new Ticket(ticket, ttl.toSeconds());
    }

    public Optional<String> consume(String ticket, String jobId) {
        if (ticket == null || ticket.isBlank() || jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(key(ticket));
        if (value == null) return Optional.empty();
        String expectedPrefix = jobId + "|";
        return value.startsWith(expectedPrefix) ? Optional.of(value.substring(expectedPrefix.length())) : Optional.empty();
    }

    private String key(String ticket) {
        return KEY_PREFIX + ticket;
    }

    public record Ticket(String value, long expiresInSeconds) {}
}