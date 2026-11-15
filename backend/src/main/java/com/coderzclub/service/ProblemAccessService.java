package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Single place for problem listing, detail, and practice execution decisions.
 * Contest submit is granted by ContestSubmissionAdmissionService, not here.
 *
 * Organization-private problems ({@code organizationId} set) are invisible to
 * non-members except when an eligible LIVE/ENDED contest grants contest-only
 * visibility. Platform-scoped null-{@code organizationId} problems keep legacy rules.
 */
@Service
public class ProblemAccessService {
    private final ContestAccessService contestAccessService;
    private final OrganizationAccessService organizationAccess;
    private final OrganizationVisibilityPolicy visibilityPolicy;

    public ProblemAccessService(
        ContestAccessService contestAccessService,
        OrganizationAccessService organizationAccess,
        OrganizationVisibilityPolicy visibilityPolicy
    ) {
        this.contestAccessService = contestAccessService;
        this.organizationAccess = organizationAccess;
        this.visibilityPolicy = visibilityPolicy;
    }

    public static boolean isAdmin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    public boolean canListRestrictedProblems(User user) {
        return isAdmin(user);
    }

    /**
     * Practice list filter. Platform admins see everything. Everyone else sees
     * platform-scoped practice problems plus PUBLIC problems from organizations
     * they actively belong to. When {@code activeOrganizationId} is set, the list
     * is narrowed to platform practice plus that organization's problems (for
     * members / platform admins).
     *
     * Returns criteria to combine with other filters in a single {@code $and}
     * (Spring Data Mongo cannot stack multiple null-key {@code $or} criteria).
     */
    public Criteria practiceListingCriteria(User user) {
        return practiceListingCriteria(user, null);
    }

    public Criteria practiceListingCriteria(User user, String activeOrganizationId) {
        String scopedOrg = visibilityPolicy.normalize(activeOrganizationId);
        if (canListRestrictedProblems(user)) {
            if (scopedOrg != null) {
                return visibilityPolicy.exactOrganizationCriteria(scopedOrg);
            }
            return null;
        }
        List<String> memberships = membershipOrganizationIds(user);
        if (scopedOrg != null) {
            if (!memberships.contains(scopedOrg)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization membership is required");
            }
            // Institution admins authoring contests may pick:
            // - platform PUBLIC practice problems
            // - any problem owned by their active organization (including CONTEST_ONLY)
            // Platform CONTEST_ONLY and other orgs' problems stay excluded.
            if (canAdministerOrganization(user, scopedOrg)) {
                return new Criteria().orOperator(
                    new Criteria().andOperator(
                        visibilityPolicy.platformScopedCriteria(),
                        ProblemAvailability.practiceListingCriteria()
                    ),
                    visibilityPolicy.exactOrganizationCriteria(scopedOrg)
                );
            }
            return new Criteria().andOperator(
                visibilityPolicy.platformOrOrganizationsCriteria(List.of(scopedOrg)),
                ProblemAvailability.practiceListingCriteria()
            );
        }
        return new Criteria().andOperator(
            visibilityPolicy.platformOrOrganizationsCriteria(memberships),
            ProblemAvailability.practiceListingCriteria()
        );
    }

    private boolean canAdministerOrganization(User user, String organizationId) {
        User actor = effectiveUser(user);
        if (actor == null || !StringUtils.hasText(organizationId)) {
            return false;
        }
        return organizationAccess.findActiveMembership(organizationId, actor.getId())
            .map(membership -> membership.canAdministerOrganization())
            .orElse(false);
    }

    public void applyPracticeListingFilter(Query query, User user) {
        applyPracticeListingFilter(query, user, null);
    }

    public void applyPracticeListingFilter(Query query, User user, String activeOrganizationId) {
        if (query == null) {
            return;
        }
        Criteria criteria = practiceListingCriteria(user, activeOrganizationId);
        if (criteria != null) {
            query.addCriteria(criteria);
        }
    }

