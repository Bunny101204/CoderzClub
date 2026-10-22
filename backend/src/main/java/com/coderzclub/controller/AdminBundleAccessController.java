package com.coderzclub.controller;

import com.coderzclub.dto.BundleGrantWriteRequest;
import com.coderzclub.dto.BundleVisibilityWriteRequest;
import com.coderzclub.model.BundleAccessGrant;
import com.coderzclub.model.ProblemBundle;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import com.coderzclub.service.BundleAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/bundles/{bundleId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBundleAccessController {
    private final BundleAccessService bundleAccessService;
    private final UserRepository userRepository;

    public AdminBundleAccessController(BundleAccessService bundleAccessService, UserRepository userRepository) {
        this.bundleAccessService = bundleAccessService;
        this.userRepository = userRepository;
    }

    @GetMapping("/access")
    public ResponseEntity<?> access(@PathVariable String bundleId) {
        return ResponseEntity.ok(bundleAccessService.listGrants(bundleId));
    }

    @PutMapping("/visibility")
    public ResponseEntity<?> visibility(@PathVariable String bundleId, @RequestBody BundleVisibilityWriteRequest body) {
        ProblemBundle bundle = bundleAccessService.setVisibility(bundleId, body == null ? null : body.getVisibility());
        return ResponseEntity.ok(Map.of(
            "id", bundle.getId(),
            "visibility", BundleAccessService.visibilityOf(bundle)
        ));
    }

    @PostMapping("/grants")
    public ResponseEntity<?> addGrant(
        @PathVariable String bundleId,
        @RequestBody BundleGrantWriteRequest body,
        Authentication authentication
    ) {
        BundleAccessGrant grant = bundleAccessService.addGrant(
            bundleId,
            body == null ? null : body.getSubjectType(),
            body == null ? null : body.getSubjectId(),
            currentUserId(authentication)
        );
        return ResponseEntity.ok(Map.of(
            "id", grant.getId(),
            "bundleId", grant.getBundleId(),
            "subjectType", grant.getSubjectType(),
            "subjectId", grant.getSubjectId()
        ));
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<?> removeGrant(@PathVariable String bundleId, @PathVariable String grantId) {
        bundleAccessService.removeGrant(bundleId, grantId);
        return ResponseEntity.ok(Map.of("removed", true));
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).map(User::getId).orElse(null);
    }
}
