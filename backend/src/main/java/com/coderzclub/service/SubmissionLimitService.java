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

/**
 * Redis-backed submission limit service.
 */
@Service
public class SubmissionLimitService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private SubmissionRepository submissionRepository; // fallback

    @Value("${submission.limit.daily:100}")
    private int dailyLimit;

    @Value("${submission.limit.perProblemDaily:50}")
    private int perProblemLimit;

    @Value("${submission.limit.cooldownMs:2000}")
    private long cooldownMs;

    @Value("${submission.limit.redisFailOpen:false}")
    private boolean redisFailOpen;

    private String dailyKey(String userId) {
        return "coderzclub:rate:daily:" + userId + ":" + LocalDate.now().format(DATE_FMT);
    }

    private String problemKey(String userId, String problemId) {
        return "coderzclub:rate:problem:" + userId + ":" + problemId + ":" + LocalDate.now().format(DATE_FMT);
    }

    private String lastSubmitKey(String userId) {
        return "coderzclub:rate:last-submit:" + userId;
    }

    /**
     * Atomically record a submission attempt: increments counters and sets TTLs.
     */
    public void recordSubmissionAttempt(String userId, String problemId) {
        try {
            String dKey = dailyKey(userId);
            Long d = redis.opsForValue().increment(dKey);
            if (d != null && d == 1L) {
                // set expiry to next midnight + 1 hour (so 24-25 hours)
                long seconds = secondsUntilNextMidnight() + 3600;
                redis.expire(dKey, seconds, TimeUnit.SECONDS);
            }

            String pKey = problemKey(userId, problemId);
            Long p = redis.opsForValue().increment(pKey);
            if (p != null && p == 1L) {
                long seconds = secondsUntilNextMidnight() + 3600;
                redis.expire(pKey, seconds, TimeUnit.SECONDS);
            }

            String lastKey = lastSubmitKey(userId);
            redis.opsForValue().set(lastKey, String.valueOf(System.currentTimeMillis()), cooldownMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            if (redisFailOpen) {
                // best-effort fallback to MongoDB counts
                // no-op here: repository operations are used in other methods when needed
                return;
            }
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public boolean canSubmit(String userId, String problemId) {
        // Combined check: cooldown + limits
        if (!canSubmitNow(userId)) return false;
        if (hasExceededDailyLimit(userId)) return false;
        if (hasExceededProblemLimit(userId, problemId)) return false;
        return true;
    }

    public boolean canSubmitNow(String userId) {
        try {
            String lastKey = lastSubmitKey(userId);
            Long ttl = redis.getExpire(lastKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) return false;
            return true;
        } catch (Exception e) {
            if (redisFailOpen) return true;
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public long getCooldownSeconds(String userId) {
        try {
            String lastKey = lastSubmitKey(userId);
            Long ttl = redis.getExpire(lastKey, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            if (redisFailOpen) return 0;
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public boolean hasExceededDailyLimit(String userId) {
        try {
            String dKey = dailyKey(userId);
            String val = redis.opsForValue().get(dKey);
            int count = val != null ? Integer.parseInt(val) : 0;
            return count >= dailyLimit;
        } catch (Exception e) {
            if (redisFailOpen) return fallbackHasExceededDailyLimit(userId);
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public boolean hasExceededProblemLimit(String userId, String problemId) {
        try {
            String pKey = problemKey(userId, problemId);
            String val = redis.opsForValue().get(pKey);
            int count = val != null ? Integer.parseInt(val) : 0;
            return count >= perProblemLimit;
        } catch (Exception e) {
            if (redisFailOpen) return fallbackHasExceededProblemLimit(userId, problemId);
            throw new RuntimeException("Redis unavailable", e);
        }
    }

    public int getRemainingDailySubmissions(String userId) {
        try {
            String dKey = dailyKey(userId);
            String val = redis.opsForValue().get(dKey);
            int count = val != null ? Integer.parseInt(val) : 0;
            return Math.max(0, dailyLimit - count);
        } catch (Exception e) {
            if (redisFailOpen) return fallbackGetRemainingDaily(userId);
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
            if (redisFailOpen) return fallbackGetRemainingProblem(userId, problemId);
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

}


