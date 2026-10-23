package com.coderzclub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemReadCacheTest {
    private final Map<String, String> redisStore = new HashMap<>();
    private MongoTemplate mongo;
    private ProblemReadCache cache;

    @BeforeEach
    void setUp() {
        redisStore.clear();
        mongo = mock(MongoTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(values.setIfAbsent(anyString(), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (redisStore.containsKey(key)) {
                return false;
            }
            redisStore.put(key, invocation.getArgument(1));
            return true;
        });
        when(values.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(redisStore.getOrDefault(key, "0")) + 1;
            redisStore.put(key, Long.toString(next));
            return next;
        });
        cache = new ProblemReadCache(redis, new ObjectMapper(), mongo, new OperationalMetrics(new SimpleMeterRegistry()), 600, 600);
    }

    @Test
    void listCacheKeepsBoundedDefaultPagesAndDropsStaleVersions() {
        assertTrue(cache.listCacheable(null, null, null, null, false, 0, 20));
        assertTrue(cache.listCacheable("EASY", null, null, null, false, 4, 10));
        assertFalse(cache.listCacheable(null, null, null, "two sum", false, 0, 20));
        assertFalse(cache.listCacheable(null, null, List.of("arrays"), null, false, 0, 20));
        assertFalse(cache.listCacheable(null, "ALGORITHMS", null, null, false, 0, 20));
        assertFalse(cache.listCacheable(null, null, null, null, true, 0, 20));
        assertFalse(cache.listCacheable(null, null, null, null, false, 5, 20));
        assertFalse(cache.listCacheable(null, null, null, null, false, 0, 100));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("problems", List.of(Map.of("id", "p1", "title", "Two Sum")));
        body.put("totalItems", 1);
        when(mongo.find(any(Query.class), eq(com.coderzclub.model.Problem.class))).thenAnswer(invocation -> {
            com.coderzclub.model.Problem problem = new com.coderzclub.model.Problem();
            problem.setId("p1");
            return List.of(problem);
        });
        cache.writeList(false, 0, 20, null, body);
        String stored = redisStore.values().stream().filter(value -> value.contains("Two Sum")).findFirst().orElseThrow();
        assertFalse(stored.contains("solved"));
        assertFalse(stored.contains("draft"));
        assertFalse(stored.contains("hiddenTestCases"));

        assertEquals("Two Sum", problemTitle(cache.readList(false, 0, 20, null)));
        cache.bumpListVersion();
        assertNull(cache.readList(false, 0, 20, null));
    }

    @Test
    void practiceListIsNotServedAfterProblemBecomesContestOnly() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("problems", List.of(Map.of("id", "p1", "title", "Secret")));
        body.put("totalItems", 1);
        when(mongo.find(any(Query.class), eq(com.coderzclub.model.Problem.class))).thenReturn(List.of());
        cache.writeList(false, 0, 10, null, body);
        assertNull(cache.readList(false, 0, 10, null));
    }

    @Test
    void listRedisFailureFallsOpen() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RuntimeException("redis down"));
        ProblemReadCache failing = new ProblemReadCache(
            redis, new ObjectMapper(), mongo, new OperationalMetrics(new SimpleMeterRegistry()), 600, 600);
        assertNull(failing.readList(false, 0, 20, null));
    }

    @SuppressWarnings("unchecked")
    private static String problemTitle(Map<String, Object> body) {
        List<Map<String, Object>> problems = (List<Map<String, Object>>) body.get("problems");
        return String.valueOf(problems.get(0).get("title"));
    }
}
