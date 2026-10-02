package com.coderzclub.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class ProductionSecretValidator {
    private static final Set<String> INSECURE = Set.of(
        "secret", "changeme", "password", "admin", "replace-me", "jwtsecret", "test");

    private final boolean required;
    private final String jwtSecret;

    public ProductionSecretValidator(
        @Value("${app.require-secure-secrets:false}") boolean required,
        @Value("${jwt.secret:}") String jwtSecret
    ) {
        this.required = required;
        this.jwtSecret = jwtSecret;
    }

    @PostConstruct
    public void validate() {
        if (required) {
            requireSecureJwt(jwtSecret);
        }
    }

    static void requireSecureJwt(String jwtSecret) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required for this deployment");
        }
        String normalized = jwtSecret.trim().toLowerCase(Locale.ROOT);
        if (INSECURE.contains(normalized)) {
            throw new IllegalStateException("JWT_SECRET is an insecure placeholder");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 64) {
            throw new IllegalStateException("JWT_SECRET must be at least 64 bytes for HS512");
        }
    }
}
