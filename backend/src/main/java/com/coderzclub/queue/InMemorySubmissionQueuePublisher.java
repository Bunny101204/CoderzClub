package com.coderzclub.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory submission queue publisher for local/dev environments.
 */
@Component
public class InMemorySubmissionQueuePublisher implements SubmissionQueuePublisher {

    private static final Logger logger = LoggerFactory.getLogger(InMemorySubmissionQueuePublisher.class);
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    @Override
    public void publishJob(String jobId) {
        logger.debug("Publishing job to in-memory queue: {}", jobId);
        queue.offer(jobId);
    }

    public BlockingQueue<String> getQueue() {
        return queue;
    }
}
