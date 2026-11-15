package com.coderzclub.service;

import com.coderzclub.dto.OrganizationCreateRequest;
import com.coderzclub.dto.OrganizationMemberRequest;
import com.coderzclub.dto.OrganizationRoleUpdateRequest;
import com.coderzclub.dto.OrganizationUpdateRequest;
import com.coderzclub.model.Organization;
import com.coderzclub.model.OrganizationBranding;
import com.coderzclub.model.OrganizationMember;
import com.coderzclub.model.OrganizationMemberStatus;
import com.coderzclub.model.OrganizationRole;
import com.coderzclub.model.OrganizationStatus;
import com.coderzclub.model.User;
import com.coderzclub.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OrganizationService {
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern HTTPS_ASSET = Pattern.compile("^https://[\\w.-]+(?:/[\\w./%?=&+#@~-]*)?$", Pattern.CASE_INSENSITIVE);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final OrganizationAccessService access;

    public OrganizationService(
        MongoTemplate mongoTemplate,
        UserRepository userRepository,
        OrganizationAccessService access
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.access = access;
    }

    public Map<String, Object> createOrganization(Authentication authentication, OrganizationCreateRequest request) {
        access.ensurePlatformAdmin(authentication);
        User actor = access.requireUser(authentication);
        String slug = normalizeSlug(request.getSlug());
        if (mongoTemplate.exists(Query.query(Criteria.where("slug").is(slug)), Organization.class)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization slug already exists");
        }
        Organization organization = new Organization();
        organization.setName(request.getName().trim());
        organization.setSlug(slug);
        organization.setCategory(blankToNull(request.getCategory()));
        organization.setStatus(OrganizationStatus.ACTIVE.name());
        organization.setCreatedBy(actor.getId());
        organization.setCreatedAt(new Date());
        organization.setUpdatedAt(new Date());
        Organization saved = mongoTemplate.save(organization);

        String ownerUserId = StringUtils.hasText(request.getOwnerUserId())
            ? request.getOwnerUserId().trim()
            : actor.getId();
        User owner = requireAssignableUser(ownerUserId);
        upsertMembership(saved.getId(), owner.getId(), List.of(OrganizationRole.ORG_OWNER.name()), actor.getId());
        return toOrganizationMap(saved, List.of(OrganizationRole.ORG_OWNER.name()));
    }

    public List<Map<String, Object>> listOrganizations(Authentication authentication) {
        access.ensurePlatformAdmin(authentication);
        List<Organization> organizations = mongoTemplate.find(
            new Query().with(Sort.by(Sort.Direction.ASC, "name")),
            Organization.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Organization organization : organizations) {
            rows.add(toOrganizationMap(organization, List.of()));
        }
        return rows;
    }

    public List<Map<String, Object>> listMyOrganizations(Authentication authentication) {
        User user = access.requireUser(authentication);
        if (access.isPlatformAdmin(authentication)) {
            return listOrganizations(authentication);
        }
        List<OrganizationMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("userId").is(user.getId())
                .and("status").is(OrganizationMemberStatus.ACTIVE.name())),
            OrganizationMember.class);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<String, OrganizationMember> byOrg = new HashMap<>();
        for (OrganizationMember membership : memberships) {
            byOrg.put(membership.getOrganizationId(), membership);
        }
        List<Organization> organizations = mongoTemplate.find(
            Query.query(Criteria.where("_id").in(byOrg.keySet())
                .and("status").is(OrganizationStatus.ACTIVE.name())),
            Organization.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Organization organization : organizations) {
            OrganizationMember membership = byOrg.get(organization.getId());
            rows.add(toOrganizationMap(organization, membership == null ? List.of() : membership.getRoles()));
        }
        return rows;
    }

    public Map<String, Object> getOrganization(Authentication authentication, String organizationId) {
        access.requireAnyRole(
            authentication,
            organizationId,
            OrganizationRole.ORG_OWNER,
            OrganizationRole.ORG_ADMIN,
            OrganizationRole.INSTRUCTOR,
            OrganizationRole.STUDENT
        );
        Organization organization = access.requireOrganization(organizationId);
        User user = access.requireUser(authentication);
        List<String> roles = access.isPlatformAdmin(authentication)
            ? List.of(OrganizationRole.ORG_OWNER.name())
            : access.requireActiveMembership(organizationId, user.getId()).getRoles();
        return toOrganizationMap(organization, roles);
    }

    public Map<String, Object> updateOrganization(
        Authentication authentication,
        String organizationId,
        OrganizationUpdateRequest request
    ) {
        access.requireOrgAdministrator(authentication, organizationId);
        Organization organization = access.requireOrganization(organizationId);
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Update body is required");
        }
        if (StringUtils.hasText(request.getName())) {
            String name = request.getName().trim();
            if (name.length() < 2 || name.length() > 120) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 2-120 characters");
            }
            organization.setName(name);
        }
        if (request.getBranding() != null) {
            organization.setBranding(sanitizeBranding(request.getBranding(), organization.getBranding()));
        }
        organization.setUpdatedAt(new Date());
        Organization saved = mongoTemplate.save(organization);
        return toOrganizationMap(saved, viewerRoles(authentication, organizationId));
    }

    public Map<String, Object> resetBranding(Authentication authentication, String organizationId) {
        access.requireOrgAdministrator(authentication, organizationId);
        Organization organization = access.requireOrganization(organizationId);
        organization.setBranding(new OrganizationBranding());
        organization.setUpdatedAt(new Date());
        Organization saved = mongoTemplate.save(organization);
        return toOrganizationMap(saved, viewerRoles(authentication, organizationId));
    }

    public Map<String, Object> previewBranding(Authentication authentication, String organizationId) {
        access.requireOrgAdministrator(authentication, organizationId);
        Organization organization = access.requireOrganization(organizationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", organizationId);
        body.put("branding", organization.getBranding() == null ? new OrganizationBranding() : organization.getBranding());
        body.put("fallbackDisplayName", organization.getName());
        return body;
    }

    private List<String> viewerRoles(Authentication authentication, String organizationId) {
        User user = access.requireUser(authentication);
        return access.isPlatformAdmin(authentication)
            ? List.of(OrganizationRole.ORG_OWNER.name())
            : access.requireActiveMembership(organizationId, user.getId()).getRoles();
    }

    public Map<String, Object> addOrUpdateMember(
        Authentication authentication,
        String organizationId,
        OrganizationMemberRequest request
    ) {
        access.requireOrgAdministrator(authentication, organizationId);
        User actor = access.requireUser(authentication);
        access.rejectPlatformRoleEscalation(request.getRoles());
        List<String> roles = normalizeRequestedRoles(request.getRoles());
        protectOwnerDemotion(organizationId, request.getUserId(), roles, actor.getId(), authentication);
        User memberUser = requireAssignableUser(request.getUserId());
        OrganizationMember membership = upsertMembership(organizationId, memberUser.getId(), roles, actor.getId());
        return toMemberMap(membership, memberUser);
    }

    public Map<String, Object> updateMemberRoles(
        Authentication authentication,
        String organizationId,
        String userId,
        OrganizationRoleUpdateRequest request
    ) {
        OrganizationMemberRequest body = new OrganizationMemberRequest();
        body.setUserId(userId);
        body.setRoles(request.getRoles());
        return addOrUpdateMember(authentication, organizationId, body);
    }

    public Map<String, Object> removeMember(Authentication authentication, String organizationId, String userId) {
        access.requireOrgAdministrator(authentication, organizationId);
        User actor = access.requireUser(authentication);
        if (!StringUtils.hasText(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        OrganizationMember membership = mongoTemplate.findOne(
            Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(userId)),
            OrganizationMember.class);
        if (membership == null || !membership.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        if (membership.hasRole(OrganizationRole.ORG_OWNER) && countActiveOwners(organizationId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove the last organization owner");
        }
        membership.setStatus(OrganizationMemberStatus.REMOVED.name());
        membership.setRemovedAt(new Date());
        membership.setRemovedBy(actor.getId());
        mongoTemplate.save(membership);
        revokeOrganizationBatchCoordinatorGrants(organizationId, userId);
        User memberUser = userRepository.findById(userId).orElse(null);
        return toMemberMap(membership, memberUser);
    }

    private void revokeOrganizationBatchCoordinatorGrants(String organizationId, String userId) {
        List<com.coderzclub.model.Batch> batches = mongoTemplate.find(
            Query.query(Criteria.where("organizationId").is(organizationId)),
            com.coderzclub.model.Batch.class);
        if (batches.isEmpty()) {
            return;
        }
        List<String> batchIds = batches.stream()
            .map(com.coderzclub.model.Batch::getId)
            .filter(StringUtils::hasText)
            .toList();
        if (batchIds.isEmpty()) {
            return;
        }
        mongoTemplate.remove(
            Query.query(Criteria.where("userId").is(userId).and("batchId").in(batchIds)),
            com.coderzclub.model.BatchCoordinator.class);
    }

    public List<Map<String, Object>> listMembers(Authentication authentication, String organizationId) {
        access.requireOrgAdministrator(authentication, organizationId);
        List<OrganizationMember> memberships = mongoTemplate.find(
            Query.query(Criteria.where("organizationId").is(organizationId)
                .and("status").is(OrganizationMemberStatus.ACTIVE.name()))
                .with(Sort.by(Sort.Direction.ASC, "addedAt")),
            OrganizationMember.class);
        Map<String, User> users = loadUsers(memberships.stream().map(OrganizationMember::getUserId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrganizationMember membership : memberships) {
            rows.add(toMemberMap(membership, users.get(membership.getUserId())));
        }
        return rows;
    }

    private OrganizationMember upsertMembership(
        String organizationId,
        String userId,
        List<String> roles,
        String actorUserId
    ) {
        OrganizationMember existing = mongoTemplate.findOne(
            Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(userId)),
            OrganizationMember.class);
        Date now = new Date();
        if (existing == null) {
            OrganizationMember created = new OrganizationMember();
            created.setOrganizationId(organizationId);
            created.setUserId(userId);
            created.setRoles(roles);
            created.setStatus(OrganizationMemberStatus.ACTIVE.name());
            created.setAddedAt(now);
            created.setAddedBy(actorUserId);
            return mongoTemplate.save(created);
        }
        existing.setRoles(roles);
        existing.setStatus(OrganizationMemberStatus.ACTIVE.name());
        existing.setRemovedAt(null);
        existing.setRemovedBy(null);
        if (existing.getAddedAt() == null) {
            existing.setAddedAt(now);
        }
        if (!StringUtils.hasText(existing.getAddedBy())) {
            existing.setAddedBy(actorUserId);
        }
        return mongoTemplate.save(existing);
    }

    private void protectOwnerDemotion(
        String organizationId,
        String targetUserId,
        List<String> nextRoles,
        String actorUserId,
        Authentication authentication
    ) {
        boolean keepsOwner = nextRoles.stream().anyMatch(role -> OrganizationRole.ORG_OWNER.name().equals(role));
        OrganizationMember existing = mongoTemplate.findOne(
            Query.query(Criteria.where("organizationId").is(organizationId).and("userId").is(targetUserId)),
            OrganizationMember.class);
        if (existing != null && existing.isActive() && existing.hasRole(OrganizationRole.ORG_OWNER) && !keepsOwner) {
            if (countActiveOwners(organizationId) <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot demote the last organization owner");
            }
        }
        if (!access.isPlatformAdmin(authentication)
            && targetUserId.equals(actorUserId)
            && !keepsOwner
            && existing != null
            && existing.hasRole(OrganizationRole.ORG_OWNER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owners cannot demote themselves");
        }
        if (nextRoles.stream().anyMatch(role -> OrganizationRole.ORG_OWNER.name().equals(role))
            && !access.isPlatformAdmin(authentication)) {
            OrganizationMember actorMembership = access.requireActiveMembership(organizationId, actorUserId);
            if (!actorMembership.hasRole(OrganizationRole.ORG_OWNER)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only an owner can assign the owner role");
            }
        }
    }

    private long countActiveOwners(String organizationId) {
        return mongoTemplate.count(
            Query.query(Criteria.where("organizationId").is(organizationId)
                .and("status").is(OrganizationMemberStatus.ACTIVE.name())
                .and("roles").is(OrganizationRole.ORG_OWNER.name())),
            OrganizationMember.class);
    }

    private User requireAssignableUser(String userId) {
        User user = userRepository.findById(userId)
            .filter(candidate -> !candidate.isDeleted())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!user.canAuthenticate()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User cannot join an organization");
        }
        return user;
    }

    private List<String> normalizeRequestedRoles(List<String> roles) {
        access.rejectPlatformRoleEscalation(roles);
        List<String> normalized = roles.stream()
            .map(OrganizationRole::from)
            .map(Enum::name)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one organization role is required");
        }
        return normalized;
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slug must be lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private OrganizationBranding sanitizeBranding(OrganizationBranding incoming, OrganizationBranding existing) {
        OrganizationBranding current = existing == null ? new OrganizationBranding() : existing;
        OrganizationBranding next = new OrganizationBranding();
        next.setDisplayName(firstNonBlank(incoming.getDisplayName(), current.getDisplayName()));
        if (next.getDisplayName() != null && next.getDisplayName().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name is too long");
        }
        next.setLogoUrl(sanitizeHttpsAsset(firstNonBlank(incoming.getLogoUrl(), current.getLogoUrl()), "logoUrl"));
        next.setFaviconUrl(sanitizeHttpsAsset(firstNonBlank(incoming.getFaviconUrl(), current.getFaviconUrl()), "faviconUrl"));
        next.setPrimaryColor(sanitizeHexColor(firstNonBlank(incoming.getPrimaryColor(), current.getPrimaryColor()), "primaryColor"));
        next.setSecondaryColor(sanitizeHexColor(firstNonBlank(incoming.getSecondaryColor(), current.getSecondaryColor()), "secondaryColor"));
        next.setLandingTagline(sanitizePlainText(firstNonBlank(incoming.getLandingTagline(), current.getLandingTagline()), 160, "landingTagline"));
        next.setEmailFromName(sanitizePlainText(firstNonBlank(incoming.getEmailFromName(), current.getEmailFromName()), 80, "emailFromName"));
        return next;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null) {
            String trimmed = preferred.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return fallback;
    }

    private String sanitizeHttpsAsset(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:")
            || lower.startsWith("data:")
            || lower.contains("<script")
            || lower.endsWith(".svg")
            || lower.contains(".svg?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a safe https image URL");
        }
        if (!HTTPS_ASSET.matcher(trimmed).matches() || trimmed.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be an https URL");
        }
        return trimmed;
    }

    private String sanitizePlainText(String value, int maxLen, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.trim().replaceAll("(?i)<[^>]*>", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > maxLen) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        }
        return cleaned;
    }

    private String sanitizeHexColor(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!HEX_COLOR.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a hex color like #0A7");
        }
        return trimmed;
    }

    private Map<String, User> loadUsers(List<String> userIds) {
        Map<String, User> users = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return users;
        }
        for (User user : userRepository.findAllById(userIds)) {
            users.put(user.getId(), user);
        }
        return users;
    }

    private Map<String, Object> toOrganizationMap(Organization organization, List<String> viewerRoles) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", organization.getId());
        map.put("slug", organization.getSlug());
        map.put("name", organization.getName());
        map.put("category", organization.getCategory());
        map.put("status", organization.getStatus());
        map.put("branding", organization.getBranding());
        map.put("entitlements", organization.getEntitlements());
        map.put("createdAt", organization.getCreatedAt());
        map.put("updatedAt", organization.getUpdatedAt());
        map.put("viewerRoles", viewerRoles == null ? List.of() : viewerRoles);
        return map;
    }

    private Map<String, Object> toMemberMap(OrganizationMember membership, User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", membership.getId());
        map.put("organizationId", membership.getOrganizationId());
        map.put("userId", membership.getUserId());
        map.put("roles", membership.getRoles());
        map.put("status", membership.getStatus());
        map.put("addedAt", membership.getAddedAt());
        map.put("removedAt", membership.getRemovedAt());
        if (user != null) {
            map.put("username", user.getUsername());
            map.put("email", user.getEmail());
            map.put("accountRole", user.getRole());
        }
        return map;
    }
}
