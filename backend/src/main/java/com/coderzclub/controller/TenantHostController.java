package com.coderzclub.controller;

import com.coderzclub.config.TrustedRequestHostResolver;
import com.coderzclub.service.OrganizationDomainService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public tenant hint derived from a trusted request host.
 * Disabled by default via {@code app.custom-domains.public-resolution-enabled}.
 * Never authorizes organization-private data access — membership checks remain authoritative.
 */
@RestController
@RequestMapping("/api/public/tenant")
public class TenantHostController {
    private final TrustedRequestHostResolver hostResolver;
    private final OrganizationDomainService domainService;

    public TenantHostController(
        TrustedRequestHostResolver hostResolver,
        OrganizationDomainService domainService
    ) {
        this.hostResolver = hostResolver;
        this.domainService = domainService;
    }

    @GetMapping
    public ResponseEntity<?> resolve(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("publicResolutionEnabled", domainService.isPublicResolutionEnabled());
        body.put("trustsForwardedHost", hostResolver.trustsForwardedHost());
        if (!domainService.isPublicResolutionEnabled()) {
            body.put("organizationId", null);
            body.put("hostname", null);
            return ResponseEntity.ok(body);
        }
        String rawHost = hostResolver.resolveTrustedHostname(request);
        String organizationId = domainService.resolveOrganizationIdForHostname(rawHost);
        String normalized = null;
        try {
            if (rawHost != null) {
                normalized = OrganizationDomainService.normalizeHostname(rawHost);
            }
        } catch (Exception ignored) {
            normalized = null;
        }
        body.put("hostname", organizationId == null ? null : normalized);
        body.put("organizationId", organizationId);
        return ResponseEntity.ok(body);
    }
}
