package com.coderzclub.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Contest submit body. Identity comes from the URL path; extra JSON fields are ignored.
 * The idempotency key is taken from the request header, not the JSON body.
 */
public class ContestSubmissionJobRequest extends SubmissionExecutionRequest {
    @JsonIgnore
    private String idempotencyKey;

    @JsonIgnore
    public String getIdempotencyKey() { return idempotencyKey; }

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
