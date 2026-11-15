package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "organization_members")
@CompoundIndexes({
    @CompoundIndex(name = "organizationId_userId_unique_idx", def = "{'organizationId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "userId_organizationId_idx", def = "{'userId': 1, 'organizationId': 1}")
})
public class OrganizationMember {
    @Id
    private String id;
    private String organizationId;
    private String userId;
    private List<String> roles = new ArrayList<>();
    private String status = OrganizationMemberStatus.ACTIVE.name();
    private Date addedAt = new Date();
    private String addedBy;
    private Date removedAt;
    private String removedBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) {
        this.roles = normalizeRoles(roles);
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getAddedAt() { return addedAt; }
    public void setAddedAt(Date addedAt) { this.addedAt = addedAt; }
    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    public Date getRemovedAt() { return removedAt; }
    public void setRemovedAt(Date removedAt) { this.removedAt = removedAt; }
    public String getRemovedBy() { return removedBy; }
    public void setRemovedBy(String removedBy) { this.removedBy = removedBy; }

    public boolean isActive() {
        return OrganizationMemberStatus.from(status).isActive();
    }

    public Set<OrganizationRole> roleSet() {
        Set<OrganizationRole> parsed = new LinkedHashSet<>();
        for (String role : normalizeRoles(roles)) {
            parsed.add(OrganizationRole.from(role));
        }
        return parsed;
    }

    public boolean hasRole(OrganizationRole role) {
        return role != null && roleSet().contains(role);
    }

    public boolean canAdministerOrganization() {
        return roleSet().stream().anyMatch(OrganizationRole::canAdministerOrganization);
    }

    private static List<String> normalizeRoles(List<String> roles) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (roles == null) {
            return new ArrayList<>();
        }
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            unique.add(OrganizationRole.from(role).name());
        }
        return new ArrayList<>(unique);
    }
}
