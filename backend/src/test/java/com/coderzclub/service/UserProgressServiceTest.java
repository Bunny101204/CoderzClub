package com.coderzclub.service;

import com.coderzclub.dto.ProblemProgressEntry;
import com.coderzclub.dto.UserProfileStatsResponse;
import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void profileStatsWithNoSubmissionsIsZeroed() {
        UserProfileStatsResponse stats = profileStats(List.of(), List.of());
        assertEquals(0, stats.getUniqueProblemsSolved());
        assertEquals(0L, stats.getAcceptedJudgedSubmissions());
        assertEquals(0L, stats.getCompletedStudentSubmissions());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0, stats.getDifficultySolved().get("EASY"));
        assertEquals(0, stats.getDifficultySolved().get("MEDIUM"));
        assertEquals(0, stats.getDifficultySolved().get("HARD"));
        assertTrue(stats.getActivity().isEmpty());
        assertEquals(UserProgressService.SUCCESS_RATE_DEFINITION, stats.getSuccessRateDefinition());
        assertEquals("UTC", stats.getActivityTimezone());
        assertEquals(12, stats.getTotalPoints());
        assertEquals(3, stats.getCurrentStreak());
        assertEquals(7, stats.getLongestStreak());
    }

    @Test
    void profileStatsCountsAcceptedSubmissionAsSolved() {
        Problem problem = problem("mongo-1", 1, "EASY");
        UserProfileStatsResponse stats = profileStats(
            List.of(attempt("mongo-1", "ACCEPTED", null, "2026-01-02T12:00:00Z")),
            List.of(problem));
        assertEquals(1, stats.getUniqueProblemsSolved());
        assertEquals(1L, stats.getAcceptedJudgedSubmissions());
        assertEquals(1L, stats.getCompletedStudentSubmissions());
        assertEquals(1.0, stats.getSuccessRate());
        assertEquals(1, stats.getDifficultySolved().get("EASY"));
        assertEquals("2026-01-02", stats.getActivity().get(0).get("date"));
        assertEquals(1, stats.getActivity().get(0).get("count"));
    }

    @Test
    void profileStatsStudentVerdictIsAttemptedNotSolved() {
        UserProfileStatsResponse stats = profileStats(
            List.of(attempt("p1", "WRONG_ANSWER", null, "2026-01-02T12:00:00Z")),
            List.of(problem("p1", null, "MEDIUM")));
        assertEquals(0, stats.getUniqueProblemsSolved());
        assertEquals(0L, stats.getAcceptedJudgedSubmissions());
        assertEquals(1L, stats.getCompletedStudentSubmissions());
        assertEquals(0.0, stats.getSuccessRate());
        assertEquals(0, stats.getDifficultySolved().get("MEDIUM"));
        assertEquals(1, stats.getActivity().get(0).get("count"));
    }

    @Test
    void profileStatsExcludesInfrastructureFromSuccessRateDenominator() {
        UserProfileStatsResponse stats = profileStats(List.of(
            attempt("p1", "ACCEPTED", null, "2026-01-02T12:00:00Z"),
            attempt("p1", "INTERNAL_ERROR", null, "2026-01-02T13:00:00Z"),
            attempt("p1", "WRONG_ANSWER", null, "2026-01-02T14:00:00Z")
        ), List.of(problem("p1", null, "EASY")));
        assertEquals(1, stats.getUniqueProblemsSolved());
        assertEquals(1L, stats.getAcceptedJudgedSubmissions());
        assertEquals(2L, stats.getCompletedStudentSubmissions());
        assertEquals(0.5, stats.getSuccessRate());
        assertEquals(2, stats.getActivity().get(0).get("count"));
    }

    @Test
    void profileStatsMultipleAttemptsCountProblemOnceWhenSolved() {
        UserProfileStatsResponse stats = profileStats(List.of(
            attempt("p1", "WRONG_ANSWER", null, "2026-01-02T12:00:00Z"),
            attempt("p1", "ACCEPTED", null, "2026-01-02T13:00:00Z")
        ), List.of(problem("p1", null, "HARD")));
        assertEquals(1, stats.getUniqueProblemsSolved());
        assertEquals(1L, stats.getAcceptedJudgedSubmissions());
        assertEquals(2L, stats.getCompletedStudentSubmissions());
        assertEquals(1, stats.getDifficultySolved().get("HARD"));
    }

    @Test
    void profileStatsNormalizesLegacyDifficultyLabels() {
        UserProfileStatsResponse stats = profileStats(List.of(
            attempt("p-easy", "ACCEPTED", null, "2026-01-02T12:00:00Z"),
            attempt("p-med", "ACCEPTED", null, "2026-01-02T12:01:00Z"),
            attempt("p-hard", "ACCEPTED", null, "2026-01-02T12:02:00Z")
        ), List.of(
            problem("p-easy", null, "BASIC"),
            problem("p-med", null, "INTERMEDIATE"),
            problem("p-hard", null, "ADVANCED")
        ));
        assertEquals(3, stats.getUniqueProblemsSolved());
        assertEquals(1, stats.getDifficultySolved().get("EASY"));
        assertEquals(1, stats.getDifficultySolved().get("MEDIUM"));
        assertEquals(1, stats.getDifficultySolved().get("HARD"));
    }

    @Test
    void profileStatsIgnoresNullCreatedAtForActivity() {
        Submission missingDate = attempt("p1", "ACCEPTED", null, "2026-01-02T12:00:00Z");
        missingDate.setCreatedAt(null);
        UserProfileStatsResponse stats = profileStats(
            List.of(missingDate, attempt("p1", "WRONG_ANSWER", null, "2026-01-03T00:00:00Z")),
            List.of(problem("p1", null, "EASY")));
        assertEquals(1, stats.getUniqueProblemsSolved());
        assertEquals(1, stats.getActivity().size());
        assertEquals("2026-01-03", stats.getActivity().get(0).get("date"));
        assertEquals(1, stats.getActivity().get(0).get("count"));
    }

    @Test
    void profileStatsResolvesNumericProblemIdAlias() {
        Problem problem = problem("mongo-26", 26, "EASY");
        UserProfileStatsResponse stats = profileStats(
            List.of(attempt("26", "ACCEPTED", null, "2026-01-02T12:00:00Z")),
            List.of(problem));
        assertEquals(1, stats.getUniqueProblemsSolved());
        assertEquals(1, stats.getDifficultySolved().get("EASY"));
    }

    @Test
    void profileStatsQueriesProjectedSubmissionsOnceAndProblemsOnce() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        Submission submission = attempt("p1", "ACCEPTED", null, "2026-01-02T12:00:00Z");
        Problem problem = problem("p1", 1, "EASY");
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(submission));
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(List.of(problem));

        UserProfileStatsResponse stats = new UserProgressService(mongo).profileStats(user());

        assertEquals(1, stats.getUniqueProblemsSolved());
        verify(mongo, times(1)).find(any(Query.class), eq(Submission.class));
        verify(mongo, times(1)).find(any(Query.class), eq(Problem.class));
        verify(mongo, never()).aggregate(any(Aggregation.class), eq("submissions"), eq(org.bson.Document.class));

        ArgumentCaptor<Query> submissionQuery = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(submissionQuery.capture(), eq(Submission.class));
        org.bson.Document fields = submissionQuery.getValue().getFieldsObject();
        assertEquals(1, fields.get("problemId"));
        assertEquals(1, fields.get("result"));
        assertEquals(1, fields.get("verdict"));
        assertEquals(1, fields.get("createdAt"));
    }

    @Test
    void analyzeSubmissionsUsesClassifierForOutcomesAndCounts() {
        UserProgressService.SubmissionAnalysis analysis = UserProgressService.analyzeSubmissions(List.of(
            attempt("p1", "ACCEPTED", null, "2026-01-02T12:00:00Z"),
            attempt("p1", "WRONG_ANSWER", null, "2026-01-02T13:00:00Z"),
            attempt("p2", "INTERNAL_ERROR", null, "2026-01-02T14:00:00Z")
        ));
        assertEquals("SOLVED", SubmissionAttemptClassifier.problemStatus(analysis.outcomesByProblem().get("p1")));
        assertEquals(null, SubmissionAttemptClassifier.problemStatus(analysis.outcomesByProblem().get("p2")));
        assertEquals(1L, analysis.accepted());
        assertEquals(2L, analysis.student());
        assertEquals(2, analysis.activity().get(0).get("count"));
    }

    private static UserProfileStatsResponse profileStats(List<Submission> submissions, List<Problem> problems) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(submissions);
        when(mongo.find(any(Query.class), eq(Problem.class))).thenReturn(problems);
        return new UserProgressService(mongo).profileStats(user());
    }

    private static User user() {
        User user = new User();
        user.setId("user-1");
        user.setTotalPoints(12);
        user.setCurrentStreak(3);
        user.setLongestStreak(7);
        return user;
    }

    private static Problem problem(String id, Integer numericId, String difficulty) {
        Problem problem = new Problem();
        problem.setId(id);
        problem.setNumericId(numericId);
        problem.setDifficulty(difficulty);
        return problem;
    }

    private static ProblemProgressEntry entry(String id, String status) {
        ProblemProgressEntry entry = new ProblemProgressEntry();
        entry.setProblemId(id);
        entry.setStatus(status);
        return entry;
    }

    private static Submission attempt(String result, String instant) {
        return attempt(null, result, null, instant);
    }

    private static Submission attempt(String problemId, String result, String verdict, String instant) {
        Submission submission = new Submission();
        submission.setProblemId(problemId);
        submission.setResult(result);
        submission.setVerdict(verdict);
        if (instant != null) {
            submission.setCreatedAt(Date.from(java.time.Instant.parse(instant)));
        }
        return submission;
    }
}
