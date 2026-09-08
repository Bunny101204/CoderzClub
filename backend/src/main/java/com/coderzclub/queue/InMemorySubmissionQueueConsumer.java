package com.coderzclub.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Profile("local")
public class InMemorySubmissionQueueConsumer implements SubmissionQueueConsumer {

    private final InMemorySubmissionQueuePublisher publisher;

    @Autowired
    public InMemorySubmissionQueueConsumer(InMemorySubmissionQueuePublisher publisher) {
        this.publisher = publisher;
    }

    private ExecutorService executor;

    @Override
    public void start(MessageHandler handler, int concurrency) {
        executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        for (int i = 0; i < Math.max(1, concurrency); i++) {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String jobId = publisher.getQueue().poll(5, TimeUnit.SECONDS);
                        if (jobId != null) handler.handle(jobId);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    @Override
    public void stop() {
        if (executor != null) executor.shutdownNow();
    }
}
