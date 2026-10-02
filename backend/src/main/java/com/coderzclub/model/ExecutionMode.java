package com.coderzclub.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExecutionMode {
    STANDARD_PER_CASE,
    FUNCTION_HARNESS_BATCH,
    BATCH_STDIN_PROGRAM,
    FUNCTION;

    @JsonCreator
    public static ExecutionMode fromValue(String value) {
        if (value == null || value.isBlank() || "STDIN_STDOUT".equalsIgnoreCase(value)) {
            return STANDARD_PER_CASE;
        }
        if ("FUNCTION".equalsIgnoreCase(value)) return FUNCTION;
        return valueOf(value.toUpperCase());
    }

    public static ExecutionMode canonical(ExecutionMode mode) {
        if (mode == null || mode == FUNCTION) {
            return STANDARD_PER_CASE;
        }
        return mode;
    }

    public static boolean isLegacyAlias(ExecutionMode mode) {
        return mode == FUNCTION;
    }
}