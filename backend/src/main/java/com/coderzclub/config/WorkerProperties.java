package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Submission worker configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {
    private boolean enabled = true;
    private int concurrency = 2;
    private int maxAttempts = 3;
    private long leaseDurationSeconds = 60;
    private long retryDelaySeconds = 30;
    private long retryMaxDelaySeconds = 900;
    private long retryJitterSeconds = 5;
    private long recoveryIntervalSeconds = 30;
    private long pollTimeoutSeconds = 5;
    private int maxTestcaseConcurrency = 2;
    private int maxGlobalJudge0Concurrency = 8;
    private int maxPerLanguageJudge0Concurrency = 2;
    private boolean stopHiddenOnFailure = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public long getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(long retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public long getRetryMaxDelaySeconds() { return retryMaxDelaySeconds; }
    public void setRetryMaxDelaySeconds(long retryMaxDelaySeconds) { this.retryMaxDelaySeconds = retryMaxDelaySeconds; }
    public long getRetryJitterSeconds() { return retryJitterSeconds; }
    public void setRetryJitterSeconds(long retryJitterSeconds) { this.retryJitterSeconds = retryJitterSeconds; }

    public long retryDelayMillis(int attemptNumber) {
        long base = Math.max(0, retryDelaySeconds);
        long maximum = Math.max(base, retryMaxDelaySeconds);
        int exponent = Math.max(0, attemptNumber - 1);
        long exponential = exponent >= 63 || base > maximum / (1L << Math.min(exponent, 62))
            ? maximum : Math.min(maximum, base * (1L << exponent));
        long jitter = Math.max(0, retryJitterSeconds);
        long offset = jitter == 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Math.max(0, Math.min(maximum, exponential + offset)) * 1000L;
    }

    public long getRecoveryIntervalSeconds() {
        return recoveryIntervalSeconds;
    }

    public void setRecoveryIntervalSeconds(long recoveryIntervalSeconds) {
        this.recoveryIntervalSeconds = recoveryIntervalSeconds;
    }

    public long getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(long pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    public int getMaxTestcaseConcurrency() { return maxTestcaseConcurrency; }
    public void setMaxTestcaseConcurrency(int value) { maxTestcaseConcurrency = value; }
    public int getMaxGlobalJudge0Concurrency() { return maxGlobalJudge0Concurrency; }
    public void setMaxGlobalJudge0Concurrency(int value) { maxGlobalJudge0Concurrency = value; }
    public int getMaxPerLanguageJudge0Concurrency() { return maxPerLanguageJudge0Concurrency; }
    public void setMaxPerLanguageJudge0Concurrency(int value) { maxPerLanguageJudge0Concurrency = value; }
    public boolean isStopHiddenOnFailure() { return stopHiddenOnFailure; }
    public void setStopHiddenOnFailure(boolean value) { stopHiddenOnFailure = value; }
}
