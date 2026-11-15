package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "organizations")
public class Organization {
    @Id
    private String id;
    @Indexed(unique = true)
    private String slug;
    private String name;
    private String category;
    private String status = OrganizationStatus.ACTIVE.name();
    private OrganizationBranding branding = new OrganizationBranding();
    private OrganizationEntitlements entitlements = new OrganizationEntitlements();
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private String createdBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OrganizationBranding getBranding() { return branding; }
    public void setBranding(OrganizationBranding branding) {
        this.branding = branding == null ? new OrganizationBranding() : branding;
    }
    public OrganizationEntitlements getEntitlements() { return entitlements; }
    public void setEntitlements(OrganizationEntitlements entitlements) {
        this.entitlements = entitlements == null ? new OrganizationEntitlements() : entitlements;
    }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public boolean isActive() {
        return OrganizationStatus.ACTIVE == OrganizationStatus.from(status);
    }
}
