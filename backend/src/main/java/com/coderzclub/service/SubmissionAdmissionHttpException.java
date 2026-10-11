package com.coderzclub.service;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Rate-limit / queue admission rejection with a prebuilt safe HTTP body.
 */
public class SubmissionAdmissionHttpException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, Object> body;

    public SubmissionAdmissionHttpException(HttpStatus status, Map<String, Object> body) {
        super(body == null ? "Admission rejected" : String.valueOf(body.get("error")));
        this.status = status;
        this.body = body == null ? Map.of("error", "Admission rejected") : body;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
