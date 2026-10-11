package com.coderzclub.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Profile("local")
public class InMemoryContestRunPublisher implements ContestRunPublisher {
    private static final Logger logger = LoggerFactory.getLogger(InMemoryContestRunPublisher.class);
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    @Override
    public void publishRun(String runId) {
        logger.debug("Publishing contest run to in-memory queue");
        if (!queue.offer(runId)) {
            throw new IllegalStateException("Contest run in-memory queue rejected publish");
        }
    }

    public BlockingQueue<String> getQueue() {
        return queue;
    }
}
