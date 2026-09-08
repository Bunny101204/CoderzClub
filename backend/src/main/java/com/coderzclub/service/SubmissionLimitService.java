package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Service
public class SubmissionLimitService {

    private static final String LUA_SCRIPT = """
        local cooldownKey = KEYS[1]
        local dailyKey = KEYS[2]
        local problemKey = KEYS[3]
        local cooldownUntil = tonumber(ARGV[1])
        local dailyLimit = tonumber(ARGV[2])
        local problemLimit = tonumber(ARGV[3])
        local windowTtlSeconds = tonumber(ARGV[4])
        local now = tonumber(ARGV[5])
        local cooldownMs = tonumber(ARGV[6])
        local cooldownValue = redis.call('GET', cooldownKey)
        if cooldownValue ~= false and tonumber(cooldownValue) > now then
            return 0
        end
        local dailyCount = tonumber(redis.call('GET', dailyKey) or '0')
        if dailyCount >= dailyLimit then
            return 1
        end
        local problemCount = tonumber(redis.call('GET', problemKey) or '0')
        if problemCount >= problemLimit then
            return 2
        end
        local dailyExists = redis.call('EXISTS', dailyKey)
        redis.call('INCR', dailyKey)
        if dailyExists == 0 then
            redis.call('EXPIRE', dailyKey, windowTtlSeconds)
        end
        local problemExists = redis.call('EXISTS', problemKey)
        redis.call('INCR', problemKey)
        if problemExists == 0 then
            redis.call('EXPIRE', problemKey, windowTtlSeconds)
        end
        redis.call('SET', cooldownKey, cooldownUntil, 'PX', cooldownMs)
        return 3
        """;

    private final SubmissionRepository submissionRepository;
    private final StringRedisTemplate redis;
    private final SubmissionLimitsConfig config;
    @Autowired(required = false)
    private OperationalMetrics metrics;

    public SubmissionLimitService() {
        this(null, null, new SubmissionLimitsConfig());
    }

    public SubmissionLimitService(SubmissionRepository submissionRepository, StringRedisTemplate redis) {
        this(submissionRepository, redis, new SubmissionLimitsConfig());
    }

    @Autowired
    public SubmissionLimitService(SubmissionRepository submissionRepository, StringRedisTemplate redis,
                                  SubmissionLimitsConfig config) {
        this.submissionRepository = submissionRepository;
        this.redis = redis;
        this.config = config;
    }

    public SubmissionLimitDecision tryAcquireSubmissionSlot(String userId, String problemId) {
        if (userId == null || userId.isBlank()) {
            return SubmissionLimitDecision.rejected("INVALID_USER");
        }

        try {
            long now = System.currentTimeMillis();
            long cooldownUntil = now + config.getCooldownMs();
            Long result = redis.execute(new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                List.of(buildCooldownKey(userId), buildDailyKey(userId), buildProblemKey(userId, problemId)),
                String.valueOf(cooldownUntil), String.valueOf(config.getDaily()),
                String.valueOf(config.getPerProblemDaily()), String.valueOf(secondsUntilNextMidnight(now)),
                String.valueOf(now), String.valueOf(config.getCooldownMs()));

            if (result == null) return unavailable();
            if (result == 3L) { metric("allowed", "none"); return SubmissionLimitDecision.allowed(); }
            if (result == 0L) { metric("rejected", "COOLDOWN"); return SubmissionLimitDecision.rejected("COOLDOWN"); }
            if (result == 1L) { metric("rejected", "DAILY_LIMIT"); return SubmissionLimitDecision.rejected("DAILY_LIMIT"); }
            metric("rejected", "PROBLEM_LIMIT"); return SubmissionLimitDecision.rejected("PROBLEM_LIMIT");
        } catch (Exception ex) {
            metric("rejected", "REDIS_UNAVAILABLE");
            if (metrics != null) metrics.dependencyError("redis");
            return unavailable();
        }
    }

