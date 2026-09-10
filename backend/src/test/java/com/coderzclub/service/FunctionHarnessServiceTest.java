package com.coderzclub.service;

import com.coderzclub.model.SubmissionJob;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FunctionHarnessServiceTest {
    private final FunctionHarnessService harness = new FunctionHarnessService();

    @Test
    void generatesVersionedWrappersForJavaCppAndPython() {
        assertTrue(harness.wrap(62, "return input.trim();").contains("static String solve"));
        assertTrue(harness.wrap(54, "return input;").contains("string solve"));
        assertTrue(harness.wrap(71, "return input_text.strip()").contains("def solve"));
    }

    @Test
    void fullProgramsAreRejectedByHarnessGate() {
        assertFalse(harness.supports(62, "public class Main { static void main(String[] a) {} }",
            tests("a"), List.of(), FunctionHarnessService.FORMAT_VERSION));
    }

    @Test
    void acceptedMultiCaseOutputMapsInOrder() {
        List<SubmissionJob.TestCase> tests = tests("a", "b");
        Map<String, Object> response = response(3, "Accepted", "CZ1:YQ==\nCZ1:Yg==\n");

        List<SubmissionJob.TestResult> results = harness.mapResults(response, tests);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed());
        assertTrue(results.get(1).isPassed());
    }

    @Test
    void laterCaseWrongAnswerIsLocalToThatCase() {
        List<SubmissionJob.TestCase> tests = tests("a", "b");
        List<SubmissionJob.TestResult> results = harness.mapResults(
            response(3, "Accepted", "CZ1:YQ==\nCZ1:eA==\n"), tests);

        assertTrue(results.get(0).isPassed());
        assertFalse(results.get(1).isPassed());
        assertEquals("WRONG_ANSWER", results.get(1).getErrorType());
    }

    @Test
    void insufficientOutputIsDetected() {
        List<SubmissionJob.TestResult> results = harness.mapResults(
            response(3, "Accepted", "CZ1:YQ==\n"), tests("a", "b"));

        assertEquals("INSUFFICIENT_OUTPUT", results.get(1).getErrorType());
    }

    @Test
    void compilationAndRuntimeFailuresAreClassified() {
        assertEquals("COMPILATION_ERROR", harness.mapResults(
            response(6, "Compilation error", ""), tests("a")).get(0).getErrorType());
        assertEquals("RUNTIME_ERROR", harness.mapResults(
            response(7, "Runtime error", ""), tests("a")).get(0).getErrorType());
    }

    @Test
    void hiddenValuesAreNotPresentInEventPayload() throws Exception {
        SubmissionJob job = new SubmissionJob();
        job.setId("job");
        job.setStatus(SubmissionJob.JobStatus.COMPLETED);
        job.setFinalResult("WRONG_ANSWER");
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(
            com.coderzclub.dto.SubmissionJobEvent.from(job));

        assertFalse(json.contains("secret-input"));
        assertFalse(json.contains("secret-output"));
    }

    private List<SubmissionJob.TestCase> tests(String... values) {
        return java.util.Arrays.stream(values)
            .map(value -> new SubmissionJob.TestCase(value, value, null)).toList();
    }

    private Map<String, Object> response(int status, String description, String stdout) {
        return Map.of("status", Map.of("id", status, "description", description), "stdout", stdout);
    }
}