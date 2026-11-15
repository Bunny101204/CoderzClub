package com.coderzclub.model;

import java.util.Locale;

/**
 * Organization-scoped roles. These never grant platform {@code ADMIN}.
 */
public enum OrganizationRole {
    ORG_OWNER,
    ORG_ADMIN,
    INSTRUCTOR,
    STUDENT;

    public static OrganizationRole from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Organization role is required");
        }
        try {
            return OrganizationRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported organization role");
        }
    }

    public boolean canAdministerOrganization() {
        return this == ORG_OWNER || this == ORG_ADMIN;
    }

    public boolean canManageMembers() {
        return canAdministerOrganization();
    }

    public boolean canManageInstructors() {
        return this == ORG_OWNER || this == ORG_ADMIN;
    }
}
