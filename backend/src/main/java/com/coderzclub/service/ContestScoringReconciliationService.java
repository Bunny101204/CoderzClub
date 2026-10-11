package com.coderzclub.service;

import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("worker")
public class ContestScoringReconciliationService {
    private static final Logger logger = LoggerFactory.getLogger(ContestScoringReconciliationService.class);

    private final MongoTemplate mongoTemplate;
    private final ContestScoringService scoringService;
    private final OperationalMetrics metrics;
    private final int batchSize;

    public ContestScoringReconciliationService(
        MongoTemplate mongoTemplate,
        ContestScoringService scoringService,
        OperationalMetrics metrics,
        @Value("${contest.scoring.repair-batch-size:50}") int batchSize
    ) {
        this.mongoTemplate = mongoTemplate;
        this.scoringService = scoringService;
        this.metrics = metrics;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${contest.scoring.repair-fixed-delay-ms:15000}")
    public void repairPendingContestScoring() {
        Query query = Query.query(Criteria.where("contestScoringStatus").is(ContestScoringStatus.PENDING))
            .with(Sort.by(Sort.Direction.ASC, "createdAt"))
            .limit(batchSize);
        List<Submission> pending = mongoTemplate.find(query, Submission.class);
        for (Submission submission : pending) {
            try {
                scoringService.apply(submission);
                if (metrics != null) {
                    metrics.contestScoringRepair();
                }
            } catch (RuntimeException failure) {
                logger.warn("contest_scoring_repair_failed submissionId={}", submission.getId(), failure);
            }
        }
    }
}
