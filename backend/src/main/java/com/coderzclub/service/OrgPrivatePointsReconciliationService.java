package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.User;
import com.coderzclub.model.UserSolvedProblem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Audit and optional correction of historical organization-private solves that
 * inflated global {@code User.totalPoints} / {@code problemsSolved}.
 *
 * <p>Default is dry-run. Writes require
 * {@code coderzclub.reconcile.org-private-points.enabled=true} and
 * {@code dry-run=false}. Corrections are idempotent: already-zero awards and
 * already-clamped users are skipped. An audit document is always written when
 * mutations are requested.
 */
@Service
public class OrgPrivatePointsReconciliationService {
    private static final Logger logger = LoggerFactory.getLogger(OrgPrivatePointsReconciliationService.class);
    public static final String AUDIT_COLLECTION = "org_private_points_reconciliation_audits";

    private final MongoTemplate mongoTemplate;
    private final OrganizationVisibilityPolicy visibilityPolicy;

    @Value("${coderzclub.reconcile.org-private-points.enabled:false}")
    private boolean enabled;

    @Value("${coderzclub.reconcile.org-private-points.dry-run:true}")
    private boolean dryRun;

    public OrgPrivatePointsReconciliationService(
        MongoTemplate mongoTemplate,
        OrganizationVisibilityPolicy visibilityPolicy
    ) {
        this.mongoTemplate = mongoTemplate;
        this.visibilityPolicy = visibilityPolicy;
    }

    public Map<String, Object> audit() {
        return reconcile(false);
    }

