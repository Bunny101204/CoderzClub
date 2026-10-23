package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemBundle;
import com.coderzclub.repository.ProblemBundleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemIdentityResolverTest {

    @Test
    void twentyProblemIdsResolveInOneQueryAndKeepOrder() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        List<String> ids = new ArrayList<>();
        List<Problem> shuffled = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add("p" + i);
            Problem problem = new Problem();
            problem.setId("p" + i);
            problem.setNumericId(i + 1);
            shuffled.add(problem);
        }
        Collections.shuffle(shuffled);
        AtomicInteger finds = new AtomicInteger();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenAnswer(invocation -> {
            finds.incrementAndGet();
            Query query = invocation.getArgument(0);
            assertEquals(1, query.getFieldsObject().get("numericId"));
            assertNull(query.getFieldsObject().get("statement"));
            return shuffled;
        });

        ProblemBundle first = bundle("b1", ids.subList(0, 10));
        ProblemBundle second = bundle("b2", ids.subList(10, 20));
        new ProblemIdentityResolver(mongo).applyCanonicalProblemIds(List.of(first, second));

        assertEquals(1, finds.get());
        assertEquals(ids.subList(0, 10), first.getProblemIds());
        assertEquals(ids.subList(10, 20), second.getProblemIds());
        assertEquals(10, first.getTotalProblems());
    }

    @Test
    void numericAliasUsesOneFollowUpQuery() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Problem problem = new Problem();
        problem.setId("abc");
        problem.setNumericId(5);
        AtomicInteger finds = new AtomicInteger();
        when(mongo.find(any(Query.class), eq(Problem.class))).thenAnswer(invocation -> {
            int call = finds.incrementAndGet();
            Query query = invocation.getArgument(0);
            if (call == 1) {
                assertEquals(true, query.getQueryObject().containsKey("_id"));
                return List.of();
            }
            assertTrue(String.valueOf(query.getQueryObject().get("numericId")).contains("5"));
            return List.of(problem);
        });

        ProblemBundle bundle = bundle("b1", List.of("5", "missing"));
        new ProblemIdentityResolver(mongo).applyCanonicalProblemIds(List.of(bundle));

        assertEquals(2, finds.get());
        assertEquals(List.of("abc"), bundle.getProblemIds());
    }

    @Test
    void bundleDetailNormalizesThroughOneBatchQuery() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProblemBundleRepository bundles = mock(ProblemBundleRepository.class);
        List<String> ids = new ArrayList<>();
        List<Problem> shuffled = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add("p" + i);
            Problem problem = new Problem();
            problem.setId("p" + i);
            shuffled.add(problem);
        }
        Collections.shuffle(shuffled);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(shuffled);
        ProblemBundle bundle = bundle("bundle", new ArrayList<>(ids));
        when(bundles.findById("bundle")).thenReturn(Optional.of(bundle));

        ProblemBundleService service = new ProblemBundleService();
        ReflectionTestUtils.setField(service, "problemBundleRepository", bundles);
        ReflectionTestUtils.setField(service, "problemIdentityResolver", new ProblemIdentityResolver(mongo));

        ProblemBundle loaded = service.getBundleById("bundle");
        assertEquals(ids, loaded.getProblemIds());
        verify(mongo, times(1)).find(any(Query.class), eq(Problem.class));
    }

    private static ProblemBundle bundle(String id, List<String> problemIds) {
        ProblemBundle bundle = new ProblemBundle();
        bundle.setId(id);
        bundle.setProblemIds(new ArrayList<>(problemIds));
        return bundle;
    }
}
