package com.coderzclub.service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Classifies persisted submission result/verdict strings for progress and profile stats.
 * Job lifecycle statuses such as COMPLETED are never treated as ACCEPTED.
 */
public final class SubmissionAttemptClassifier {
    public enum Kind {
        ACCEPTED,
        STUDENT_VERDICT,
        INFRASTRUCTURE,
        IGNORED
    }

    private static final Set<String> LIFECYCLE = Set.of(
        "COMPLETED", "QUEUED", "PENDING", "RUNNING", "RETRYING", "FAILED",
        "IN_QUEUE", "PROCESSING", "CANCELLED"
    );

    private static final Set<String> STUDENT = Set.of(
        "WRONG_ANSWER", "COMPILATION_ERROR", "RUNTIME_ERROR", "TIME_LIMIT_EXCEEDED",
        "MEMORY_LIMIT_EXCEEDED", "COMPILATION_TIME_LIMIT_EXCEEDED"
    );

    private SubmissionAttemptClassifier() {}

    public static Kind classify(String result, String verdict) {
        Kind fromResult = classifyOne(result);
        Kind fromVerdict = classifyOne(verdict);
        if (fromResult == Kind.ACCEPTED || fromVerdict == Kind.ACCEPTED) {
            return Kind.ACCEPTED;
        }
        if (fromResult == Kind.STUDENT_VERDICT || fromVerdict == Kind.STUDENT_VERDICT) {
            return Kind.STUDENT_VERDICT;
        }
        if (fromResult == Kind.INFRASTRUCTURE || fromVerdict == Kind.INFRASTRUCTURE) {
            return Kind.INFRASTRUCTURE;
        }
        return Kind.IGNORED;
    }

    public static Kind classifyOne(String raw) {
        if (raw == null || raw.isBlank()) {
            return Kind.IGNORED;
        }
        String key = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if ("ACCEPTED".equals(key)) {
            return Kind.ACCEPTED;
        }
        if (LIFECYCLE.contains(key)) {
            return Kind.IGNORED;
        }
        if (ExecutionVerdictMapper.isInfrastructureFailure(key)) {
            return Kind.INFRASTRUCTURE;
        }
        if (STUDENT.contains(key) || key.startsWith("RUNTIME_ERROR")) {
            return Kind.STUDENT_VERDICT;
        }
        return Kind.IGNORED;
    }

    public static String problemStatus(Collection<Kind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return null;
        }
        if (kinds.contains(Kind.ACCEPTED)) {
            return "SOLVED";
        }
        if (kinds.contains(Kind.STUDENT_VERDICT)) {
            return "ATTEMPTED";
        }
        return null;
    }

    public static boolean countsTowardActivity(String result, String verdict) {
        Kind kind = classify(result, verdict);
        return kind == Kind.ACCEPTED || kind == Kind.STUDENT_VERDICT;
    }
}
