package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Declares the contest-run queue arguments. A live broker's x-max-length behavior is
 * checked by the GCP contest-run load test; this test does not require RabbitMQ.
 */
class RabbitContestRunQueueConfigTest {
    @Test
    void contestRunQueueRejectsPublishAtConfiguredCapacity() {
        ContestRunProperties properties = new ContestRunProperties();
        RabbitContestRunQueueConfig config = new RabbitContestRunQueueConfig();
        Queue queue = config.contestRunQueue(properties);
        assertEquals(properties.getQueue().getMaxLength(), queue.getArguments().get("x-max-length"));
        assertEquals("reject-publish", queue.getArguments().get("x-overflow"));

        properties.getQueue().setMaxLength(50);
        Queue configured = config.contestRunQueue(properties);
        assertEquals(50, configured.getArguments().get("x-max-length"));
        assertEquals("reject-publish", configured.getArguments().get("x-overflow"));
    }

    @Test
    void retryAndDeadLetterQueuesDoNotUseRejectPublishCapacity() {
        ContestRunProperties properties = new ContestRunProperties();
        RabbitContestRunQueueConfig config = new RabbitContestRunQueueConfig();
        Queue retry = config.contestRunRetryQueue(properties);
        Queue deadLetter = config.contestRunDeadLetterQueue(properties);
        assertFalse(retry.getArguments().containsKey("x-max-length"));
        assertFalse(retry.getArguments().containsKey("x-overflow"));
        assertFalse(deadLetter.getArguments().containsKey("x-max-length"));
        assertFalse(deadLetter.getArguments().containsKey("x-overflow"));
    }
}
