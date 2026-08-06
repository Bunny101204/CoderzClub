package com.coderzclub.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class InMemorySubmissionQueueConsumer implements SubmissionQueueConsumer {

    private final InMemorySubmissionQueuePublisher publisher;

    @Autowired
    public InMemorySubmissionQueueConsumer(InMemorySubmissionQueuePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public String consumeJob(long timeoutSeconds) {
        try {
            BlockingQueue<String> queue = publisher.getQueue();
            return queue.poll(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
