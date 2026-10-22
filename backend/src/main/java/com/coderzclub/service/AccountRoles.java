package com.coderzclub.service;

import java.util.Locale;

/**
 * Account roles that the server is allowed to store.
 * Public registration always uses {@link #USER}. Only an authenticated
 * administrator may assign {@link #ADMIN} through the admin role endpoint.
 */
public final class AccountRoles {
    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";

    private AccountRoles() {
    }

    public static String publicRegistrationRole() {
        return USER;
    }

    public static String assignableRole(String requested) {
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        String role = requested.trim().toUpperCase(Locale.ROOT);
        if (!USER.equals(role) && !ADMIN.equals(role)) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        return role;
    }
}
