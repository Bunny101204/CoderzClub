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
}