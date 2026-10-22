package com.coderzclub.service;

import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestProblemSolve;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.Submission;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestScoringServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:01:00Z");
    private static final Instant SUBMITTED = Instant.parse("2026-06-01T11:59:59Z");

    private MongoTemplate mongo;
    private ContestProblemSnapshotService snapshots;
    private ContestScoringService service;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        snapshots = mock(ContestProblemSnapshotService.class);
        service = new ContestScoringService(
            mongo, snapshots, Clock.fixed(NOW, ZoneOffset.UTC), new OperationalMetrics(new SimpleMeterRegistry()));
        when(mongo.upsert(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Submission.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(mongo.insert(any(ContestProblemSolve.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongo.exists(any(Query.class), eq(ContestScore.class))).thenReturn(false);
    }

    @Test
    void practiceSubmissionIsIgnored() {
        Submission practice = contestSubmission("WRONG_ANSWER");
        practice.setContestId(null);
        service.apply(practice);
        verify(mongo, never()).upsert(any(), any(), eq(ContestScore.class));
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
    }

    @Test
    void wrongAnswerCreatesZeroParticipant() {
        Submission submission = contestSubmission("WRONG_ANSWER");
        service.apply(submission);
        verify(mongo).upsert(any(Query.class), any(Update.class), eq(ContestScore.class));
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
        verify(mongo, never()).updateFirst(any(), any(), eq(ContestScore.class));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void compilationErrorCreatesZeroParticipant() {
        Submission submission = contestSubmission("COMPILATION_ERROR");
        service.apply(submission);
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void firstAcceptedAwardsSnapshotPointsNotLiveProblem() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        Submission submission = contestSubmission("ACCEPTED");
        service.apply(submission);
        ArgumentCaptor<ContestProblemSolve> solve = ArgumentCaptor.forClass(ContestProblemSolve.class);
        verify(mongo).insert(solve.capture());
        assertEquals(100, solve.getValue().getAwardedPoints());
        assertEquals("snap-1", solve.getValue().getContestProblemSnapshotId());
        assertEquals(SUBMITTED, solve.getValue().getContestSubmittedAt());
        ArgumentCaptor<Update> scoreUpdate = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), scoreUpdate.capture(), eq(ContestScore.class));
        String update = String.valueOf(scoreUpdate.getValue().getUpdateObject());
        assertTrue(update.contains("totalPoints"));
        assertTrue(update.contains("100"));
        assertTrue(update.contains("solvedProblemIds"));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void contestOnlySnapshotAwardsItsPoints() {
        stubSnapshot(25, ProblemAvailability.CONTEST_ONLY);
        service.apply(contestSubmission("ACCEPTED"));
        ArgumentCaptor<ContestProblemSolve> solve = ArgumentCaptor.forClass(ContestProblemSolve.class);
        verify(mongo).insert(solve.capture());
        assertEquals(25, solve.getValue().getAwardedPoints());
    }

    @Test
    void zeroPointAcceptedIsAllowed() {
        stubSnapshot(0, ProblemAvailability.PUBLIC);
        service.apply(contestSubmission("ACCEPTED"));
        ArgumentCaptor<ContestProblemSolve> solve = ArgumentCaptor.forClass(ContestProblemSolve.class);
        verify(mongo).insert(solve.capture());
        assertEquals(0, solve.getValue().getAwardedPoints());
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
    }

    @Test
    void negativeSnapshotPointsAreNotAwarded() {
        stubSnapshot(-5, ProblemAvailability.PUBLIC);
        service.apply(contestSubmission("ACCEPTED"));
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
        verify(mongo, never()).updateFirst(any(), any(), eq(ContestScore.class));
    }

    @Test
    void delayedAfterEndAcceptedStillScores() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        Submission submission = contestSubmission("ACCEPTED");
        service.apply(submission);
        verify(mongo).insert(any(ContestProblemSolve.class));
        verify(snapshots).findById("snap-1");
        verify(mongo, never()).findById(any(), eq(com.coderzclub.model.Contest.class));
    }

    @Test
    void secondAcceptedReusesSolveAndDoesNotIncrementTwice() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        ContestProblemSolve existing = new ContestProblemSolve();
        existing.setContestId("c1");
        existing.setUserId("u1");
        existing.setProblemId("p1");
        existing.setAwardedPoints(100);
        when(mongo.insert(any(ContestProblemSolve.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSolve.class))).thenReturn(existing);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(1, 0L, null));
        when(mongo.exists(any(Query.class), eq(ContestScore.class))).thenReturn(true);
        service.apply(contestSubmission("ACCEPTED"));
        verify(mongo, times(1)).insert(any(ContestProblemSolve.class));
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
    }

    @Test
    void caseBSolveExistsThenScoreIncrementAppliesOnce() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        ContestProblemSolve existing = new ContestProblemSolve();
        existing.setContestId("c1");
        existing.setUserId("u1");
        existing.setProblemId("p1");
        existing.setAwardedPoints(100);
        when(mongo.insert(any(ContestProblemSolve.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSolve.class))).thenReturn(existing);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        Submission submission = contestSubmission("ACCEPTED");
        service.apply(submission);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void caseCScoreAlreadyIncrementedRepairOnlyMarksApplied() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        ContestProblemSolve existing = new ContestProblemSolve();
        existing.setContestId("c1");
        existing.setUserId("u1");
        existing.setProblemId("p1");
        existing.setAwardedPoints(100);
        when(mongo.insert(any(ContestProblemSolve.class))).thenThrow(new DuplicateKeyException("dup"));
        when(mongo.findOne(any(Query.class), eq(ContestProblemSolve.class))).thenReturn(existing);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.exists(any(Query.class), eq(ContestScore.class))).thenReturn(true);
        Submission submission = contestSubmission("ACCEPTED");
        service.apply(submission);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(Submission.class));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void concurrentParticipantInsertRaceDoesNotEscapeDuplicateKey() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        when(mongo.upsert(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenThrow(new DuplicateKeyException("contest score unique"));
        Submission submission = contestSubmission("ACCEPTED");
        service.apply(submission);
        verify(mongo).insert(any(ContestProblemSolve.class));
        assertEquals(ContestScoringStatus.APPLIED, submission.getContestScoringStatus());
    }

    @Test
    void awardDoesNotUpsertScoreRow() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        service.apply(contestSubmission("ACCEPTED"));
        verify(mongo).upsert(any(Query.class), any(Update.class), eq(ContestScore.class));
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
    }

    @Test
    void missingScoreRowKeepsPendingForRepair() {
        stubSnapshot(100, ProblemAvailability.PUBLIC);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class)))
            .thenReturn(UpdateResult.acknowledged(0, 0L, null));
        when(mongo.exists(any(Query.class), eq(ContestScore.class))).thenReturn(false);
        Submission submission = contestSubmission("ACCEPTED");
        assertThrows(IllegalStateException.class, () -> service.apply(submission));
        assertEquals(ContestScoringStatus.PENDING, submission.getContestScoringStatus());
        verify(mongo, never()).updateFirst(any(Query.class), any(Update.class), eq(Submission.class));
    }

    @Test
    void appliedSubmissionIsNotReprocessed() {
        Submission submission = contestSubmission("ACCEPTED");
        submission.setContestScoringStatus(ContestScoringStatus.APPLIED);
        service.apply(submission);
        verify(mongo, never()).upsert(any(), any(), eq(ContestScore.class));
        verify(snapshots, never()).findById(any());
    }

    @Test
    void incoherentSnapshotIsNotAwarded() {
        ContestProblemSnapshot snapshot = snapshot(100, ProblemAvailability.PUBLIC);
        snapshot.setContestId("other");
        when(snapshots.findById("snap-1")).thenReturn(snapshot);
        service.apply(contestSubmission("ACCEPTED"));
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
    }

    @Test
    void missingSnapshotDoesNotFallBackToProblem() {
        when(snapshots.findById("snap-1")).thenReturn(null);
        Submission submission = contestSubmission("ACCEPTED");
        assertThrows(IllegalStateException.class, () -> service.apply(submission));
        verify(mongo, never()).insert(any(ContestProblemSolve.class));
        assertEquals(ContestScoringStatus.PENDING, submission.getContestScoringStatus());
    }

    @Test
    void twoAcceptedProblemsEachAwardOnce() {
        ContestProblemSnapshot first = snapshot(100, ProblemAvailability.PUBLIC);
        ContestProblemSnapshot second = snapshot(40, ProblemAvailability.PUBLIC);
        second.setId("snap-2");
        second.setProblemId("p2");
        when(snapshots.findById("snap-1")).thenReturn(first);
        when(snapshots.findById("snap-2")).thenReturn(second);
        service.apply(contestSubmission("ACCEPTED"));
        Submission other = contestSubmission("ACCEPTED");
        other.setProblemId("p2");
        other.setContestProblemSnapshotId("snap-2");
        other.setId("sub-2");
        service.apply(other);
        verify(mongo, times(2)).insert(any(ContestProblemSolve.class));
        verify(mongo, times(2)).updateFirst(any(Query.class), any(Update.class), eq(ContestScore.class));
    }

    @Test
    void scoringDoesNotTouchRedis() {
        stubSnapshot(10, ProblemAvailability.PUBLIC);
        service.apply(contestSubmission("ACCEPTED"));
        verify(mongo, never()).findById(eq("c1"), eq(com.coderzclub.model.Contest.class));
    }

    private void stubSnapshot(int points, ProblemAvailability availability) {
        when(snapshots.findById("snap-1")).thenReturn(snapshot(points, availability));
    }

    private static ContestProblemSnapshot snapshot(int points, ProblemAvailability availability) {
        ContestProblemSnapshot snapshot = new ContestProblemSnapshot();
        snapshot.setId("snap-1");
        snapshot.setContestId("c1");
        snapshot.setProblemId("p1");
        snapshot.setPoints(points);
        snapshot.setAvailability(availability);
        return snapshot;
    }

    private static Submission contestSubmission(String result) {
        return Submission.builder()
            .id("sub-1")
            .userId("u1")
            .problemId("p1")
            .submissionJobId("job-1")
            .result(result)
            .contestId("c1")
            .contestSubmittedAt(SUBMITTED)
            .contestProblemSnapshotId("snap-1")
            .contestScoringStatus(ContestScoringStatus.PENDING)
            .build();
    }
}
