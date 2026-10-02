package com.coderzclub.dto;

import com.coderzclub.model.User;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UserProfileResponse {
    private UserProfileResponse() {}

    public static Map<String, Object> from(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (user == null) {
            return map;
        }
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("createdAt", user.getCreatedAt());
        map.put("bio", user.getBio());
        map.put("location", user.getLocation());
        map.put("website", user.getWebsite());
        map.put("accountStatus", user.getAccountStatus() == null ? "ACTIVE" : user.getAccountStatus());
        return map;
    }

    public static Date omitSecrets(User user) {
        return user == null ? null : user.getCreatedAt();
    }
}
