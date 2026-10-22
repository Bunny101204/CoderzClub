package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemAccessServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T15:00:00Z");

    private MongoTemplate mongo;
    private ContestProperties properties;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        properties = new ContestProperties();
    }

    @Test
    void missingAvailabilityIsPublicForListDetailAndPractice() {
        Problem problem = problem("p1", null);
        ProblemAccessService access = accessAt(NOW);
        User student = user("u1");
        assertTrue(ProblemAvailability.isPublic(problem));
        assertTrue(access.canView(student, problem));
        assertTrue(access.canView(null, problem));
        access.requirePracticeExecute(student, problem);
        Query query = new Query();
        access.applyPracticeListingFilter(query, student);
        assertTrue(String.valueOf(query.getQueryObject()).contains("CONTEST_ONLY"));
        verify(mongo, never()).exists(any(Query.class), eq(Contest.class));
    }

    @Test
    void publicProblemStaysVisibleAndExecutable() {
        Problem problem = problem("p1", ProblemAvailability.PUBLIC);
        ProblemAccessService access = accessAt(NOW);
        assertTrue(access.canView(user("u1"), problem));
        access.requirePracticeExecute(user("u1"), problem);
    }

    @Test
    void contestOnlyIsOmittedFromNormalListingTotalsQuery() {
        ProblemAccessService access = accessAt(NOW);
        Query query = new Query();
        access.applyPracticeListingFilter(query, user("u1"));
        Document body = query.getQueryObject();
        Document availability = (Document) body.get("availability");
        assertEquals("CONTEST_ONLY", String.valueOf(availability.get("$ne")));
    }

    @Test
    void adminListingDoesNotExcludeContestOnly() {
        ProblemAccessService access = accessAt(NOW);
        Query query = new Query();
        access.applyPracticeListingFilter(query, admin());
        assertFalse(String.valueOf(query.getQueryObject()).contains("CONTEST_ONLY"));
    }

    @Test
    void adminCanViewContestOnly() {
        ProblemAccessService access = accessAt(NOW);
        assertTrue(access.canView(admin(), problem("secret", ProblemAvailability.CONTEST_ONLY)));
        verify(mongo, never()).exists(any(Query.class), eq(Contest.class));
    }

    @Test
    void outsiderReceivesNotFoundForContestOnly() {
        ProblemAccessService access = accessAt(START);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        Problem secret = problem("secret", ProblemAvailability.CONTEST_ONLY);
        assertFalse(access.canView(user("outsider"), secret));
        assertFalse(access.canView(null, secret));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> access.requireViewable(user("outsider"), secret));
        assertEquals(404, ex.getStatusCode().value());
        verify(mongo, never()).exists(any(Query.class), eq(Contest.class));
    }

    @Test
    void eligibleUserBeforeStartCannotView() {
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(false);
        ProblemAccessService access = accessAt(NOW);
        Problem secret = problem("secret", ProblemAvailability.CONTEST_ONLY);
        assertFalse(access.canView(user("u1"), secret));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("secret"));
        assertTrue(query.contains("startAt"));
    }

    @Test
    void eligibleUserAtExactStartCanView() {
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        ProblemAccessService access = accessAt(START);
        Problem secret = problem("secret", ProblemAvailability.CONTEST_ONLY);
        assertTrue(access.canView(user("u1"), secret));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("startAt"));
        assertTrue(query.contains("2026-06-01T13:00:00Z"));
    }

    @Test
    void eligibleUserDuringLiveCanView() {
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        Instant live = START.plusSeconds(60);
        ProblemAccessService access = accessAt(live);
        assertTrue(access.canView(user("u1"), problem("secret", ProblemAvailability.CONTEST_ONLY)));
    }

    @Test
    void eligibleUserAfterEndedCanView() {
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        ProblemAccessService access = accessAt(END);
        assertTrue(access.canView(user("u1"), problem("secret", ProblemAvailability.CONTEST_ONLY)));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("endAt"));
    }

    @Test
    void allUsersFlagCanGrantLiveOrEndedContestOnlyProblem() {
        properties.setGlobalEnabled(true);
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        ProblemAccessService access = accessAt(START);
        assertTrue(access.canView(user("u1"), problem("secret", ProblemAvailability.CONTEST_ONLY)));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertTrue(query.contains("ALL_USERS"));
        assertTrue(query.contains("startAt"));
    }

    @Test
    void inactiveBatchDoesNotGrantLiveProblemAccess() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        Batch inactive = new Batch();
        inactive.setId("b1");
        inactive.setActive(false);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(inactive));
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(false);
        ProblemAccessService access = accessAt(START);
        assertFalse(access.canView(user("u1"), problem("secret", ProblemAvailability.CONTEST_ONLY)));
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(captor.capture(), eq(Contest.class));
        String query = String.valueOf(captor.getValue().getQueryObject());
        assertFalse(query.contains("startAt"));
        assertTrue(query.contains("endAt"));
    }

    @Test
    void practiceExecuteRejectsContestOnlyForStudentsEvenWhenReadable() {
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        ProblemAccessService access = accessAt(START);
        Problem secret = problem("secret", ProblemAvailability.CONTEST_ONLY);
        assertTrue(access.canView(user("u1"), secret));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> access.requirePracticeExecute(user("u1"), secret));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void adminMayPracticeExecuteContestOnly() {
        ProblemAccessService access = accessAt(NOW);
        access.requirePracticeExecute(admin(), problem("secret", ProblemAvailability.CONTEST_ONLY));
    }

    @Test
    void bundleViewHidesContestOnlyFromNonAdmin() {
        ProblemAccessService access = accessAt(START);
        stubActiveMember();
        when(mongo.exists(any(Query.class), eq(Contest.class))).thenReturn(true);
        Problem secret = problem("secret", ProblemAvailability.CONTEST_ONLY);
        assertTrue(access.canView(user("u1"), secret));
        assertFalse(access.canViewInBundle(user("u1"), secret));
        assertTrue(access.canViewInBundle(admin(), secret));
        assertTrue(access.canViewInBundle(user("u1"), problem("p1", null)));
    }

    private ProblemAccessService accessAt(Instant instant) {
        ContestAccessService contestAccess = new ContestAccessService(
            mongo, properties, Clock.fixed(instant, ZoneOffset.UTC));
        return new ProblemAccessService(contestAccess);
    }

    private void stubActiveMember() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setActive(true);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
    }

    private static Problem problem(String id, ProblemAvailability availability) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle("Two Sum");
        problem.setAvailability(availability);
        return problem;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setRole("USER");
        user.setAccountStatus("ACTIVE");
        return user;
    }

    private static User admin() {
        User user = user("admin");
        user.setRole("ADMIN");
        return user;
    }

    private static BatchMember member(String batchId, String userId) {
        BatchMember member = new BatchMember();
        member.setBatchId(batchId);
        member.setUserId(userId);
        return member;
    }
}
