package com.coderzclub.service;

import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.ContestProblemSolve;
import com.coderzclub.model.ContestScore;
import com.coderzclub.model.ContestScoringStatus;
import com.coderzclub.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Idempotent contest POINTS_FIRST_ACCEPTED projection. Does not consult live
 * contest status or live Problem.points. Does not implement time penalties.
 */
@Service
public class ContestScoringService {
    private static final Logger logger = LoggerFactory.getLogger(ContestScoringService.class);

    private final MongoTemplate mongoTemplate;
    private final ContestProblemSnapshotService snapshotService;
    private final Clock clock;
    private final OperationalMetrics metrics;

    public ContestScoringService(
        MongoTemplate mongoTemplate,
        ContestProblemSnapshotService snapshotService,
        Clock clock,
        OperationalMetrics metrics
    ) {
        this.mongoTemplate = mongoTemplate;
        this.snapshotService = snapshotService;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void apply(Submission submission) {
        if (submission == null || SubmissionService.isPracticeJob(submission)) {
            return;
        }
        if (submission.getContestScoringStatus() == ContestScoringStatus.APPLIED) {
            return;
        }
        if (blank(submission.getContestId()) || blank(submission.getUserId()) || blank(submission.getProblemId())) {
            logger.warn("contest_scoring_skipped_incomplete_identity submissionId={}", submission.getId());
            markApplied(submission);
            return;
        }

        Instant now = clock.instant();
        upsertParticipant(submission.getContestId(), submission.getUserId(), now);
        if (metrics != null) {
            metrics.contestScoringProcessed();
        }

        if ("ACCEPTED".equals(submission.getResult())) {
            awardFirstAccepted(submission, now);
        }

        markApplied(submission);
    }

    private void awardFirstAccepted(Submission submission, Instant now) {
        ContestProblemSnapshot snapshot = snapshotService.findById(submission.getContestProblemSnapshotId());
        if (snapshot == null) {
            throw new IllegalStateException("Contest problem snapshot not found for scoring");
        }
        if (!submission.getContestId().equals(snapshot.getContestId())
            || !submission.getProblemId().equals(snapshot.getProblemId())) {
            logger.error("contest_scoring_rejected_incoherent_context submissionId={} contestId={} problemId={} snapshotId={}",
                submission.getId(), submission.getContestId(), submission.getProblemId(),
                submission.getContestProblemSnapshotId());
            return;
        }
        if (snapshot.getPoints() < 0) {
            logger.error("contest_scoring_rejected_negative_points submissionId={} snapshotId={}",
                submission.getId(), snapshot.getId());
            return;
        }

        ContestProblemSolve solve = insertOrLoadSolve(submission, snapshot, now);
        boolean firstIncrement = applySolveToScore(solve, now);
        if (firstIncrement) {
            if (metrics != null) {
                metrics.contestFirstSolveAwarded();
            }
            return;
        }
        if (metrics != null) {
            metrics.contestDuplicateSolveIgnored();
        }
    }

    private ContestProblemSolve insertOrLoadSolve(Submission submission, ContestProblemSnapshot snapshot, Instant now) {
        ContestProblemSolve solve = new ContestProblemSolve();
        solve.setContestId(submission.getContestId());
        solve.setUserId(submission.getUserId());
        solve.setProblemId(submission.getProblemId());
        solve.setContestProblemSnapshotId(snapshot.getId());
        solve.setAwardedPoints(snapshot.getPoints());
        solve.setAwardedSubmissionJobId(submission.getSubmissionJobId());
        solve.setAwardedSubmissionId(submission.getId());
        solve.setContestSubmittedAt(submission.getContestSubmittedAt());
        solve.setCreatedAt(now);
        solve.setAppliedAt(now);
        try {
            mongoTemplate.insert(solve);
            return solve;
        } catch (DuplicateKeyException duplicate) {
            ContestProblemSolve existing = mongoTemplate.findOne(
                Query.query(Criteria.where("contestId").is(submission.getContestId())
                    .and("userId").is(submission.getUserId())
                    .and("problemId").is(submission.getProblemId())),
                ContestProblemSolve.class
            );
            if (existing == null) {
                throw duplicate;
            }
            return existing;
        }
    }

    /**
     * Conditional increment without upsert. If the row already contains the
     * problem, this is a clean no-op. Never upsert here: a non-matching
     * {@code solvedProblemIds $ne} query plus upsert would try to insert a
     * second ContestScore and collide with the unique (contestId, userId) index.
     */
    boolean applySolveToScore(ContestProblemSolve solve, Instant now) {
        Query query = Query.query(Criteria.where("contestId").is(solve.getContestId())
            .and("userId").is(solve.getUserId())
            .and("solvedProblemIds").ne(solve.getProblemId()));
        Update update = new Update()
            .inc("totalPoints", solve.getAwardedPoints())
            .inc("solvedCount", 1)
            .addToSet("solvedProblemIds", solve.getProblemId())
            .set("updatedAt", now);
        if (mongoTemplate.updateFirst(query, update, ContestScore.class).getModifiedCount() > 0) {
            return true;
        }
        boolean alreadyAwarded = mongoTemplate.exists(
            Query.query(Criteria.where("contestId").is(solve.getContestId())
                .and("userId").is(solve.getUserId())
                .and("solvedProblemIds").is(solve.getProblemId())),
            ContestScore.class
        );
        if (alreadyAwarded) {
            return false;
        }
        throw new IllegalStateException("Contest score row is not ready for award");
    }

    /**
     * Idempotent participant row. Concurrent first inserts may race the unique
     * (contestId, userId) index; DuplicateKeyException means the other worker won.
     */
    void upsertParticipant(String contestId, String userId, Instant now) {
        Query query = Query.query(Criteria.where("contestId").is(contestId).and("userId").is(userId));
        Update update = new Update()
            .setOnInsert("contestId", contestId)
            .setOnInsert("userId", userId)
            .setOnInsert("totalPoints", 0)
            .setOnInsert("solvedCount", 0)
            .setOnInsert("solvedProblemIds", new ArrayList<String>())
            .setOnInsert("createdAt", now)
            .set("updatedAt", now);
        try {
            mongoTemplate.upsert(query, update, ContestScore.class);
        } catch (DuplicateKeyException raced) {
            logger.info("contest_score_insert_raced contestId={} userId={}", contestId, userId);
        }
    }

    void markApplied(Submission submission) {
        if (submission.getId() == null) {
            submission.setContestScoringStatus(ContestScoringStatus.APPLIED);
            return;
        }
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(submission.getId())
                .and("contestScoringStatus").ne(ContestScoringStatus.APPLIED)),
            new Update().set("contestScoringStatus", ContestScoringStatus.APPLIED),
            Submission.class
        );
        submission.setContestScoringStatus(ContestScoringStatus.APPLIED);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
