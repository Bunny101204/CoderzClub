package com.coderzclub.service;

import com.coderzclub.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionLimitServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void cooldownBlocksImmediateResubmission() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(0L);

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate);
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("COOLDOWN"));
    }

    @Test
    void dailyLimitIsEnforced() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate);
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("DAILY"));
    }

    @Test
    void perProblemLimitIsEnforced() {
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(2L);

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate);
        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertFalse(decision.isAllowed());
        assertTrue(decision.getReason().contains("PROBLEM"));
    }

    @Test
    void concurrentRequestsCannotExceedLimits() {
        when(redisTemplate.execute(any(), anyList(), any())).thenAnswer(invocation -> {
            return 3L;
        });

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate);
        SubmissionLimitDecision first = service.tryAcquireSubmissionSlot("user-1", "problem-1");
        SubmissionLimitDecision second = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertTrue(first.isAllowed());
        assertFalse(second.isAllowed());
    }

    @Test
    void redisFailureRespectsFailOpenSetting() {
        doThrow(new RedisSystemException("boom")).when(redisTemplate).execute(any(), anyList(), any());

        SubmissionLimitService service = new SubmissionLimitService(submissionRepository, redisTemplate);
        ReflectionTestUtils.setField(service, "redisFailOpen", true);

        SubmissionLimitDecision decision = service.tryAcquireSubmissionSlot("user-1", "problem-1");

        assertTrue(decision.isAllowed());
        assertTrue(decision.getReason().contains("REDIS"));
    }
}
