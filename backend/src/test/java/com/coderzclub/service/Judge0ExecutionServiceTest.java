package com.coderzclub.service;

import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.model.SubmissionJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void payloadUsesJudge0BytesExactlyOnceFromConfiguredKb() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(10);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setMemoryLimitKb(512);

        Map<String, Object> payload = Judge0ExecutionService.buildPayload(
            "code", 62, "input", 3, limits, provider);

        assertEquals(512, payload.get("memory_limit"));
        assertEquals(30, payload.get("cpu_time_limit"));
        assertEquals(30, payload.get("compile_time_limit"));
    }

    @Test
    void multilineCustomStdinReachesTheProviderPayloadUnchanged() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(10);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        String stdin = "5 2\n10 20 30 40 50";
        Map<String, Object> javaPayload = Judge0ExecutionService.buildPayload(
            "import java.util.*; class Main { public static void main(String[] args) { Scanner in = new Scanner(System.in); System.out.println(in.nextInt()); } }",
            62, stdin, 1, limits, provider);
        Map<String, Object> pythonPayload = Judge0ExecutionService.buildPayload(
            "import sys\nprint(sys.stdin.read())",
            71, stdin, 1, limits, provider);
        assertEquals(stdin, javaPayload.get("stdin"));
        assertEquals(stdin, pythonPayload.get("stdin"));
    }

    @Test
    void harnessPathKeepsSixtySecondCpuCap() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(15);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        Map<String, Object> payload = Judge0ExecutionService.buildPayload(
            "code", 62, "input", 10, limits, provider);
        assertEquals(60, payload.get("cpu_time_limit"));
    }

    @Test
    void status13IsInternalErrorAndIsNotComparedAsWrongAnswer() {
        Map<String, Object> response = Map.of(
            "status", Map.of("id", 13, "description", "Internal Error"),
            "stdout", "1"
        );

        String errorType = Judge0ExecutionService.parseErrorType(response);

        assertEquals("INTERNAL_ERROR", errorType);
        assertTrue(ExecutionVerdictMapper.isInfrastructureFailure(errorType));

        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setPassed(false);
        result.setErrorType(errorType);
        result.setActualOutput("1");
        result.setExpectedOutput("1");

        assertFalse(result.isPassed());
        assertEquals("INTERNAL_ERROR", ExecutionVerdictMapper.fromTestResults(List.of(result)));
        assertNotEquals("WRONG_ANSWER", ExecutionVerdictMapper.fromTestResults(List.of(result)));
    }

    @Test
    void missingProviderResponseIsInternalError() {
        assertEquals("INTERNAL_ERROR", Judge0ExecutionService.parseErrorType(null));
        assertEquals("INTERNAL_ERROR", Judge0ExecutionService.parseErrorType(Map.of()));
    }
}