package com.coderzclub.service;

import com.coderzclub.config.ContestRunProperties;
import com.coderzclub.model.ContestRunJob;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestRunLeaseServiceTest {
    private MongoTemplate mongo;
    private ContestRunLeaseService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        service = new ContestRunLeaseService(mongo, new ContestRunProperties());
    }

    @Test
    void queueExpiryUpdatesOnlyWaitingRunsAndKeepsCreatedAt() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestRunJob.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        assertTrue(service.failIfQueueExpired("run-1", "RUN_EXPIRED", "Run waited too long. Please run again."));

        verify(mongo).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(ContestRunJob.class));
        String query = queryCaptor.getValue().getQueryObject().toString();
        assertTrue(query.contains("QUEUED"), query);
        assertTrue(query.contains("RUNNING"), query);
        assertTrue(query.contains("leaseExpiresAt"), query);
        Document set = setFields(updateCaptor.getValue());
        assertEquals(ContestRunJob.Status.FAILED, set.get("status"));
        assertEquals("RUN_EXPIRED", set.get("errorCode"));
        assertEquals("Run waited too long. Please run again.", set.get("errorMessage"));
        assertFalse(set.containsKey("createdAt"));
    }

    @Test
    void releaseToQueuedDoesNotRefreshCreatedAt() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestRunJob.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        assertTrue(service.releaseToQueuedIfOwned("run-1", "worker-1"));

        verify(mongo).updateFirst(any(Query.class), updateCaptor.capture(), eq(ContestRunJob.class));
        Document set = setFields(updateCaptor.getValue());
        assertEquals(ContestRunJob.Status.QUEUED, set.get("status"));
        assertFalse(set.containsKey("createdAt"));
        assertFalse(set.containsKey("attemptCount"));
        assertFalse(updateCaptor.getValue().getUpdateObject().containsKey("$inc"));
    }

    @Test
    void claimDoesNotRefreshCreatedAt() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(), eq(ContestRunJob.class)))
            .thenReturn(new ContestRunJob());
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        service.claim("run-1", "worker-1");

        verify(mongo).findAndModify(any(Query.class), updateCaptor.capture(), any(), eq(ContestRunJob.class));
        Document set = setFields(updateCaptor.getValue());
        assertEquals(ContestRunJob.Status.RUNNING, set.get("status"));
        assertFalse(set.containsKey("createdAt"));
    }

    private static Document setFields(Update update) {
        return (Document) update.getUpdateObject().get("$set");
    }
}
