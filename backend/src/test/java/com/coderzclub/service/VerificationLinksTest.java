package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationLinksTest {
    @Test
    void unlistedClientOriginFallsBackToConfiguredFrontend() {
        String base = VerificationLinks.frontendBase(
            "https://evil.example",
            List.of("https://coderzclub.coderzclub1.workers.dev"),
            "https://coderzclub.coderzclub1.workers.dev"
        );
        assertEquals("https://coderzclub.coderzclub1.workers.dev", base);
    }

    @Test
    void allowlistedClientOriginIsKept() {
        String base = VerificationLinks.frontendBase(
            "https://coderzclub.coderzclub1.workers.dev/",
            List.of("https://coderzclub.coderzclub1.workers.dev"),
            "http://localhost:5173"
        );
        assertEquals("https://coderzclub.coderzclub1.workers.dev", base);
    }

    @Test
    void missingConfigurationUsesLocalFrontendDefault() {
        assertEquals("http://localhost:5173", VerificationLinks.frontendBase(null, List.of(), "  "));
    }
}
