package com.coderzclub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ExecutionTimeoutValidator {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTimeoutValidator.class);

    private final Judge0ProviderProperties providerProperties;
    private final WorkerProperties workerProperties;

    public ExecutionTimeoutValidator(Judge0ProviderProperties providerProperties, WorkerProperties workerProperties) {
        this.providerProperties = providerProperties;
        this.workerProperties = workerProperties;
    }

    @PostConstruct
    void validate() {
        ExecutionTimeoutPolicy.validate(providerProperties, workerProperties);
        long envelope = ExecutionTimeoutPolicy.executionEnvelopeSeconds(providerProperties);
        logger.info(
            "execution_timeout_envelope compileWallSeconds={} runCpuSeconds={} runWallSeconds={} "
                + "serverMaxCpuSeconds={} serverMaxWallSeconds={} memoryKb={} serverMaxMemoryKb={} "
                + "slackSeconds={} envelopeSeconds={} httpTimeoutSeconds={} connectTimeoutSeconds={} "
                + "leaseSeconds={} heartbeatSeconds={} wait={} note=compile_uses_judge0_MAX_limits_not_api_field",
            providerProperties.getServerMaxWallTimeLimitSeconds(),
            ExecutionTimeoutPolicy.effectiveMaxCpuSeconds(providerProperties),
            ExecutionTimeoutPolicy.effectiveWallTimeSeconds(providerProperties),
            providerProperties.getServerMaxCpuTimeLimitSeconds(),
            providerProperties.getServerMaxWallTimeLimitSeconds(),
            ExecutionTimeoutPolicy.effectiveMemoryLimitKb(providerProperties),
            providerProperties.getServerMaxMemoryLimitKb(),
            providerProperties.getQueueSlackSeconds(),
            envelope,
            providerProperties.getTimeoutSeconds(),
            providerProperties.getConnectTimeoutSeconds(),
            workerProperties.getLeaseDurationSeconds(),
            workerProperties.getHeartbeatIntervalSeconds(),
            providerProperties.isWait());
    }
}
