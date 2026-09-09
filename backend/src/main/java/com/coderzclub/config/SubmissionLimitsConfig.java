package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for submission security and performance limits
 */
@Component
@ConfigurationProperties(prefix = "submission")
public class SubmissionLimitsConfig {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionLimitsConfig.class);

    private int daily = 100;
    private int perProblemDaily = 50;
    private long cooldownMs = 2000L;
    private boolean redisFailOpen = false;
    private String timeZone = "UTC";

    // Code size validation
    private int maxCodeLength = 100000;
    private int minCodeLength = 1;

    // Output truncation limits (bytes)
    private int maxStdoutLength = 1048576; // 1 MB
    private int maxStderrLength = 262144;  // 256 KB
    private int maxCompileOutputLength = 262144; // 256 KB

    // Testcase limits
    private int maxTestCasesPerProblem = 500;
    private int maxTestCaseInputLength = 1048576; // 1 MB
    private int maxTestCaseOutputLength = 1048576; // 1 MB

    // Execution limits
    private int maxExecutionTimeSeconds = 15;
    private int maxMemoryKb = 262144; // 256 MB

    @PostConstruct
    void validateAndLog() {
        if (daily < 0 || perProblemDaily < 0 || cooldownMs < 0
                || maxCodeLength < 1 || maxStdoutLength < 1 || maxStderrLength < 1
                || maxCompileOutputLength < 1 || maxTestCasesPerProblem < 1
                || maxTestCaseInputLength < 1 || maxTestCaseOutputLength < 1
                || maxExecutionTimeSeconds < 1 || maxMemoryKb < 1) {
            throw new IllegalStateException("Submission limits must be non-negative and size/time limits must be positive");
        }
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new IllegalStateException("Invalid submission.time-zone: " + timeZone, e);
        }
        logger.info("Submission limits configured: daily={}, perProblemDaily={}, cooldownMs={}, "
                + "timeZone={}, redisFailOpen={}, maxCodeLength={}, maxStdoutLength={}, "
                + "maxStderrLength={}, maxCompileOutputLength={}, maxTestCasesPerProblem={}, "
                + "maxTestCaseInputLength={}, maxTestCaseOutputLength={}, maxExecutionTimeSeconds={}, "
                + "maxMemoryKb={}", daily, perProblemDaily, cooldownMs, timeZone, redisFailOpen,
                maxCodeLength, maxStdoutLength, maxStderrLength, maxCompileOutputLength,
                maxTestCasesPerProblem, maxTestCaseInputLength, maxTestCaseOutputLength,
                maxExecutionTimeSeconds, maxMemoryKb);
    }

    public int getDaily() { return daily; }
    public void setDaily(int daily) { this.daily = daily; }
    public int getPerProblemDaily() { return perProblemDaily; }
    public void setPerProblemDaily(int perProblemDaily) { this.perProblemDaily = perProblemDaily; }
    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long cooldownMs) { this.cooldownMs = cooldownMs; }
    public boolean isRedisFailOpen() { return redisFailOpen; }
    public void setRedisFailOpen(boolean redisFailOpen) { this.redisFailOpen = redisFailOpen; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    // Getters
    public int getMaxCodeLength() {
        return maxCodeLength;
    }

    public void setMaxCodeLength(int maxCodeLength) {
        this.maxCodeLength = maxCodeLength;
    }

    public int getMinCodeLength() {
        return minCodeLength;
    }

    public void setMinCodeLength(int minCodeLength) {
        this.minCodeLength = minCodeLength;
    }

    public int getMaxStdoutLength() {
        return maxStdoutLength;
    }

    public void setMaxStdoutLength(int maxStdoutLength) {
        this.maxStdoutLength = maxStdoutLength;
    }

    public int getMaxStderrLength() {
        return maxStderrLength;
    }

    public void setMaxStderrLength(int maxStderrLength) {
        this.maxStderrLength = maxStderrLength;
    }

    public int getMaxCompileOutputLength() {
        return maxCompileOutputLength;
    }

    public void setMaxCompileOutputLength(int maxCompileOutputLength) {
        this.maxCompileOutputLength = maxCompileOutputLength;
    }

    public int getMaxTestCasesPerProblem() {
        return maxTestCasesPerProblem;
    }

    public void setMaxTestCasesPerProblem(int maxTestCasesPerProblem) {
        this.maxTestCasesPerProblem = maxTestCasesPerProblem;
    }

    public int getMaxTestCaseInputLength() {
        return maxTestCaseInputLength;
    }

    public void setMaxTestCaseInputLength(int maxTestCaseInputLength) {
        this.maxTestCaseInputLength = maxTestCaseInputLength;
    }

    public int getMaxTestCaseOutputLength() {
        return maxTestCaseOutputLength;
    }

    public void setMaxTestCaseOutputLength(int maxTestCaseOutputLength) {
        this.maxTestCaseOutputLength = maxTestCaseOutputLength;
    }

    public int getMaxExecutionTimeSeconds() {
        return maxExecutionTimeSeconds;
    }

    public void setMaxExecutionTimeSeconds(int maxExecutionTimeSeconds) {
        this.maxExecutionTimeSeconds = maxExecutionTimeSeconds;
    }

    public int getMaxMemoryKb() {
        return maxMemoryKb;
    }

    public void setMaxMemoryKb(int maxMemoryKb) {
        this.maxMemoryKb = maxMemoryKb;
    }
}
