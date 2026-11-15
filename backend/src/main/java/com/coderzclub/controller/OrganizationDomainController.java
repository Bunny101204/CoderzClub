package com.coderzclub.controller;

import com.coderzclub.service.OrganizationDomainService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/organizations/{organizationId}/domains")
public class OrganizationDomainController {
    private final OrganizationDomainService domainService;

    public OrganizationDomainController(OrganizationDomainService domainService) {
        this.domainService = domainService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication, @PathVariable String organizationId) {
        return ResponseEntity.ok(Map.of("domains", domainService.list(authentication, organizationId)));
    }

    @PostMapping
    public ResponseEntity<?> register(
        Authentication authentication,
        @PathVariable String organizationId,
        @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(domainService.register(
            authentication,
            organizationId,
            body == null ? null : body.get("hostname")
        ));
    }

    @PostMapping("/{domainId}/verify-local")
    public ResponseEntity<?> verifyLocal(
        Authentication authentication,
        @PathVariable String organizationId,
        @PathVariable String domainId,
        @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(domainService.markVerifiedLocally(
            authentication,
            organizationId,
            domainId,
            body == null ? null : body.get("token")
        ));
    }

    @PostMapping("/{domainId}/activate")
    public ResponseEntity<?> activate(
        Authentication authentication,
        @PathVariable String organizationId,
        @PathVariable String domainId
    ) {
        return ResponseEntity.ok(domainService.activate(authentication, organizationId, domainId));
    }

    @PostMapping("/{domainId}/disable")
    public ResponseEntity<?> disable(
        Authentication authentication,
        @PathVariable String organizationId,
        @PathVariable String domainId
    ) {
        return ResponseEntity.ok(domainService.disable(authentication, organizationId, domainId));
    }
}
