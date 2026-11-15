package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationResourceCompatibilityTest {
    private final OrganizationResourceCompatibility compatibility = new OrganizationResourceCompatibility();

    @Test
    void blankAndPlatformTokensRemainLegacyScoped() {
        assertTrue(compatibility.isLegacyPlatformScoped(null));
        assertTrue(compatibility.isLegacyPlatformScoped(""));
        assertNull(compatibility.normalizeStoredOrganizationId("PLATFORM"));
        assertTrue(compatibility.sameTenant(null, null));
        assertFalse(compatibility.sameTenant(null, "org-a"));
        assertTrue(compatibility.sameTenant("org-a", "org-a"));
        assertFalse(compatibility.sameTenant("org-a", "org-b"));
    }
}
