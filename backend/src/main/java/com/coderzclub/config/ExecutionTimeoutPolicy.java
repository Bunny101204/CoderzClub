package com.coderzclub.config;

/**
 * Coherent timeout envelope for wait=true Judge0 execution.
 * <p>
 * Invariant: max legitimate Judge0 execution envelope
 * &lt; provider HTTP request timeout
 * &lt; job lease duration,
 * with heartbeat comfortably below the lease.
 */
public final class ExecutionTimeoutPolicy {

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 20;
    public static final int DEFAULT_HTTP_TIMEOUT_SECONDS = 120;
    public static final int DEFAULT_COMPILE_TIME_LIMIT_SECONDS = 30;
    public static final int DEFAULT_MAX_CPU_TIME_LIMIT_SECONDS = 60;
    public static final int DEFAULT_QUEUE_SLACK_SECONDS = 15;
    public static final int DEFAULT_LEASE_DURATION_SECONDS = 180;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 45;
    public static final int DEFAULT_MAX_CONNECT_RETRIES = 2;

    private ExecutionTimeoutPolicy() {}

    public static int cpuTimeLimitSeconds(int maxExecutionTimeSeconds, int testcaseCount, int maxCpuCap) {
        int cap = Math.max(1, maxCpuCap);
        return Math.min(cap, Math.max(1, maxExecutionTimeSeconds * Math.max(1, testcaseCount)));
    }

    public static long executionEnvelopeSeconds(int compileTimeLimitSeconds, int maxCpuCap, int queueSlackSeconds) {
        return (long) Math.max(1, compileTimeLimitSeconds) + Math.max(1, maxCpuCap) + Math.max(0, queueSlackSeconds);
    }

    public static void validate(Judge0ProviderProperties provider, WorkerProperties worker) {
        if (provider == null || worker == null) {
            throw new IllegalStateException("Timeout policy dependencies are missing");
        }
        if (!provider.isWait()) {
            throw new IllegalStateException(
                "judge0.provider.wait must be true; wait=false / token polling is not supported");
        }
        long envelope = executionEnvelopeSeconds(
            provider.getCompileTimeLimitSeconds(),
            provider.getMaxCpuTimeLimitSeconds(),
            provider.getQueueSlackSeconds());
        long httpTimeout = provider.getTimeoutSeconds();
        long lease = worker.getLeaseDurationSeconds();
        long heartbeat = worker.getHeartbeatIntervalSeconds();
        if (httpTimeout <= envelope) {
            throw new IllegalStateException(
                "judge0.provider.timeout-seconds (" + httpTimeout
                    + ") must be greater than the execution envelope compile+maxCpu+slack ("
                    + envelope + ")");
        }
        if (lease <= httpTimeout) {
            throw new IllegalStateException(
                "worker.lease-duration-seconds (" + lease
                    + ") must be greater than judge0.provider.timeout-seconds (" + httpTimeout + ")");
        }
        if (heartbeat < 1) {
            throw new IllegalStateException("worker.heartbeat-interval-seconds must be at least 1");
        }
        if (heartbeat * 2 >= lease) {
            throw new IllegalStateException(
                "worker.heartbeat-interval-seconds (" + heartbeat
                    + ") must be comfortably below worker.lease-duration-seconds (" + lease
                    + "); require heartbeat * 2 < lease");
        }
        if (provider.getConnectTimeoutSeconds() < 1) {
            throw new IllegalStateException("judge0.provider.connect-timeout-seconds must be at least 1");
        }
    }
}
