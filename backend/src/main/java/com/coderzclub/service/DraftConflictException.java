package com.coderzclub.service;

import java.time.Instant;

/**
 * Stored revision did not match the caller's expected revision.
 * The message stays a stable code so logs cannot include source text.
 */
public class DraftConflictException extends RuntimeException {
    private final String code;
    private final long revision;
    private final Instant updatedAt;

    public DraftConflictException(String code, long revision, Instant updatedAt) {
        super("DRAFT_CONFLICT");
        this.code = code;
        this.revision = revision;
        this.updatedAt = updatedAt;
    }

    public String getCode() { return code; }
    public long getRevision() { return revision; }
    public Instant getUpdatedAt() { return updatedAt; }
}
