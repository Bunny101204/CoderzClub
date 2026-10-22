package com.coderzclub.service;

import org.springframework.http.HttpStatus;

/**
 * Stable, non-disclosing contest admission failures.
 */
public class ContestAdmissionException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public ContestAdmissionException(HttpStatus status, String error) {
        super(error);
        this.status = status;
        this.error = error;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public static ContestAdmissionException notFound() {
        return new ContestAdmissionException(HttpStatus.NOT_FOUND, "Contest not found");
    }

    public static ContestAdmissionException notLive() {
        return new ContestAdmissionException(HttpStatus.CONFLICT, "CONTEST_NOT_LIVE");
    }

    public static ContestAdmissionException ended() {
        return new ContestAdmissionException(HttpStatus.CONFLICT, "CONTEST_ENDED");
    }
}
