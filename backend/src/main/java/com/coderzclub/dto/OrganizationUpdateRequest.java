package com.coderzclub.dto;

import com.coderzclub.model.OrganizationBranding;

/**
 * Partial organization profile update. Only organization owners/admins may apply changes.
 * Branding assets are validated for safe https URLs and hex colors server-side.
 */
public class OrganizationUpdateRequest {
    private String name;
    private OrganizationBranding branding;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public OrganizationBranding getBranding() { return branding; }
    public void setBranding(OrganizationBranding branding) { this.branding = branding; }
}
