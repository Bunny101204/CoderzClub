package com.coderzclub.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Application-side custom hostname binding. Edge TLS/DNS remain external.
 * Only VERIFIED/ACTIVE hostnames may resolve a tenant; Host spoofing alone never grants access.
 */
@Document(collection = "organization_domains")
public class OrganizationDomain {
    @Id
    private String id;
    @Indexed
    private String organizationId;
    @Indexed(unique = true)
    private String hostname;
    private String status = OrganizationDomainStatus.PENDING.name();
    private String verificationToken;
    private Date verificationExpiresAt;
    private Date verifiedAt;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private String createdBy;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVerificationToken() { return verificationToken; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    public Date getVerificationExpiresAt() { return verificationExpiresAt; }
    public void setVerificationExpiresAt(Date verificationExpiresAt) { this.verificationExpiresAt = verificationExpiresAt; }
    public Date getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Date verifiedAt) { this.verifiedAt = verifiedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