    public boolean canView(User user, Problem problem) {
        if (problem == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        if (!canAccessOrganizationScope(user, problem.getOrganizationId())) {
            // Contest eligibility may still grant CONTEST_ONLY / org contest problems.
            User actor = effectiveUser(user);
            if (actor == null) {
                return false;
            }
            return contestAccessService.hasAccessibleLiveOrEndedContestContaining(actor, problem.getId());
        }
        if (ProblemAvailability.isPublic(problem)) {
            return true;
        }
        User actor = effectiveUser(user);
        if (actor == null) {
            return false;
        }
        return contestAccessService.hasAccessibleLiveOrEndedContestContaining(actor, problem.getId());
    }

    public void requireViewable(User user, Problem problem) {
        if (!canView(user, problem)) {
            throw notFound();
        }
    }

    /**
     * Ordinary practice Run / Submit. CONTEST_ONLY is rejected for everyone
     * except admins (authoring). Org-private PUBLIC requires membership.
     * Eligible LIVE contest submit uses the contest submission endpoint.
     */
    public void requirePracticeExecute(User user, Problem problem) {
        if (problem == null) {
            throw notFound();
        }
        if (!ProblemAvailability.isPublic(problem)) {
            if (isAdmin(user) && user != null && !user.isDeleted()) {
                return;
            }
            throw notFound();
        }
        if (!canAccessOrganizationScope(user, problem.getOrganizationId())) {
            throw notFound();
        }
        if (isAdmin(user) || ProblemAvailability.isPublic(problem)) {
            return;
        }
        throw notFound();
    }

    public boolean canViewInBundle(User user, Problem problem) {
        if (problem == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        return ProblemAvailability.isPublic(problem)
            && canAccessOrganizationScope(user, problem.getOrganizationId());
    }

    public boolean isAnonymouslyCacheable(Problem problem) {
        return problem != null
            && ProblemAvailability.isPublic(problem)
            && visibilityPolicy.isPlatformScoped(problem.getOrganizationId());
    }

    public void requireCanManageProblem(User user, Problem problem, String activeOrganizationId) {
        if (problem == null) {
            throw notFound();
        }
        if (visibilityPolicy.isPlatformScoped(problem.getOrganizationId())) {
            if (!isAdmin(user)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
            }
            if (StringUtils.hasText(activeOrganizationId)) {
                throw notFound();
            }
            return;
        }
        if (isAdmin(user)) {
            if (StringUtils.hasText(activeOrganizationId)
                && !visibilityPolicy.sameTenant(problem.getOrganizationId(), activeOrganizationId)) {
                throw notFound();
            }
            return;
        }
        String scoped = visibilityPolicy.normalize(activeOrganizationId);
        if (scoped == null || !visibilityPolicy.sameTenant(problem.getOrganizationId(), scoped)) {
            throw notFound();
        }
        User actor = effectiveUser(user);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        var membership = organizationAccess.requireActiveMembership(scoped, actor.getId());
        if (!membership.canAdministerOrganization()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient organization role");
        }
        organizationAccess.requireActiveOrganization(scoped);
    }

    public void requireCanCreateProblem(User user, String activeOrganizationId) {
        if (isAdmin(user)) {
            if (StringUtils.hasText(activeOrganizationId)) {
                organizationAccess.requireActiveOrganization(activeOrganizationId.trim());
            }
            return;
        }
        String scoped = visibilityPolicy.normalize(activeOrganizationId);
        if (scoped == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
        }
        User actor = effectiveUser(user);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        var membership = organizationAccess.requireActiveMembership(scoped, actor.getId());
        if (!membership.canAdministerOrganization()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient organization role");
        }
        organizationAccess.requireActiveOrganization(scoped);
    }

    private boolean canAccessOrganizationScope(User user, String organizationId) {
        if (visibilityPolicy.isPlatformScoped(organizationId)) {
            return true;
        }
        if (!organizationAccess.isActiveOrganizationId(organizationId)) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        User actor = effectiveUser(user);
        if (actor == null) {
            return false;
        }
        return organizationAccess.findActiveMembership(organizationId, actor.getId()).isPresent();
    }

    private List<String> membershipOrganizationIds(User user) {
        User actor = effectiveUser(user);
        if (actor == null) {
            return List.of();
        }
        return organizationAccess.listActiveOrganizationIdsForUser(actor.getId());
    }

    private static User effectiveUser(User user) {
        if (user == null || user.isDeleted() || user.getId() == null) {
            return null;
        }
        return user;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Problem not found");
    }
}
