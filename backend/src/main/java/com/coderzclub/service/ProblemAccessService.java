package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.ProblemAvailability;
import com.coderzclub.model.User;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single place for problem listing, detail, and practice execution decisions.
 * Contest submit is granted by ContestSubmissionAdmissionService, not here.
 */
@Service
public class ProblemAccessService {
    private final ContestAccessService contestAccessService;

    public ProblemAccessService(ContestAccessService contestAccessService) {
        this.contestAccessService = contestAccessService;
    }

    public static boolean isAdmin(User user) {
        return user != null && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    public boolean canListRestrictedProblems(User user) {
        return isAdmin(user);
    }

    public void applyPracticeListingFilter(Query query, User user) {
        if (query == null || canListRestrictedProblems(user)) {
            return;
        }
        query.addCriteria(ProblemAvailability.practiceListingCriteria());
    }

    public boolean canView(User user, Problem problem) {
        if (problem == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
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
     * except admins (authoring). Eligible LIVE contest submit uses the
     * contest submission endpoint, not this method.
     */
    public void requirePracticeExecute(User user, Problem problem) {
        if (problem == null) {
            throw notFound();
        }
        if (ProblemAvailability.isPublic(problem)) {
            return;
        }
        if (isAdmin(user) && !user.isDeleted()) {
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
        return ProblemAvailability.isPublic(problem);
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
