package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationLinksAllowlistTest {

    @Test
    void clientOriginMustMatchConfiguredAllowlistExactly() {
        List<String> allowed = List.of("https://app.coderzclub.site", "http://127.0.0.1:4179");
        assertEquals(
            "https://app.coderzclub.site",
            VerificationLinks.frontendBase("https://app.coderzclub.site", allowed, "https://fallback.example")
        );
        assertEquals(
            "https://fallback.example",
            VerificationLinks.frontendBase("https://evil.example.test", allowed, "https://fallback.example")
        );
        assertEquals(
            "https://fallback.example",
            VerificationLinks.frontendBase("https://app.coderzclub.site.evil.test", allowed, "https://fallback.example")
        );
    }
}
