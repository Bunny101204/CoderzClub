package com.coderzclub.service;

import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for validating submissions against configured security and performance limits.
 * Ensures code sizes, output sizes, and testcase limits are enforced.
 */
@Service
public class SubmissionValidator {

    @Autowired
    private SubmissionLimitsConfig limitsConfig;

    /**
     * Validate a submission code
     */
    public void validateCode(String code) throws IllegalArgumentException {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("Code cannot be empty");
        }

        if (code.length() > limitsConfig.getMaxCodeLength()) {
            throw new IllegalArgumentException(
                String.format("Code exceeds maximum length of %d characters. Submitted: %d characters",
                    limitsConfig.getMaxCodeLength(), code.length())
            );
        }

        if (code.length() < limitsConfig.getMinCodeLength()) {
            throw new IllegalArgumentException(
                String.format("Code is below minimum length of %d character",
                    limitsConfig.getMinCodeLength())
            );
        }
    }

    /**
     * Validate a language ID against Judge0 supported languages
     */
    public void validateLanguageId(Integer languageId) throws IllegalArgumentException {
        if (languageId == null) {
            throw new IllegalArgumentException("languageId is required");
        }

        // Judge0 supports languages 1-91 (with some gaps)
        // Valid ranges: 1-52 (older versions), 71-91 (newer versions)
        if (languageId < 1 || languageId > 91) {
            throw new IllegalArgumentException(
                String.format("Invalid languageId: %d. Must be between 1 and 91", languageId)
            );
        }
    }

    /**
     * Validate problem testcases against size limits
     */
    public void validateProblemTestCases(Problem problem) throws IllegalArgumentException {
        if (problem == null) {
            throw new IllegalArgumentException("Problem cannot be null");
        }

        List<TestCase> publicTestCases = problem.getPublicTestCases();
        List<TestCase> hiddenTestCases = problem.getHiddenTestCases();

        // Count total testcases
        int totalTestCases = (publicTestCases != null ? publicTestCases.size() : 0) +
                            (hiddenTestCases != null ? hiddenTestCases.size() : 0);

        if (totalTestCases > limitsConfig.getMaxTestCasesPerProblem()) {
            throw new IllegalArgumentException(
                String.format("Problem exceeds maximum testcases (%d). Total: %d",
                    limitsConfig.getMaxTestCasesPerProblem(), totalTestCases)
            );
        }

        // Validate each public testcase
        if (publicTestCases != null) {
            for (int i = 0; i < publicTestCases.size(); i++) {
                TestCase tc = publicTestCases.get(i);
                validateTestCaseSize("public", i, tc);
            }
        }

        // Validate each hidden testcase
        if (hiddenTestCases != null) {
            for (int i = 0; i < hiddenTestCases.size(); i++) {
                TestCase tc = hiddenTestCases.get(i);
                validateTestCaseSize("hidden", i, tc);
            }
        }
    }

    /**
     * Validate a single testcase input/output sizes
     */
    private void validateTestCaseSize(String type, int index, TestCase testCase) throws IllegalArgumentException {
        if (testCase == null) {
            return;
        }

        // Check input size
        if (testCase.getInput() != null) {
            int inputLength = testCase.getInput().length();
            if (inputLength > limitsConfig.getMaxTestCaseInputLength()) {
                throw new IllegalArgumentException(
                    String.format("%s testcase %d input exceeds maximum size (%d bytes). Size: %d bytes",
                        type, index, limitsConfig.getMaxTestCaseInputLength(), inputLength)
                );
            }
        }

        // Check expected output size
        String expectedOutput = testCase.getOutput();
        if (expectedOutput != null) {
            int outputLength = expectedOutput.length();
            if (outputLength > limitsConfig.getMaxTestCaseOutputLength()) {
                throw new IllegalArgumentException(
                    String.format("%s testcase %d output exceeds maximum size (%d bytes). Size: %d bytes",
                        type, index, limitsConfig.getMaxTestCaseOutputLength(), outputLength)
                );
            }
        }
    }

    /**
     * Truncate output to prevent MongoDB storage bloat
     */
    public String truncateOutput(String output, int maxLength) {
        if (output == null) {
            return null;
        }
        if (output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "\n[OUTPUT TRUNCATED - exceeded " + maxLength + " bytes]";
    }

    /**
     * Check if output was truncated
     */
    public boolean isOutputTruncated(String output, int maxLength) {
        return output != null && output.length() > maxLength;
    }
}
