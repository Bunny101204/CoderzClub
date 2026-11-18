package com.coderzclub.config;

/**
 * Coherent timeout envelope for wait=true Judge0 execution.
 * <p>
 * Invariant: max legitimate Judge0 execution envelope
 * &lt; provider HTTP request timeout
 * &lt; job lease duration,
 * with heartbeat comfortably below the lease.
 * <p>
 * Judge0 CE 1.13.1 notes (verified against upstream {@code isolate_job.rb} /
 * {@code submissions_controller.rb}):
 * <ul>
 *   <li>Runtime uses submission {@code cpu_time_limit} / {@code wall_time_limit}
 *       / {@code memory_limit} (API-permitted fields).</li>
 *   <li>Compilation ignores any client {@code compile_time_limit} field (not
 *       permitted). Compile isolate always uses {@code Config::MAX_CPU_TIME_LIMIT}
 *       and {@code Config::MAX_WALL_TIME_LIMIT}.</li>
 *   <li>Payload CPU/wall/memory must stay ≤ the configured server maxima.</li>
 * </ul>
 */
public final class ExecutionTimeoutPolicy {

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 20;
    public static final int DEFAULT_HTTP_TIMEOUT_SECONDS = 120;
    /** App-side runtime CPU ceiling; effective payload CPU is min(this, server max). */
    public static final int DEFAULT_MAX_CPU_TIME_LIMIT_SECONDS = 15;
    /** Stock Judge0 1.13.x MAX_CPU_TIME_LIMIT (also used for the compile isolate -t). */
    public static final int DEFAULT_SERVER_MAX_CPU_TIME_LIMIT_SECONDS = 15;
    /** Runtime wall-clock request; must be ≤ MAX_WALL_TIME_LIMIT and ≥ CPU budget. */
    public static final int DEFAULT_WALL_TIME_LIMIT_SECONDS = 20;
    /** Stock Judge0 1.13.x MAX_WALL_TIME_LIMIT (also used for the compile isolate -w). */
    public static final int DEFAULT_SERVER_MAX_WALL_TIME_LIMIT_SECONDS = 20;
    public static final int DEFAULT_QUEUE_SLACK_SECONDS = 15;
    public static final int DEFAULT_LEASE_DURATION_SECONDS = 180;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 45;
    public static final int DEFAULT_MAX_CONNECT_RETRIES = 2;
    public static final int DEFAULT_MEMORY_LIMIT_KB = 262144;
    /** Stock Judge0 1.13.x MAX_MEMORY_LIMIT (KB). */
    public static final int DEFAULT_SERVER_MAX_MEMORY_LIMIT_KB = 512000;

    private ExecutionTimeoutPolicy() {}

    public static int effectiveMaxCpuSeconds(Judge0ProviderProperties provider) {
        int appCap = Math.max(1, provider.getMaxCpuTimeLimitSeconds());
        int serverCap = Math.max(1, provider.getServerMaxCpuTimeLimitSeconds());
        return Math.min(appCap, serverCap);
    }

    public static int effectiveWallTimeSeconds(Judge0ProviderProperties provider) {
        int cpu = effectiveMaxCpuSeconds(provider);
        int requested = Math.max(1, provider.getWallTimeLimitSeconds());
        int serverCap = Math.max(1, provider.getServerMaxWallTimeLimitSeconds());
        // Wall must cover CPU; Judge0 validates wall ∈ [1, MAX_WALL].
        return Math.min(serverCap, Math.max(cpu, requested));
    }

    public static int effectiveMemoryLimitKb(Judge0ProviderProperties provider) {
        int requested = Math.max(1, provider.getMemoryLimitKb());
        int serverCap = Math.max(1, provider.getServerMaxMemoryLimitKb());
        return Math.min(requested, serverCap);
    }

    /**
     * Per Judge0 <em>runtime</em> submission CPU budget.
     * STANDARD_PER_CASE uses {@code testcaseCount=1} (one sandbox per case).
     * FUNCTION_HARNESS_BATCH uses the full case count in one sandbox and is
     * still clamped to the effective server-compatible CPU cap.
     */
    public static int cpuTimeLimitSeconds(int maxExecutionTimeSeconds, int testcaseCount, int maxCpuCap) {
        int cap = Math.max(1, maxCpuCap);
        return Math.min(cap, Math.max(1, maxExecutionTimeSeconds * Math.max(1, testcaseCount)));
    }

