package com.coderzclub.service;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalMetricsAdmissionStageTest {

    @Test
    void admissionStageTimersUseOnlyBoundedStageTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);

        Timer.Sample sample = metrics.admissionStageTimer();
        metrics.stopAdmissionStageTimer(sample, "user_lookup");
        metrics.stopAdmissionStageTimer(null, "total");
        metrics.stopAdmissionStageTimer(metrics.admissionStageTimer(), "not_a_real_stage");

        Set<String> stages = registry.find("submission.admission.stage").meters().stream()
            .map(meter -> meter.getId().getTag("stage"))
            .collect(Collectors.toSet());
        assertTrue(stages.contains("user_lookup"));
        assertTrue(stages.contains("total"));
        assertTrue(stages.contains("queue_admission"));
        assertEquals("unknown", boundedUnknown(registry, "submission.admission.stage"));
        assertTrue(stages.stream().noneMatch(stage ->
            stage != null && (stage.contains("userId") || stage.contains("jobId") || stage.contains("@"))));
        assertEquals(0, countHighCardinalityTagKeys(registry));
    }

    @Test
    void queueAdmissionStageTimersRecordWithoutChangingCountsUntilStopped() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);
        Timer.Sample rabbit = metrics.queueAdmissionStageTimer();
        Timer.Sample mongo = metrics.queueAdmissionStageTimer();
        Timer.Sample total = metrics.queueAdmissionStageTimer();
        metrics.stopQueueAdmissionStageTimer(rabbit, "rabbit_queue_info");
        metrics.stopQueueAdmissionStageTimer(mongo, "mongo_oldest_job");
        metrics.stopQueueAdmissionStageTimer(total, "total");
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "rabbit_queue_info").timer().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "mongo_oldest_job").timer().count());
        assertEquals(1, registry.find("submission.queue_admission.stage").tag("stage", "total").timer().count());
    }

    private static String boundedUnknown(SimpleMeterRegistry registry, String name) {
        Timer timer = registry.find(name).tag("stage", "unknown").timer();
        return timer == null ? null : timer.getId().getTag("stage");
    }

    private static long countHighCardinalityTagKeys(SimpleMeterRegistry registry) {
        return registry.getMeters().stream()
            .map(Meter::getId)
            .flatMap(id -> id.getTags().stream())
            .filter(tag -> Set.of("username", "userId", "problemId", "jobId", "ip", "requestId").contains(tag.getKey()))
            .count();
    }
}
