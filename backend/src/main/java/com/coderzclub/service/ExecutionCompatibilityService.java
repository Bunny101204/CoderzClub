package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExecutionCompatibilityService {
    public static final String BATCH_STDIN_UNSUPPORTED =
        "BATCH_STDIN_PROGRAM is not currently supported: safe generic stdin batching is not implemented. "
            + "Configure STANDARD_PER_CASE or FUNCTION_HARNESS_BATCH. There is no silent fallback.";

    private final LanguageCapabilityCatalog catalog;
    private final FunctionHarnessService harnessService;

    public ExecutionCompatibilityService(LanguageCapabilityCatalog catalog, FunctionHarnessService harnessService) {
        this.catalog = catalog;
        this.harnessService = harnessService;
    }

    public ExecutionMode configuredMode(ExecutionMode raw) {
        return ExecutionMode.canonical(raw);
    }

    public String fallbackReason(ExecutionMode raw) {
        if (raw == ExecutionMode.FUNCTION) {
            return "legacy_FUNCTION_mapped_to_STANDARD_PER_CASE";
        }
        return null;
    }

    public void validate(ExecutionMode rawMode, Integer languageId, String code,
                         List<SubmissionJob.TestCase> publicTests, List<SubmissionJob.TestCase> hiddenTests,
                         String testcaseVersion) {
        ExecutionMode mode = configuredMode(rawMode);
        if (mode == ExecutionMode.BATCH_STDIN_PROGRAM) {
            throw new IncompatibleExecutionException(BATCH_STDIN_UNSUPPORTED);
        }
        if (!catalog.isEnabled(languageId)) {
            throw new IncompatibleExecutionException("Language " + languageId + " is not enabled");
        }
        if (!catalog.supports(languageId, mode)) {
            throw new IncompatibleExecutionException(
                "Language " + languageId + " does not support execution mode " + mode);
        }
        if (mode == ExecutionMode.FUNCTION_HARNESS_BATCH) {
            if (!harnessService.supports(languageId, code, publicTests, hiddenTests, testcaseVersion)) {
                throw new IncompatibleExecutionException(
                    "FUNCTION_HARNESS_BATCH requires testcaseVersion line-v1, Java/Python/C++ function-body code, and single-line testcase inputs");
            }
        }
    }
}
