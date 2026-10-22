package com.coderzclub.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Locale;

/**
 * Practice-vs-contest availability for a problem.
 * Missing/null stored values are treated as {@link #PUBLIC} so legacy documents
 * keep their current practice-bank behavior without a backfill migration.
 */
public enum ProblemAvailability {
    PUBLIC,
    CONTEST_ONLY;

    @JsonCreator
    public static ProblemAvailability fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("availability must be PUBLIC or CONTEST_ONLY");
        }
    }

    public static ProblemAvailability of(Problem problem) {
        if (problem == null || problem.getAvailability() == null) {
            return PUBLIC;
        }
        return problem.getAvailability();
    }

    public static boolean isPublic(Problem problem) {
        return of(problem) == PUBLIC;
    }

    public static boolean isContestOnly(Problem problem) {
        return of(problem) == CONTEST_ONLY;
    }

    /**
     * Mongo filter for the ordinary practice list. {@code $ne} matches missing,
     * null, and PUBLIC documents and excludes CONTEST_ONLY.
     */
    public static Criteria practiceListingCriteria() {
        return Criteria.where("availability").ne(CONTEST_ONLY);
    }
}
