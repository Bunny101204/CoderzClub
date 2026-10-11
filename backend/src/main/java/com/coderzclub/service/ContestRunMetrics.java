package com.coderzclub.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class ContestRunMetrics {
    private static final Set<String> REASONS = Set.of(
        "none",
        "VALIDATION",
        "NOT_FOUND",
        "NOT_LIVE",
        "ENDED",
        "NO_PUBLIC_TESTS",
        "QUEUE_DEPTH",
        "QUEUE_AGE",
        "QUEUE_UNAVAILABLE",
        "DISABLED",
        "COOLDOWN",
        "DAILY_LIMIT",
        "PROBLEM_LIMIT",
        "REDIS_UNAVAILABLE",
        "PUBLISH_FAILED",
        "INCOMPATIBLE",
        "SNAPSHOT_MISSING",
        "LEASE_EXPIRED",
        "EXECUTION_FAILED"
    );

    private final MeterRegistry registry;

    public ContestRunMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void admitted() {
        Counter.builder("contest.run.admitted").register(registry).increment();
    }

    public void rejected(String reason) {
        Counter.builder("contest.run.rejected")
            .tag("reason", bound(reason))
            .register(registry)
            .increment();
    }

    public void started() {
        Counter.builder("contest.run.started").register(registry).increment();
    }

    public void completed() {
        Counter.builder("contest.run.completed").register(registry).increment();
    }

    public void failed(String reason) {
        Counter.builder("contest.run.failed")
            .tag("reason", bound(reason))
            .register(registry)
            .increment();
    }

    private static String bound(String reason) {
        if (reason != null && REASONS.contains(reason)) {
            return reason;
        }
        return "unknown";
    }
}
