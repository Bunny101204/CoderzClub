package com.coderzclub.queue;

public interface SubmissionQueueConsumer {
    String consumeJob(long timeoutSeconds);
}
