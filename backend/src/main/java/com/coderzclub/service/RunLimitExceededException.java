package com.coderzclub.service;

public class RunLimitExceededException extends RuntimeException {
    private final SubmissionLimitDecision decision;

    public RunLimitExceededException(SubmissionLimitDecision decision) {
        super(decision == null ? "RATE_LIMIT_EXCEEDED" : decision.getReason());
        this.decision = decision;
    }

    public SubmissionLimitDecision getDecision() {
        return decision;
    }
}