    /**
     * @param applyWrites when true and {@code dry-run=false}, applies idempotent corrections.
     */
    public Map<String, Object> reconcile(boolean applyWrites) {
        Map<String, Object> report = new HashMap<>();
        String runId = UUID.randomUUID().toString();
        report.put("runId", runId);
        report.put("generatedAt", Instant.now().toString());
        report.put("dryRun", dryRun || !applyWrites);
        report.put("enabled", enabled);
        report.put("mutationsApplied", false);
        report.put("applyRequested", applyWrites);

        if (!enabled) {
            report.put("status", "SKIPPED");
            report.put("message", "Set coderzclub.reconcile.org-private-points.enabled=true to run");
            return report;
        }

        Map<String, Problem> byId = loadOrgPrivateProblems();
        List<Map<String, Object>> inflatedSolvedRows = new ArrayList<>();
        List<Map<String, Object>> userInflationCandidates = new ArrayList<>();
        List<Map<String, Object>> mutationLog = new ArrayList<>();
        int pendingWithPositiveAward = 0;
        int appliedWithPositiveAward = 0;

        Map<String, Integer> inflatedPointsByUser = new HashMap<>();
        Map<String, Set<String>> inflatedProblemsByUser = new HashMap<>();
        List<UserSolvedProblem> positiveOrgPrivateRows = new ArrayList<>();

        for (UserSolvedProblem row : mongoTemplate.findAll(UserSolvedProblem.class)) {
            Problem problem = byId.get(row.getProblemId());
            if (problem == null || row.getPointsAwarded() <= 0) {
                continue;
            }
            positiveOrgPrivateRows.add(row);
            Map<String, Object> entry = new HashMap<>();
            entry.put("userId", row.getUserId());
            entry.put("problemId", row.getProblemId());
            entry.put("organizationId", problem.getOrganizationId());
            entry.put("pointsAwarded", row.getPointsAwarded());
            entry.put("rewardStatus", row.getRewardStatus() == null ? null : row.getRewardStatus().name());
            entry.put("submissionId", row.getSubmissionId());
            inflatedSolvedRows.add(entry);

            if (row.getRewardStatus() == UserSolvedProblem.RewardStatus.APPLIED) {
                appliedWithPositiveAward++;
                inflatedPointsByUser.merge(row.getUserId(), row.getPointsAwarded(), Integer::sum);
                inflatedProblemsByUser
                    .computeIfAbsent(row.getUserId(), ignored -> new HashSet<>())
                    .add(row.getProblemId());
            } else {
                pendingWithPositiveAward++;
            }
        }

        for (Map.Entry<String, Integer> entry : inflatedPointsByUser.entrySet()) {
            User user = mongoTemplate.findById(entry.getKey(), User.class);
            Map<String, Object> candidate = new HashMap<>();
            candidate.put("userId", entry.getKey());
            candidate.put("username", user == null ? null : user.getUsername());
            candidate.put("currentTotalPoints", user == null ? null : user.getTotalPoints());
            candidate.put("currentProblemsSolved", user == null ? null : user.getProblemsSolved());
            candidate.put("suspectedInflatedPoints", entry.getValue());
            candidate.put("suspectedInflatedProblemIds", inflatedProblemsByUser.get(entry.getKey()));
            if (user != null && user.getSolvedProblemIds() != null) {
                List<String> retainedOrgPrivate = user.getSolvedProblemIds().stream()
                    .filter(byId::containsKey)
                    .toList();
                candidate.put("orgPrivateIdsStillOnUser", retainedOrgPrivate);
            }
            userInflationCandidates.add(candidate);
        }

        boolean shouldWrite = applyWrites && !dryRun;
        int clampedRows = 0;
        int usersCorrected = 0;
        if (shouldWrite) {
            for (UserSolvedProblem row : positiveOrgPrivateRows) {
                if (row.getPointsAwarded() <= 0) {
                    continue;
                }
                int before = row.getPointsAwarded();
                Query q = Query.query(Criteria.where("_id").is(row.getId()).and("pointsAwarded").gt(0));
                Update update = new Update().set("pointsAwarded", 0);
                var result = mongoTemplate.updateFirst(q, update, UserSolvedProblem.class);
                if (result.getModifiedCount() > 0) {
                    clampedRows++;
                    mutationLog.add(Map.of(
                        "type", "CLAMP_SOLVED_ROW",
                        "userId", row.getUserId(),
                        "problemId", row.getProblemId(),
                        "beforePoints", before,
                        "afterPoints", 0
                    ));
                }
            }

            for (Map.Entry<String, Integer> entry : inflatedPointsByUser.entrySet()) {
                String userId = entry.getKey();
                int inflate = entry.getValue();
                Set<String> problemIds = inflatedProblemsByUser.getOrDefault(userId, Set.of());
                User user = mongoTemplate.findById(userId, User.class);
                if (user == null || inflate <= 0) {
                    continue;
                }
                int pointsBefore = user.getTotalPoints();
                int solvedBefore = user.getProblemsSolved();
                int pointsAfter = Math.max(0, pointsBefore - inflate);
                int solvedAfter = Math.max(0, solvedBefore - problemIds.size());

                Query userQuery = Query.query(Criteria.where("_id").is(userId));
                Update userUpdate = new Update()
                    .set("totalPoints", pointsAfter)
                    .set("problemsSolved", solvedAfter);
                if (user.getSolvedProblemIds() != null && !problemIds.isEmpty()) {
                    userUpdate.pullAll("solvedProblemIds", problemIds.toArray());
                }
                var updated = mongoTemplate.updateFirst(userQuery, userUpdate, User.class);
                if (updated.getModifiedCount() > 0) {
                    usersCorrected++;
                    Map<String, Object> log = new HashMap<>();
                    log.put("type", "CORRECT_USER_TOTALS");
                    log.put("userId", userId);
                    log.put("pointsBefore", pointsBefore);
                    log.put("pointsAfter", pointsAfter);
                    log.put("problemsSolvedBefore", solvedBefore);
                    log.put("problemsSolvedAfter", solvedAfter);
                    log.put("removedProblemIds", problemIds);
                    mutationLog.add(log);
                }
            }
            report.put("mutationsApplied", clampedRows > 0 || usersCorrected > 0);
        }

        report.put("status", "COMPLETED");
        report.put("orgPrivateProblemCount", byId.size());
        report.put("inflatedSolvedRowCount", inflatedSolvedRows.size());
        report.put("appliedWithPositiveAward", appliedWithPositiveAward);
        report.put("pendingOrFailedWithPositiveAward", pendingWithPositiveAward);
        report.put("affectedUserCount", userInflationCandidates.size());
        report.put("inflatedSolvedRows", inflatedSolvedRows);
        report.put("userInflationCandidates", userInflationCandidates);
        report.put("clampedSolvedRows", clampedRows);
        report.put("usersCorrected", usersCorrected);
        report.put("mutationLog", mutationLog);
        report.put("message", !shouldWrite
            ? "Dry-run only. No database records were modified."
            : "Idempotent corrections applied where inflated awards were found.");

        Map<String, Object> auditDoc = new HashMap<>(report);
        auditDoc.put("_id", runId);
        auditDoc.put("createdAt", new Date());
        mongoTemplate.save(auditDoc, AUDIT_COLLECTION);

        logger.info(
            "Org-private points reconciliation runId={} dryRun={} inflatedRows={} users={} clamped={} corrected={}",
            runId,
            !shouldWrite,
            inflatedSolvedRows.size(),
            userInflationCandidates.size(),
            clampedRows,
            usersCorrected);
        return report;
    }

    private Map<String, Problem> loadOrgPrivateProblems() {
        List<Problem> orgPrivateProblems = mongoTemplate.find(
            Query.query(Criteria.where("organizationId").exists(true).ne(null).ne("")),
            Problem.class);
        Map<String, Problem> byId = new HashMap<>();
        for (Problem problem : orgPrivateProblems) {
            if (visibilityPolicy.normalize(problem.getOrganizationId()) != null) {
                byId.put(problem.getId(), problem);
            }
        }
        return byId;
    }
}
