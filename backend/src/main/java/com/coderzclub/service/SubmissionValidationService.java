package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SubmissionValidationService {

    @Value("${app.submission.max-code-length:200000}")
    private int maxCodeLength;

    @Value("${app.submission.max-testcase-count:50}")
    private int maxTestcaseCount;

    @Value("${app.submission.max-testcase-input-length:200000}")
    private int maxTestcaseInputLength;

    @Value("${app.submission.max-testcase-output-length:200000}")
    private int maxTestcaseOutputLength;

    @Value("${app.submission.max-stdout-length:64000}")
    private int maxStdoutLength;

    @Value("${app.submission.max-stderr-length:64000}")
    private int maxStderrLength;

    @Value("${app.submission.max-compile-output-length:32000}")
    private int maxCompileOutputLength;

    @Value("${app.submission.max-memory-kb:512000}")
    private int maxMemoryKb;

    @Autowired(required = false)
    private LanguageCapabilityCatalog languageCatalog;

    public void validateSubmissionRequest(String problemId, String code, Integer languageId) {
        if (problemId == null || problemId.isBlank()) {
            throw new IllegalArgumentException("problemId is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (code.length() > maxCodeLength) {
            throw new IllegalArgumentException("code exceeds maximum length");
        }
        if (languageId == null) {
            throw new IllegalArgumentException("languageId is required");
        }
        if (!isSupportedLanguage(languageId)) {
            throw new IllegalArgumentException("unsupported language");
        }
    }

    public void validateProblemTestcases(Problem problem) {
        validateTestCaseList(problem.getPublicTestCases(), "public");
        validateTestCaseList(problem.getHiddenTestCases(), "hidden");
    }

    public void validateTestCaseList(List<? extends TestCase> testCases, String kind) {
        if (testCases == null) {
            return;
        }
        if (testCases.size() > maxTestcaseCount) {
            throw new IllegalArgumentException(kind + " testcases exceed maximum count");
        }
        for (TestCase testCase : testCases) {
            if (testCase == null) {
                continue;
            }
            if (testCase.getInput() != null && testCase.getInput().length() > maxTestcaseInputLength) {
                throw new IllegalArgumentException(kind + " testcase input exceeds maximum size");
            }
            if (testCase.getOutput() != null && testCase.getOutput().length() > maxTestcaseOutputLength) {
                throw new IllegalArgumentException(kind + " testcase output exceeds maximum size");
            }
        }
    }

    public Map<String, Object> sanitizeJudge0Response(Map<String, Object> responseMap) {
        if (responseMap == null) {
            return Map.of();
        }

        Map<String, Object> sanitized = new java.util.LinkedHashMap<>();
        sanitized.put("status", sanitizeStatus(responseMap.get("status")));
        if (responseMap.get("time") != null) {
            sanitized.put("time", responseMap.get("time"));
        }
        if (responseMap.get("memory") != null) {
            sanitized.put("memory", responseMap.get("memory"));
        }
        sanitized.put("stdout", truncateOutput(responseMap.get("stdout"), maxStdoutLength));
        sanitized.put("stderr", truncateOutput(responseMap.get("stderr"), maxStderrLength));
        sanitized.put("compile_output", truncateOutput(responseMap.get("compile_output"), maxCompileOutputLength));
        return sanitized;
    }

    public String truncateOutput(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...[truncated]";
    }

    private Map<String, Object> sanitizeStatus(Object status) {
        if (status instanceof Map<?, ?> statusMap) {
            Map<String, Object> sanitizedStatus = new java.util.LinkedHashMap<>();
            Object id = statusMap.get("id");
            Object description = statusMap.get("description");
            if (id != null) {
                sanitizedStatus.put("id", id);
            }
            if (description != null) {
                sanitizedStatus.put("description", description);
            }
            return sanitizedStatus;
        }
        return Map.of();
    }

    private boolean isSupportedLanguage(Integer languageId) {
        LanguageCapabilityCatalog catalog = languageCatalog != null ? languageCatalog : new LanguageCapabilityCatalog();
        return catalog.isEnabled(languageId);
    }

    public int getMaxMemoryKb() {
        return maxMemoryKb;
    }
}
