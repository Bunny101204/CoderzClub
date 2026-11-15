package com.coderzclub.service;

import com.coderzclub.config.ContestProperties;
import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchMember;
import com.coderzclub.model.Contest;
import com.coderzclub.model.ContestAudienceType;
import com.coderzclub.model.ContestStatus;
import com.coderzclub.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only contest eligibility and problem-grant queries.
 * Extracted so {@link ProblemAccessService} can consult contests without a
 * {@link ContestService} cycle.
 */
@Service
public class ContestAccessService {
    private final MongoTemplate mongoTemplate;
    private final ContestProperties contestProperties;
    private final Clock clock;
    private final OrganizationAccessService organizationAccess;

    public ContestAccessService(MongoTemplate mongoTemplate, ContestProperties contestProperties, Clock clock) {
        this(mongoTemplate, contestProperties, clock, null);
    }

    @Autowired
    public ContestAccessService(
        MongoTemplate mongoTemplate,
        ContestProperties contestProperties,
        Clock clock,
        OrganizationAccessService organizationAccess
    ) {
        this.mongoTemplate = mongoTemplate;
        this.contestProperties = contestProperties;
        this.clock = clock;
        this.organizationAccess = organizationAccess;
    }

    public ContestStatus status(Contest contest) {
        return status(contest, clock.instant());
    }

    public ContestStatus status(Contest contest, Instant now) {
        return ContestStatus.of(contest, now);
    }

    public boolean isEligible(User user, Contest contest, UserBatchScope scope) {
        return isEligible(user, contest, scope, clock.instant());
    }

    public boolean isEligible(User user, Contest contest, UserBatchScope scope, Instant now) {
        if (user == null || !user.canAuthenticate() || contest == null || !contest.isPublished()) {
            return false;
        }
        if (!canSeeOrganizationScopedContest(user, contest)) {
            return false;
        }
        if (contest.getAudienceType() == ContestAudienceType.ALL_USERS) {
            return contestProperties.isGlobalEnabled();
        }
        if (contest.getAudienceType() != ContestAudienceType.BATCHES) {
            return false;
        }
        List<String> contestBatches = contest.getBatchIds() == null ? List.of() : contest.getBatchIds();
        if (contestBatches.isEmpty() || scope == null) {
            return false;
        }
        ContestStatus current = status(contest, now);
        if (current == ContestStatus.ENDED) {
            return intersects(contestBatches, scope.allBatchIds);
        }
        if (current == ContestStatus.UPCOMING || current == ContestStatus.LIVE) {
            return intersects(contestBatches, scope.activeBatchIds);
        }
        return false;
    }

