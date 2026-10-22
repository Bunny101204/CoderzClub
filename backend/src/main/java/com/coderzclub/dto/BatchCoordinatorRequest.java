package com.coderzclub.dto;

public class BatchCoordinatorRequest {
    private String userId;
    private String institution;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
}
