package com.coderzclub.dto;

import jakarta.validation.constraints.NotBlank;

public class ResendVerificationRequest {
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;

    public ResendVerificationRequest() {}

    public ResendVerificationRequest(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
    }
}