    public UserBatchScope scopeForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return UserBatchScope.EMPTY;
        }
        List<BatchMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)),
            BatchMember.class
        );
        List<String> allIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (BatchMember membership : memberships) {
            if (membership.getBatchId() == null || membership.getBatchId().isBlank()) {
                continue;
            }
            if (seen.add(membership.getBatchId())) {
                allIds.add(membership.getBatchId());
            }
        }
        if (allIds.isEmpty()) {
            return UserBatchScope.EMPTY;
        }
        List<Batch> batches = mongoTemplate.find(
            Query.query(Criteria.where("_id").in(allIds)),
            Batch.class
        );
        List<String> activeIds = new ArrayList<>();
        Set<String> members = new HashSet<>(allIds);
        for (Batch batch : batches) {
            if (batch != null && batch.getId() != null && members.contains(batch.getId()) && batch.isActive()) {
                activeIds.add(batch.getId());
            }
        }
        return new UserBatchScope(allIds, activeIds);
    }

    /**
     * Published contests the user may list: UPCOMING/LIVE (active batch), ENDED
     * (historical membership), and ALL_USERS when the flag is on.
     */
    public Criteria publishedEligibilityCriteria(UserBatchScope scope, Instant now) {
        return publishedEligibilityCriteria(scope, now, null);
    }

    public Criteria publishedEligibilityCriteria(UserBatchScope scope, Instant now, User user) {
        List<Criteria> or = new ArrayList<>();
        if (scope != null && !scope.activeBatchIds.isEmpty()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.BATCHES)
                .and("batchIds").in(scope.activeBatchIds)
                .and("endAt").gt(now));
        }
        if (scope != null && !scope.allBatchIds.isEmpty()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.BATCHES)
                .and("batchIds").in(scope.allBatchIds)
                .and("endAt").lte(now));
        }
        if (contestProperties.isGlobalEnabled()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.ALL_USERS));
        }
        if (or.isEmpty()) {
            return null;
        }
        return new Criteria().andOperator(
            Criteria.where("published").is(true),
            organizationVisibilityCriteria(user),
            new Criteria().orOperator(or.toArray(Criteria[]::new))
        );
    }

    /**
     * True when at least one published LIVE or ENDED contest that contains
     * {@code problemId} grants this user access. Does not load contest documents.
     * UPCOMING contests never grant problem detail.
     *
     * Query uses equality on {@code published} + multikey {@code problemIds},
     * then residual eligibility predicates. Indexed by
     * {@code published_problemIds_idx}. batchIds is not in that index because
     * MongoDB cannot compound two array fields.
     */
    public boolean hasAccessibleLiveOrEndedContestContaining(User user, String problemId) {
        if (user == null || !user.canAuthenticate() || user.getId() == null
            || problemId == null || problemId.isBlank()) {
            return false;
        }
        UserBatchScope scope = scopeForUser(user.getId());
        Criteria grant = liveOrEndedGrantCriteria(scope, clock.instant(), user);
        if (grant == null) {
            return false;
        }
        Query query = new Query(new Criteria().andOperator(
            Criteria.where("published").is(true),
            Criteria.where("problemIds").is(problemId),
            grant
        ));
        return mongoTemplate.exists(query, Contest.class);
    }

    private Criteria liveOrEndedGrantCriteria(UserBatchScope scope, Instant now, User user) {
        List<Criteria> or = new ArrayList<>();
        if (scope != null && !scope.activeBatchIds.isEmpty()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.BATCHES)
                .and("batchIds").in(scope.activeBatchIds)
                .and("startAt").lte(now)
                .and("endAt").gt(now));
        }
        if (scope != null && !scope.allBatchIds.isEmpty()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.BATCHES)
                .and("batchIds").in(scope.allBatchIds)
                .and("endAt").lte(now));
        }
        if (contestProperties.isGlobalEnabled()) {
            or.add(Criteria.where("audienceType").is(ContestAudienceType.ALL_USERS)
                .and("startAt").lte(now));
        }
        if (or.isEmpty()) {
            return null;
        }
        return new Criteria().andOperator(
            organizationVisibilityCriteria(user),
            new Criteria().orOperator(or.toArray(Criteria[]::new))
        );
    }

    public boolean canSeeOrganizationScopedContest(User user, Contest contest) {
        if (contest == null || !StringUtils.hasText(contest.getOrganizationId())) {
            return true;
        }
        if (user == null || user.getId() == null) {
            return false;
        }
        if (organizationAccess == null) {
            return organizationAccessFallback(user.getId(), contest.getOrganizationId());
        }
        if (!organizationAccess.isActiveOrganizationId(contest.getOrganizationId())) {
            return false;
        }
        return organizationAccess.findActiveMembership(contest.getOrganizationId(), user.getId()).isPresent();
    }

    /**
     * Platform admins and organization owners/admins may monitor an org contest
     * (leaderboard / participation) without being batch members.
     */
    public boolean canMonitorContest(User user, Contest contest) {
        if (user == null || contest == null || user.isDeleted()) {
            return false;
        }
        if (user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }
        if (!StringUtils.hasText(contest.getOrganizationId()) || user.getId() == null) {
            return false;
        }
        if (organizationAccess == null) {
            return false;
        }
        return organizationAccess.findActiveMembership(contest.getOrganizationId(), user.getId())
            .map(com.coderzclub.model.OrganizationMember::canAdministerOrganization)
            .orElse(false);
    }

    private Criteria organizationVisibilityCriteria(User user) {
        Criteria legacy = new Criteria().orOperator(
            Criteria.where("organizationId").exists(false),
            Criteria.where("organizationId").is(null),
            Criteria.where("organizationId").is("")
        );
        if (user == null || user.getId() == null) {
            return legacy;
        }
        List<String> organizationIds = organizationAccess == null
            ? List.of()
            : organizationAccess.listActiveOrganizationIdsForUser(user.getId());
        if (organizationIds.isEmpty()) {
            return legacy;
        }
        return new Criteria().orOperator(
            legacy,
            Criteria.where("organizationId").in(organizationIds)
        );
    }

    private boolean organizationAccessFallback(String userId, String organizationId) {
        return mongoTemplate.exists(
            Query.query(Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is("ACTIVE")),
            com.coderzclub.model.OrganizationMember.class);
    }

    private static boolean intersects(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> membership = new HashSet<>(right);
        for (String id : left) {
            if (id != null && membership.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public static final class UserBatchScope {
        static final UserBatchScope EMPTY = new UserBatchScope(List.of(), List.of());
        final List<String> allBatchIds;
        final List<String> activeBatchIds;

        UserBatchScope(List<String> allBatchIds, List<String> activeBatchIds) {
            this.allBatchIds = allBatchIds == null ? List.of() : allBatchIds;
            this.activeBatchIds = activeBatchIds == null ? List.of() : activeBatchIds;
        }
    }
}
