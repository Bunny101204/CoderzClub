package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionCompatibilityServiceTest {
    private final ExecutionCompatibilityService service = new ExecutionCompatibilityService(
        new LanguageCapabilityCatalog(), new FunctionHarnessService());

    @Test
    void legacyFunctionMapsToStandardWithReason() {
        assertEquals(ExecutionMode.STANDARD_PER_CASE, service.configuredMode(ExecutionMode.FUNCTION));
        assertEquals("legacy_FUNCTION_mapped_to_STANDARD_PER_CASE", service.fallbackReason(ExecutionMode.FUNCTION));
    }

    @Test
    void harnessIncompatibleCodeIsRejected() {
        IncompatibleExecutionException ex = assertThrows(IncompatibleExecutionException.class, () ->
            service.validate(ExecutionMode.FUNCTION_HARNESS_BATCH, 62,
                "public class Main { public static void main(String[] a) {} }",
                List.of(new SubmissionJob.TestCase("a", "a", null)), List.of(),
                FunctionHarnessService.FORMAT_VERSION));
        assertTrue(ex.getMessage().contains("FUNCTION_HARNESS_BATCH"));
    }

    @Test
    void standardRemainsValid() {
        service.validate(null, 71, "print(1)", List.of(new SubmissionJob.TestCase("1", "1", null)), List.of(), "v1");
    }

    @Test
    void batchStdinIsAlwaysRejectedWithoutFallback() {
        IncompatibleExecutionException ex = assertThrows(IncompatibleExecutionException.class, () ->
            service.validate(ExecutionMode.BATCH_STDIN_PROGRAM, 71, "print(1)",
                List.of(new SubmissionJob.TestCase("1", "1", null)), List.of(), "v1"));
        assertTrue(ex.getMessage().contains("not currently supported"));
        assertTrue(ex.getMessage().contains("no silent fallback"));
    }

    private static void assertTrue(boolean value) {
        org.junit.jupiter.api.Assertions.assertTrue(value);
    }
}
