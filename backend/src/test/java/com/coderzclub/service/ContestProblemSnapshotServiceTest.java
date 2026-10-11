package com.coderzclub.service;

import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ExecutionMode;
import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestProblemSnapshotServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private MongoTemplate mongo;
    private ContestProblemSnapshotService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        service = new ContestProblemSnapshotService(mongo, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ensureSnapshotsInsertsMissingPublicAndContestOnlyProblems() {
        Problem publicProblem = problem("p1", ProblemAvailability.PUBLIC);
        Problem secret = problem("p2", ProblemAvailability.CONTEST_ONLY);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(publicProblem, secret));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(null);
        when(mongo.insert(any(ContestProblemSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureSnapshots(contest("c1", List.of("p1", "p2")));

        verify(mongo, times(2)).insert(any(ContestProblemSnapshot.class));
    }

    @Test
    void ensureSnapshotsDoesNotOverwriteExistingDocuments() {
        Problem problem = problem("p1", ProblemAvailability.PUBLIC);
        ContestProblemSnapshot existing = ContestProblemSnapshot.fromProblem("c1", problem, NOW.minusSeconds(60));
        existing.setId("snap-p1");
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(existing);

        service.ensureSnapshots(contest("c1", List.of("p1")));

        verify(mongo, never()).insert(any(ContestProblemSnapshot.class));
        verify(mongo, never()).save(any(ContestProblemSnapshot.class));
    }

    @Test
    void duplicateKeyDuringInsertIsIdempotentWhenDocumentExists() {
        Problem problem = problem("p1", ProblemAvailability.PUBLIC);
        ContestProblemSnapshot existing = ContestProblemSnapshot.fromProblem("c1", problem, NOW);
        existing.setId("snap-p1");
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class)))
            .thenReturn(null)
            .thenReturn(existing);
        when(mongo.insert(any(ContestProblemSnapshot.class)))
            .thenThrow(new DuplicateKeyException("dup"));

        service.ensureSnapshots(contest("c1", List.of("p1")));

        verify(mongo).insert(any(ContestProblemSnapshot.class));
    }

    @Test
    void negativePointsRejectsBeforeAnyInsert() {
        Problem valid = problem("p1", ProblemAvailability.PUBLIC);
        valid.setPoints(10);
        Problem invalid = problem("p2", ProblemAvailability.PUBLIC);
        invalid.setPoints(-1);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(valid, invalid));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.ensureSnapshots(contest("c1", List.of("p1", "p2"))));
        assertEquals("problem points must be 0 or greater", ex.getMessage());
        verify(mongo, never()).insert(any(ContestProblemSnapshot.class));
    }

    @Test
    void zeroPointsAreAllowed() {
        Problem zero = problem("p1", ProblemAvailability.PUBLIC);
        zero.setPoints(0);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(zero));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSnapshot.class))).thenReturn(null);
        when(mongo.insert(any(ContestProblemSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.ensureSnapshots(contest("c1", List.of("p1")));
        verify(mongo).insert(any(ContestProblemSnapshot.class));
    }

    @Test
    void missingProblemPreventsSnapshotCompletion() {
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.ensureSnapshots(contest("c1", List.of("p1"))));
        assertEquals("Problem not found", ex.getMessage());
        verify(mongo, never()).insert(any(ContestProblemSnapshot.class));
    }

    private static Contest contest(String id, List<String> problemIds) {
        Contest contest = new Contest();
        contest.setId(id);
        contest.setProblemIds(problemIds);
        return contest;
    }

    private static Problem problem(String id, ProblemAvailability availability) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setTitle(id);
        problem.setAvailability(availability);
        problem.setExecutionMode(ExecutionMode.STANDARD_PER_CASE);
        problem.setTestcaseVersion("v1");
        problem.setPublicTestCases(List.of(new TestCase("1", "1", null)));
        problem.setHiddenTestCases(List.of(new TestCase("h", "h", null)));
        return problem;
    }
}
