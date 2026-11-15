package com.coderzclub.service;

import com.coderzclub.model.Batch;
import com.coderzclub.model.BatchCoordinator;
import com.coderzclub.model.OrganizationRole;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BatchAccessService {
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccess;

    public BatchAccessService(
        MongoTemplate mongoTemplate,
        UserRepository userRepository,
        OrganizationAccessService organizationAccess
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.organizationAccess = organizationAccess;
    }

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public void ensureAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required");
        }
    }

    /**
     * Resolves an optional active organization context. Missing header keeps legacy
     * platform scope. Present header values are membership-validated and never trusted alone.
     */
    public String resolveOptionalOrganizationContext(Authentication authentication, String organizationHeader) {
        if (!StringUtils.hasText(organizationHeader)) {
            return null;
        }
        return organizationAccess.requireAuthorizedOrganizationId(authentication, organizationHeader);
    }

    /**
     * Platform-scoped batch management stays platform-admin only. Organization-scoped
     * management requires an organization owner/admin (or platform admin).
     */
    public void ensureCanManageBatchesInContext(Authentication authentication, String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            ensureAdmin(authentication);
            return;
        }
        organizationAccess.requireOrgAdministrator(authentication, organizationId);
    }

    public void ensureCanManageBatch(Authentication authentication, String batchId) {
        Batch batch = requireBatch(batchId);
        if (isAdmin(authentication)) {
            return;
        }
        if (StringUtils.hasText(batch.getOrganizationId())) {
            organizationAccess.requireOrgAdministrator(authentication, batch.getOrganizationId());
            return;
        }
        ensureAdmin(authentication);
    }

    public void ensureCanReadBatch(Authentication authentication, String batchId) {
        Batch batch = requireBatch(batchId);
        if (isAdmin(authentication)) {
            return;
        }
        User user = requireUser(authentication);
        if (StringUtils.hasText(batch.getOrganizationId())) {
            if (organizationAccess.canAccessOrganizationResource(authentication, batch.getOrganizationId())
                && hasOrgBatchStaffRole(batch.getOrganizationId(), user.getId())) {
                return;
            }
            if (coordinates(user.getId(), batchId)
                && organizationAccess.canAccessOrganizationResource(authentication, batch.getOrganizationId())) {
                return;
            }
            // Hide cross-tenant existence from unauthorized callers.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        if (!coordinates(user.getId(), batchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this batch");
        }
    }

    public void ensureCanSearchUsers(Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        User user = requireUser(authentication);
        if (searchableOrganizationIds(user.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot search students");
        }
    }

    /**
     * Platform admins may search the whole directory. Everyone else may only search
     * active members of organizations they administer or coordinate batches for.
     * Returns {@code null} for unrestricted platform-admin search.
     */
    public java.util.Set<String> searchableUserIds(Authentication authentication) {
        if (isAdmin(authentication)) {
            return null;
        }
        User user = requireUser(authentication);
        java.util.Set<String> organizationIds = searchableOrganizationIds(user.getId());
        if (organizationIds.isEmpty()) {
            return java.util.Set.of();
        }
        return mongoTemplate.find(
                Query.query(Criteria.where("organizationId").in(organizationIds)
                    .and("status").is(com.coderzclub.model.OrganizationMemberStatus.ACTIVE.name())),
                com.coderzclub.model.OrganizationMember.class)
            .stream()
            .map(com.coderzclub.model.OrganizationMember::getUserId)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toSet());
    }

    private java.util.Set<String> searchableOrganizationIds(String userId) {
        java.util.Set<String> organizationIds = new java.util.HashSet<>();
        for (com.coderzclub.model.OrganizationMember membership : mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)
                .and("status").is(com.coderzclub.model.OrganizationMemberStatus.ACTIVE.name())),
            com.coderzclub.model.OrganizationMember.class
        )) {
            if (membership.canAdministerOrganization()
                && organizationAccess.isActiveOrganizationId(membership.getOrganizationId())) {
                organizationIds.add(membership.getOrganizationId());
            }
        }
        java.util.List<BatchCoordinator> grants = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(userId)),
            BatchCoordinator.class
        );
        if (!grants.isEmpty()) {
            java.util.List<String> batchIds = grants.stream().map(BatchCoordinator::getBatchId).toList();
            for (Batch batch : mongoTemplate.find(Query.query(Criteria.where("_id").in(batchIds)), Batch.class)) {
                if (StringUtils.hasText(batch.getOrganizationId())
                    && organizationAccess.isActiveOrganizationId(batch.getOrganizationId())
                    && organizationAccess.findActiveMembership(batch.getOrganizationId(), userId).isPresent()) {
                    organizationIds.add(batch.getOrganizationId());
                }
            }
        }
        return organizationIds;
    }

    public User requireUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findByUsername(authentication.getName())
            .filter(user -> !user.isDeleted())
            .filter(User::canAuthenticate)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    public boolean coordinates(String userId, String batchId) {
        if (userId == null || batchId == null) {
            return false;
        }
        return mongoTemplate.exists(
            Query.query(Criteria.where("userId").is(userId).and("batchId").is(batchId)),
            BatchCoordinator.class);
    }

    private Batch requireBatch(String batchId) {
        if (!StringUtils.hasText(batchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        Batch batch = mongoTemplate.findById(batchId.trim(), Batch.class);
        if (batch == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found");
        }
        return batch;
    }

    /**
     * Organization owners/admins may read all batches in their tenant.
     * Instructors follow coordinator assignment (assigned batches only).
     */
    private boolean hasOrgBatchStaffRole(String organizationId, String userId) {
        return organizationAccess.findActiveMembership(organizationId, userId)
            .map(membership -> membership.hasRole(OrganizationRole.ORG_OWNER)
                || membership.hasRole(OrganizationRole.ORG_ADMIN))
            .orElse(false);
    }
}
