package com.coderzclub.service;

import com.coderzclub.repository.SubmissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Redis-backed submission limit service.
*/

/**
 * Service to handle submission limits and rate limiting.
 *
 * This implementation uses Redis-backed atomic counters and TTLs so that
 * concurrent submissions cannot bypass rate limiting.
 */
@Service
public class SubmissionLimitService {

    private static final int DEFAULT_DAILY_SUBMISSION_LIMIT = 100;
    private static final int DEFAULT_PROBLEM_SUBMISSION_LIMIT = 50;
    private static final long MIN_SUBMISSION_INTERVAL_MS = 2000L;

    private SubmissionRepository submissionRepository;
    private StringRedisTemplate redis;
    private int dailyLimit = DEFAULT_DAILY_SUBMISSION_LIMIT;
    private int perProblemLimit = DEFAULT_PROBLEM_SUBMISSION_LIMIT;
    private long cooldownMs = MIN_SUBMISSION_INTERVAL_MS;

    @Value("${app.redis.fail-open:false}")
    private boolean redisFailOpen;

    public SubmissionLimitService() {
        // Default constructor required for some test setups and proxy creation.
    }

    @Autowired
    public SubmissionLimitService(SubmissionRepository submissionRepository, StringRedisTemplate redis) {
        this.submissionRepository = submissionRepository;
        this.redis = redis;
    }

    public SubmissionLimitDecision tryAcquireSubmissionSlot(String userId, String problemId) {
        if (userId == null || userId.isBlank()) {
            return SubmissionLimitDecision.rejected("INVALID_USER");
        }

        try {
            String cooldownKey = buildCooldownKey(userId);
            String dailyKey = buildDailyKey(userId);
            String problemKey = buildProblemKey(userId, problemId);
            long now = System.currentTimeMillis();
            long cooldownUntil = now + cooldownMs;
            long dailyWindowSeconds = Duration.ofDays(1).getSeconds();
            long problemWindowSeconds = Duration.ofDays(1).getSeconds();

            String luaScript = """
                local cooldownKey = KEYS[1]
                local dailyKey = KEYS[2]
                local problemKey = KEYS[3]
                local cooldownUntil = tonumber(ARGV[1])
                local dailyLimit = tonumber(ARGV[2])
                local problemLimit = tonumber(ARGV[3])
                local dailyWindowSeconds = tonumber(ARGV[4])
                local problemWindowSeconds = tonumber(ARGV[5])
                local now = tonumber(ARGV[6])
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
                redis.call('INCR', dailyKey)
                redis.call('EXPIRE', dailyKey, dailyWindowSeconds)
                redis.call('INCR', problemKey)
                redis.call('EXPIRE', problemKey, problemWindowSeconds)
                redis.call('SET', cooldownKey, cooldownUntil, 'PX', 2000)
                return 3
                """;

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
            Long result = redis.execute(
                script,
                List.of(cooldownKey, dailyKey, problemKey),
                String.valueOf(cooldownUntil),
                String.valueOf(dailyLimit),
                String.valueOf(perProblemLimit),
                String.valueOf(dailyWindowSeconds),
                String.valueOf(problemWindowSeconds),
                String.valueOf(now)
            );

            if (result == null) {
                return SubmissionLimitDecision.rejected("REDIS_UNAVAILABLE");
            }

            if (result == 3L) {
                return SubmissionLimitDecision.allowed();
            }
            if (result == 0L) {
                return SubmissionLimitDecision.rejected("COOLDOWN");
            }
            if (result == 1L) {
                return SubmissionLimitDecision.rejected("DAILY_LIMIT");
            }
            return SubmissionLimitDecision.rejected("PROBLEM_LIMIT");
        } catch (Exception ex) {
            if (redisFailOpen) {
                return SubmissionLimitDecision.allowed("REDIS_FALLBACK");
            }
            return SubmissionLimitDecision.rejected("REDIS_UNAVAILABLE");
        }
    }

    public void recordSubmissionAttempt(String userId, String problemId) {
        tryAcquireSubmissionSlot(userId, problemId);
    }

    public boolean hasExceededDailyLimit(String userId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.countByUserIdAndCreatedAtAfter(userId, today);
        return count >= DEFAULT_DAILY_SUBMISSION_LIMIT;
    }

    public boolean hasExceededProblemLimit(String userId, String problemId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.countByUserIdAndProblemIdAndCreatedAtAfter(userId, problemId, today);
        return count >= perProblemLimit;
    }

    public int getRemainingDailySubmissions(String userId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.countByUserIdAndCreatedAtAfter(userId, today);
        return Math.max(0, dailyLimit - (int) count);
    }

    public int getRemainingProblemSubmissions(String userId, String problemId) {
        Date today = getStartOfDay(new Date());
        long count = submissionRepository.countByUserIdAndProblemIdAndCreatedAtAfter(userId, problemId, today);
        return Math.max(0, perProblemLimit - (int) count);
    }

    public boolean canSubmitNow(String userId) {
        return getCooldownSeconds(userId) == 0;
    }

    public long getCooldownSeconds(String userId) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }

        String cooldownKey = buildCooldownKey(userId);
        try {
            String value = redis.opsForValue().get(cooldownKey);
            if (value == null) {
                return 0L;
            }
            long expiresAt = Long.parseLong(value);
            long remaining = expiresAt - System.currentTimeMillis();
            return Math.max(0L, remaining / 1000L);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String buildCooldownKey(String userId) {
        return "submission:cooldown:" + userId;
    }

    private String buildDailyKey(String userId) {
        return "submission:daily:" + userId;
    }

    private String buildProblemKey(String userId, String problemId) {
        return "submission:problem:" + userId + ":" + problemId;
    }

    private Date getStartOfDay(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();

    }
}

    /*public int getRemainingDailySubmissions(String userId) {
        try {
            String dKey = dailyKey(userId);
            String val = redis.opsForValue().get(dKey);
            int count = val != null ? Integer.parseInt(val) : 0;
            return Math.max(0, dailyLimit - count);
        } catch (Exception e) {
            if (redisFailOpen1) return fallbackGetRemainingDaily(userId);
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public int getRemainingProblemSubmissions(String userId, String problemId) {
        try {
            String pKey = problemKey(userId, problemId);
            String val = redis.opsForValue().get(pKey);
            int count = val != null ? Integer.parseInt(val) : 0;
            return Math.max(0, perProblemLimit - count);
        } catch (Exception e) {
            if (redisFailOpen1) return fallbackGetRemainingProblem(userId, problemId);
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    // Backups using MongoDB counts (only used when redisFailOpen=true)
    private boolean fallbackHasExceededDailyLimit(String userId) {
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now()))
            .count();
        return count >= dailyLimit;
    }

    private boolean fallbackHasExceededProblemLimit(String userId, String problemId) {
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> problemId.equals(s.getProblemId()) && s.getCreatedAt() != null && s.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now()))
            .count();
        return count >= perProblemLimit;
    }

    private int fallbackGetRemainingDaily(String userId) {
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now()))
            .count();
        return Math.max(0, dailyLimit - (int) count);
    }

    private int fallbackGetRemainingProblem(String userId, String problemId) {
        long count = submissionRepository.findByUserId(userId)
            .stream()
            .filter(s -> problemId.equals(s.getProblemId()) && s.getCreatedAt() != null && s.getCreatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isEqual(LocalDate.now()))
            .count();
        return Math.max(0, perProblemLimit - (int) count);
    }

    private long secondsUntilNextMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMid = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, nextMid).getSeconds();
    }

}*/


