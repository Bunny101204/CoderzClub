package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestAccessServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");

    private MongoTemplate mongo;
    private ContestProperties properties;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        properties = new ContestProperties();
    }

    @Test
    void noMembershipAndGlobalDisabledDoesNotScanContests() {
        ContestAccessService service = serviceAt(NOW);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        assertFalse(service.hasAccessibleLiveOrEndedContestContaining(user("u1"), "p1"));
        verify(mongo, never()).exists(any(Query.class), eq(Contest.class));
    }

    @Test
    void liveGrantQueryUsesPublishedProblemIdAndStartBoundary() {
        ContestAccessService service = serviceAt(START);
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        assertTrue(service.hasAccessibleLiveOrEndedContestContaining(user("u1"), "p1"));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("published"));
        assertTrue(query.contains("problemIds"));
        assertTrue(query.contains("p1"));
        assertTrue(query.contains("startAt"));
    }

    @Test
    void upcomingClockDoesNotTreatStartAsLiveInCapturedQuery() {
        ContestAccessService before = serviceAt(NOW);
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(false);
        assertFalse(before.hasAccessibleLiveOrEndedContestContaining(user("u1"), "p1"));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("startAt"));
        assertTrue(query.contains(NOW.toString()) || query.contains("2026-06-01T12:00:00Z"));
    }

    @Test
    void isEligibleUsesCapturedInstantForEndedVsLiveMembership() {
        Instant liveNow = Instant.parse("2026-06-01T14:00:00Z");
        Instant end = Instant.parse("2026-06-01T15:00:00Z");
        ContestAccessService service = serviceAt(liveNow);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        Batch inactive = new Batch();
        inactive.setId("b1");
        inactive.setActive(false);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(inactive));
        Contest contest = new Contest();
        contest.setPublished(true);
        contest.setAudienceType(ContestAudienceType.BATCHES);
        contest.setBatchIds(List.of("b1"));
        contest.setStartAt(START);
        contest.setEndAt(end);
        User actor = user("u1");
        ContestAccessService.UserBatchScope scope = service.scopeForUser("u1");
        assertFalse(service.isEligible(actor, contest, scope, liveNow));
        assertTrue(service.isEligible(actor, contest, scope, end));
    }

    @Test
    void inactiveStudentIsNotEligibleForALiveBatchContest() {
        Instant liveNow = Instant.parse("2026-06-01T14:00:00Z");
        ContestAccessService service = serviceAt(liveNow);
        stubActiveMember();
        Contest contest = new Contest();
        contest.setPublished(true);
        contest.setAudienceType(ContestAudienceType.BATCHES);
        contest.setBatchIds(List.of("b1"));
        contest.setStartAt(START);
        contest.setEndAt(Instant.parse("2026-06-01T15:00:00Z"));
        User actor = user("u1");
        actor.setAccountStatus("INACTIVE");
        ContestAccessService.UserBatchScope scope = service.scopeForUser("u1");
        assertFalse(service.isEligible(actor, contest, scope, liveNow));
    }

    private ContestAccessService serviceAt(Instant instant) {
        return new ContestAccessService(mongo, properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void stubActiveMember() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setActive(true);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static BatchMember member(String batchId, String userId) {
        BatchMember member = new BatchMember();
        member.setBatchId(batchId);
        member.setUserId(userId);
        return member;
    }
}
