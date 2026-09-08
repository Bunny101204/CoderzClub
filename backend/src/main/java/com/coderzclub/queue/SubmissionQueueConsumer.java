package com.coderzclub.queue;

public interface SubmissionQueueConsumer {
    enum MessageDisposition {
        ACK,
        RETRY,
        DEAD_LETTER
    }

    @FunctionalInterface
    interface MessageHandler {
        MessageDisposition handle(String jobId);
    }

    void start(MessageHandler handler, int concurrency);

    default void stop() {
    }
}
