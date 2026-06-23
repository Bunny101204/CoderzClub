package com.coderzclub.service;

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
        svc = new SubmissionLimitService();
        try {
            java.lang.reflect.Field fRedis = SubmissionLimitService.class.getDeclaredField("redis");
            fRedis.setAccessible(true);
            fRedis.set(svc, redis);

            java.lang.reflect.Field fRepo = SubmissionLimitService.class.getDeclaredField("submissionRepository");
            fRepo.setAccessible(true);
            fRepo.set(svc, repo);

            java.lang.reflect.Field fDaily = SubmissionLimitService.class.getDeclaredField("dailyLimit");
            fDaily.setAccessible(true);
            fDaily.setInt(svc, 3);

            java.lang.reflect.Field fProblem = SubmissionLimitService.class.getDeclaredField("perProblemLimit");
            fProblem.setAccessible(true);
            fProblem.setInt(svc, 2);

            java.lang.reflect.Field fCooldown = SubmissionLimitService.class.getDeclaredField("cooldownMs");
            fCooldown.setAccessible(true);
            fCooldown.setLong(svc, 2000L);

            java.lang.reflect.Field fFailOpen = SubmissionLimitService.class.getDeclaredField("redisFailOpen");
            fFailOpen.setAccessible(true);
            fFailOpen.setBoolean(svc, false);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testDailyLimitExceeded() {
        String userId = "u1";
        String key = "coderzclub:rate:daily:u1:" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        when(ops.get(key)).thenReturn("3");

        assertTrue(svc.hasExceededDailyLimit(userId));
        assertEquals(0, svc.getRemainingDailySubmissions(userId));
    }

    @Test
    public void testProblemLimitExceeded() {
        String userId = "u1";
        String pid = "p1";
        String key = "coderzclub:rate:problem:u1:p1:" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        when(ops.get(key)).thenReturn("2");

        assertTrue(svc.hasExceededProblemLimit(userId, pid));
        assertEquals(0, svc.getRemainingProblemSubmissions(userId, pid));
    }

    @Test
    public void testCooldownActive() {
        String userId = "u1";
        String lastKey = "coderzclub:rate:last-submit:u1";
        when(redis.getExpire(lastKey, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(1L);

        assertFalse(svc.canSubmitNow(userId));
        assertTrue(svc.getCooldownSeconds(userId) >= 1);
    }
}
