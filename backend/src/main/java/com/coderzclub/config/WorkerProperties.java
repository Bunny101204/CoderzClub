package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private long recoveryIntervalSeconds = 30;
    private long pollTimeoutSeconds = 5;

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
}
