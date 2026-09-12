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
    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("submission.worker.active", activeWorkers);
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
    public void workerStarted() { activeWorkers.incrementAndGet(); }
    public void workerStopped() { activeWorkers.decrementAndGet(); }
}