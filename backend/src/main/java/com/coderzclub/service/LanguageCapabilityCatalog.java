package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intentional CoderzClub language catalog. Entries are the editor-supported languages,
 * not every syntactically valid Judge0 ID. liveProviderVerified stays false until a
 * live /languages probe exists. BATCH_STDIN_PROGRAM is reserved and unsupported.
 */
@Component
public class LanguageCapabilityCatalog {
    private final Map<Integer, LanguageCapability> byId = new LinkedHashMap<>();

    public LanguageCapabilityCatalog() {
        add(62, "Java", true);
        add(71, "Python", true);
        add(54, "C++", true);
        add(50, "C", false);
        add(51, "C#", false);
        add(63, "JavaScript", false);
        add(74, "TypeScript", false);
        add(60, "Go", false);
        add(68, "PHP", false);
        add(72, "Ruby", false);
        add(73, "Rust", false);
        add(78, "Kotlin", false);
        add(81, "Scala", false);
        add(83, "Swift", false);
    }

    private void add(int id, String name, boolean harness) {
        byId.put(id, new LanguageCapability(id, name, true, true, false, harness, false));
    }

    public LanguageCapability get(Integer languageId) {
        if (languageId == null) {
            return null;
        }
        return byId.get(languageId);
    }

    public boolean isEnabled(Integer languageId) {
        LanguageCapability capability = get(languageId);
        return capability != null && capability.enabled();
    }

    public boolean supports(Integer languageId, ExecutionMode mode) {
        LanguageCapability capability = get(languageId);
        if (capability == null || !capability.enabled()) {
            return false;
        }
        ExecutionMode canonical = ExecutionMode.canonical(mode);
        return switch (canonical) {
            case FUNCTION_HARNESS_BATCH -> capability.functionHarnessBatch();
            case BATCH_STDIN_PROGRAM -> false;
            default -> capability.standardPerCase();
        };
    }

    public List<LanguageCapability> all() {
        return new ArrayList<>(byId.values());
    }
}
