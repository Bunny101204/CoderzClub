package com.coderzclub.queue;

/**
 * The broker accepted the connection but refused this contest-run publish because the
 * queue is at its hard capacity. This is not a client-facing message.
 */
public class ContestRunPublishRejectedException extends RuntimeException {
    public ContestRunPublishRejectedException() {
        super("Contest run queue rejected publish");
    }
}
