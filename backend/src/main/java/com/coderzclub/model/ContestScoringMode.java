package com.coderzclub.model;

/**
 * Contest ranking/scoring policy. v1 is POINTS_FIRST_ACCEPTED only.
 * If a future mode uses time, it must rank by {@code contestSubmittedAt}
 * (admission clock), never Judge0 {@code completedAt} or queue duration.
 */
public enum ContestScoringMode {
    POINTS_FIRST_ACCEPTED
}
