package com.coderzclub.service;

import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestProblemSolve;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.Submission;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestScoringConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:01:00Z");

    private InMemoryContestStore store;
    private ContestProblemSnapshotService snapshots;
    private ContestScoringService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryContestStore();
        snapshots = mock(ContestProblemSnapshotService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.upsert(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenAnswer(invocation -> store.upsertScore(invocation.getArgument(0), invocation.getArgument(1)));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenAnswer(invocation -> store.awardIfNew(invocation.getArgument(0), invocation.getArgument(1)));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Submission.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.insert(any(ContestProblemSolve.class)))
            .thenAnswer(invocation -> store.insertSolve(invocation.getArgument(0)));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSolve.class)))
            .thenAnswer(invocation -> store.findSolve(invocation.getArgument(0)));
        when(mongo.exists(any(Query.class), eq(ContestScore.class)))
            .thenAnswer(invocation -> store.alreadyAwarded(invocation.getArgument(0)));
        service = new ContestScoringService(
            mongo, snapshots, Clock.fixed(NOW, ZoneOffset.UTC), new OperationalMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void concurrentAcceptedDifferentProblemsCreateOneScoreAndSumPoints() throws Exception {
        stubSnapshot("snap-a", "pA", 10);
        stubSnapshot("snap-b", "pB", 20);
        runConcurrent(
            () -> service.apply(accepted("sub-a", "pA", "snap-a")),
            () -> service.apply(accepted("sub-b", "pB", "snap-b"))
        );
        ContestScore score = store.onlyScore();
        assertEquals(30, score.getTotalPoints());
        assertEquals(2, score.getSolvedCount());
        assertEquals(2, score.getSolvedProblemIds().size());
        assertEquals(2, store.solves.size());
    }

    @Test
    void concurrentWrongAnswerAndAcceptedCreateOneScoreAndAwardOnce() throws Exception {
        stubSnapshot("snap-a", "pA", 15);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runConcurrent(
            () -> {
                try {
                    service.apply(verdict("sub-wa", "pZ", "snap-z", "WRONG_ANSWER"));
                } catch (RuntimeException ex) {
                    failure.set(ex);
                    throw ex;
                }
            },
            () -> {
                try {
                    service.apply(accepted("sub-ac", "pA", "snap-a"));
                } catch (RuntimeException ex) {
                    failure.set(ex);
                    throw ex;
                }
            }
        );
        assertNull(failure.get());
        ContestScore score = store.onlyScore();
        assertEquals(15, score.getTotalPoints());
        assertEquals(1, score.getSolvedCount());
        assertEquals(1, store.solves.size());
    }

    @Test
    void concurrentAcceptedSameProblemAwardsOnceAndApplies() throws Exception {
        stubSnapshot("snap-a", "pA", 40);
        Submission first = accepted("sub-1", "pA", "snap-a");
        Submission second = accepted("sub-2", "pA", "snap-a");
        runConcurrent(() -> service.apply(first), () -> service.apply(second));
        ContestScore score = store.onlyScore();
        assertEquals(40, score.getTotalPoints());
        assertEquals(1, score.getSolvedCount());
        assertEquals(1, store.solves.size());
        assertEquals(ContestScoringStatus.APPLIED, first.getContestScoringStatus());
        assertEquals(ContestScoringStatus.APPLIED, second.getContestScoringStatus());
    }

    @Test
    void concurrentRepairOfSamePendingSubmissionAwardsOnce() throws Exception {
        stubSnapshot("snap-a", "pA", 8);
        Submission pending = accepted("sub-same", "pA", "snap-a");
        runConcurrent(() -> service.apply(pending), () -> service.apply(pending));
        ContestScore score = store.onlyScore();
        assertEquals(8, score.getTotalPoints());
        assertEquals(1, score.getSolvedCount());
        assertEquals(1, store.solves.size());
        assertEquals(ContestScoringStatus.APPLIED, pending.getContestScoringStatus());
    }

    private void stubSnapshot(String id, String problemId, int points) {
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setId(id);
        snapshot.setContestId("c1");
        snapshot.setProblemId(problemId);
        snapshot.setPoints(points);
        when(snapshots.findById(id)).thenReturn(snapshot);
    }

    private static Submission accepted(String id, String problemId, String snapshotId) {
        return verdict(id, problemId, snapshotId, "ACCEPTED");
    }

    private static Submission verdict(String id, String problemId, String snapshotId, String result) {
        return Submission.builder()
            .id(id)
            .userId("u1")
            .problemId(problemId)
            .submissionJobId("job-" + id)
            .result(result)
            .contestId("c1")
            .contestSubmittedAt(NOW)
            .contestProblemSnapshotId(snapshotId)
            .contestScoringStatus(ContestScoringStatus.PENDING)
            .build();
    }

    private static void runConcurrent(ThrowingRunnable left, ThrowingRunnable right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<?> first = pool.submit(() -> {
            start.await();
            left.run();
            return null;
        });
        Future<?> second = pool.submit(() -> {
            start.await();
            right.run();
            return null;
        });
        start.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Document-atomic participant scores plus unique solve/score indexes.
     */
    static final class InMemoryContestStore {
        final Map<String, ContestScore> scores = new ConcurrentHashMap<>();
        final Map<String, ContestProblemSolve> solves = new ConcurrentHashMap<>();

        UpdateResult upsertScore(Query query, Update update) {
            Document filter = query.getQueryObject();
            String key = scoreKey(filter.getString("contestId"), filter.getString("userId"));
            ContestScore created = new ContestScore();
            created.setContestId(filter.getString("contestId"));
            created.setUserId(filter.getString("userId"));
            created.setTotalPoints(0);
            created.setSolvedCount(0);
            created.setSolvedProblemIds(new ArrayList<>());
            ContestScore existing = scores.putIfAbsent(key, created);
            if (existing != null) {
                throw new DuplicateKeyException("score unique " + key);
            }
            return UpdateResult.acknowledged(0, 0L, null);
        }

        UpdateResult awardIfNew(Query query, Update update) {
            Document filter = query.getQueryObject();
            String contestId = filter.getString("contestId");
            String userId = filter.getString("userId");
            String problemId = neValue(filter.get("solvedProblemIds"));
            int points = incPoints(update);
            String key = scoreKey(contestId, userId);
            synchronized (lock(key)) {
                ContestScore score = scores.get(key);
                if (score == null || score.getSolvedProblemIds().contains(problemId)) {
                    return UpdateResult.acknowledged(0, 0L, null);
                }
                score.setTotalPoints(score.getTotalPoints() + points);
                score.setSolvedCount(score.getSolvedCount() + 1);
                score.getSolvedProblemIds().add(problemId);
                return UpdateResult.acknowledged(1, 1L, null);
            }
        }

        ContestProblemSolve insertSolve(ContestProblemSolve solve) {
            String key = solveKey(solve.getContestId(), solve.getUserId(), solve.getProblemId());
            ContestProblemSolve existing = solves.putIfAbsent(key, solve);
            if (existing != null) {
                throw new DuplicateKeyException("solve unique " + key);
            }
            return solve;
        }

        ContestProblemSolve findSolve(Query query) {
            Document filter = query.getQueryObject();
            return solves.get(solveKey(filter.getString("contestId"), filter.getString("userId"), filter.getString("problemId")));
        }

        boolean alreadyAwarded(Query query) {
            Document filter = query.getQueryObject();
            ContestScore score = scores.get(scoreKey(filter.getString("contestId"), filter.getString("userId")));
            Object problemId = filter.get("solvedProblemIds");
            return score != null && problemId instanceof String id && score.getSolvedProblemIds().contains(id);
        }

        ContestScore onlyScore() {
            assertEquals(1, scores.size());
            return scores.values().iterator().next();
        }

        private static String scoreKey(String contestId, String userId) {
            return contestId + "|" + userId;
        }

        private static String solveKey(String contestId, String userId, String problemId) {
            return contestId + "|" + userId + "|" + problemId;
        }

        private static Object lock(String key) {
            return key.intern();
        }

        private static String neValue(Object solvedFilter) {
            if (solvedFilter instanceof Document document) {
                return String.valueOf(document.get("$ne"));
            }
            return null;
        }

        private static int incPoints(Update update) {
            Document inc = (Document) update.getUpdateObject().get("$inc");
            return inc.getInteger("totalPoints", 0);
        }
    }
}
