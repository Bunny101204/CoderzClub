package com.coderzclub.service;

import com.coderzclub.dto.ProblemContentUpdate;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.TestCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemContentServiceTest {
    private final ProblemContentService service = new ProblemContentService(
        new ProblemExecutionConfigService(), new SubmissionValidator());

    @Test
    void omittedHiddenTestsArePreserved() {
        Problem problem = seeded();
        ProblemContentUpdate update = new ProblemContentUpdate();
        update.setTitle("New title");
        update.setStatement("New statement");
        service.apply(problem, update);
        assertEquals("New title", problem.getTitle());
        assertEquals("secret", problem.getHiddenTestCases().get(0).getInput());
        assertEquals(ExecutionMode.STANDARD_PER_CASE, problem.getExecutionMode());
        assertEquals("Keep me", problem.getCategory());
    }

    @Test
    void executionModeStillRejectsBatchStdin() {
        Problem problem = seeded();
        ProblemContentUpdate update = new ProblemContentUpdate();
        update.setExecutionMode("BATCH_STDIN_PROGRAM");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.apply(problem, update));
        assertEquals(true, ex.getMessage().contains("not currently supported"));
        assertEquals("Keep me", problem.getCategory());
        assertEquals("secret", problem.getHiddenTestCases().get(0).getInput());
    }

    private static Problem seeded() {
        Problem problem = new Problem();
        problem.setTitle("Old");
        problem.setStatement("Old statement");
        problem.setCategory("Keep me");
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(new ArrayList<>(List.of(new TestCase("1", "1", "public"))));
        problem.setHiddenTestCases(new ArrayList<>(List.of(new TestCase("secret", "2", null))));
        return problem;
    }
}
