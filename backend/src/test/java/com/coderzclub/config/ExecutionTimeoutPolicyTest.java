package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTimeoutPolicyTest {

    @Test
    void defaultOrderingSupportsHarnessSixtySecondCpu() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        WorkerProperties worker = new WorkerProperties();
        ExecutionTimeoutPolicy.validate(provider, worker);

        long envelope = ExecutionTimeoutPolicy.executionEnvelopeSeconds(
            provider.getCompileTimeLimitSeconds(),
            provider.getMaxCpuTimeLimitSeconds(),
            provider.getQueueSlackSeconds());
        assertEquals(105, envelope);
        assertEquals(60, provider.getMaxCpuTimeLimitSeconds());
        assertTrue(provider.getTimeoutSeconds() > envelope);
        assertTrue(worker.getLeaseDurationSeconds() > provider.getTimeoutSeconds());
        assertTrue(worker.getHeartbeatIntervalSeconds() * 2 < worker.getLeaseDurationSeconds());
        assertTrue(provider.isWait());
        assertEquals(60, ExecutionTimeoutPolicy.cpuTimeLimitSeconds(15, 8, 60));
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
