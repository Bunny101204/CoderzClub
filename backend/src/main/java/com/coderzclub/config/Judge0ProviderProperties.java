package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Judge0 provider settings.
 * <p>
 * Judge0 memory_limit is expressed in KB. Runtime {@code cpu_time_limit} /
 * {@code wall_time_limit} are ordinary submission fields. Compilation limits
 * are <em>not</em> client-configurable on Judge0 CE 1.13.1 — the compile
 * isolate always uses server {@code MAX_CPU_TIME_LIMIT} / {@code MAX_WALL_TIME_LIMIT}.
 */
@Component
@ConfigurationProperties(prefix = "judge0.provider")
public class Judge0ProviderProperties {
    public enum AuthenticationMode { RAPID_API, SELF_HOSTED }

    private String baseUrl;
    private AuthenticationMode authenticationMode = AuthenticationMode.SELF_HOSTED;
    private String apiKey;
    private String hostHeader;
    private boolean wait = true;
    private long connectTimeoutSeconds = ExecutionTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private long timeoutSeconds = ExecutionTimeoutPolicy.DEFAULT_HTTP_TIMEOUT_SECONDS;
    private int maxCpuTimeLimitSeconds = ExecutionTimeoutPolicy.DEFAULT_MAX_CPU_TIME_LIMIT_SECONDS;
    /** Mirrors Judge0 MAX_CPU_TIME_LIMIT; payload CPU never exceeds this. */
    private int serverMaxCpuTimeLimitSeconds = ExecutionTimeoutPolicy.DEFAULT_SERVER_MAX_CPU_TIME_LIMIT_SECONDS;
    private int wallTimeLimitSeconds = ExecutionTimeoutPolicy.DEFAULT_WALL_TIME_LIMIT_SECONDS;
    /** Mirrors Judge0 MAX_WALL_TIME_LIMIT; also the compile-isolate wall budget. */
    private int serverMaxWallTimeLimitSeconds = ExecutionTimeoutPolicy.DEFAULT_SERVER_MAX_WALL_TIME_LIMIT_SECONDS;
    private int queueSlackSeconds = ExecutionTimeoutPolicy.DEFAULT_QUEUE_SLACK_SECONDS;
    private int maxConnectRetries = ExecutionTimeoutPolicy.DEFAULT_MAX_CONNECT_RETRIES;
    private String memoryLimitUnit = "KB";
    private int memoryLimitKb = ExecutionTimeoutPolicy.DEFAULT_MEMORY_LIMIT_KB;
    /** Mirrors Judge0 MAX_MEMORY_LIMIT (KB). */
    private int serverMaxMemoryLimitKb = ExecutionTimeoutPolicy.DEFAULT_SERVER_MAX_MEMORY_LIMIT_KB;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public AuthenticationMode getAuthenticationMode() { return authenticationMode; }
    public void setAuthenticationMode(AuthenticationMode authenticationMode) { this.authenticationMode = authenticationMode; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getHostHeader() { return hostHeader; }
    public void setHostHeader(String hostHeader) { this.hostHeader = hostHeader; }
    public boolean isWait() { return wait; }
    public void setWait(boolean wait) { this.wait = wait; }
    public long getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(long connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxCpuTimeLimitSeconds() { return maxCpuTimeLimitSeconds; }
    public void setMaxCpuTimeLimitSeconds(int maxCpuTimeLimitSeconds) { this.maxCpuTimeLimitSeconds = maxCpuTimeLimitSeconds; }
    public int getServerMaxCpuTimeLimitSeconds() { return serverMaxCpuTimeLimitSeconds; }
    public void setServerMaxCpuTimeLimitSeconds(int serverMaxCpuTimeLimitSeconds) {
        this.serverMaxCpuTimeLimitSeconds = serverMaxCpuTimeLimitSeconds;
    }
    public int getWallTimeLimitSeconds() { return wallTimeLimitSeconds; }
    public void setWallTimeLimitSeconds(int wallTimeLimitSeconds) { this.wallTimeLimitSeconds = wallTimeLimitSeconds; }
    public int getServerMaxWallTimeLimitSeconds() { return serverMaxWallTimeLimitSeconds; }
    public void setServerMaxWallTimeLimitSeconds(int serverMaxWallTimeLimitSeconds) {
        this.serverMaxWallTimeLimitSeconds = serverMaxWallTimeLimitSeconds;
    }
    public int getQueueSlackSeconds() { return queueSlackSeconds; }
    public void setQueueSlackSeconds(int queueSlackSeconds) { this.queueSlackSeconds = queueSlackSeconds; }
    public int getMaxConnectRetries() { return maxConnectRetries; }
    public void setMaxConnectRetries(int maxConnectRetries) { this.maxConnectRetries = maxConnectRetries; }
    public String getMemoryLimitUnit() { return memoryLimitUnit; }
    public void setMemoryLimitUnit(String memoryLimitUnit) { this.memoryLimitUnit = memoryLimitUnit; }
    public int getMemoryLimitKb() { return memoryLimitKb; }
    public void setMemoryLimitKb(int memoryLimitKb) { this.memoryLimitKb = memoryLimitKb; }
    public int getServerMaxMemoryLimitKb() { return serverMaxMemoryLimitKb; }
    public void setServerMaxMemoryLimitKb(int serverMaxMemoryLimitKb) {
        this.serverMaxMemoryLimitKb = serverMaxMemoryLimitKb;
    }
}
