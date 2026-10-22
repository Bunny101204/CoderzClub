package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmissionAttemptClassifierTest {
    @Test
    void acceptedWinsOverWrongAnswer() {
        assertEquals("SOLVED", SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.STUDENT_VERDICT,
            SubmissionAttemptClassifier.Kind.ACCEPTED
        )));
    }

    @Test
    void waOnlyIsAttempted() {
        assertEquals(SubmissionAttemptClassifier.Kind.STUDENT_VERDICT,
            SubmissionAttemptClassifier.classify("WRONG_ANSWER", null));
        assertEquals("ATTEMPTED", SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.STUDENT_VERDICT)));
    }

    @Test
    void internalErrorIsNotSolvedOrAttempted() {
        assertEquals(SubmissionAttemptClassifier.Kind.INFRASTRUCTURE,
            SubmissionAttemptClassifier.classify("INTERNAL_ERROR", null));
        assertNull(SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.INFRASTRUCTURE)));
    }

    @Test
    void acceptedStillWinsIfInfrastructureAlsoPresent() {
        assertEquals("SOLVED", SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.INFRASTRUCTURE,
            SubmissionAttemptClassifier.Kind.ACCEPTED)));
    }

    @Test
    void completedJobStatusIsIgnored() {
        assertEquals(SubmissionAttemptClassifier.Kind.IGNORED,
            SubmissionAttemptClassifier.classify("COMPLETED", null));
        assertNull(SubmissionAttemptClassifier.problemStatus(Set.of(SubmissionAttemptClassifier.Kind.IGNORED)));
    }

    @Test
    void acceptedThenWrongAnswerRemainsSolved() {
        assertEquals("SOLVED", SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.ACCEPTED,
            SubmissionAttemptClassifier.Kind.STUDENT_VERDICT)));
    }

    @Test
    void runtimeErrorVariantsCountAsStudentAttempts() {
        assertEquals(SubmissionAttemptClassifier.Kind.STUDENT_VERDICT,
            SubmissionAttemptClassifier.classify("RUNTIME_ERROR_NZEC", null));
        assertEquals("ATTEMPTED", SubmissionAttemptClassifier.problemStatus(Set.of(
            SubmissionAttemptClassifier.Kind.STUDENT_VERDICT)));
    }

    @Test
    void activityIncludesStudentVerdictsAndExcludesInfrastructure() {
        assertEquals(true, SubmissionAttemptClassifier.countsTowardActivity("ACCEPTED", null));
        assertEquals(true, SubmissionAttemptClassifier.countsTowardActivity("WRONG_ANSWER", null));
        assertEquals(false, SubmissionAttemptClassifier.countsTowardActivity("INTERNAL_ERROR", null));
        assertEquals(false, SubmissionAttemptClassifier.countsTowardActivity("COMPLETED", null));
    }
}
