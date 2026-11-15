package com.coderzclub.model;

import java.util.Locale;

public enum OrganizationMemberStatus {
    ACTIVE,
    REMOVED;

    public static OrganizationMemberStatus from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        return OrganizationMemberStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}
