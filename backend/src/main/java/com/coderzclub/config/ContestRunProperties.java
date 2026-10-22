package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "contest.run")
public class ContestRunProperties {
    private boolean enabled = true;
    /** Mongo TTL for the ephemeral run document. This is not the queue-age execution cutoff. */
    private int jobTtlMinutes = 45;
    private long leaseDurationSeconds = 180;
    private int maxAttempts = 2;
    @NestedConfigurationProperty
    private final Queue queue = new Queue();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getJobTtlMinutes() {
        return jobTtlMinutes;
    }

    public void setJobTtlMinutes(int jobTtlMinutes) {
        this.jobTtlMinutes = jobTtlMinutes;
    }

    public long getLeaseDurationSeconds() {
        return leaseDurationSeconds;
    }

    public void setLeaseDurationSeconds(long leaseDurationSeconds) {
        this.leaseDurationSeconds = leaseDurationSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Queue getQueue() {
        return queue;
    }

    public static class Queue {
        private String name = "coderzclub.contest-runs";
        private String exchange = "coderzclub.contest-runs.exchange";
        private String routingKey = "contest.run";
        private String retryRoutingKey = "contest.run.retry";
        private String deadLetterRoutingKey = "contest.run.dlq";
        private String dlqName = "coderzclub.contest-runs.dlq";
        private String retryName = "coderzclub.contest-runs.retry";
        private boolean durable = true;
        private int prefetch = 1;
        private int concurrency = 1;
        private int maxLength = 200;
        private int maxDepth = 200;
        private int admissionMaxDepth = 50;
        /**
         * Advisory admission cutoff and the worker's queue-age cutoff, both measured from
         * the original {@code ContestRunJob.createdAt}. Not the Mongo TTL.
         */
        private long admissionMaxOldestAgeSeconds = 90;
        private long retryDelayMs = 5000L;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getRetryRoutingKey() { return retryRoutingKey; }
        public void setRetryRoutingKey(String retryRoutingKey) { this.retryRoutingKey = retryRoutingKey; }
        public String getDeadLetterRoutingKey() { return deadLetterRoutingKey; }
        public void setDeadLetterRoutingKey(String deadLetterRoutingKey) { this.deadLetterRoutingKey = deadLetterRoutingKey; }
        public String getDlqName() { return dlqName; }
        public void setDlqName(String dlqName) { this.dlqName = dlqName; }
        public String getRetryName() { return retryName; }
        public void setRetryName(String retryName) { this.retryName = retryName; }
        public boolean isDurable() { return durable; }
        public void setDurable(boolean durable) { this.durable = durable; }
        public int getPrefetch() { return prefetch; }
        public void setPrefetch(int prefetch) { this.prefetch = prefetch; }
        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
        public int getMaxLength() { return maxLength; }
        public void setMaxLength(int maxLength) { this.maxLength = maxLength; }
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
        public int getAdmissionMaxDepth() { return admissionMaxDepth; }
        public void setAdmissionMaxDepth(int admissionMaxDepth) { this.admissionMaxDepth = admissionMaxDepth; }
        public long getAdmissionMaxOldestAgeSeconds() { return admissionMaxOldestAgeSeconds; }
        public void setAdmissionMaxOldestAgeSeconds(long admissionMaxOldestAgeSeconds) {
            this.admissionMaxOldestAgeSeconds = admissionMaxOldestAgeSeconds;
        }
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
}
