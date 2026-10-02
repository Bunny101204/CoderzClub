package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCapabilityCatalogTest {
    private final LanguageCapabilityCatalog catalog = new LanguageCapabilityCatalog();

    @Test
    void catalogContainsOnlyIntentionalEditorLanguages() {
        assertEquals(14, catalog.all().size());
        assertTrue(catalog.isEnabled(62));
        assertTrue(catalog.isEnabled(71));
        assertTrue(catalog.isEnabled(54));
        assertTrue(catalog.isEnabled(50));
    }

    @Test
    void randomIdsInsideLegacyValidatorRangeAreNotAdvertised() {
        assertNull(catalog.get(1));
        assertNull(catalog.get(7));
        assertNull(catalog.get(45));
        assertNull(catalog.get(91));
        assertFalse(catalog.isEnabled(1));
        assertFalse(catalog.isEnabled(45));
        assertFalse(catalog.isEnabled(91));
    }

    @Test
    void batchStdinIsUnsupportedForEveryCatalogLanguage() {
        for (LanguageCapability capability : catalog.all()) {
            assertFalse(capability.batchStdinProgram());
            assertFalse(capability.liveProviderVerified());
            assertTrue(capability.enabled());
            assertTrue(capability.standardPerCase());
            assertFalse(catalog.supports(capability.id(), ExecutionMode.BATCH_STDIN_PROGRAM));
        }
    }

    @Test
    void harnessSupportIsOnlyForJavaPythonCpp() {
        assertTrue(catalog.supports(62, ExecutionMode.FUNCTION_HARNESS_BATCH));
        assertTrue(catalog.supports(71, ExecutionMode.FUNCTION_HARNESS_BATCH));
        assertTrue(catalog.supports(54, ExecutionMode.FUNCTION_HARNESS_BATCH));
        assertFalse(catalog.supports(50, ExecutionMode.FUNCTION_HARNESS_BATCH));
        assertFalse(catalog.supports(60, ExecutionMode.FUNCTION_HARNESS_BATCH));
        assertTrue(catalog.supports(60, ExecutionMode.STANDARD_PER_CASE));
    }

    @Test
    void stdinStdoutAliasIsStandard() {
        assertEquals(ExecutionMode.STANDARD_PER_CASE, ExecutionMode.fromValue("STDIN_STDOUT"));
        assertEquals(ExecutionMode.STANDARD_PER_CASE, ExecutionMode.canonical(null));
        assertEquals(ExecutionMode.STANDARD_PER_CASE, ExecutionMode.canonical(ExecutionMode.FUNCTION));
    }
}
