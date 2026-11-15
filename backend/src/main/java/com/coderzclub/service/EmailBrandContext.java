package com.coderzclub.service;

/**
 * Safe branding values that may appear in transactional email copy.
 * Never includes remote HTML/JS; display names are plain text only.
 */
public record EmailBrandContext(String productName, String fromDisplayName) {
    public static EmailBrandContext platform() {
        return new EmailBrandContext("CoderzClub", "CoderzClub");
    }

    public static EmailBrandContext of(String displayName) {
        String cleaned = sanitizeDisplayName(displayName);
        if (cleaned == null) {
            return platform();
        }
        return new EmailBrandContext(cleaned, cleaned);
    }

    public static String sanitizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            return null;
        }
        // Strip tags and control chars to keep plain-text templates safe.
        String safe = trimmed
            .replaceAll("(?i)<[^>]*>", "")
            .replaceAll("[\\p{Cntrl}<>]", "")
            .replaceAll("\\s+", " ")
            .trim();
        return safe.isBlank() ? null : safe;
    }
}
