package com.coderzclub.service;

public class SubmissionLimitDecision {
    private final boolean allowed;
    private final String reason;

    private SubmissionLimitDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static SubmissionLimitDecision allowed() {
        return new SubmissionLimitDecision(true, "ALLOWED");
    }

    public static SubmissionLimitDecision allowed(String reason) {
        return new SubmissionLimitDecision(true, reason);
    }

    public static SubmissionLimitDecision rejected(String reason) {
        return new SubmissionLimitDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
