package com.coderzclub.queue;

public interface ContestRunConsumer {
    void start(SubmissionQueueConsumer.MessageHandler handler, int concurrency);

    default void stop() {
    }
}
