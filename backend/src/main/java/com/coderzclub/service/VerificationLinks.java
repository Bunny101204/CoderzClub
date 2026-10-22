package com.coderzclub.service;

import java.util.List;

/**
 * Builds the frontend origin used in verification emails.
 * A browser-supplied origin is used only when it is an exact allowlisted origin.
 */
public final class VerificationLinks {
    private VerificationLinks() {
    }

    public static String frontendBase(String clientOrigin, List<String> allowedOrigins, String configuredFrontend) {
        String client = strip(clientOrigin);
        if (client != null && allowedOrigins != null) {
            for (String allowed : allowedOrigins) {
                String candidate = strip(allowed);
                if (candidate != null && client.equalsIgnoreCase(candidate)) {
                    return client;
                }
            }
        }
        String configured = strip(configuredFrontend);
        if (configured != null) {
            return configured;
        }
        return "http://localhost:5173";
    }

    static String strip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
