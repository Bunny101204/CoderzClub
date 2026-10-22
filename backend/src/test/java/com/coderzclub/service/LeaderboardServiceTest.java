package com.coderzclub.service;

import com.coderzclub.dto.LeaderboardEntry;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {
    @Mock private StringRedisTemplate redis;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper unusedMapper;
    @Mock private ValueOperations<String, String> values;
    @Mock private ZSetOperations<String, String> sortedSets;

    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(sortedSets);
        service = new LeaderboardService();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "redis", redis);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userRepository", userRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "cacheTtlSeconds", 15L);
    }

    @Test
    void updateUsesAtomicSortedSetWriteAndNeverScansKeys() {
        User user = user("u1", "alice", 120);
        when(redis.execute(any(SessionCallback.class))).thenReturn(List.of());

        service.update(user);

        verify(redis).execute(any(SessionCallback.class));
        verify(redis, never()).keys(anyString());
    }

    @Test
    void cacheUsesCurrentVersionSoVersionIncrementInvalidatesOldTopN() throws Exception {
        User user = user("u1", "alice", 120);
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(1L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("7");
        when(values.get("coderzclub:leaderboard:top:7:0:1")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0L, 0L)).thenReturn(Set.of("u1"));
        when(userRepository.findAllById(Set.of("u1"))).thenReturn(List.of(user));

        service.top(1);

        verify(values).set(eq("coderzclub:leaderboard:top:7:0:1"), anyString(), eq(15L), any());
        service.invalidate();
        verify(values).increment("coderzclub:leaderboard:version");
        verify(redis, never()).keys(anyString());
    }

    @Test
    void redisOutageFallsBackToIndexedMongoPage() {
        User user = user("u1", "alice", 120);
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(1L);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        when(userRepository.findByAccountStatusNot(eq("DELETED"), any())).thenReturn(new PageImpl<>(List.of(user)));

        List<LeaderboardEntry> result = service.top(1);

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).getId());
        assertEquals(1L, result.get(0).getRank());
    }

    @Test
    void scoreUpdateIsSentToSortedSetAndVersionIsIncremented() {
        User user = user("u1", "alice", 250);
        when(redis.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(redis);
        });
        when(redis.exec()).thenReturn(List.of());

        service.update(user);

        verify(redis).execute(any(SessionCallback.class));
        verify(sortedSets).add("coderzclub:leaderboard", "u1", 250.0);
        verify(values).increment("coderzclub:leaderboard:version");
        verify(redis, never()).keys(anyString());
    }

    @Test
    void leaderboardKeepsSortedSetOrderAfterScoreUpdate() {
        User leader = user("u1", "alice", 250);
        User runnerUp = user("u2", "bob", 200);
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(2L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("8");
        when(values.get("coderzclub:leaderboard:top:8:0:2")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0L, 1L))
            .thenReturn(new LinkedHashSet<>(List.of("u1", "u2")));
        when(userRepository.findAllById(any())).thenReturn(List.of(runnerUp, leader));

        List<LeaderboardEntry> result = service.top(2);

        assertEquals("u1", result.get(0).getId());
        assertEquals(1L, result.get(0).getRank());
        assertEquals("u2", result.get(1).getId());
        assertEquals(2L, result.get(1).getRank());
    }

    @Test
    void deletedUsersAreExcludedFromHydratedLeaderboard() {
        User active = user("u1", "alice", 100);
        User deleted = user("u2", "bob", 90);
        deleted.setAccountStatus("DELETED");
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(1L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("1");
        when(values.get("coderzclub:leaderboard:top:1:0:2")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0L, 1L))
            .thenReturn(new LinkedHashSet<>(List.of("u1", "u2")));
        when(userRepository.findAllById(any())).thenReturn(List.of(active, deleted));
        when(userRepository.findByAccountStatusNot(eq("DELETED"), any()))
            .thenReturn(new PageImpl<>(List.of(active)));

        List<LeaderboardEntry> result = service.top(2);

        assertTrue(result.stream().noneMatch(entry -> "bob".equals(entry.getUsername())));
        assertTrue(result.stream().noneMatch(entry -> "u2".equals(entry.getId())));
    }

    @Test
    void staleCachedDeletedUsernameIsNotReturned() throws Exception {
        User active = user("u1", "alice", 100);
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(1L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("3");
        String stale = new ObjectMapper().writeValueAsString(List.of(
            Map.of("id", "u9", "username", "deleted-u9", "totalPoints", 500, "problemsSolved", 9, "rank", 1),
            Map.of("id", "u1", "username", "alice", "totalPoints", 100, "problemsSolved", 1, "rank", 2)
        ));
        when(values.get("coderzclub:leaderboard:top:3:0:2")).thenReturn(stale);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0L, 1L)).thenReturn(Set.of("u1"));
        when(userRepository.findAllById(any())).thenReturn(List.of(active));

        List<LeaderboardEntry> result = service.top(2);

        assertTrue(result.stream().noneMatch(entry -> entry.getUsername() != null && entry.getUsername().startsWith("deleted-")));
        assertEquals("alice", result.get(0).getUsername());
    }

    @Test
    void equalScoresKeepDeterministicIdOrderFromRedisMembers() {
        User a = user("aaa", "ann", 50);
        User b = user("bbb", "ben", 50);
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(2L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("4");
        when(values.get("coderzclub:leaderboard:top:4:0:2")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0L, 1L))
            .thenReturn(new LinkedHashSet<>(List.of("aaa", "bbb")));
        when(userRepository.findAllById(any())).thenReturn(List.of(b, a));

        List<LeaderboardEntry> result = service.top(2);

        assertEquals("aaa", result.get(0).getId());
        assertEquals("bbb", result.get(1).getId());
        assertEquals(1L, result.get(0).getRank());
        assertEquals(2L, result.get(1).getRank());
    }

    @Test
    void pageSizeIsCapped() {
        when(userRepository.countByAccountStatusNot("DELETED")).thenReturn(0L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("1");
        when(values.get(anyString())).thenReturn(null);
        when(sortedSets.reverseRange(eq("coderzclub:leaderboard"), eq(0L), eq(49L))).thenReturn(Set.of());
        when(userRepository.findByAccountStatusNot(eq("DELETED"), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));

        Map<String, Object> body = service.page(0, 1000);
        assertEquals(50, body.get("size"));
        assertEquals(0, body.get("page"));
    }

    @Test
    void deleteRemovesParticipantFromSortedSet() {
        when(redis.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(redis);
        });
        when(redis.exec()).thenReturn(List.of());
        service.removeParticipant("u1");
        verify(sortedSets).remove("coderzclub:leaderboard", "u1");
        verify(values).increment("coderzclub:leaderboard:version");
    }

    private User user(String id, String username, int points) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setTotalPoints(points);
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
