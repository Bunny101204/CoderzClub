package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeSortedArraysExpectedOutputTest {

    @Test
    void seedTypoExpectedIsNotTheTwoPointerMerge() {
        String input = "3 2\n2 4 6\n1 3";
        String storedTypo = "1 2 3 4 6 6";
        String correct = MergeSortedArraysExpectedOutput.twoPointerMergeOutput(
            new int[]{2, 4, 6}, new int[]{1, 3});
        assertEquals("1 2 3 4 6", correct);
        assertNotEquals(storedTypo, correct);
        assertEquals(correct, MergeSortedArraysExpectedOutput.expectedIfValidSortedInput(input).orElseThrow());
        assertFalse(Judge0ExecutionService.outputsMatch(correct, storedTypo, true));
        assertTrue(Judge0ExecutionService.outputsMatch(correct, correct, true));
    }

    @Test
    void studentTwoPointerAgreesWithPublicEmptyFirstArray() {
        Optional<String> expected = MergeSortedArraysExpectedOutput.expectedIfValidSortedInput("0 3\n\n1 2 3");
        assertEquals("1 2 3", expected.orElseThrow());
    }

    @Test
    void incorrectStoredExpectedWouldRejectValidTwoPointerOutput() {
        String validStudentStdout = "1 2 3 4 6";
        String previouslyIncorrectExpected = "1 2 3 4 6 6";
        assertFalse(Judge0ExecutionService.outputsMatch(validStudentStdout, previouslyIncorrectExpected, true),
            "A duplicated last value in expected output incorrectly rejects a valid merge");
    }

    @Test
    void duplicateValuesAreKept() {
        assertEquals("5 5", MergeSortedArraysExpectedOutput.twoPointerMergeOutput(new int[]{5}, new int[]{5}));
    }
}

class CodingDurationTest {
    @Test
    void rejectsNegativeNaNAndHugeValues() {
        assertEquals(null, CodingDuration.sanitize(-1));
        assertEquals(null, CodingDuration.sanitize(Integer.MAX_VALUE));
        assertEquals(null, CodingDuration.sanitize(Double.NaN));
        assertEquals(null, CodingDuration.sanitize("not-a-number"));
        assertEquals(42, CodingDuration.sanitize(42));
        assertEquals(CodingDuration.MAX_SECONDS, CodingDuration.sanitize(CodingDuration.MAX_SECONDS));
        assertEquals(null, CodingDuration.sanitize(CodingDuration.MAX_SECONDS + 1L));
    }
}

class ExecutionUserFacingTest {
    @Test
    void neverSurfacesLiteralNullProviderText() {
        assertTrue(ExecutionUserFacing.isUnusableProviderText(null));
        assertTrue(ExecutionUserFacing.isUnusableProviderText("Judge0 provider error: null"));
        assertEquals(ExecutionUserFacing.PROVIDER_UNAVAILABLE,
            ExecutionUserFacing.studentDetail("INTERNAL_ERROR", "Judge0 provider error: null", false));
        assertEquals(ExecutionUserFacing.JUDGE0_PROVIDER_ERROR,
            ExecutionUserFacing.clientErrorCode("INTERNAL_ERROR"));
        assertEquals(ExecutionUserFacing.HIDDEN_WRONG_ANSWER,
            ExecutionUserFacing.studentDetail("WRONG_ANSWER", "secret", true));
    }
}

class ProviderNullMessageRegressionTest {
    @Test
    void parseErrorMessageDoesNotReturnNullLiteral() {
        Map<String, Object> response = Map.of(
            "status", Map.of("id", 13, "description", "Judge0 provider error: null"));
        assertEquals("INTERNAL_ERROR", Judge0ExecutionService.parseErrorType(response));
        String message = Judge0ExecutionService.parseErrorMessage(response);
        assertEquals(ExecutionUserFacing.PROVIDER_UNAVAILABLE, message);
        assertFalse(message.contains("null"));
    }
}
