package com.coderzclub.controller;

import com.coderzclub.service.OrgPrivatePointsReconciliationService;
import com.coderzclub.service.OrganizationAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Platform-admin reconciliation for historical org-private global point inflation.
 * Defaults to dry-run. Writes require feature flag + {@code dry-run=false} + {@code apply=true}.
 */
@RestController
@RequestMapping("/api/admin/reconciliation")
public class OrgPrivatePointsReconciliationController {
    private final OrgPrivatePointsReconciliationService reconciliationService;
    private final OrganizationAccessService organizationAccess;

    public OrgPrivatePointsReconciliationController(
        OrgPrivatePointsReconciliationService reconciliationService,
        OrganizationAccessService organizationAccess
    ) {
        this.reconciliationService = reconciliationService;
        this.organizationAccess = organizationAccess;
    }

    @PostMapping("/org-private-points")
    public ResponseEntity<Map<String, Object>> auditOrgPrivatePoints(
        Authentication authentication,
        @RequestParam(defaultValue = "false") boolean apply
    ) {
        organizationAccess.ensurePlatformAdmin(authentication);
        return ResponseEntity.ok(reconciliationService.reconcile(apply));
    }
}
