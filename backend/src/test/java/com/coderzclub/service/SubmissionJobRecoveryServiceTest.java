package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.queue.SubmissionQueuePublisher;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionJobRecoveryServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private SubmissionQueuePublisher publisher;
    @Mock
    private WorkerProperties workerProperties;
    @Mock
    private SubmissionJobEventService eventService;
    @InjectMocks
    private SubmissionJobRecoveryService recoveryService;

    @Test
    void expiredLeaseTransitionsOnlyOnce() {
        SubmissionJob job = expiredJob(1, 3);
        final int[] findCount = {0};
        when(mongoTemplate.find(any(), eq(SubmissionJob.class)))
            .thenAnswer(invocation -> findCount[0]++ == 0 ? List.of(job) : List.of());
        when(workerProperties.retryDelayMillis(1)).thenReturn(1000L);
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        recoveryService.recoverStuckJobs();

        verify(mongoTemplate).updateFirst(any(), any(), eq(SubmissionJob.class));
        verify(publisher, never()).publishJob(any());
    }

    @Test
    void expiredLeaseTimesOutAtMaxAttempts() {
        SubmissionJob job = expiredJob(3, 3);
        final int[] findCount = {0};
        when(mongoTemplate.find(any(), eq(SubmissionJob.class)))
            .thenAnswer(invocation -> findCount[0]++ == 0 ? List.of(job) : List.of());
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        recoveryService.recoverStuckJobs();

        verify(mongoTemplate).updateFirst(any(), any(), eq(SubmissionJob.class));
        verify(publisher, never()).publishJob(any());
    }

    private SubmissionJob expiredJob(int attempts, int maxAttempts) {
        SubmissionJob job = new SubmissionJob();
        job.setId("job-1");
        job.setStatus(SubmissionJob.JobStatus.RUNNING);
        job.setLockedBy("worker-1");
        job.setLockedUntil(new Date(System.currentTimeMillis() - 1000));
        job.setAttemptCount(attempts);
        job.setMaxAttempts(maxAttempts);
        return job;
    }
}
