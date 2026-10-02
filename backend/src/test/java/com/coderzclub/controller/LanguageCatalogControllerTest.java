package com.coderzclub.controller;

import com.coderzclub.service.LanguageCapability;
import com.coderzclub.service.LanguageCapabilityCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LanguageCatalogControllerTest {
    @Test
    void catalogEndpointExposesIntentionalLanguagesOnly() {
        Map<String, Object> body = new LanguageCatalogController(new LanguageCapabilityCatalog()).list();
        @SuppressWarnings("unchecked")
        List<LanguageCapability> languages = (List<LanguageCapability>) body.get("languages");
        assertEquals(false, body.get("liveProviderVerified"));
        assertEquals(14, languages.size());
        assertFalse(languages.stream().anyMatch(item -> item.id() == 1 || item.id() == 45 || item.id() == 91));
        assertFalse(languages.stream().anyMatch(LanguageCapability::batchStdinProgram));
        assertFalse(languages.stream().anyMatch(LanguageCapability::liveProviderVerified));
    }
}
