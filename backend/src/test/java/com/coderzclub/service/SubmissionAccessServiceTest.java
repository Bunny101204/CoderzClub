package com.coderzclub.service;

import com.coderzclub.model.Problem;
import com.coderzclub.model.Submission;
import com.coderzclub.model.User;
import com.coderzclub.repository.ProblemRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionAccessServiceTest {

    private final ProblemRepository problems = mock(ProblemRepository.class);
    private final ProblemAccessService problemAccess = mock(ProblemAccessService.class);
    private final OrganizationAccessService organizationAccess = mock(OrganizationAccessService.class);
    private final OrganizationVisibilityPolicy visibility =
        new OrganizationVisibilityPolicy(new OrganizationResourceCompatibility());
    private final SubmissionAccessService access =
        new SubmissionAccessService(problems, problemAccess, organizationAccess, visibility);

    @Test
    void publicOwnSubmissionRemainsVisible() {
        User viewer = user("u1");
        Submission submission = submission("u1", "p-public");
        Problem problem = new Problem();
        problem.setId("p-public");
        when(problems.findById("p-public")).thenReturn(Optional.of(problem));

        assertTrue(access.canViewOwnSubmission(viewer, submission));
    }

    @Test
    void removedMemberLosesOrgPrivateHistory() {
        User viewer = user("u1");
        Submission submission = submission("u1", "p-org");
        Problem problem = new Problem();
        problem.setId("p-org");
        problem.setOrganizationId("org-a");
        when(problems.findById("p-org")).thenReturn(Optional.of(problem));
        when(organizationAccess.isActiveOrganizationId("org-a")).thenReturn(true);
        when(organizationAccess.findActiveMembership("org-a", "u1")).thenReturn(Optional.empty());

        assertFalse(access.canViewOwnSubmission(viewer, submission));
    }

    @Test
    void activeMemberKeepsOrgPrivateHistory() {
        User viewer = user("u1");
        Submission submission = submission("u1", "p-org");
        Problem problem = new Problem();
        problem.setId("p-org");
        problem.setOrganizationId("org-a");
        when(problems.findById("p-org")).thenReturn(Optional.of(problem));
        when(organizationAccess.isActiveOrganizationId("org-a")).thenReturn(true);
        when(organizationAccess.findActiveMembership("org-a", "u1"))
            .thenReturn(Optional.of(new com.coderzclub.model.OrganizationMember()));

        assertTrue(access.canViewOwnSubmission(viewer, submission));
    }

    @Test
    void suspendedOrganizationHidesHistory() {
        User viewer = user("u1");
        Submission submission = submission("u1", "p-org");
        Problem problem = new Problem();
        problem.setId("p-org");
        problem.setOrganizationId("org-a");
        when(problems.findById("p-org")).thenReturn(Optional.of(problem));
        when(organizationAccess.isActiveOrganizationId("org-a")).thenReturn(false);

        assertFalse(access.canViewOwnSubmission(viewer, submission));
    }

    @Test
    void otherUsersSubmissionNeverVisible() {
        assertFalse(access.canViewOwnSubmission(user("u1"), submission("u2", "p-public")));
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setRole("USER");
        return user;
    }

    private static Submission submission(String userId, String problemId) {
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(problemId);
        return submission;
    }
}
