package com.coderzclub.queue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Profile("local")
public class InMemoryContestRunConsumer implements ContestRunConsumer {
    private final InMemoryContestRunPublisher publisher;
    private ExecutorService executor;

    public InMemoryContestRunConsumer(InMemoryContestRunPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void start(SubmissionQueueConsumer.MessageHandler handler, int concurrency) {
        executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        int workers = Math.max(1, concurrency);
        for (int i = 0; i < workers; i++) {
            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String runId = publisher.getQueue().poll(5, TimeUnit.SECONDS);
                        if (runId != null) {
                            handler.handle(runId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