    public static int cpuTimeLimitSeconds(int maxExecutionTimeSeconds, int testcaseCount,
                                          Judge0ProviderProperties provider) {
        return cpuTimeLimitSeconds(maxExecutionTimeSeconds, testcaseCount, effectiveMaxCpuSeconds(provider));
    }

    /**
     * Worst-case wait=true wall budget for one provider call:
     * compile isolate (always MAX_WALL) + runtime wall + queue slack.
     */
    public static long executionEnvelopeSeconds(int compileWallSeconds, int runWallSeconds, int queueSlackSeconds) {
        return (long) Math.max(1, compileWallSeconds) + Math.max(1, runWallSeconds) + Math.max(0, queueSlackSeconds);
    }

    public static long executionEnvelopeSeconds(Judge0ProviderProperties provider) {
        return executionEnvelopeSeconds(
            Math.max(1, provider.getServerMaxWallTimeLimitSeconds()),
            effectiveWallTimeSeconds(provider),
            provider.getQueueSlackSeconds());
    }

    public static void validate(Judge0ProviderProperties provider, WorkerProperties worker) {
        if (provider == null || worker == null) {
            throw new IllegalStateException("Timeout policy dependencies are missing");
        }
        if (!provider.isWait()) {
            throw new IllegalStateException(
                "judge0.provider.wait must be true; wait=false / token polling is not supported");
        }
        if (provider.getServerMaxCpuTimeLimitSeconds() < 1) {
            throw new IllegalStateException("judge0.provider.server-max-cpu-time-limit-seconds must be at least 1");
        }
        if (provider.getServerMaxWallTimeLimitSeconds() < 1) {
            throw new IllegalStateException("judge0.provider.server-max-wall-time-limit-seconds must be at least 1");
        }
        if (provider.getServerMaxMemoryLimitKb() < 1) {
            throw new IllegalStateException("judge0.provider.server-max-memory-limit-kb must be at least 1");
        }
        if (!"KB".equalsIgnoreCase(provider.getMemoryLimitUnit())) {
            throw new IllegalStateException("judge0.provider.memory-limit-unit must be KB");
        }
        if (provider.getMaxCpuTimeLimitSeconds() > provider.getServerMaxCpuTimeLimitSeconds()) {
            throw new IllegalStateException(
                "judge0.provider.max-cpu-time-limit-seconds (" + provider.getMaxCpuTimeLimitSeconds()
                    + ") exceeds judge0.provider.server-max-cpu-time-limit-seconds ("
                    + provider.getServerMaxCpuTimeLimitSeconds()
                    + "); lower the app cap or raise Judge0 MAX_CPU_TIME_LIMIT together");
        }
        if (provider.getWallTimeLimitSeconds() > provider.getServerMaxWallTimeLimitSeconds()) {
            throw new IllegalStateException(
                "judge0.provider.wall-time-limit-seconds (" + provider.getWallTimeLimitSeconds()
                    + ") exceeds judge0.provider.server-max-wall-time-limit-seconds ("
                    + provider.getServerMaxWallTimeLimitSeconds()
                    + "); lower the app wall or raise Judge0 MAX_WALL_TIME_LIMIT together");
        }
        if (provider.getWallTimeLimitSeconds() < 1) {
            throw new IllegalStateException("judge0.provider.wall-time-limit-seconds must be at least 1");
        }
        if (effectiveWallTimeSeconds(provider) < effectiveMaxCpuSeconds(provider)) {
            throw new IllegalStateException(
                "effective wall_time_limit must be >= effective cpu_time_limit");
        }
        if (provider.getMemoryLimitKb() > provider.getServerMaxMemoryLimitKb()) {
            throw new IllegalStateException(
                "judge0.provider.memory-limit-kb (" + provider.getMemoryLimitKb()
                    + ") exceeds judge0.provider.server-max-memory-limit-kb ("
                    + provider.getServerMaxMemoryLimitKb() + ")");
        }
        // Judge0 memory_limit minimum is 2048 KB.
        if (effectiveMemoryLimitKb(provider) < 2048) {
            throw new IllegalStateException("effective memory_limit must be at least 2048 KB");
        }

        long envelope = executionEnvelopeSeconds(provider);
        long httpTimeout = provider.getTimeoutSeconds();
        long lease = worker.getLeaseDurationSeconds();
        long heartbeat = worker.getHeartbeatIntervalSeconds();
        if (httpTimeout <= envelope) {
            throw new IllegalStateException(
                "judge0.provider.timeout-seconds (" + httpTimeout
                    + ") must be greater than the execution envelope compileWall+runWall+slack ("
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
