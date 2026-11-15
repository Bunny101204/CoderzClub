package com.coderzclub.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class OrganizationRoleUpdateRequest {
    @NotEmpty
    private List<String> roles = new ArrayList<>();

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
    }
}
