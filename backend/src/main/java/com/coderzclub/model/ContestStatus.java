package com.coderzclub.model;

import java.time.Instant;

/**
 * Derived contest lifecycle. Never persisted; compute from published + startAt/endAt + now.
 *
 * <ul>
 *   <li>unpublished → DRAFT</li>
 *   <li>now &lt; startAt → UPCOMING</li>
 *   <li>startAt ≤ now &lt; endAt → LIVE</li>
 *   <li>now ≥ endAt → ENDED</li>
 * </ul>
 */
public enum ContestStatus {
    DRAFT,
    UPCOMING,
    LIVE,
    ENDED;

    public static ContestStatus of(boolean published, Instant startAt, Instant endAt, Instant now) {
        if (!published) {
            return DRAFT;
        }
        if (startAt == null || endAt == null || now == null) {
            return DRAFT;
        }
        if (now.isBefore(startAt)) {
            return UPCOMING;
        }
        if (now.isBefore(endAt)) {
            return LIVE;
        }
        return ENDED;
    }

    public static ContestStatus of(Contest contest, Instant now) {
        if (contest == null) {
            return DRAFT;
        }
        return of(contest.isPublished(), contest.getStartAt(), contest.getEndAt(), now);
    }
}
