package com.coderzclub.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Compatibility helpers for gradually attaching organization ownership to
 * batches, contests, and reports. Null/blank organization IDs mean legacy
 * platform-scoped records and must keep working under existing rules.
 */
@Component
public class OrganizationResourceCompatibility {
    public static final String PLATFORM_SCOPE = "PLATFORM";

    public boolean isLegacyPlatformScoped(String organizationId) {
        return !StringUtils.hasText(organizationId);
    }

    public String normalizeStoredOrganizationId(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return null;
        }
        String trimmed = organizationId.trim();
        if (PLATFORM_SCOPE.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    public boolean sameTenant(String leftOrganizationId, String rightOrganizationId) {
        String left = normalizeStoredOrganizationId(leftOrganizationId);
        String right = normalizeStoredOrganizationId(rightOrganizationId);
        if (left == null && right == null) {
            return true;
        }
        return left != null && left.equals(right);
    }
}
