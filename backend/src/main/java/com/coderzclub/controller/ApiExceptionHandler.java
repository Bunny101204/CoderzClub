package com.coderzclub.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import com.coderzclub.service.ContestAdmissionException;
import com.coderzclub.service.DraftConflictException;
import com.coderzclub.service.SubmissionAdmissionHttpException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Request body is invalid"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE).body(Map.of("error", "Request body is too large"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
    }

    @ExceptionHandler(ContestAdmissionException.class)
    public ResponseEntity<Map<String, Object>> handleContestAdmission(ContestAdmissionException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("error", ex.getError()));
    }

    @ExceptionHandler(DraftConflictException.class)
    public ResponseEntity<Map<String, Object>> handleDraftConflict(DraftConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "DRAFT_CONFLICT");
        body.put("code", ex.getCode());
        body.put("revision", ex.getRevision());
        body.put("updatedAt", ex.getUpdatedAt());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SubmissionAdmissionHttpException.class)
    public ResponseEntity<Map<String, Object>> handleSubmissionAdmission(SubmissionAdmissionHttpException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getBody());
    }
}
