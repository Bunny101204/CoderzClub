package com.coderzclub.queue;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySubmissionQueueTopologyTest {

    @Test
    void localWorkerTopologyDeliversJobToInMemoryConsumer() throws Exception {
        InMemorySubmissionQueuePublisher publisher = new InMemorySubmissionQueuePublisher();
        InMemorySubmissionQueueConsumer consumer = new InMemorySubmissionQueueConsumer(publisher);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> seen = new AtomicReference<>();
        consumer.start(jobId -> {
            seen.set(jobId);
            latch.countDown();
            return SubmissionQueueConsumer.MessageDisposition.ACK;
        }, 1);
        publisher.publishJob("job-local");
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("job-local", seen.get());
        consumer.stop();
    }
}
