package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "submission.queue")
public class SubmissionQueueProperties {
    private String name = "coderzclub.submissions";
    private String exchange = "coderzclub.submissions.exchange";
    private String routingKey = "submission";
    private String retryRoutingKey = "submission.retry";
    private String deadLetterRoutingKey = "submission.dlq";
    private String dlqName = "coderzclub.submissions.dlq";
    private String retryName = "coderzclub.submissions.retry";
    private boolean durable = true;
    private int prefetch = 10;
    private int concurrency = 2;
    private int maxLength = 10000;
    private long retryDelayMs = 5000L;
    private int maxDepth = 10000;
    private int admissionMaxDepth = 9000;
    private long admissionMaxOldestAgeSeconds = 300;

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
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    public int getAdmissionMaxDepth() { return admissionMaxDepth; }
    public void setAdmissionMaxDepth(int value) { admissionMaxDepth = value; }
    public long getAdmissionMaxOldestAgeSeconds() { return admissionMaxOldestAgeSeconds; }
    public void setAdmissionMaxOldestAgeSeconds(long value) { admissionMaxOldestAgeSeconds = value; }
}