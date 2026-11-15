package com.coderzclub.service;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Consistent visibility rules for multi-tenant resources.
 *
 * <ol>
 *   <li><b>Public platform</b> — {@code organizationId} null/blank and ordinary public
 *       availability; visible under existing public rules.</li>
 *   <li><b>Legacy platform-owned</b> — {@code organizationId} null/blank; keep historical
 *       ADMIN/USER/coordinator/audience behavior. Never auto-assign to an institution.</li>
 *   <li><b>Organization-private</b> — non-null {@code organizationId}; only active members
 *       of that organization (or platform ADMIN) may access. Cross-tenant IDs return 404.</li>
 *   <li><b>Batch-restricted</b> — eligibility via batch membership/coordinator grants, plus
 *       organization membership when the batch is org-owned.</li>
 *   <li><b>Contest-restricted</b> — eligibility via contest audience rules, plus organization
 *       membership when the contest is org-owned.</li>
 * </ol>
 *
 * Knowing a resource ID or spoofing {@code X-Organization-Id} never grants access.
 */
@Component
public class OrganizationVisibilityPolicy {
    private final OrganizationResourceCompatibility compatibility;

    public OrganizationVisibilityPolicy(OrganizationResourceCompatibility compatibility) {
        this.compatibility = compatibility;
    }

    public boolean isPlatformScoped(String organizationId) {
        return compatibility.isLegacyPlatformScoped(organizationId);
    }

    public boolean isOrganizationPrivate(String organizationId) {
        return !isPlatformScoped(organizationId);
    }

    /**
     * Mongo criteria: platform-scoped documents, optionally unioned with specific org ids.
     */
    public Criteria platformOrOrganizationsCriteria(Collection<String> organizationIds) {
        List<Criteria> or = new ArrayList<>();
        or.add(platformScopedCriteria());
        if (organizationIds != null) {
            List<String> cleaned = new ArrayList<>();
            for (String id : organizationIds) {
                String normalized = compatibility.normalizeStoredOrganizationId(id);
                if (normalized != null) {
                    cleaned.add(normalized);
                }
            }
            if (!cleaned.isEmpty()) {
                or.add(Criteria.where("organizationId").in(cleaned));
            }
        }
        if (or.size() == 1) {
            return or.get(0);
        }
        return new Criteria().orOperator(or.toArray(Criteria[]::new));
    }

    public Criteria platformScopedCriteria() {
        return new Criteria().orOperator(
            Criteria.where("organizationId").exists(false),
            Criteria.where("organizationId").is(null),
            Criteria.where("organizationId").is("")
        );
    }

    public Criteria exactOrganizationCriteria(String organizationId) {
        String normalized = compatibility.normalizeStoredOrganizationId(organizationId);
        if (normalized == null) {
            return platformScopedCriteria();
        }
        return Criteria.where("organizationId").is(normalized);
    }

    public boolean viewerMayAccessOrganizationResource(
        String resourceOrganizationId,
        Collection<String> viewerOrganizationIds,
        boolean platformAdmin
    ) {
        if (platformAdmin) {
            return true;
        }
        if (isPlatformScoped(resourceOrganizationId)) {
            return true;
        }
        if (viewerOrganizationIds == null || viewerOrganizationIds.isEmpty()) {
            return false;
        }
        String expected = compatibility.normalizeStoredOrganizationId(resourceOrganizationId);
        for (String candidate : viewerOrganizationIds) {
            if (expected != null && expected.equals(compatibility.normalizeStoredOrganizationId(candidate))) {
                return true;
            }
        }
        return false;
    }

    public String normalize(String organizationId) {
        return compatibility.normalizeStoredOrganizationId(organizationId);
    }

    public boolean sameTenant(String left, String right) {
        return compatibility.sameTenant(left, right);
    }

    public boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
