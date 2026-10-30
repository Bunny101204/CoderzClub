package com.coderzclub.service;

/**
 * Result of trying to own one client attempt.
 * OWNED means this request may create the job.
 * REPLAY means that job already exists and must be returned as-is.
 * IN_PROGRESS means another request is still accepting the same attempt.
 */
public final class IdempotencyClaim {
    public enum Kind { ABSENT, OWNED, REPLAY, IN_PROGRESS }

    private final Kind kind;
    private final String recordId;
    private final String resourceId;
    private final String operationKey;

    private IdempotencyClaim(Kind kind, String recordId, String resourceId, String operationKey) {
        this.kind = kind;
        this.recordId = recordId;
        this.resourceId = resourceId;
        this.operationKey = operationKey;
    }

    public static IdempotencyClaim absent() {
        return new IdempotencyClaim(Kind.ABSENT, null, null, null);
    }

    public static IdempotencyClaim owned(String recordId) {
        return new IdempotencyClaim(Kind.OWNED, recordId, null, null);
    }

    public static IdempotencyClaim replay(String resourceId) {
        return new IdempotencyClaim(Kind.REPLAY, null, resourceId, null);
    }

    public static IdempotencyClaim inProgress(String resourceId) {
        return inProgress(resourceId, null);
    }

    public static IdempotencyClaim inProgress(String resourceId, String operationKey) {
        return new IdempotencyClaim(Kind.IN_PROGRESS, null, resourceId, operationKey);
    }

    public boolean owned() {
        return kind == Kind.OWNED;
    }

    public boolean replay() {
        return kind == Kind.REPLAY;
    }

    public boolean inProgress() {
        return kind == Kind.IN_PROGRESS;
    }

    public String recordId() {
        return recordId;
    }

    public String resourceId() {
        return resourceId;
    }

    public String operationKey() {
        return operationKey;
    }
}
