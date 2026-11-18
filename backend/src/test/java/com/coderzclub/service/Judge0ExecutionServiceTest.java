package com.coderzclub.service;

import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.model.SubmissionJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        provider.setMaxCpuTimeLimitSeconds(15);
        provider.setServerMaxCpuTimeLimitSeconds(15);
        provider.setWallTimeLimitSeconds(20);
        provider.setServerMaxWallTimeLimitSeconds(20);

        Map<String, Object> payload = Judge0ExecutionService.buildPayload(
            "code", 62, "input", 3, limits, provider);

        assertEquals(512, payload.get("memory_limit"));
        // 10s * 3 cases = 30, but clamped to server-compatible maxCpu 15
        assertEquals(15, payload.get("cpu_time_limit"));
        assertEquals(20, payload.get("wall_time_limit"));
        assertFalse(payload.containsKey("compile_time_limit"));
        assertEquals(false, payload.get("enable_network"));
        assertEquals(false, payload.get("enable_per_process_and_thread_time_limit"));
        assertEquals(false, payload.get("enable_per_process_and_thread_memory_limit"));
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
    void whitespaceOnlyAndEmptyStdinArePreservedInPayload() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(10);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();

        Map<String, Object> space = Judge0ExecutionService.buildPayload(
            "code", 71, " ", 1, limits, provider);
        Map<String, Object> spaces = Judge0ExecutionService.buildPayload(
            "code", 71, "  ", 1, limits, provider);
        Map<String, Object> empty = Judge0ExecutionService.buildPayload(
            "code", 71, "", 1, limits, provider);
        Map<String, Object> missing = Judge0ExecutionService.buildPayload(
            "code", 71, null, 1, limits, provider);

        assertEquals(" ", space.get("stdin"));
        assertEquals("  ", spaces.get("stdin"));
        assertEquals("", empty.get("stdin"));
        assertFalse(missing.containsKey("stdin"));
    }

    @Test
    void leadingTrailingAndInteriorWhitespaceStdinAreNeverDiscarded() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(10);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();

        String leading = "  abca";
        String trailing = "abca  ";
        String tabs = "\tabc\t";
        String mixed = " \nAa\n ";

        assertEquals(leading, Judge0ExecutionService.buildPayload("c", 71, leading, 1, limits, provider).get("stdin"));
        assertEquals(trailing, Judge0ExecutionService.buildPayload("c", 71, trailing, 1, limits, provider).get("stdin"));
        assertEquals(tabs, Judge0ExecutionService.buildPayload("c", 71, tabs, 1, limits, provider).get("stdin"));
        assertEquals(mixed, Judge0ExecutionService.buildPayload("c", 71, mixed, 1, limits, provider).get("stdin"));
    }

    @Test
    void outputsMatchTrimsOuterWhitespaceButKeepsInteriorStructure() {
        assertTrue(Judge0ExecutionService.outputsMatch("3\n", "3", true));
        assertTrue(Judge0ExecutionService.outputsMatch("a 1\nA 1\n", "a 1\nA 1", true));
        assertTrue(Judge0ExecutionService.outputsMatch("  2  ", "  2", true));
        assertFalse(Judge0ExecutionService.outputsMatch("A 1\na 1", "a 1\nA 1", true));
        assertTrue(Judge0ExecutionService.outputsMatch("No Output", "", false));
        assertTrue(Judge0ExecutionService.outputsMatch("9223372036854775807", "9223372036854775807", true));
        assertFalse(Judge0ExecutionService.outputsMatch("1 2 3", "1 2 4", true));
    }

    @Test
    void harnessPathClampsToServerCompatibleCpuCap() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(15);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        Map<String, Object> payload = Judge0ExecutionService.buildPayload(
            "code", 62, "input", 10, limits, provider);
        assertEquals(15, payload.get("cpu_time_limit"));
        assertEquals(20, payload.get("wall_time_limit"));
        assertFalse(payload.containsKey("compile_time_limit"));
    }

    @Test
    void standardPerCaseUsesPerCaseBudgetWithinServerMax() {
        SubmissionLimitsConfig limits = new SubmissionLimitsConfig();
        limits.setMaxExecutionTimeSeconds(15);
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        Map<String, Object> payload = Judge0ExecutionService.buildPayload(
            "code", 71, "1\n", 1, limits, provider);
        assertEquals(15, payload.get("cpu_time_limit"));
        assertEquals(20, payload.get("wall_time_limit"));
        assertEquals(262144, payload.get("memory_limit"));
        assertEquals(false, payload.get("enable_network"));
    }

    @Test
    void legacySixtySecondAppCapFailsValidationAgainstStockJudge0() {
        Judge0ProviderProperties provider = new Judge0ProviderProperties();
        provider.setMaxCpuTimeLimitSeconds(60);
        provider.setServerMaxCpuTimeLimitSeconds(15);
        assertThrows(IllegalStateException.class,
            () -> com.coderzclub.config.ExecutionTimeoutPolicy.validate(
                provider, new com.coderzclub.config.WorkerProperties()));
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