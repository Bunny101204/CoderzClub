package com.coderzclub.service;

import com.coderzclub.service.ProblemNumericIdAllocator.ProblemNumericIdCounter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ProblemNumericIdAllocatorTest {

    @Test
    void concurrentAllocationsUseAtomicCounterOperation() throws Exception {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        AtomicInteger sequence = new AtomicInteger();
        when(mongoTemplate.findAndModify(
            ArgumentMatchers.any(Query.class),
            ArgumentMatchers.any(Update.class),
            ArgumentMatchers.any(FindAndModifyOptions.class),
            ArgumentMatchers.eq(ProblemNumericIdCounter.class),
            ArgumentMatchers.eq("counters")))
            .thenAnswer(invocation -> {
                ProblemNumericIdCounter counter = new ProblemNumericIdCounter();
                counter.setSeq(sequence.incrementAndGet());
                return counter;
            });

        ProblemNumericIdAllocator allocator = new ProblemNumericIdAllocator(mongoTemplate);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Set<Integer> allocated = ConcurrentHashMap.newKeySet();
            Set<Future<?>> futures = ConcurrentHashMap.newKeySet();
            IntStream.range(0, 32).forEach(ignored -> futures.add(executor.submit(() -> allocated.add(allocator.allocateNext()))));
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            assertEquals(32, allocated.size());
            verify(mongoTemplate, org.mockito.Mockito.times(32)).findAndModify(
                ArgumentMatchers.any(Query.class),
                ArgumentMatchers.any(Update.class),
                ArgumentMatchers.any(FindAndModifyOptions.class),
                ArgumentMatchers.eq(ProblemNumericIdCounter.class),
                ArgumentMatchers.eq("counters"));
        } finally {
            executor.shutdownNow();
        }
    }
}
