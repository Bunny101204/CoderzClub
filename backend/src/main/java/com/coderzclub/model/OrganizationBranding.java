package com.coderzclub.model;

/**
 * Optional white-label settings. Missing values fall back to CoderzClub defaults
 * in the presentation layer; this object never grants authorization.
 */
public class OrganizationBranding {
    private String displayName;
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;
    private String faviconUrl;
    private String landingTagline;
    private String emailFromName;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }
    public String getFaviconUrl() { return faviconUrl; }
    public void setFaviconUrl(String faviconUrl) { this.faviconUrl = faviconUrl; }
    public String getLandingTagline() { return landingTagline; }
    public void setLandingTagline(String landingTagline) { this.landingTagline = landingTagline; }
    public String getEmailFromName() { return emailFromName; }
    public void setEmailFromName(String emailFromName) { this.emailFromName = emailFromName; }
}
