package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTimeoutPolicyTest {

    @Test
    void defaultOrderingMatchesStockJudge0Limits() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        WorkerProperties worker = new WorkerProperties();
        ExecutionTimeoutPolicy.validate(provider, worker);

        // compileWall(20) + runWall(20) + slack(15) = 55
        long envelope = ExecutionTimeoutPolicy.executionEnvelopeSeconds(provider);
        assertEquals(55, envelope);
        assertEquals(15, provider.getMaxCpuTimeLimitSeconds());
        assertEquals(15, provider.getServerMaxCpuTimeLimitSeconds());
        assertEquals(20, provider.getWallTimeLimitSeconds());
        assertEquals(20, provider.getServerMaxWallTimeLimitSeconds());
        assertEquals(15, ExecutionTimeoutPolicy.effectiveMaxCpuSeconds(provider));
        assertEquals(20, ExecutionTimeoutPolicy.effectiveWallTimeSeconds(provider));
        assertTrue(provider.getTimeoutSeconds() > envelope);
        assertTrue(worker.getLeaseDurationSeconds() > provider.getTimeoutSeconds());
        assertTrue(worker.getHeartbeatIntervalSeconds() * 2 < worker.getLeaseDurationSeconds());
        assertTrue(provider.isWait());
        assertEquals(15, ExecutionTimeoutPolicy.cpuTimeLimitSeconds(15, 1, provider));
        assertEquals(15, ExecutionTimeoutPolicy.cpuTimeLimitSeconds(15, 8, provider));
    }

    @Test
    void appCpuAboveServerMaxIsRejected() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setMaxCpuTimeLimitSeconds(60);
        provider.setServerMaxCpuTimeLimitSeconds(15);
        assertThrows(IllegalStateException.class,
            () -> ExecutionTimeoutPolicy.validate(provider, new WorkerProperties()));
    }

    @Test
    void wallAboveServerMaxIsRejected() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setWallTimeLimitSeconds(30);
        provider.setServerMaxWallTimeLimitSeconds(20);
        assertThrows(IllegalStateException.class,
            () -> ExecutionTimeoutPolicy.validate(provider, new WorkerProperties()));
    }

    @Test
    void memoryAboveServerMaxIsRejected() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setMemoryLimitKb(600000);
        provider.setServerMaxMemoryLimitKb(512000);
        assertThrows(IllegalStateException.class,
            () -> ExecutionTimeoutPolicy.validate(provider, new WorkerProperties()));
    }

    @Test
    void invalidHttpTimeoutIsRejected() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setTimeoutSeconds(40);
        WorkerProperties worker = new WorkerProperties();
        assertThrows(IllegalStateException.class, () -> ExecutionTimeoutPolicy.validate(provider, worker));
    }

    @Test
    void waitFalseIsRejected() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setWait(false);
        assertThrows(IllegalStateException.class, () -> ExecutionTimeoutPolicy.validate(provider, new WorkerProperties()));
    }

    @Test
    void leaseMustExceedHttpTimeout() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        WorkerProperties worker = new WorkerProperties();
        worker.setLeaseDurationSeconds(provider.getTimeoutSeconds());
        assertThrows(IllegalStateException.class, () -> ExecutionTimeoutPolicy.validate(provider, worker));
    }
}
