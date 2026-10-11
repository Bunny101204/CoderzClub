package com.coderzclub.config;

import com.coderzclub.controller.AdminContestController;
import com.coderzclub.controller.ContestController;
import com.coderzclub.controller.ContestSubmissionController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContestSecurityPatternTest {
    @Test
    void adminContestApiIsAdminOnly() {
        RequestMapping mapping = AdminContestController.class.getAnnotation(RequestMapping.class);
        PreAuthorize pre = AdminContestController.class.getAnnotation(PreAuthorize.class);
        assertEquals("/api/admin/contests", mapping.value()[0]);
        assertEquals("hasRole('ADMIN')", pre.value());
    }

    @Test
    void userContestApiRequiresAuthenticatedUserOrAdmin() {
        RequestMapping mapping = ContestController.class.getAnnotation(RequestMapping.class);
        PreAuthorize pre = ContestController.class.getAnnotation(PreAuthorize.class);
        assertEquals("/api/contests", mapping.value()[0]);
        assertEquals("hasAnyRole('USER','ADMIN')", pre.value());
    }

    @Test
    void contestSubmitApiRequiresAuthenticatedUserOrAdmin() {
        RequestMapping mapping = ContestSubmissionController.class.getAnnotation(RequestMapping.class);
        PreAuthorize pre = ContestSubmissionController.class.getAnnotation(PreAuthorize.class);
        assertEquals("/api/contests", mapping.value()[0]);
        assertEquals("hasAnyRole('USER','ADMIN')", pre.value());
    }

    @Test
    void contestRunApiRequiresAuthenticatedUserOrAdmin() {
        PreAuthorize pre = com.coderzclub.controller.ContestRunController.class.getAnnotation(PreAuthorize.class);
        assertEquals("hasAnyRole('USER','ADMIN')", pre.value());
        org.springframework.security.web.util.matcher.AntPathRequestMatcher runs =
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/contest-runs/**");
        assertTrue(runs.matches(request("/api/contest-runs/run-1", "GET")));
    }

    @Test
    void securityConfigAdminPatternCoversContestMutations() {
        org.springframework.security.web.util.matcher.AntPathRequestMatcher admin =
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/admin/**");
        assertTrue(admin.matches(request("/api/admin/contests", "POST")));
        assertTrue(admin.matches(request("/api/admin/contests/c1", "PATCH")));
        assertTrue(admin.matches(request("/api/admin/contests/c1/publish", "POST")));
        assertTrue(admin.matches(request("/api/admin/contests/c1/unpublish", "POST")));
        assertTrue(admin.matches(request("/api/admin/contests/c1", "DELETE")));
        assertFalse(admin.matches(request("/api/contests", "GET")));
        assertFalse(admin.matches(request("/api/contests/c1", "GET")));
    }

    @Test
    void securityConfigUserPatternDoesNotOpenAdminContests() {
        org.springframework.security.web.util.matcher.AntPathRequestMatcher exact =
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/contests");
        org.springframework.security.web.util.matcher.AntPathRequestMatcher nested =
            new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/contests/**");
        assertTrue(exact.matches(request("/api/contests", "GET")));
        assertTrue(nested.matches(request("/api/contests/c1", "GET")));
        assertTrue(nested.matches(request("/api/contests/c1/problems/p1", "GET")));
        assertTrue(nested.matches(request("/api/contests/c1/leaderboard", "GET")));
        assertTrue(nested.matches(request("/api/contests/c1/problems/p1/submission-jobs", "POST")));
        assertTrue(nested.matches(request("/api/contests/c1/problems/p1/runs", "POST")));
        assertFalse(nested.matches(request("/api/admin/contests", "POST")));
        assertFalse(exact.matches(request("/api/admin/contests", "POST")));
    }

    private static org.springframework.mock.web.MockHttpServletRequest request(String uri, String method) {
        org.springframework.mock.web.MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
