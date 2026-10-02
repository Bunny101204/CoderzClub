package com.coderzclub.service;

import com.coderzclub.dto.ProblemProgressEntry;
import com.coderzclub.model.Submission;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProgressServiceTest {
    @Test
    void difficultyCountsUniqueSolvedProblems() {
        ProblemProgressEntry easy1 = entry("p1", "SOLVED");
        ProblemProgressEntry easyAgain = entry("p1", "SOLVED");
        ProblemProgressEntry medium = entry("p2", "SOLVED");
        Map<String, String> difficulty = Map.of("p1", "EASY", "p2", "MEDIUM");
        Map<String, Integer> counts = UserProgressService.difficultyCounts(
            List.of(easy1, easyAgain, medium), difficulty);
        assertEquals(1, counts.get("EASY"));
        assertEquals(1, counts.get("MEDIUM"));
        assertEquals(0, counts.get("HARD"));
    }

    @Test
    void noAcceptedSubmissionsYieldZeroDifficultyCounts() {
        Map<String, Integer> counts = UserProgressService.difficultyCounts(List.of(), Map.of("p1", "EASY"));
        assertEquals(0, counts.get("EASY"));
        assertEquals(0, counts.get("MEDIUM"));
        assertEquals(0, counts.get("HARD"));
    }

    @Test
    void easyAndHardAreCountedSeparately() {
        Map<String, Integer> counts = UserProgressService.difficultyCounts(
            List.of(entry("p1", "SOLVED"), entry("p2", "SOLVED")),
            Map.of("p1", "EASY", "p2", "HARD"));
        assertEquals(1, counts.get("EASY"));
        assertEquals(0, counts.get("MEDIUM"));
        assertEquals(1, counts.get("HARD"));
    }

    @Test
    void activityCountsAcceptedAndWrongAnswerOnSameUtcDay() {
        List<Map<String, Object>> activity = UserProgressService.activityFromStudentAttempts(List.of(
            attempt("ACCEPTED", "2026-01-02T01:00:00Z"),
            attempt("WRONG_ANSWER", "2026-01-02T23:00:00Z")
        ));
        assertEquals(1, activity.size());
        assertEquals("2026-01-02", activity.get(0).get("date"));
        assertEquals(2, activity.get(0).get("count"));
    }

    @Test
    void activityExcludesInternalErrorAndLifecycleCompleted() {
        List<Map<String, Object>> activity = UserProgressService.activityFromStudentAttempts(List.of(
            attempt("INTERNAL_ERROR", "2026-01-02T12:00:00Z"),
            attempt("COMPLETED", "2026-01-02T13:00:00Z"),
            attempt("ACCEPTED", "2026-01-02T14:00:00Z")
        ));
        assertEquals(1, activity.size());
        assertEquals("2026-01-02", activity.get(0).get("date"));
        assertEquals(1, activity.get(0).get("count"));
    }

    @Test
    void activityAggregatesDifferentUtcDatesSeparately() {
        List<Map<String, Object>> activity = UserProgressService.activityFromStudentAttempts(List.of(
            attempt("TIME_LIMIT_EXCEEDED", "2026-01-02T23:00:00Z"),
            attempt("COMPILATION_ERROR", "2026-01-03T00:30:00Z")
        ));
        assertEquals(2, activity.size());
        assertEquals("2026-01-02", activity.get(0).get("date"));
        assertEquals(1, activity.get(0).get("count"));
        assertEquals("2026-01-03", activity.get(1).get("date"));
        assertEquals(1, activity.get(1).get("count"));
        assertTrue(SubmissionAttemptClassifier.countsTowardActivity("RUNTIME_ERROR", null));
        assertTrue(SubmissionAttemptClassifier.countsTowardActivity("MEMORY_LIMIT_EXCEEDED", null));
    }

    @Test
    void successRateUsesStudentJudgedDenominator() {
        long accepted = 1;
        long student = 4;
        assertEquals(0.25, (double) accepted / student);
    }

    private static ProblemProgressEntry entry(String id, String status) {
        ProblemProgressEntry entry = new ProblemProgressEntry();
        entry.setProblemId(id);
        entry.setStatus(status);
        return entry;
    }

    private static Submission attempt(String result, String instant) {
        Submission submission = new Submission();
        submission.setResult(result);
        submission.setCreatedAt(Date.from(java.time.Instant.parse(instant)));
        return submission;
    }
}
