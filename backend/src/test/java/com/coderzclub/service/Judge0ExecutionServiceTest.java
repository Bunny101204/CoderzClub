package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Judge0ExecutionServiceTest {

    @Test
    void expectedNaMatchesMissingProgramOutput() {
        assertTrue(Judge0ExecutionService.outputsMatch("No Output", "N/A", false));
    }

    @Test
    void expectedNaDoesNotMatchDifferentOutput() {
        assertFalse(Judge0ExecutionService.outputsMatch("2", "N/A", true));
    }

    @Test
    void literalNaOutputStillMatchesLiteralNaExpectation() {
        assertTrue(Judge0ExecutionService.outputsMatch("N/A", "N/A", true));
    }
}