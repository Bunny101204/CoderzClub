package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContestPropertiesTest {
    @Test
    void globalContestsDefaultDisabled() {
        assertFalse(new ContestProperties().isGlobalEnabled());
    }

    @Test
    void globalContestsCanBeEnabledWithoutCodeChanges() {
        ContestProperties properties = new ContestProperties();
        properties.setGlobalEnabled(true);
        assertTrue(properties.isGlobalEnabled());
    }
}
