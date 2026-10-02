package com.coderzclub.service;

public class IncompatibleExecutionException extends RuntimeException {
    private final String reason;

    public IncompatibleExecutionException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
