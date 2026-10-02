package com.coderzclub.controller;

import com.coderzclub.service.RunLimitExceededException;
import com.coderzclub.service.SubmissionLimitDecision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

final class RateLimitResponses {
    private RateLimitResponses() {}

    static ResponseEntity<Map<String, Object>> from(RunLimitExceededException ex) {
        return from(ex.getDecision());
    }

    static ResponseEntity<Map<String, Object>> from(SubmissionLimitDecision decision) {
        String reason = decision == null ? "RATE_LIMIT_EXCEEDED" : decision.getReason();
        Map<String, Object> body = new HashMap<>();
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        switch (reason) {
            case "COOLDOWN" -> {
                body.put("error", "RATE_LIMIT_EXCEEDED");
                body.put("message", "Please wait before running code again.");
            }
            case "DAILY_LIMIT" -> {
                body.put("error", "DAILY_LIMIT_EXCEEDED");
                body.put("message", "You have exceeded your daily run limit.");
            }
            case "PROBLEM_LIMIT" -> {
                body.put("error", "PROBLEM_LIMIT_EXCEEDED");
                body.put("message", "You have exceeded your run limit for this problem today.");
            }
            case "REDIS_UNAVAILABLE" -> {
                body.put("error", "RATE_LIMIT_UNAVAILABLE");
                body.put("message", "Rate limit service unavailable. Please try again later.");
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
            default -> {
                body.put("error", "RATE_LIMIT_EXCEEDED");
                body.put("message", "Run limit exceeded.");
            }
        }
        body.put("reason", reason);
        return ResponseEntity.status(status).body(body);
    }
}
