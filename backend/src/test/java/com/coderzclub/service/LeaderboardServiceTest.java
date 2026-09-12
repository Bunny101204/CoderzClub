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
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LeaderboardServiceTest {
    @Mock private StringRedisTemplate redis;
    @Mock private UserRepository userRepository;
    @Mock private ObjectMapper objectMapper;
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
        when(userRepository.count()).thenReturn(1L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("7");
        when(values.get("coderzclub:leaderboard:top:7:1")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0, 0L)).thenReturn(Set.of("u1"));
        when(userRepository.findAllById(Set.of("u1"))).thenReturn(List.of(user));

        service.top(1);

        verify(values).set(eq("coderzclub:leaderboard:top:7:1"), anyString(), eq(15L), any());
        service.invalidate();
        verify(values).increment("coderzclub:leaderboard:version");
        verify(redis, never()).keys(anyString());
    }

    @Test
    void redisOutageFallsBackToIndexedMongoPage() {
        User user = user("u1", "alice", 120);
        when(userRepository.count()).thenReturn(1L);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        when(userRepository.findAllByOrderByTotalPointsDesc(any())).thenReturn(new PageImpl<>(List.of(user)));

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
        when(userRepository.count()).thenReturn(2L);
        when(values.get("coderzclub:leaderboard:version")).thenReturn("8");
        when(values.get("coderzclub:leaderboard:top:8:2")).thenReturn(null);
        when(sortedSets.reverseRange("coderzclub:leaderboard", 0, 1L))
            .thenReturn(new LinkedHashSet<>(List.of("u1", "u2")));
        when(userRepository.findAllById(any())).thenReturn(List.of(runnerUp, leader));

        List<LeaderboardEntry> result = service.top(2);

        assertEquals("u1", result.get(0).getId());
        assertEquals(1L, result.get(0).getRank());
        assertEquals("u2", result.get(1).getId());
        assertEquals(2L, result.get(1).getRank());
    }

    private User user(String id, String username, int points) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setTotalPoints(points);
        return user;
    }
}
