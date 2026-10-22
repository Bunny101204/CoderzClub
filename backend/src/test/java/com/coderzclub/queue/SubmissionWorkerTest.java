package com.coderzclub.queue;

import com.coderzclub.model.SubmissionJob;
import com.coderzclub.repository.SubmissionTestResultRepository;
import com.coderzclub.service.SubmissionJobLeaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionWorkerTest {

    @Mock
    private SubmissionTestResultRepository resultRepository;

    @Mock
    private SubmissionJobLeaseService leaseService;

    @InjectMocks
    private SubmissionWorker worker;

    @Test
    void staleWorkerCannotSaveResultRowsAfterLeaseLoss() {
        SubmissionJob.TestResult testResult = new SubmissionJob.TestResult();
        when(leaseService.isOwned("job-1", "stale-worker")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> worker.saveResults(
            "job-1", "stale-worker", 2, List.of(testResult), 1, 1));

        verify(resultRepository, never()).save(any());
        verify(leaseService).isOwned(eq("job-1"), eq("stale-worker"));
    }

    @Test
    void acceptedResultsStayAccepted() {
        SubmissionJob.TestResult passed = new SubmissionJob.TestResult();
        passed.setPassed(true);
        assertEquals("ACCEPTED", worker.analyzeResults(List.of(passed)));
    }

    @Test
    void wrongAnswerRemainsWrongAnswer() {
        assertEquals("WRONG_ANSWER", worker.analyzeResults(List.of(failed("WRONG_ANSWER"))));
        assertEquals("WRONG_ANSWER", worker.analyzeResults(List.of(failed(null))));
    }

    @Test
    void compilationErrorAliasesProduceCompilationVerdict() {
        assertEquals("COMPILATION_ERROR", worker.analyzeResults(List.of(failed("COMPILATION_ERROR"))));
        assertEquals("COMPILATION_ERROR", worker.analyzeResults(List.of(failed("Compilation Error"))));
    }

    @Test
    void runtimeErrorAliasesProduceRuntimeVerdict() {
        assertEquals("RUNTIME_ERROR", worker.analyzeResults(List.of(failed("RUNTIME_ERROR"))));
        assertEquals("RUNTIME_ERROR", worker.analyzeResults(List.of(failed("Runtime Error"))));
    }

    @Test
    void timeLimitAliasesProduceTimeLimitVerdict() {
        assertEquals("TIME_LIMIT_EXCEEDED", worker.analyzeResults(List.of(failed("TIME_LIMIT_EXCEEDED"))));
        assertEquals("TIME_LIMIT_EXCEEDED", worker.analyzeResults(List.of(failed("Time Limit Exceeded"))));
    }

    @Test
    void memoryLimitAliasesProduceMemoryLimitVerdict() {
        assertEquals("MEMORY_LIMIT_EXCEEDED", worker.analyzeResults(List.of(failed("MEMORY_LIMIT_EXCEEDED"))));
        assertEquals("MEMORY_LIMIT_EXCEEDED", worker.analyzeResults(List.of(failed("Memory Limit Exceeded"))));
    }

    @Test
    void executionAndProviderErrorsNeverBecomeWrongAnswer() {
        assertEquals("INTERNAL_ERROR", worker.analyzeResults(List.of(failed("EXECUTION_ERROR"))));
        assertEquals("INTERNAL_ERROR", worker.analyzeResults(List.of(failed("Execution Error"))));
        assertEquals("INTERNAL_ERROR", worker.analyzeResults(List.of(failed("INTERNAL_ERROR"))));
        assertEquals("INTERNAL_ERROR", worker.analyzeResults(List.of(failed("PROVIDER_UNAVAILABLE"))));
        assertNotEquals("WRONG_ANSWER", worker.analyzeResults(List.of(failed("EXECUTION_ERROR"))));
        assertNotEquals("WRONG_ANSWER", worker.analyzeResults(List.of(failed("INTERNAL_ERROR"))));
    }

    private static SubmissionJob.TestResult failed(String errorType) {
        SubmissionJob.TestResult result = new SubmissionJob.TestResult();
        result.setPassed(false);
        result.setErrorType(errorType);
        return result;
    }
}