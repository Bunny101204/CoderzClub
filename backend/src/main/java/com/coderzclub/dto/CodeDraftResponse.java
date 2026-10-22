package com.coderzclub.dto;

import java.time.Instant;

public class CodeDraftResponse {
    private String code;
    private long revision;
    private Instant updatedAt;

    public CodeDraftResponse() {}

    public CodeDraftResponse(String code, long revision, Instant updatedAt) {
        this.code = code;
        this.revision = revision;
        this.updatedAt = updatedAt;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