    private void metric(String outcome, String reason) { if (metrics != null) metrics.admission(outcome, reason); }

    private SubmissionLimitDecision unavailable() {
        return config.isRedisFailOpen()
            ? SubmissionLimitDecision.allowed("REDIS_FALLBACK")
            : SubmissionLimitDecision.rejected("REDIS_UNAVAILABLE");
    }

    @Deprecated
    public void recordSubmissionAttempt(String userId, String problemId) {
        tryAcquireSubmissionSlot(userId, problemId);
    }

    public boolean hasExceededDailyLimit(String userId) {
        return getRemainingDailySubmissions(userId) == 0;
    }

    public boolean hasExceededProblemLimit(String userId, String problemId) {
        return getRemainingProblemSubmissions(userId, problemId) == 0;
    }

    public int getRemainingDailySubmissions(String userId) {
        try {
            return remainingFromRedis(buildDailyKey(userId), config.getDaily());
        } catch (Exception ex) {
            return config.isRedisFailOpen() ? remainingDailyFromMongo(userId) : 0;
        }
    }

    public int getRemainingProblemSubmissions(String userId, String problemId) {
        try {
            return remainingFromRedis(buildProblemKey(userId, problemId), config.getPerProblemDaily());
        } catch (Exception ex) {
            return config.isRedisFailOpen() ? remainingProblemFromMongo(userId, problemId) : 0;
        }
    }

    public boolean canSubmitNow(String userId) {
        return getCooldownSeconds(userId) == 0;
    }

    public long getCooldownSeconds(String userId) {
        if (userId == null || userId.isBlank()) return 0L;
        try {
            String value = redis.opsForValue().get(buildCooldownKey(userId));
            if (value == null) return 0L;
            return Math.max(0L, (Long.parseLong(value) - System.currentTimeMillis()) / 1000L);
        } catch (Exception ex) {
            return config.isRedisFailOpen() ? 0L : Math.max(1L, config.getCooldownMs() / 1000L);
        }
    }

    private int remainingFromRedis(String key, int limit) {
        String value = redis.opsForValue().get(key);
        long count = value == null ? 0L : Long.parseLong(value);
        return Math.max(0, limit - (int) count);
    }

    private int remainingDailyFromMongo(String userId) {
        long count = submissionRepository.countByUserIdAndCreatedAtAfter(userId, startOfToday());
        return Math.max(0, config.getDaily() - (int) count);
    }

    private int remainingProblemFromMongo(String userId, String problemId) {
        long count = submissionRepository.countByUserIdAndProblemIdAndCreatedAtAfter(userId, problemId, startOfToday());
        return Math.max(0, config.getPerProblemDaily() - (int) count);
    }

    private String buildCooldownKey(String userId) {
        return "coderzclub:submission:cooldown:" + userId;
    }

    private String buildDailyKey(String userId) {
        return "coderzclub:submission:daily:" + currentDate() + ":" + userId;
    }

    private String buildProblemKey(String userId, String problemId) {
        return "coderzclub:submission:problem:" + currentDate() + ":" + userId + ":" + problemId;
    }

    private String currentDate() {
        return LocalDate.now(ZoneId.of(config.getTimeZone())).toString().replace("-", "");
    }

    private Date startOfToday() {
        ZoneId zone = ZoneId.of(config.getTimeZone());
        return Date.from(LocalDate.now(zone).atStartOfDay(zone).toInstant());
    }

    private long secondsUntilNextMidnight(long nowMillis) {
        ZoneId zone = ZoneId.of(config.getTimeZone());
        ZonedDateTime nextMidnight = Instant.ofEpochMilli(nowMillis).atZone(zone)
            .toLocalDate().plusDays(1).atStartOfDay(zone);
        return Math.max(1L, (nextMidnight.toInstant().toEpochMilli() - nowMillis + 999L) / 1000L);
    }
}
