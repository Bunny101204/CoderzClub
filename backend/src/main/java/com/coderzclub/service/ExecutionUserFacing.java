package com.coderzclub.service;

import org.slf4j.MDC;

/**
 * Student-safe execution messages. Technical provider details stay in logs.
 */
public final class ExecutionUserFacing {
    public static final String JUDGE0_PROVIDER_ERROR = "JUDGE0_PROVIDER_ERROR";
    public static final String PROVIDER_UNAVAILABLE =
        "Execution service is temporarily unavailable. Please retry.";
    public static final String INFRASTRUCTURE_NOT_WRONG_ANSWER =
        "Judging infrastructure failed. This is not a wrong answer.";
    public static final String HIDDEN_WRONG_ANSWER = "Wrong answer on a hidden test.";
    public static final int CLIENT_DETAIL_LIMIT = 2000;

    private ExecutionUserFacing() {}

    public static String bound(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= CLIENT_DETAIL_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, CLIENT_DETAIL_LIMIT);
    }

    public static boolean isUnusableProviderText(String raw) {
        if (raw == null) {
            return true;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return true;
        }
        return trimmed.toLowerCase().endsWith(": null") || trimmed.toLowerCase().endsWith(":null");
    }

    public static String infrastructureMessage() {
        return PROVIDER_UNAVAILABLE;
    }

    public static String clientErrorCode(String verdict) {
        if (ExecutionVerdictMapper.INTERNAL_ERROR.equals(verdict)
            || ExecutionVerdictMapper.isInfrastructureFailure(verdict)) {
            return JUDGE0_PROVIDER_ERROR;
        }
        return verdict;
    }

    public static String studentDetail(String verdict, String providerOrErrorText, boolean hiddenFailure) {
        if (verdict == null || verdict.isBlank()) {
            return hiddenFailure ? HIDDEN_WRONG_ANSWER : bound(providerOrErrorText);
        }
        return switch (verdict) {
            case ExecutionVerdictMapper.INTERNAL_ERROR -> PROVIDER_UNAVAILABLE;
            case ExecutionVerdictMapper.COMPILATION_ERROR ->
                isUnusableProviderText(providerOrErrorText)
                    ? "Compilation failed."
                    : bound(providerOrErrorText);
            case ExecutionVerdictMapper.RUNTIME_ERROR ->
                isUnusableProviderText(providerOrErrorText)
                    ? "A runtime error occurred."
                    : bound(providerOrErrorText);
            case ExecutionVerdictMapper.TIME_LIMIT_EXCEEDED ->
                isUnusableProviderText(providerOrErrorText) ? "Time limit exceeded." : bound(providerOrErrorText);
            case ExecutionVerdictMapper.MEMORY_LIMIT_EXCEEDED ->
                isUnusableProviderText(providerOrErrorText) ? "Memory limit exceeded." : bound(providerOrErrorText);
            case ExecutionVerdictMapper.WRONG_ANSWER ->
                hiddenFailure ? HIDDEN_WRONG_ANSWER : (isUnusableProviderText(providerOrErrorText) ? "Wrong answer." : bound(providerOrErrorText));
            default ->
                ExecutionVerdictMapper.isInfrastructureFailure(verdict)
                    ? PROVIDER_UNAVAILABLE
                    : (hiddenFailure ? HIDDEN_WRONG_ANSWER : bound(providerOrErrorText));
        };
    }

    public static String jobDiagnostic(String verdict) {
        if (ExecutionVerdictMapper.INTERNAL_ERROR.equals(verdict)
            || ExecutionVerdictMapper.isInfrastructureFailure(verdict)) {
            return INFRASTRUCTURE_NOT_WRONG_ANSWER;
        }
        if (ExecutionVerdictMapper.WRONG_ANSWER.equals(verdict)) {
            return HIDDEN_WRONG_ANSWER;
        }
        return studentDetail(verdict, null, false);
    }

    public static String correlationId() {
        String id = MDC.get("correlationId");
        return id == null || id.isBlank() ? null : id;
    }
}
