package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conservative access policy for submission history.
 *
 * <p>Public-platform submissions (problem {@code organizationId} null) remain visible
 * to their owner. Organization-private submissions require an active membership in
 * that organization (or platform admin). Removed members therefore lose access to
 * org-private history until a retention policy is explicitly approved.
 */
@Service
public class SubmissionAccessService {
    private final ProblemRepository problemRepository;
    private final ProblemAccessService problemAccessService;
    private final OrganizationAccessService organizationAccess;
    private final OrganizationVisibilityPolicy visibilityPolicy;

    public SubmissionAccessService(
        ProblemRepository problemRepository,
        ProblemAccessService problemAccessService,
        OrganizationAccessService organizationAccess,
        OrganizationVisibilityPolicy visibilityPolicy
    ) {
        this.problemRepository = problemRepository;
        this.problemAccessService = problemAccessService;
        this.organizationAccess = organizationAccess;
        this.visibilityPolicy = visibilityPolicy;
    }

    public boolean canViewOwnSubmission(User viewer, Submission submission) {
        if (viewer == null || viewer.isDeleted() || viewer.getId() == null || submission == null) {
            return false;
        }
        if (!viewer.getId().equals(submission.getUserId())) {
            return false;
        }
        if (ProblemAccessService.isAdmin(viewer)) {
            return true;
        }
        Problem problem = loadProblem(submission.getProblemId());
        if (problem == null) {
            // Orphaned submission: treat as owner-only platform history.
            return true;
        }
        String organizationId = visibilityPolicy.normalize(problem.getOrganizationId());
        if (organizationId == null) {
            return true;
        }
        if (!organizationAccess.isActiveOrganizationId(organizationId)) {
            return false;
        }
        return organizationAccess.findActiveMembership(organizationId, viewer.getId()).isPresent();
    }

    public void requireViewOwnSubmission(User viewer, Submission submission) {
        if (!canViewOwnSubmission(viewer, submission)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found");
        }
    }

    /**
     * Whether the viewer may see history rows for this problem id (including aliases).
     * Used to filter list endpoints without leaking org-private problem existence.
     */
    public boolean canListHistoryForProblem(User viewer, String problemId) {
        if (viewer == null || viewer.isDeleted() || !StringUtils.hasText(problemId)) {
            return false;
        }
        if (ProblemAccessService.isAdmin(viewer)) {
            return true;
        }
        Problem problem = loadProblem(problemId);
        if (problem == null) {
            return true;
        }
        String organizationId = visibilityPolicy.normalize(problem.getOrganizationId());
        if (organizationId == null) {
            return true;
        }
        if (!organizationAccess.isActiveOrganizationId(organizationId)) {
            return false;
        }
        return organizationAccess.findActiveMembership(organizationId, viewer.getId()).isPresent();
    }

    public boolean canViewProblemContent(User viewer, String problemId) {
        Problem problem = loadProblem(problemId);
        if (problem == null) {
            return false;
        }
        return problemAccessService.canView(viewer, problem);
    }

    private Problem loadProblem(String problemId) {
        if (!StringUtils.hasText(problemId)) {
            return null;
        }
        return problemRepository.findById(problemId.trim()).orElseGet(() -> {
            try {
                return problemRepository.findByNumericId(Integer.valueOf(problemId.trim())).orElse(null);
            } catch (NumberFormatException ignored) {
                return null;
            }
        });
    }
}
