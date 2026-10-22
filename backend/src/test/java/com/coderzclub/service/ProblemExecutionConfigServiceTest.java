package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemExecutionConfigServiceTest {
    private final ProblemExecutionConfigService service = new ProblemExecutionConfigService();

    @Test
    void applyChangesOnlyExecutionFields() {
        Problem problem = seededProblem();
        service.apply(problem, ExecutionMode.FUNCTION_HARNESS_BATCH, FunctionHarnessService.FORMAT_VERSION);

        assertEquals(ExecutionMode.FUNCTION_HARNESS_BATCH, problem.getExecutionMode());
        assertEquals("line-v1", problem.getTestcaseVersion());
        assertEquals("Keep me", problem.getTitle());
        assertEquals("Full statement", problem.getStatement());
        assertEquals("1", problem.getPublicTestCases().get(0).getInput());
        assertEquals("secret", problem.getHiddenTestCases().get(0).getInput());
        assertEquals("ALGORITHMS", problem.getCategory());
    }

    @Test
    void batchStdinCannotBeConfigured() {
        Problem problem = seededProblem();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.apply(problem, ExecutionMode.BATCH_STDIN_PROGRAM, "v1"));
        assertTrue(ex.getMessage().contains("not currently supported"));
        assertEquals(ExecutionMode.STANDARD_PER_CASE, problem.getExecutionMode());
        assertEquals("Full statement", problem.getStatement());
    }

    @Test
    void applyReturnsSameProblemInstance() {
        Problem problem = seededProblem();
        assertSame(problem, service.apply(problem, ExecutionMode.STANDARD_PER_CASE, "v1"));
    }

    private static Problem seededProblem() {
        Problem problem = new Problem();
        problem.setId("p1");
        problem.setTitle("Keep me");
        problem.setStatement("Full statement");
        problem.setCategory("ALGORITHMS");
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(new ArrayList<>(List.of(new TestCase("1", "1", "public"))));
        problem.setHiddenTestCases(new ArrayList<>(List.of(new TestCase("secret", "2", null))));
        return problem;
    }
}
