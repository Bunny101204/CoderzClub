package com.coderzclub.service;

import com.coderzclub.model.Organization;
import com.coderzclub.model.OrganizationMember;
import com.coderzclub.model.OrganizationMemberStatus;
import com.coderzclub.model.OrganizationRole;
import com.coderzclub.model.OrganizationStatus;
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

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative organization authorization. Client-supplied organization IDs are
 * never trusted without an active membership check (or platform ADMIN).
 */
@Service
public class OrganizationAccessService {
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;

    public OrganizationAccessService(MongoTemplate mongoTemplate, UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
    }

    public boolean isPlatformAdmin(Authentication authentication) {
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

    public void ensurePlatformAdmin(Authentication authentication) {
        if (!isPlatformAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access is required");
        }
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

    public Organization requireActiveOrganization(String organizationId) {
        Organization organization = requireOrganization(organizationId);
        if (!organization.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization is not active");
        }
        return organization;
    }

    public Organization requireOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organization id is required");
        }
        Organization organization = mongoTemplate.findById(organizationId, Organization.class);
        if (organization == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        return organization;
    }

    public Optional<OrganizationMember> findActiveMembership(String organizationId, String userId) {
        if (!StringUtils.hasText(organizationId) || !StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        OrganizationMember member = mongoTemplate.findOne(
            Query.query(Criteria.where("organizationId").is(organizationId)
                .and("userId").is(userId)
                .and("status").is(OrganizationMemberStatus.ACTIVE.name())),
            OrganizationMember.class);
        return Optional.ofNullable(member);
    }

    public java.util.List<String> listActiveOrganizationIdsForUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return java.util.List.of();
        }
        java.util.List<String> membershipIds = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(userId)
                    .and("status").is(OrganizationMemberStatus.ACTIVE.name())),
                OrganizationMember.class)
            .stream()
            .map(OrganizationMember::getOrganizationId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (membershipIds.isEmpty()) {
            return java.util.List.of();
        }
        return mongoTemplate.find(
                Query.query(Criteria.where("_id").in(membershipIds)
                    .and("status").is(OrganizationStatus.ACTIVE.name())),
                Organization.class)
            .stream()
            .map(Organization::getId)
            .filter(StringUtils::hasText)
            .toList();
    }

    public boolean isActiveOrganizationId(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return false;
        }
        Organization organization = mongoTemplate.findById(organizationId.trim(), Organization.class);
        return organization != null && organization.isActive();
    }

    public OrganizationMember requireActiveMembership(String organizationId, String userId) {
        return findActiveMembership(organizationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization membership is required"));
    }

    public OrganizationMember requireAnyRole(
        Authentication authentication,
        String organizationId,
        OrganizationRole... roles
    ) {
        User user = requireUser(authentication);
        if (isPlatformAdmin(authentication)) {
            OrganizationMember synthetic = new OrganizationMember();
            synthetic.setOrganizationId(organizationId);
            synthetic.setUserId(user.getId());
            synthetic.setRoles(java.util.List.of(OrganizationRole.ORG_OWNER.name()));
            synthetic.setStatus(OrganizationMemberStatus.ACTIVE.name());
            requireActiveOrganization(organizationId);
            return synthetic;
        }
        requireActiveOrganization(organizationId);
        OrganizationMember membership = requireActiveMembership(organizationId, user.getId());
        Set<OrganizationRole> required = EnumSet.noneOf(OrganizationRole.class);
        if (roles != null) {
            for (OrganizationRole role : roles) {
                if (role != null) {
                    required.add(role);
                }
            }
        }
        if (!required.isEmpty() && membership.roleSet().stream().noneMatch(required::contains)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient organization role");
        }
        return membership;
    }

    public OrganizationMember requireOrgAdministrator(Authentication authentication, String organizationId) {
        return requireAnyRole(
            authentication,
            organizationId,
            OrganizationRole.ORG_OWNER,
            OrganizationRole.ORG_ADMIN
        );
    }

    /**
     * Resolve the active organization context. The header value is ignored unless
     * the caller is a platform admin or an active member of that organization.
     */
    public String requireAuthorizedOrganizationId(Authentication authentication, String requestedOrganizationId) {
        if (!StringUtils.hasText(requestedOrganizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Organization-Id is required");
        }
        String organizationId = requestedOrganizationId.trim();
        if (isPlatformAdmin(authentication)) {
            requireActiveOrganization(organizationId);
            return organizationId;
        }
        User user = requireUser(authentication);
        requireActiveOrganization(organizationId);
        requireActiveMembership(organizationId, user.getId());
        return organizationId;
    }

    public boolean canAccessOrganizationResource(
        Authentication authentication,
        String resourceOrganizationId
    ) {
        if (!StringUtils.hasText(resourceOrganizationId)) {
            // Legacy platform-scoped resources remain accessible under existing rules.
            return true;
        }
        if (isPlatformAdmin(authentication)) {
            Organization organization = mongoTemplate.findById(resourceOrganizationId, Organization.class);
            return organization != null;
        }
        try {
            User user = requireUser(authentication);
            return findActiveMembership(resourceOrganizationId, user.getId()).isPresent()
                && requireActiveOrganization(resourceOrganizationId).isActive();
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    public void ensureCanAccessOrganizationResource(
        Authentication authentication,
        String resourceOrganizationId
    ) {
        if (!StringUtils.hasText(resourceOrganizationId)) {
            return;
        }
        if (!canAccessOrganizationResource(authentication, resourceOrganizationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
    }

    public void rejectPlatformRoleEscalation(Iterable<String> roles) {
        if (roles == null) {
            return;
        }
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String normalized = role.trim().toUpperCase();
            if ("ADMIN".equals(normalized) || "PLATFORM_ADMIN".equals(normalized) || "ROLE_ADMIN".equals(normalized)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Organization roles cannot grant platform administration");
            }
            OrganizationRole.from(normalized);
        }
    }

    public boolean isSuspended(Organization organization) {
        return organization != null
            && OrganizationStatus.SUSPENDED == OrganizationStatus.from(organization.getStatus());
    }
}
