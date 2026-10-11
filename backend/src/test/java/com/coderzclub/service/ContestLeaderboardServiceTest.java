package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestLeaderboardServiceTest {
    private static final Instant START = Instant.parse("2026-06-01T13:00:00Z");
    private static final Instant LIVE = Instant.parse("2026-06-01T14:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T15:00:00Z");

    private MongoTemplate mongo;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ContestLeaderboardService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ContestAccessService access = new ContestAccessService(
            mongo, new ContestProperties(), Clock.fixed(LIVE, ZoneOffset.UTC));
        service = new ContestLeaderboardService(
            mongo, access, Clock.fixed(LIVE, ZoneOffset.UTC), redis, new ObjectMapper(),
            new OperationalMetrics(new SimpleMeterRegistry()), 5);
    }

    @Test
    void outsiderIsNotFound() {
        stubContest(liveContest());
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of());
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> service.page(user("outsider", "x"), "c1", 0, 20));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(mongo, never()).find(any(Query.class), eq(ContestScore.class));
    }

    @Test
    void upcomingEligibleIsNotLive() {
        Contest upcoming = liveContest();
        upcoming.setStartAt(START);
        ContestLeaderboardService upcomingService = serviceAt(START.minusSeconds(1));
        when(mongo.findById("c1", Contest.class)).thenReturn(upcoming);
        stubActiveMember();
        ContestAdmissionException ex = assertThrows(ContestAdmissionException.class,
            () -> upcomingService.page(user("u1", "alice"), "c1", 0, 20));
        assertEquals("CONTEST_NOT_LIVE", ex.getError());
    }

    @Test
    void liveEligibleReadsIndexedMongoPage() {
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenReturn(null);
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(2L);
        ContestScore first = score("u-b", 50, 1);
        ContestScore second = score("u-a", 50, 1);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of(first, second));
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of(user("u-b", "bob"), user("u-a", "ann")));

        Map<String, Object> body = service.page(user("u1", "alice"), "c1", 0, 20);

        assertEquals("POINTS_FIRST_ACCEPTED", body.get("scoringMode"));
        assertEquals(2L, body.get("totalItems"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        assertEquals(1, entries.get(0).get("rank"));
        assertEquals("bob", entries.get(0).get("username"));
        ArgumentCaptor<Query> pages = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(pages.capture(), eq(ContestScore.class));
        Query pageQuery = pages.getValue();
        assertEquals(0L, pageQuery.getSkip());
        assertEquals(20, pageQuery.getLimit());
        assertTrue(String.valueOf(pageQuery.getSortObject()).contains("totalPoints"));
        assertTrue(String.valueOf(pageQuery.getSortObject()).contains("solvedCount"));
        assertTrue(String.valueOf(pageQuery.getSortObject()).contains("userId"));
        verify(mongo).find(any(Query.class), eq(User.class));
    }

    @Test
    void sizeIsCappedAt50AndPaginationUsesSkip() {
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenReturn(null);
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(100L);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of());
        service.page(user("u1", "alice"), "c1", 2, 999);
        ArgumentCaptor<Query> pages = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(pages.capture(), eq(ContestScore.class));
        assertEquals(100L, pages.getValue().getSkip());
        assertEquals(50, pages.getValue().getLimit());
    }

    @Test
    void endedEligibleCanRead() {
        ContestLeaderboardService ended = serviceAt(END);
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenReturn(null);
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of());
        Map<String, Object> body = ended.page(user("u1", "alice"), "c1", 0, 20);
        assertEquals(0L, body.get("totalItems"));
    }

    @Test
    void redisHitSkipsMongoScoreQuery() throws Exception {
        stubContest(liveContest());
        stubActiveMember();
        String json = "{\"entries\":[{\"rank\":1,\"username\":\"bob\",\"totalPoints\":10,\"solvedCount\":1}],\"currentPage\":0,\"totalPages\":1,\"totalItems\":1,\"scoringMode\":\"POINTS_FIRST_ACCEPTED\"}";
        when(values.get("coderzclub:contest-leaderboard:c1:0:20")).thenReturn(json);
        Map<String, Object> body = service.page(user("u1", "alice"), "c1", 0, 20);
        assertEquals(1, ((List<?>) body.get("entries")).size());
        verify(mongo, never()).find(any(Query.class), eq(ContestScore.class));
    }

    @Test
    void redisFailureFallsBackToMongo() {
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of());
        Map<String, Object> body = service.page(user("u1", "alice"), "c1", 0, 20);
        assertEquals(0L, body.get("totalItems"));
        verify(mongo).find(any(Query.class), eq(ContestScore.class));
    }

    @Test
    void redisWriteFailureDoesNotAffectRead() {
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenReturn(null);
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(0L);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of());
        doThrow(new RuntimeException("redis down"))
            .when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        Map<String, Object> body = service.page(user("u1", "alice"), "c1", 0, 20);
        assertEquals(0L, body.get("totalItems"));
    }

    @Test
    void missingUserGetsPlaceholder() {
        stubContest(liveContest());
        stubActiveMember();
        when(values.get(anyString())).thenReturn(null);
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(1L);
        when(mongo.find(any(Query.class), eq(ContestScore.class))).thenReturn(List.of(score("gone", 3, 1)));
        when(mongo.find(any(Query.class), eq(User.class))).thenReturn(List.of());
        Map<String, Object> body = service.page(user("u1", "alice"), "c1", 0, 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        assertEquals("Unknown user", entries.get(0).get("username"));
    }

    @Test
    void cachedPageDoesNotLeakAliceCurrentUserToBob() throws Exception {
        stubContest(liveContest());
        stubActiveMember();
        when(mongo.count(any(Query.class), eq(ContestScore.class))).thenReturn(2L);
        when(mongo.find(any(Query.class), eq(ContestScore.class)))
            .thenReturn(List.of(score("u-alice", 20, 1), score("u-bob", 10, 1)));
        when(mongo.find(any(Query.class), eq(User.class)))
            .thenReturn(List.of(user("u-alice", "alice"), user("u-bob", "bob")));
        ArgumentCaptor<String> cached = ArgumentCaptor.forClass(String.class);
        when(values.get(anyString())).thenReturn(null);

        Map<String, Object> alicePage = service.page(user("u-alice", "alice"), "c1", 0, 20);
        verify(values).set(eq("coderzclub:contest-leaderboard:c1:0:20"), cached.capture(), eq(5L), eq(TimeUnit.SECONDS));
        String json = cached.getValue();
        assertTrue(!json.contains("currentUser"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> aliceEntries = (List<Map<String, Object>>) alicePage.get("entries");
        assertEquals(Boolean.TRUE, aliceEntries.get(0).get("currentUser"));
        assertEquals(Boolean.FALSE, aliceEntries.get(1).get("currentUser"));

        when(values.get("coderzclub:contest-leaderboard:c1:0:20")).thenReturn(json);
        Map<String, Object> bobPage = service.page(user("u-bob", "bob"), "c1", 0, 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bobEntries = (List<Map<String, Object>>) bobPage.get("entries");
        assertEquals("alice", bobEntries.get(0).get("username"));
        assertEquals(Boolean.FALSE, bobEntries.get(0).get("currentUser"));
        assertEquals("bob", bobEntries.get(1).get("username"));
        assertEquals(Boolean.TRUE, bobEntries.get(1).get("currentUser"));
        verify(mongo, org.mockito.Mockito.times(1)).find(any(Query.class), eq(ContestScore.class));
    }

    private ContestLeaderboardService serviceAt(Instant now) {
        ContestAccessService access = new ContestAccessService(mongo, new ContestProperties(), Clock.fixed(now, ZoneOffset.UTC));
        return new ContestLeaderboardService(
            mongo, access, Clock.fixed(now, ZoneOffset.UTC), redis, new ObjectMapper(),
            new OperationalMetrics(new SimpleMeterRegistry()), 5);
    }

    private void stubContest(Contest contest) {
        when(mongo.findById("c1", Contest.class)).thenReturn(contest);
    }

    private void stubActiveMember() {
        when(mongo.find(any(Query.class), eq(BatchMember.class))).thenReturn(List.of(member("b1", "u1")));
        Batch batch = new Batch();
        batch.setId("b1");
        batch.setActive(true);
        when(mongo.find(any(Query.class), eq(Batch.class))).thenReturn(List.of(batch));
    }

    private static Contest liveContest() {
        Contest contest = new Contest();
        contest.setId("c1");
        contest.setPublished(true);
        contest.setAudienceType(ContestAudienceType.BATCHES);
        contest.setBatchIds(List.of("b1"));
        contest.setStartAt(START);
        contest.setEndAt(END);
        return contest;
    }

    private static ContestScore score(String userId, int points, int solved) {
        ContestScore score = new ContestScore();
        score.setContestId("c1");
        score.setUserId(userId);
        score.setTotalPoints(points);
        score.setSolvedCount(solved);
        return score;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
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
