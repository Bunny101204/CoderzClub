package com.coderzclub.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class OperationalMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger activeWorkers = new AtomicInteger();
    private final AtomicInteger providerInflight = new AtomicInteger();
    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("submission.worker.active", activeWorkers);
        registry.gauge("judge0.provider.inflight", providerInflight);
    }
    public void admission(String outcome, String reason) { Counter.builder("submission.admission").tag("outcome", outcome).tag("reason", reason).register(registry).increment(); }
    public void retry() { Counter.builder("submission.job.retry").register(registry).increment(); }
    public void leaseLoss() { Counter.builder("submission.worker.lease.loss").register(registry).increment(); }
    public void verdict(String verdict) { Counter.builder("submission.verdict").tag("verdict", verdict).register(registry).increment(); }
    public void judge0RateLimit(int status) { Counter.builder("judge0.rate_limit").tag("status", String.valueOf(status)).register(registry).increment(); }
    public void judge0HttpError(int status) { Counter.builder("judge0.http.error").tag("status", String.valueOf(status)).register(registry).increment(); }
    public void dependencyError(String dependency) { Counter.builder("dependency.errors").tag("dependency", dependency).register(registry).increment(); }
    public Timer.Sample judge0Timer() { return Timer.start(registry); }
    public void stopJudge0Timer(Timer.Sample sample) { sample.stop(Timer.builder("judge0.latency").publishPercentiles(0.5, 0.95, 0.99).register(registry)); }
    public void executionStrategy(String configured, String used, String fallback) {
        Counter.builder("execution.strategy")
            .tag("configured", configured)
            .tag("used", used)
            .tag("fallback", fallback)
            .register(registry)
            .increment();
    }
    public void workerStarted() { activeWorkers.incrementAndGet(); }
    public void workerStopped() { activeWorkers.decrementAndGet(); }
    public Timer.Sample judge0SemaphoreWait() { return Timer.start(registry); }
    public void stopJudge0SemaphoreWait(Timer.Sample sample) {
        if (sample != null) {
            sample.stop(Timer.builder("judge0.semaphore.wait").publishPercentiles(0.5, 0.95, 0.99).register(registry));
        }
    }
    public void providerInflightIncrement() { providerInflight.incrementAndGet(); }
    public void providerInflightDecrement() { providerInflight.decrementAndGet(); }
    public int providerInflight() { return providerInflight.get(); }
    public void providerTimeout() { Counter.builder("judge0.provider.timeout").register(registry).increment(); }
    public void provider429() { Counter.builder("judge0.provider.429").register(registry).increment(); }
    public void provider5xx() { Counter.builder("judge0.provider.5xx").register(registry).increment(); }
    public void ambiguousExecutionFailure(String reason) {
        Counter.builder("judge0.execution.ambiguous").tag("reason", sanitize(reason)).register(registry).increment();
    }
    public void heartbeatFailure() { Counter.builder("submission.worker.lease.heartbeat.failure").register(registry).increment(); }
    public void duplicateSubmissionPrevented() {
        Counter.builder("submission.materialization.duplicate_prevented").register(registry).increment();
    }
    public void duplicateClaim() { Counter.builder("submission.job.duplicate_claim").register(registry).increment(); }
    private static String sanitize(String reason) {
        if (reason == null || reason.isBlank()) return "unknown";
        String trimmed = reason.length() > 40 ? reason.substring(0, 40) : reason;
        return trimmed.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }
}