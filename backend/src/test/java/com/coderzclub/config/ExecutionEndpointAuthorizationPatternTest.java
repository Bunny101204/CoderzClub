package com.coderzclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionEndpointAuthorizationPatternTest {
    @Test
    void runPublicIsDistinctFromAdminProblemCreate() {
        AntPathRequestMatcher runPublic = new AntPathRequestMatcher("/api/problems/*/run-public", "POST");
        AntPathRequestMatcher adminCreate = new AntPathRequestMatcher("/api/problems", "POST");
        assertTrue(runPublic.matches(request("/api/problems/p1/run-public", "POST")));
        assertFalse(adminCreate.matches(request("/api/problems/p1/run-public", "POST")));
    }

    @Test
    void executionPatchIsDistinctFromRunPublic() {
        AntPathRequestMatcher adminPatch = new AntPathRequestMatcher("/api/problems/*/execution", "PATCH");
        assertTrue(adminPatch.matches(request("/api/problems/p1/execution", "PATCH")));
        assertFalse(adminPatch.matches(request("/api/problems/p1/run-public", "POST")));
    }

    private static org.springframework.mock.web.MockHttpServletRequest request(String uri, String method) {
        org.springframework.mock.web.MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
