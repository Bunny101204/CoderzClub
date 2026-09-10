package com.coderzclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Judge0 provider settings. Judge0 memory_limit is expressed in KB. */
@Component
@ConfigurationProperties(prefix = "judge0.provider")
public class Judge0ProviderProperties {
    public enum AuthenticationMode { RAPID_API, SELF_HOSTED }

    private String baseUrl;
    private AuthenticationMode authenticationMode = AuthenticationMode.SELF_HOSTED;
    private String apiKey;
    private String hostHeader;
    private boolean wait = true;
    private long timeoutSeconds = 40;
    private int compileTimeLimitSeconds = 30;
    private String memoryLimitUnit = "KB";
    private int memoryLimitKb = 262144;

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
    public long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getCompileTimeLimitSeconds() { return compileTimeLimitSeconds; }
    public void setCompileTimeLimitSeconds(int compileTimeLimitSeconds) { this.compileTimeLimitSeconds = compileTimeLimitSeconds; }
    public String getMemoryLimitUnit() { return memoryLimitUnit; }
    public void setMemoryLimitUnit(String memoryLimitUnit) { this.memoryLimitUnit = memoryLimitUnit; }
    public int getMemoryLimitKb() { return memoryLimitKb; }
    public void setMemoryLimitKb(int memoryLimitKb) { this.memoryLimitKb = memoryLimitKb; }
}