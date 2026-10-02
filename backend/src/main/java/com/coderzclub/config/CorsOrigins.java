package com.coderzclub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CorsOrigins {
    private final String raw;

    public CorsOrigins(@Value("${app.cors.allowed-origins:http://localhost:5173}") String raw) {
        this.raw = raw;
    }

    public List<String> allowed() {
        return parse(raw);
    }

    public static List<String> parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of("http://localhost:5173");
        }
        List<String> origins = Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
        if (origins.isEmpty()) {
            return List.of("http://localhost:5173");
        }
        if (origins.stream().anyMatch(origin -> "*".equals(origin))) {
            throw new IllegalArgumentException(
                "Wildcard CORS origin * is not allowed with credentialed browser requests");
        }
        return origins;
    }
}
