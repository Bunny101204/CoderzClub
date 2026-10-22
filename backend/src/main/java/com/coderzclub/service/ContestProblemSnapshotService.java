package com.coderzclub.service;

import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestProblemSnapshot;
import com.coderzclub.model.Problem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContestProblemSnapshotService {
    private static final Logger logger = LoggerFactory.getLogger(ContestProblemSnapshotService.class);

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public ContestProblemSnapshotService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /**
     * Create missing snapshots for the contest's ordered problems. Existing
     * (contestId, problemId) documents are left unchanged.
     */
    public void ensureSnapshots(Contest contest) {
        List<String> problemIds = contest.getProblemIds() == null ? List.of() : contest.getProblemIds();
        if (problemIds.isEmpty()) {
            throw new IllegalArgumentException("at least one problem is required to publish");
        }
        Instant capturedAt = clock.instant();
        Query problemQuery = Query.query(Criteria.where("_id").in(problemIds));
        Map<String, Problem> byId = new HashMap<>();
        for (Problem problem : mongoTemplate.find(problemQuery, Problem.class)) {
            if (problem.getId() != null) {
                byId.put(problem.getId(), problem);
            }
        }
        for (String problemId : problemIds) {
            Problem problem = byId.get(problemId);
            if (problem == null) {
                throw new IllegalArgumentException("Problem not found");
            }
            if (problem.getPoints() < 0) {
                throw new IllegalArgumentException("problem points must be 0 or greater");
            }
        }
        for (String problemId : problemIds) {
            Problem problem = byId.get(problemId);
            if (find(contest.getId(), problemId) == null) {
                ContestProblemSnapshot snapshot = ContestProblemSnapshot.fromProblem(contest.getId(), problem, capturedAt);
                try {
                    mongoTemplate.insert(snapshot);
                } catch (DuplicateKeyException duplicate) {
                    if (find(contest.getId(), problemId) == null) {
                        throw new IllegalArgumentException("Contest problem snapshots incomplete");
                    }
                    logger.info("contest_snapshot_insert_raced contestId={} problemId={}", contest.getId(), problemId);
                }
            }
        }
        logger.info("contest_snapshots_ready contestId={} problems={}", contest.getId(), problemIds.size());
    }

    public void deleteByContestId(String contestId) {
        if (contestId == null || contestId.isBlank()) {
            return;
        }
        mongoTemplate.remove(Query.query(Criteria.where("contestId").is(contestId)), ContestProblemSnapshot.class);
        logger.info("contest_snapshots_removed contestId={}", contestId);
    }

    public ContestProblemSnapshot require(String contestId, String problemId) {
        ContestProblemSnapshot snapshot = find(contestId, problemId);
        if (snapshot == null) {
            throw ContestAdmissionException.notFound();
        }
        return snapshot;
    }

    public ContestProblemSnapshot findById(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return null;
        }
        return mongoTemplate.findById(snapshotId, ContestProblemSnapshot.class);
    }

    public ContestProblemSnapshot find(String contestId, String problemId) {
        if (contestId == null || problemId == null) {
            return null;
        }
        return mongoTemplate.findOne(
            Query.query(Criteria.where("contestId").is(contestId).and("problemId").is(problemId)),
            ContestProblemSnapshot.class
        );
    }

    public List<ContestProblemSnapshot> findByContestId(String contestId) {
        if (contestId == null || contestId.isBlank()) {
            return List.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where("contestId").is(contestId)), ContestProblemSnapshot.class);
    }
}
