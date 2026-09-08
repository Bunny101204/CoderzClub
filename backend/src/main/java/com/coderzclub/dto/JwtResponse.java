package com.coderzclub.dto;

public class JwtResponse {
    private final String token;
    private final String role;
    
    // Constructor
    public JwtResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }
    
    // Getters
    public String getToken() { return token; }
    public String getRole() { return role; }
} 