package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SubmissionLimitServiceRedisTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> ops;

    @Mock
    private SubmissionRepository repo;

    private SubmissionLimitService svc;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(redis.opsForValue()).thenReturn(ops);
        SubmissionLimitsConfig config = new SubmissionLimitsConfig();
        config.setDaily(3);
        config.setPerProblemDaily(2);
        config.setCooldownMs(2000L);
        config.setRedisFailOpen(false);
        config.setTimeZone("UTC");
        svc = new SubmissionLimitService(repo, redis, config);
    }

    @Test
    public void testDailyLimitExceeded() {
        String userId = "u1";
        String key = "coderzclub:submission:daily:" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ":u1";
        when(ops.get(key)).thenReturn("3");

        assertTrue(svc.hasExceededDailyLimit(userId));
        assertEquals(0, svc.getRemainingDailySubmissions(userId));
    }

    @Test
    public void testProblemLimitExceeded() {
        String userId = "u1";
        String pid = "p1";
        String key = "coderzclub:submission:problem:" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ":u1:p1";
        when(ops.get(key)).thenReturn("2");

        assertTrue(svc.hasExceededProblemLimit(userId, pid));
        assertEquals(0, svc.getRemainingProblemSubmissions(userId, pid));
    }

    @Test
    public void testCooldownActive() {
        String userId = "u1";
        String lastKey = "coderzclub:submission:cooldown:u1";
        when(ops.get(lastKey)).thenReturn(String.valueOf(System.currentTimeMillis() + 2000L));

        assertFalse(svc.canSubmitNow(userId));
        assertTrue(svc.getCooldownSeconds(userId) >= 1);
    }
}
