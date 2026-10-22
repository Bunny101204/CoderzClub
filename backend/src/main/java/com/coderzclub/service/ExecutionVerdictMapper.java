package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;

import java.util.List;
import java.util.Locale;

/**
 * Single mapping from per-case and harness error strings to the job's final result.
 */
public final class ExecutionVerdictMapper {
    public static final String ACCEPTED = "ACCEPTED";
    public static final String WRONG_ANSWER = "WRONG_ANSWER";
    public static final String COMPILATION_ERROR = "COMPILATION_ERROR";
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    public static final String TIME_LIMIT_EXCEEDED = "TIME_LIMIT_EXCEEDED";
    public static final String MEMORY_LIMIT_EXCEEDED = "MEMORY_LIMIT_EXCEEDED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ExecutionVerdictMapper() {}

    public static String fromErrorType(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return WRONG_ANSWER;
        }
        String key = errorType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (key) {
            case "COMPILATION_ERROR", "COMPILATION_TIME_LIMIT_EXCEEDED" -> COMPILATION_ERROR;
            case "RUNTIME_ERROR" -> RUNTIME_ERROR;
            case "TIME_LIMIT_EXCEEDED" -> TIME_LIMIT_EXCEEDED;
            case "MEMORY_LIMIT_EXCEEDED" -> MEMORY_LIMIT_EXCEEDED;
            case "INTERNAL_ERROR", "EXECUTION_ERROR", "EXECUTION_CANCELLED",
                 "PROVIDER_UNAVAILABLE", "PROVIDER_ERROR" -> INTERNAL_ERROR;
            case "WRONG_ANSWER" -> WRONG_ANSWER;
            default -> key.startsWith("RUNTIME_ERROR") ? RUNTIME_ERROR : WRONG_ANSWER;
        };
    }

    public static boolean isInfrastructureFailure(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return false;
        }
        return INTERNAL_ERROR.equals(fromErrorType(errorType));
    }

    public static String fromTestResults(List<SubmissionJob.TestResult> results) {
        if (results == null || results.isEmpty()) {
            return ACCEPTED;
        }
        boolean allPassed = results.stream().allMatch(SubmissionJob.TestResult::isPassed);
        if (allPassed) {
            return ACCEPTED;
        }
        for (SubmissionJob.TestResult result : results) {
            if (result.isPassed()) {
                continue;
            }
            if (result.getErrorType() == null || result.getErrorType().isBlank()) {
                continue;
            }
            String verdict = fromErrorType(result.getErrorType());
            if (!WRONG_ANSWER.equals(verdict)) {
                return verdict;
            }
        }
        return WRONG_ANSWER;
    }
}
