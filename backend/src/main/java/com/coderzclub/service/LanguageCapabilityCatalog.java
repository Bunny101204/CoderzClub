package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intentional CoderzClub language catalog. Entries are the editor-supported languages,
 * not every syntactically valid Judge0 ID. Only languages with {@code enabled=true} may
 * be submitted. {@code liveProviderVerified} stays false until a live Judge0 probe
 * succeeds for that language on the target runtime. BATCH_STDIN_PROGRAM is reserved
 * and unsupported.
 *
 * <p>Release gating (Phase 6.5): Python (71) and C++ (54) are verified against the
 * staging Judge0 worker path. Java (62) and JavaScript (63) fail minimal hello-world
 * on the local isolate (heap reserve / wall-clock TLE) and stay disabled until a
 * production-class Judge0 probe passes — do not silently accept submissions that
 * cannot grade.
 */
@Component
public class LanguageCapabilityCatalog {
    private final Map<Integer, LanguageCapability> byId = new LinkedHashMap<>();

    public LanguageCapabilityCatalog() {
        // Verified for release on staging Judge0 (hello-world + problem-bank Phase 6).
        add(71, "Python", true, true, true);
        add(54, "C++", true, true, true);

        // Advertised historically but blocked for release until live isolate is healthy.
        add(62, "Java", false, true, false);
        add(63, "JavaScript", false, false, false);

        // Catalog placeholders — never enabled without live verification.
        add(50, "C", false, false, false);
        add(51, "C#", false, false, false);
        add(74, "TypeScript", false, false, false);
        add(60, "Go", false, false, false);
        add(68, "PHP", false, false, false);
        add(72, "Ruby", false, false, false);
        add(73, "Rust", false, false, false);
        add(78, "Kotlin", false, false, false);
        add(81, "Scala", false, false, false);
        add(83, "Swift", false, false, false);
    }

    private void add(int id, String name, boolean enabled, boolean harness, boolean liveVerified) {
        byId.put(id, new LanguageCapability(id, name, enabled, true, false, harness, liveVerified));
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

    public List<LanguageCapability> enabled() {
        return all().stream().filter(LanguageCapability::enabled).toList();
    }

    public boolean anyLiveProviderVerified() {
        return all().stream().anyMatch(LanguageCapability::liveProviderVerified);
    }
}
