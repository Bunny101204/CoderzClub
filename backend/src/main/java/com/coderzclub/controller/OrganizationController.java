package com.coderzclub.controller;

import com.coderzclub.dto.OrganizationCreateRequest;
import com.coderzclub.dto.OrganizationMemberRequest;
import com.coderzclub.dto.OrganizationRoleUpdateRequest;
import com.coderzclub.dto.OrganizationUpdateRequest;
import com.coderzclub.service.OrganizationAccessService;
import com.coderzclub.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;
    private final OrganizationAccessService access;

    public OrganizationController(OrganizationService organizationService, OrganizationAccessService access) {
        this.organizationService = organizationService;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<?> create(
        Authentication authentication,
        @Valid @RequestBody OrganizationCreateRequest request
    ) {
        return ResponseEntity.ok(organizationService.createOrganization(authentication, request));
    }

    @GetMapping
    public ResponseEntity<?> listMine(Authentication authentication) {
        return ResponseEntity.ok(Map.of("organizations", organizationService.listMyOrganizations(authentication)));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<?> getOne(Authentication authentication, @PathVariable String organizationId) {
        return ResponseEntity.ok(organizationService.getOrganization(authentication, organizationId));
    }

    @PatchMapping("/{organizationId}")
    public ResponseEntity<?> update(
        Authentication authentication,
        @PathVariable String organizationId,
        @RequestBody OrganizationUpdateRequest request
    ) {
        return ResponseEntity.ok(organizationService.updateOrganization(authentication, organizationId, request));
    }

    @GetMapping("/{organizationId}/branding/preview")
    public ResponseEntity<?> previewBranding(Authentication authentication, @PathVariable String organizationId) {
        return ResponseEntity.ok(organizationService.previewBranding(authentication, organizationId));
    }

    @PostMapping("/{organizationId}/branding/reset")
    public ResponseEntity<?> resetBranding(Authentication authentication, @PathVariable String organizationId) {
        return ResponseEntity.ok(organizationService.resetBranding(authentication, organizationId));
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<?> listMembers(Authentication authentication, @PathVariable String organizationId) {
        return ResponseEntity.ok(Map.of("members", organizationService.listMembers(authentication, organizationId)));
    }

    @PostMapping("/{organizationId}/members")
    public ResponseEntity<?> addMember(
        Authentication authentication,
        @PathVariable String organizationId,
        @Valid @RequestBody OrganizationMemberRequest request
    ) {
        return ResponseEntity.ok(organizationService.addOrUpdateMember(authentication, organizationId, request));
    }

    @PutMapping("/{organizationId}/members/{userId}/roles")
    public ResponseEntity<?> updateRoles(
        Authentication authentication,
        @PathVariable String organizationId,
        @PathVariable String userId,
        @Valid @RequestBody OrganizationRoleUpdateRequest request
    ) {
        return ResponseEntity.ok(organizationService.updateMemberRoles(authentication, organizationId, userId, request));
    }

    @DeleteMapping("/{organizationId}/members/{userId}")
    public ResponseEntity<?> removeMember(
        Authentication authentication,
        @PathVariable String organizationId,
        @PathVariable String userId
    ) {
        return ResponseEntity.ok(organizationService.removeMember(authentication, organizationId, userId));
    }

    /**
     * Validates the caller's right to use an organization context. Does not trust
     * the header alone: membership or platform admin is required.
     */
    @GetMapping("/active-context")
    public ResponseEntity<?> activeContext(
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationId
    ) {
        String authorizedId = access.requireAuthorizedOrganizationId(authentication, organizationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", authorizedId);
        body.put("organization", organizationService.getOrganization(authentication, authorizedId));
        return ResponseEntity.ok(body);
    }
}
