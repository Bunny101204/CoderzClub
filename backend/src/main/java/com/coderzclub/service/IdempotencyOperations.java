package com.coderzclub.service;

public interface IdempotencyOperations {
    IdempotencyClaim begin(String userId, String scope, String operationKey, String subjectId);

    void complete(IdempotencyClaim claim, String resourceId);

    void abandon(IdempotencyClaim claim);

    /**
     * False when this request's claim was removed or already linked to a job.
     * Callers must not publish another job after this returns false.
     */
    boolean stillHeld(IdempotencyClaim claim);

    /**
     * Extends the short lease for a claim this request still owns.
     * False means the claim was removed or already linked, so the caller must not insert a job.
     */
    boolean renew(String userId, String scope, String operationKey);

    /**
     * True when this key may insert a job. A missing claim is allowed.
     * An open claim is refreshed only if its lease timestamp is unchanged.
     * A released or replaced claim returns false so the caller does not insert.
     */
    boolean prepareCommit(String userId, String scope, String operationKey);
}
