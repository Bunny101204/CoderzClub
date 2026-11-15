package com.coderzclub.controller;

import com.coderzclub.config.AuthenticatedUser;
import com.coderzclub.dto.ContestWriteRequest;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.ContestService;
import com.coderzclub.service.OrganizationAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Contest administration. Without {@code X-Organization-Id}, only platform admins may
 * manage legacy/platform contests. With a membership-validated organization header,
 * organization owners/admins may manage contests for that tenant only.
 */
@RestController
@RequestMapping("/api/admin/contests")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class AdminContestController {
    private final ContestService contestService;
    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccess;

    public AdminContestController(
        ContestService contestService,
        UserRepository userRepository,
        OrganizationAccessService organizationAccess
    ) {
        this.contestService = contestService;
        this.userRepository = userRepository;
        this.organizationAccess = organizationAccess;
    }

    @PostMapping
    public ResponseEntity<?> create(
        @RequestBody ContestWriteRequest body,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.create(body, requireCurrentUserId(), organizationId));
    }

    @GetMapping
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.adminList(page, size, organizationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(
        @PathVariable String id,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.adminGet(id, organizationId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(
        @PathVariable String id,
        @RequestBody ContestWriteRequest body,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.update(id, body, organizationId));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<?> publish(
        @PathVariable String id,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.publish(id, organizationId));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<?> unpublish(
        @PathVariable String id,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        return ResponseEntity.ok(contestService.unpublish(id, organizationId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
        @PathVariable String id,
        Authentication authentication,
        @RequestHeader(value = OrganizationAccessService.ORGANIZATION_HEADER, required = false) String organizationHeader
    ) {
        String organizationId = resolveOrganizationContext(authentication, organizationHeader);
        contestService.delete(id, organizationId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    private String resolveOrganizationContext(Authentication authentication, String organizationHeader) {
        if (!StringUtils.hasText(organizationHeader)) {
            organizationAccess.ensurePlatformAdmin(authentication);
            return null;
        }
        String organizationId = organizationAccess.requireAuthorizedOrganizationId(authentication, organizationHeader);
        organizationAccess.requireOrgAdministrator(authentication, organizationId);
        return organizationId;
    }

    private String requireCurrentUserId() {
        User user = AuthenticatedUser.current(userRepository)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
        return user.getId();
    }
}
