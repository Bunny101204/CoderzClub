package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(
    classes = SubmissionLimitsConfigBindingTest.TestConfiguration.class,
    properties = {
        "submission.daily=${SUBMISSION_LIMIT_DAILY:100}",
        "submission.per-problem-daily=${SUBMISSION_LIMIT_PER_PROBLEM_DAILY:50}",
        "submission.cooldown-ms=${SUBMISSION_LIMIT_COOLDOWN_MS:2000}",
        "submission.redis-fail-open=${SUBMISSION_LIMIT_REDIS_FAIL_OPEN:false}",
        "submission.time-zone=${SUBMISSION_LIMIT_TIME_ZONE:UTC}"
    }
)
@ContextConfiguration(initializers = SubmissionLimitsConfigBindingTest.EnvironmentInitializer.class)
class SubmissionLimitsConfigBindingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SubmissionLimitsConfig.class)
    static class TestConfiguration {
    }

    @Autowired
    private SubmissionLimitsConfig config;

    @Test
    void environmentOverridesBindThroughSubmissionContract() {
        assertEquals(123, config.getDaily());
        assertEquals(17, config.getPerProblemDaily());
        assertEquals(4500L, config.getCooldownMs());
        assertEquals("Asia/Kolkata", config.getTimeZone());
    }

    @Test
    void redisFailOpenDefaultsClosed() {
        assertFalse(config.isRedisFailOpen());
    }

    static class EnvironmentInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test-environment",
                Map.of(
                    "SUBMISSION_LIMIT_DAILY", "123",
                    "SUBMISSION_LIMIT_PER_PROBLEM_DAILY", "17",
                    "SUBMISSION_LIMIT_COOLDOWN_MS", "4500",
                    "SUBMISSION_LIMIT_TIME_ZONE", "Asia/Kolkata"
                )
            ));
        }
    }

}