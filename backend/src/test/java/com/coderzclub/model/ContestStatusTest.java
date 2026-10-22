package com.coderzclub.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContestStatusTest {
    private static final Instant START = Instant.parse("2026-06-01T12:00:00Z");
    private static final Instant END = Instant.parse("2026-06-01T14:00:00Z");

    @Test
    void unpublishedIsDraftRegardlessOfTime() {
        assertEquals(ContestStatus.DRAFT, ContestStatus.of(false, START, END, START.minusSeconds(60)));
        assertEquals(ContestStatus.DRAFT, ContestStatus.of(false, START, END, START));
        assertEquals(ContestStatus.DRAFT, ContestStatus.of(false, START, END, END));
    }

    @Test
    void beforeStartIsUpcoming() {
        assertEquals(ContestStatus.UPCOMING, ContestStatus.of(true, START, END, START.minusNanos(1)));
    }

    @Test
    void exactStartIsLive() {
        assertEquals(ContestStatus.LIVE, ContestStatus.of(true, START, END, START));
    }

    @Test
    void immediatelyBeforeEndIsLive() {
        assertEquals(ContestStatus.LIVE, ContestStatus.of(true, START, END, END.minusNanos(1)));
    }

    @Test
    void exactEndIsEnded() {
        assertEquals(ContestStatus.ENDED, ContestStatus.of(true, START, END, END));
    }

    @Test
    void contestOverloadUsesPersistedFields() {
        Contest contest = new Contest();
        contest.setPublished(true);
        contest.setStartAt(START);
        contest.setEndAt(END);
        assertEquals(ContestStatus.UPCOMING, ContestStatus.of(contest, START.minusSeconds(1)));
        assertEquals(ContestStatus.LIVE, ContestStatus.of(contest, START));
        assertEquals(ContestStatus.ENDED, ContestStatus.of(contest, END));
    }
}
