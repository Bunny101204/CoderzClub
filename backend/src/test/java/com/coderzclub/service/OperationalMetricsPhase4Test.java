package com.coderzclub.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OperationalMetricsPhase4Test {

    @Test
    void recordsSemaphoreWaitAndLowCardinalityProviderCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);
        Timer.Sample sample = metrics.judge0SemaphoreWait();
        metrics.stopJudge0SemaphoreWait(sample);
        metrics.providerTimeout();
        metrics.provider429();
        metrics.provider5xx();
        metrics.ambiguousExecutionFailure("http_timeout");
        metrics.duplicateSubmissionPrevented();
        metrics.duplicateClaim();
        metrics.heartbeatFailure();
        metrics.providerInflightIncrement();
        assertEquals(1, metrics.providerInflight());
        metrics.providerInflightDecrement();
        assertEquals(0, metrics.providerInflight());
        assertNotNull(registry.find("judge0.semaphore.wait").timer());
        assertEquals(1, registry.find("judge0.provider.timeout").counter().count());
        assertEquals(1, registry.find("judge0.execution.ambiguous").counter().count());
        assertEquals(1, registry.find("submission.materialization.duplicate_prevented").counter().count());
    }
}
