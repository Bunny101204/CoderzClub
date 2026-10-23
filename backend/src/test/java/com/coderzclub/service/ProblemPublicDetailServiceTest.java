package com.coderzclub.service;

import com.coderzclub.dto.PublicProblemDetail;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.TestCase;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemPublicDetailServiceTest {
    private final Map<String, String> redisStore = new HashMap<>();
    private final AtomicReference<ProblemAvailability> availability = new AtomicReference<>();
    private MongoTemplate mongo;
    private ProblemRepository repository;
    private ValueOperations<String, String> values;
    private SimpleMeterRegistry registry;
    private ObjectMapper objectMapper;
    private ProblemPublicDetailService service;

    @BeforeEach
    void setUp() {
        redisStore.clear();
        availability.set(null);
        mongo = mock(MongoTemplate.class);
        repository = mock(ProblemRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        values = ops;
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(redis.delete(anyString())).thenAnswer(invocation -> redisStore.remove(invocation.getArgument(0, String.class)) != null);
        when(mongo.findOne(any(Query.class), eq(Problem.class))).thenAnswer(invocation -> probe(invocation.getArgument(0)));
        when(repository.findById("p1")).thenAnswer(invocation -> Optional.of(fullProblem()));
        objectMapper = new ObjectMapper();
        registry = new SimpleMeterRegistry();
        ProblemReadCache cache = new ProblemReadCache(
            redis, objectMapper, mongo, new OperationalMetrics(registry), 600, 600);
        service = new ProblemPublicDetailService(
            mongo, repository, new ProblemAccessService(mock(ContestAccessService.class)), cache);
    }

    @Test
    void publicDetailHasNoHiddenOrAuthorFields() {
        assertThrows(NoSuchFieldException.class, () -> PublicProblemDetail.class.getDeclaredField("hiddenTestCases"));
        assertThrows(NoSuchFieldException.class, () -> PublicProblemDetail.class.getDeclaredField("createdBy"));
    }

    @Test
    void missLoadsMongoAndHitSkipsFullProblemLoad() throws Exception {
        Object first = service.read(null, "p1");
        assertInstanceOf(PublicProblemDetail.class, first);
        assertEquals("Solve it", ((PublicProblemDetail) first).getStatement());
        verify(repository, times(1)).findById("p1");

        Object second = service.read(null, "7");
        assertInstanceOf(PublicProblemDetail.class, second);
        assertEquals("Solve it", ((PublicProblemDetail) second).getStatement());
        verify(repository, times(1)).findById("p1");

        String cached = redisStore.get(ProblemReadCache.detailKey("p1"));
        assertTrue(cached.contains("Solve it"));
        assertFalse(cached.contains("TOP-SECRET"));
        assertFalse(cached.contains("hiddenTestCases"));
        assertFalse(cached.contains("admin-user"));
        assertFalse(objectMapper.writeValueAsString(second).contains("TOP-SECRET"));
        assertEquals(1.0, registry.get("problem.cache.detail").tag("outcome", "miss").counter().count());
        assertEquals(1.0, registry.get("problem.cache.detail").tag("outcome", "hit").counter().count());
    }

    @Test
    void publicToContestOnlyDoesNotReturnStaleCache() {
        assertInstanceOf(PublicProblemDetail.class, service.read(null, "p1"));
        availability.set(ProblemAvailability.CONTEST_ONLY);

        assertNull(service.read(null, "p1"));
        verify(repository, times(2)).findById("p1");
        assertFalse(redisStore.containsKey(ProblemReadCache.detailKey("p1")));
    }

    @Test
    void contestOnlyIsNeverWrittenToPublicCache() {
        availability.set(ProblemAvailability.CONTEST_ONLY);
        assertNull(service.read(null, "p1"));
        verify(values, never()).set(anyString(), anyString(), anyLong(), any());
        assertTrue(redisStore.isEmpty());
    }

    @Test
    void adminContestOnlyResponseIsNotTheStudentCache() {
        availability.set(ProblemAvailability.CONTEST_ONLY);
        Object body = service.read(admin(), "p1");
        assertInstanceOf(Problem.class, body);
        assertNull(((Problem) body).getHiddenTestCases());
        verify(values, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void redisFailureStillReturnsMongoDetail() {
        when(values.get(anyString())).thenThrow(new RuntimeException("redis down"));
        doAnswer(invocation -> {
            throw new RuntimeException("redis down");
        }).when(values).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        Object body = service.read(null, "p1");
        assertInstanceOf(PublicProblemDetail.class, body);
        assertEquals("Solve it", ((PublicProblemDetail) body).getStatement());
        assertEquals(1.0, registry.get("problem.cache.detail").tag("outcome", "fallback").counter().count());
    }

    @Test
    void poisonedHiddenTestsInRedisAreNotReturned() throws Exception {
        redisStore.put(ProblemReadCache.detailKey("p1"),
            "{\"id\":\"p1\",\"title\":\"Cached\",\"statement\":\"Cached statement\",\"availability\":\"PUBLIC\","
                + "\"hiddenTestCases\":[{\"input\":\"TOP-SECRET\",\"output\":\"no\"}],\"createdBy\":\"admin-user\"}");

        Object body = service.read(null, "p1");
        assertInstanceOf(PublicProblemDetail.class, body);
        assertFalse(objectMapper.writeValueAsString(body).contains("TOP-SECRET"));
        verify(repository, never()).findById(anyString());
    }

    private Problem probe(Query query) {
        Object id = query.getQueryObject().get("_id");
        Object numericId = query.getQueryObject().get("numericId");
        if (!"p1".equals(String.valueOf(id)) && !Integer.valueOf(7).equals(numericId)) {
            return null;
        }
        Problem probe = new Problem();
        probe.setId("p1");
        probe.setNumericId(7);
        probe.setAvailability(availability.get());
        return probe;
    }

    private Problem fullProblem() {
        Problem problem = new Problem();
        problem.setId("p1");
        problem.setNumericId(7);
        problem.setTitle("Two Sum");
        problem.setStatement("Solve it");
        problem.setCreatedBy("admin-user");
        problem.setAvailability(availability.get());
        problem.setPublicTestCases(List.of(new TestCase("1", "2", "shown")));
        problem.setHiddenTestCases(List.of(new TestCase("TOP-SECRET", "no", "hidden")));
        return problem;
    }

    private static User admin() {
        User user = new User();
        user.setId("admin");
        user.setUsername("admin");
        user.setRole("ADMIN");
        user.setAccountStatus("ACTIVE");
        return user;
    }
}
