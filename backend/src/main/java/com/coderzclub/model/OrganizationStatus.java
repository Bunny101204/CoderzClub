package com.coderzclub.model;

import java.util.Locale;

public enum OrganizationStatus {
    ACTIVE,
    SUSPENDED;

    public static OrganizationStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return OrganizationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
