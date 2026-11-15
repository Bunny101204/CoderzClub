package com.coderzclub.model;

import java.util.Locale;

public enum OrganizationDomainStatus {
    PENDING,
    VERIFIED,
    ACTIVE,
    DISABLED;

    public static OrganizationDomainStatus from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return OrganizationDomainStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Only ACTIVE hostnames may drive tenant routing. VERIFIED means ownership was proven
     * but public routing remains off until an administrator activates the domain and
     * {@code app.custom-domains.public-resolution-enabled} is explicitly enabled.
     */
    public boolean mayResolveTenant() {
        return this == ACTIVE;
    }
}
