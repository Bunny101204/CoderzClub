package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionQueuePropertiesTest {

    @Test
    void prefetchDefaultIsOnePerConsumer() {
        assertEquals(1, new SubmissionQueueProperties().getPrefetch());
    }

    @Test
    void listenerConcurrencyDefaultRemainsTwo() {
        assertEquals(2, new SubmissionQueueProperties().getConcurrency());
        assertEquals(2, new WorkerProperties().getConcurrency());
        assertEquals(2, new WorkerProperties().getMaxGlobalJudge0Concurrency());
    }
}
