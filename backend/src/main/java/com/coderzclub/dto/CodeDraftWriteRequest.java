package com.coderzclub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Client draft write. Ownership always comes from the authenticated user, never from this body.
 * Unknown fields such as userId are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeDraftWriteRequest {
    private String code;
    private Long expectedRevision;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Long expectedRevision) { this.expectedRevision = expectedRevision; }
}
