package com.coderzclub.service;

import com.coderzclub.model.MutationIdempotencyRecord;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MutationClaimCommitTest {
    @Test
    void sameMillisecondRenewalKeepsOwnershipButUnmatchedClaimDoesNot() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Instant now = Instant.parse("2026-11-01T00:00:00Z");
        MutationIdempotencyRecord row = new MutationIdempotencyRecord();
        row.setId("claim");
        row.setOpen(true);
        row.setCreatedAt(Date.from(now));
        when(mongo.findOne(any(Query.class), eq(MutationIdempotencyRecord.class))).thenReturn(row);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(MutationIdempotencyRecord.class)))
            .thenReturn(UpdateResult.acknowledged(1, 0L, null), UpdateResult.acknowledged(0, 0L, null));
        MutationIdempotencyService service = new MutationIdempotencyService(mongo,
            mock(SubmissionOutboxService.class), Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(service.prepareCommit("u", "submission-job", "attempt001"));
        assertFalse(service.prepareCommit("u", "submission-job", "attempt001"));
    }
}
