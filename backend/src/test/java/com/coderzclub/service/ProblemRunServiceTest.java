package com.coderzclub.service;

import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemRunServiceTest {
    @Mock
    private Judge0ExecutionService executionService;
    @Mock
    private ExecutionCompatibilityService compatibilityService;
    @Mock
    private SubmissionLimitService limitService;

    @Test
    void runPublicNeverPassesHiddenCasesAndDoesNotCreateHistory() {
        when(limitService.tryAcquireRunSlot("user-1", "p1")).thenReturn(SubmissionLimitDecision.allowed());
        ExecutionOutcome outcome = new ExecutionOutcome();
        outcome.setResults(List.of());
        when(executionService.executeTestCases(any(), any(), anyList(), anyList(), any(), any())).thenReturn(outcome);

        Problem problem = new Problem();
        problem.setId("p1");
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(List.of(new TestCase("1", "1", "public")));
        problem.setHiddenTestCases(List.of(new TestCase("hidden-in", "hidden-out", "secret")));

        ProblemRunService service = new ProblemRunService(executionService, compatibilityService, limitService);
        service.runPublicCases("user-1", problem, "print(1)", 71);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionJob.TestCase>> publicCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubmissionJob.TestCase>> hiddenCaptor = ArgumentCaptor.forClass(List.class);
        verify(executionService).executeTestCases(eq("print(1)"), eq(71), publicCaptor.capture(),
            hiddenCaptor.capture(), eq(ExecutionMode.STANDARD_PER_CASE), eq("v1"));
        assertEquals(1, publicCaptor.getValue().size());
        assertEquals("1", publicCaptor.getValue().get(0).getInput());
        assertTrue(hiddenCaptor.getValue().isEmpty());
        verify(compatibilityService).validate(
            eq(ExecutionMode.STANDARD_PER_CASE),
            eq(71),
            eq("print(1)"),
            eq(publicCaptor.getValue()),
            eq(List.of()),
            eq("v1"));
    }

    @Test
    void rateLimitRejectsBeforeJudge0() {
        when(limitService.tryAcquireRunSlot("user-1", "p1"))
            .thenReturn(SubmissionLimitDecision.rejected("COOLDOWN"));
        Problem problem = new Problem();
        problem.setId("p1");
        ProblemRunService service = new ProblemRunService(executionService, compatibilityService, limitService);

        RunLimitExceededException ex = assertThrows(RunLimitExceededException.class,
            () -> service.runPublicCases("user-1", problem, "print(1)", 71));
        assertEquals("COOLDOWN", ex.getDecision().getReason());
        verify(executionService, never()).executeTestCases(any(), any(), any(), any(), any(), any());
        verify(compatibilityService, never()).validate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void customStdinRateLimitDoesNotCallProvider() {
        when(limitService.tryAcquireRunSlot("user-1", ProblemRunService.CUSTOM_STDIN_RESOURCE))
            .thenReturn(SubmissionLimitDecision.rejected("DAILY_LIMIT"));
        ProblemRunService service = new ProblemRunService(executionService, compatibilityService, limitService);

        assertThrows(RunLimitExceededException.class,
            () -> service.runCustomStdin("user-1", "code", 71, "stdin"));
        verify(executionService, never()).executeCustomStdin(any(), any(), any());
    }

    @Test
    void customStdinAllowedUsesOneProviderCall() {
        when(limitService.tryAcquireRunSlot("user-1", ProblemRunService.CUSTOM_STDIN_RESOURCE))
            .thenReturn(SubmissionLimitDecision.allowed());
        ProblemRunService service = new ProblemRunService(executionService, compatibilityService, limitService);
        service.runCustomStdin("user-1", "code", 71, "stdin");
        verify(executionService).executeCustomStdin("code", 71, "stdin");
    }
}
