package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Judge0ProfileConcurrencyBindingTest {

    @Test
    void workerProfileDefaultMatchesListenerConcurrency() {
        assertEquals(2, bind("worker", Map.of()).getMaxGlobalJudge0Concurrency());
    }

    @Test
    void apiProfileDefaultIsIndependentOfWorkerEnv() {
        WorkerProperties api = bind("api", Map.of("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY", "9"));
        assertEquals(2, api.getMaxGlobalJudge0Concurrency());
    }

    @Test
    void workerEnvironmentOverrideIsHonored() {
        assertEquals(1, bind("worker", Map.of("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY", "1"))
            .getMaxGlobalJudge0Concurrency());
    }

    @Test
    void apiEnvironmentOverrideDoesNotUseWorkerVariable() {
        WorkerProperties api = bind("api", Map.of(
            "JUDGE0_API_MAX_CONCURRENCY", "1",
            "WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY", "9"));
        assertEquals(1, api.getMaxGlobalJudge0Concurrency());
        assertEquals(1, bind("worker", Map.of("WORKER_MAX_GLOBAL_JUDGE0_CONCURRENCY", "1"))
            .getMaxGlobalJudge0Concurrency());
    }

    private static WorkerProperties bind(String profile, Map<String, Object> environmentValues) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        environment.getPropertySources().addFirst(new MapPropertySource("test-env", environmentValues));
        try {
            environment.getPropertySources().addAfter("test-env",
                new ResourcePropertySource("profile", new ClassPathResource("application-" + profile + ".properties")));
            environment.getPropertySources().addLast(
                new ResourcePropertySource("application", new ClassPathResource("application.properties")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Binder.get(environment).bind("worker", WorkerProperties.class).get();
    }
}
