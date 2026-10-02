package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import org.springframework.stereotype.Service;

@Service
public class ProblemExecutionConfigService {
    public Problem apply(Problem existing, ExecutionMode requestedMode, String testcaseVersion) {
        if (existing == null) {
            throw new IllegalArgumentException("Problem is required");
        }
        ExecutionMode mode = ExecutionMode.canonical(requestedMode);
        if (mode == ExecutionMode.BATCH_STDIN_PROGRAM) {
            throw new IllegalArgumentException(ExecutionCompatibilityService.BATCH_STDIN_UNSUPPORTED);
        }
        if (mode == ExecutionMode.FUNCTION_HARNESS_BATCH
            && testcaseVersion != null
            && !testcaseVersion.isBlank()
            && !FunctionHarnessService.FORMAT_VERSION.equals(testcaseVersion)) {
            throw new IllegalArgumentException("FUNCTION_HARNESS_BATCH requires testcaseVersion line-v1");
        }
        existing.setExecutionMode(mode);
        if (testcaseVersion != null && !testcaseVersion.isBlank()) {
            existing.setTestcaseVersion(testcaseVersion);
        } else if (mode == ExecutionMode.FUNCTION_HARNESS_BATCH
            && !FunctionHarnessService.FORMAT_VERSION.equals(existing.getTestcaseVersion())) {
            throw new IllegalArgumentException("FUNCTION_HARNESS_BATCH requires testcaseVersion line-v1");
        }
        return existing;
    }
}
