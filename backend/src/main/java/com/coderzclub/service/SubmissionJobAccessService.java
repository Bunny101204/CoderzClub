package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.SubmissionJob;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import com.coderzclub.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SubmissionJobAccessService {
    private final UserRepository userRepository;
    private final ProblemRepository problemRepository;
    private final OrganizationAccessService organizationAccess;
    private final OrganizationVisibilityPolicy visibilityPolicy;

    public SubmissionJobAccessService(
        UserRepository userRepository,
        ProblemRepository problemRepository,
        OrganizationAccessService organizationAccess,
        OrganizationVisibilityPolicy visibilityPolicy
    ) {
        this.userRepository = userRepository;
        this.problemRepository = problemRepository;
        this.organizationAccess = organizationAccess;
        this.visibilityPolicy = visibilityPolicy;
    }

    public boolean canView(Authentication authentication, SubmissionJob job) {
        if (authentication == null || !authentication.isAuthenticated() || job == null) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                || "ADMIN".equals(authority.getAuthority()));
        if (admin) return true;
        User viewer = userRepository.findByUsername(authentication.getName())
            .filter(user -> !user.isDeleted())
            .orElse(null);
        if (viewer == null || viewer.getId() == null || !viewer.getId().equals(job.getUserId())) {
            return false;
        }
        if (!StringUtils.hasText(job.getProblemId())) {
            return true;
        }
        Problem problem = problemRepository.findById(job.getProblemId()).orElse(null);
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
}