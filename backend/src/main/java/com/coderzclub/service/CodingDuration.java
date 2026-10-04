package com.coderzclub.service;

/**
 * Client-supplied coding timer metadata. Never used for judging or ranking.
 */
public final class CodingDuration {
    public static final int MAX_SECONDS = 7 * 24 * 60 * 60;

    private CodingDuration() {}

    public static Integer sanitize(Integer seconds) {
        if (seconds == null) {
            return null;
        }
        if (seconds < 0 || seconds > MAX_SECONDS) {
            return null;
        }
        return seconds;
    }

    public static Integer sanitize(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer value) {
            return sanitize(value);
        }
        if (raw instanceof Long value) {
            if (value < 0 || value > MAX_SECONDS) {
                return null;
            }
            return value.intValue();
        }
        if (raw instanceof Double value) {
            if (value.isNaN() || value.isInfinite()) {
                return null;
            }
            long rounded = Math.round(value);
            if (rounded < 0 || rounded > MAX_SECONDS) {
                return null;
            }
            return (int) rounded;
        }
        try {
            long parsed = Long.parseLong(raw.toString().trim());
            if (parsed < 0 || parsed > MAX_SECONDS) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
