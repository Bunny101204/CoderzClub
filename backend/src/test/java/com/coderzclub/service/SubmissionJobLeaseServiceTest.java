package com.coderzclub.service;

import com.coderzclub.config.WorkerProperties;
import com.coderzclub.model.SubmissionJob;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class SubmissionJobLeaseServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SubmissionJobLeaseService leaseService;

    @Test
    void staleWorkerCannotCompleteReclaimedJob() {
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        boolean completed = leaseService.completeIfOwned("job-1", "stale-worker", new SubmissionJob());

        assertFalse(completed);
    }

    @Test
    void twoWorkersCannotClaimSameJob() {
        SubmissionJob claimed = new SubmissionJob();
        claimed.setId("job-1");
        claimed.setStatus(SubmissionJob.JobStatus.RUNNING);
        final int[] claimCount = {0};
        when(mongoTemplate.findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(SubmissionJob.class)))
            .thenAnswer(invocation -> claimCount[0]++ == 0 ? claimed : null);

        Optional<SubmissionJob> first = leaseService.claimJob("job-1", "worker-1", 60);
        Optional<SubmissionJob> second = leaseService.claimJob("job-1", "worker-2", 60);

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
        verify(mongoTemplate, org.mockito.Mockito.times(2))
            .findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(SubmissionJob.class));
    }

    @Test
    void heartbeatRenewsLockedUntilForClaimedJob() {
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertTrue(leaseService.heartbeat("job-1", "worker-1", 60));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(SubmissionJob.class));
        assertEquals("job-1", queryCaptor.getValue().getQueryObject().get("_id"));
        assertFalse(queryCaptor.getValue().getQueryObject().containsKey("id"));
        assertTrue(queryCaptor.getValue().getQueryObject().containsKey("lockedUntil"));
        assertTrue(updateCaptor.getValue().getUpdateObject().containsKey("$set"));
        assertNotNull(updateCaptor.getValue().getUpdateObject().get("$set"));
    }

    @Test
    void heartbeatReturnsFalseForWrongWorker() {
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertFalse(leaseService.heartbeat("job-1", "wrong-worker", 60));
    }

    @Test
    void validHeartbeatPreventsSecondWorkerClaim() {
        SubmissionJob claimed = new SubmissionJob();
        claimed.setId("job-1");
        when(mongoTemplate.findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(SubmissionJob.class)))
            .thenReturn(claimed, null);
        when(mongoTemplate.updateFirst(any(), any(), eq(SubmissionJob.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertTrue(leaseService.claimJob("job-1", "worker-1", 60).isPresent());
        assertTrue(leaseService.heartbeat("job-1", "worker-1", 60));
        assertTrue(leaseService.claimJob("job-1", "worker-2", 60).isEmpty());
    }

    @Test
    void retryDelayGrowsExponentiallyAndStaysBounded() {
        WorkerProperties properties = new WorkerProperties();
        properties.setRetryDelaySeconds(10);
        properties.setRetryMaxDelaySeconds(35);
        properties.setRetryJitterSeconds(0);

        assertEquals(10_000L, properties.retryDelayMillis(1));
        assertEquals(20_000L, properties.retryDelayMillis(2));
        assertEquals(35_000L, properties.retryDelayMillis(3));
        assertEquals(35_000L, properties.retryDelayMillis(5));
    }
}
