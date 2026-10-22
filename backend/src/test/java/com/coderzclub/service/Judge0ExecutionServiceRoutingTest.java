package com.coderzclub.service;

import com.coderzclub.config.Judge0ProviderProperties;
import com.coderzclub.config.SubmissionLimitsConfig;
import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.SubmissionJob;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Judge0ExecutionServiceRoutingTest {
    private RecordingService service;

    @BeforeEach
    void setUp() {
        service = new RecordingService();
        FunctionHarnessService harness = new FunctionHarnessService();
        LanguageCapabilityCatalog catalog = new LanguageCapabilityCatalog();
        service.configureForTest(
            new WorkerProperties(),
            harness,
            new ExecutionCompatibilityService(catalog, harness),
            new OperationalMetrics(new SimpleMeterRegistry()),
            new SubmissionLimitsConfig()
        );
    }

    @Test
    void standardPerCaseMakesOneProviderCallPerCase() {
        service.response = accepted("1");
        ExecutionOutcome outcome = service.executeTestCases("print(1)", 71,
            List.of(caseOf("1", "1"), caseOf("1", "1")), List.of(), ExecutionMode.STANDARD_PER_CASE, "v1");
        assertEquals(ExecutionMode.STANDARD_PER_CASE, outcome.getExecutionModeUsed());
        assertEquals(2, outcome.getProviderExecutions());
        assertEquals(2, service.calls.get());
        assertTrue(outcome.getResults().get(0).isPassed());
    }

    @Test
    void batchStdinIsRejectedAndDoesNotCallProvider() {
        IncompatibleExecutionException ex = assertThrows(IncompatibleExecutionException.class, () ->
            service.executeTestCases("print(input())", 71,
                List.of(caseOf("1", "1"), caseOf("2", "2")), List.of(), ExecutionMode.BATCH_STDIN_PROGRAM, "v1"));
        assertTrue(ex.getMessage().contains("not currently supported"));
        assertEquals(0, service.calls.get());
    }

    @Test
    void harnessBatchUsesOneGuardedProviderCall() {
        service.response = Map.of(
            "status", Map.of("id", 3, "description", "Accepted"),
            "stdout", "CZ1:YQ==\nCZ1:Yg==\n",
            "time", "0.04",
            "memory", "2048"
        );
        ExecutionOutcome outcome = service.executeTestCases("return input.trim();", 62,
            List.of(caseOf("a", "a"), caseOf("b", "b")), List.of(),
            ExecutionMode.FUNCTION_HARNESS_BATCH, FunctionHarnessService.FORMAT_VERSION);
        assertEquals(1, service.calls.get());
        assertEquals(1, outcome.getProviderExecutions());
        assertEquals(2048L, outcome.getResults().get(0).getMemory());
    }

    @Test
    void harnessDoesNotSilentlyFallBack() {
        assertThrows(IncompatibleExecutionException.class, () ->
            service.executeTestCases("public class Main { static void main(String[] a) {} }", 62,
                List.of(caseOf("a", "a")), List.of(), ExecutionMode.FUNCTION_HARNESS_BATCH,
                FunctionHarnessService.FORMAT_VERSION));
        assertEquals(0, service.calls.get());
    }

    @Test
    void harnessStatus13IsInternalErrorNotWrongAnswer() {
        service.response = Map.of(
            "status", Map.of("id", 13, "description", "Internal Error"),
            "stdout", "CZ1:YQ==\n"
        );
        ExecutionOutcome outcome = service.executeTestCases("return input.trim();", 62,
            List.of(caseOf("a", "a")), List.of(),
            ExecutionMode.FUNCTION_HARNESS_BATCH, FunctionHarnessService.FORMAT_VERSION);
        assertEquals(1, service.calls.get());
        assertEquals("INTERNAL_ERROR", outcome.getResults().get(0).getErrorType());
        assertFalse(outcome.getResults().get(0).isPassed());
        assertEquals("INTERNAL_ERROR", ExecutionVerdictMapper.fromTestResults(outcome.getResults()));
    }

    @Test
    void absentModeIsStandard() {
        service.response = accepted("1");
        ExecutionOutcome outcome = service.executeTestCases("print(1)", 71,
            List.of(caseOf("1", "1")), List.of(), null, "v1");
        assertEquals(ExecutionMode.STANDARD_PER_CASE, outcome.getExecutionModeUsed());
    }

    @Test
    void customStdinIsOneProviderCall() {
        service.response = accepted("ok");
        service.executeCustomStdin("code", 71, "stdin");
        assertEquals(1, service.calls.get());
    }

    private static SubmissionJob.TestCase caseOf(String in, String out) {
        return new SubmissionJob.TestCase(in, out, null);
    }

    private static Map<String, Object> accepted(String stdout) {
        return Map.of("status", Map.of("id", 3, "description", "Accepted"), "stdout", stdout);
    }

    static final class RecordingService extends Judge0ExecutionService {
        final AtomicInteger calls = new AtomicInteger();
        Map<String, Object> response = accepted("");

        RecordingService() {
            super(new Judge0ProviderProperties());
        }

        @Override
        protected Map<String, Object> executeProvider(String code, Integer languageId, String stdin, int testcaseCount) {
            calls.incrementAndGet();
            return response;
        }
    }
}
