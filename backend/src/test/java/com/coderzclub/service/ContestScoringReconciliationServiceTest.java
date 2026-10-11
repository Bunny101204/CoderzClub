package com.coderzclub.service;

import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.Submission;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestScoringReconciliationServiceTest {

    @Test
    void repairQueriesPendingByCreatedAtAndReusesScoringService() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ContestScoringService scoring = mock(ContestScoringService.class);
        ContestScoringReconciliationService repair = new ContestScoringReconciliationService(
            mongo, scoring, new OperationalMetrics(new SimpleMeterRegistry()), 25);
        Submission pending = Submission.builder()
            .id("sub-1")
            .contestId("c1")
            .contestScoringStatus(ContestScoringStatus.PENDING)
            .build();
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(pending));

        repair.repairPendingContestScoring();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Submission.class));
        String text = String.valueOf(query.getValue().getQueryObject());
        assertTrue(text.contains("contestScoringStatus"));
        assertTrue(query.getValue().getLimit() == 25);
        verify(scoring).apply(pending);
    }

    @Test
    void twoWorkersMayApplyTheSamePendingSubmissionThroughOneService() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        ContestScoringService scoring = mock(ContestScoringService.class);
        ContestScoringReconciliationService first = new ContestScoringReconciliationService(
            mongo, scoring, new OperationalMetrics(new SimpleMeterRegistry()), 10);
        ContestScoringReconciliationService second = new ContestScoringReconciliationService(
            mongo, scoring, new OperationalMetrics(new SimpleMeterRegistry()), 10);
        Submission pending = Submission.builder()
            .id("sub-1")
            .contestId("c1")
            .contestScoringStatus(ContestScoringStatus.PENDING)
            .build();
        when(mongo.find(any(Query.class), eq(Submission.class))).thenReturn(List.of(pending));
        first.repairPendingContestScoring();
        second.repairPendingContestScoring();
        verify(scoring, org.mockito.Mockito.times(2)).apply(pending);
    }
}
