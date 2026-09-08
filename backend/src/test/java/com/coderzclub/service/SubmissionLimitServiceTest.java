package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisSystemException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class SubmissionLimitServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void cooldownBlocksImmediateResubmission() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);

        SubmissionLimitService service = serviceWithDefaults();
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("COOLDOWN"));
    }

    @Test
    void dailyLimitIsEnforced() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);

        SubmissionLimitService service = serviceWithDefaults();
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("DAILY"));
    }

    @Test
    void perProblemLimitIsEnforced() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(2L);

        SubmissionLimitService service = serviceWithDefaults();
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("PROBLEM"));
    }

    @Test
    void concurrentRequestsCannotExceedLimits() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(3L, 1L);

        SubmissionLimitService service = serviceWithDefaults();
        SubmissionLimitDecision first = service.tryAcquireSubmissionSlot("user-1", "problem-1");
        SubmissionLimitDecision second = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertTrue(first.isAllowed());
        assertFalse(second.isAllowed());
    }

    @Test
    void configuredValuesUseCalendarDayKeysAndNextMidnightTtl() {
        SubmissionLimitsConfig config = new SubmissionLimitsConfig();
        config.setDaily(7);
        config.setPerProblemDaily(4);
        config.setCooldownMs(3500L);
        config.setTimeZone("UTC");
        when(redisTemplate.execute(any(), anyList(), any(Object[].class))).thenReturn(3L);

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate, config);
        assertTrue(service.tryAcquireSubmissionSlot("user-1", "problem-1").isAllowed());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(), keys.capture(), args.capture());

        assertTrue(keys.getValue().get(1).matches("coderzclub:submission:daily:\\d{8}:user-1"));
        assertTrue(keys.getValue().get(2).matches("coderzclub:submission:problem:\\d{8}:user-1:problem-1"));
        assertEquals("7", args.getValue()[1]);
        assertEquals("4", args.getValue()[2]);
        assertEquals("3500", args.getValue()[5]);
        long ttl = Long.parseLong((String) args.getValue()[3]);
        assertTrue(ttl > 0 && ttl <= 86400);
    }

    @Test
    void redisFailureRespectsFailOpenSetting() {
        doThrow(new RedisSystemException("boom", new RuntimeException("boom"))).when(redisTemplate).execute(any(), anyList(), any(Object[].class));

        SubmissionLimitsConfig config = new SubmissionLimitsConfig();
        config.setRedisFailOpen(true);
        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate, config);

        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertTrue(decision.isAllowed());
        assertTrue(decision.getReason().contains("REDIS"));
    }

    private SubmissionLimitService serviceWithDefaults() {
        return new SubmissionLimitService(submissionRepository, redisTemplate, new SubmissionLimitsConfig());
    }
}
