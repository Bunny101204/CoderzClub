package com.coderzclub.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Low-cardinality draft counters. Do not tag user, contest, problem, or language.
 */
@Service
public class DraftMetrics {
    private final MeterRegistry registry;

    public DraftMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void load() {
        counter("draft.load");
    }

    public void sync() {
        counter("draft.sync");
    }

    public void conflict() {
        counter("draft.conflict");
    }

    public void syncFailure() {
        counter("draft.sync_failure");
    }

    private void counter(String name) {
        Counter.builder(name).register(registry).increment();
    }
}
