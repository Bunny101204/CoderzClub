package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsOriginsTest {
    @Test
    void parsesCommaSeparatedOrigins() {
        assertEquals(2, CorsOrigins.parse("http://localhost:5173, https://app.example.com").size());
        assertEquals("https://app.example.com", CorsOrigins.parse("http://localhost:5173, https://app.example.com").get(1));
    }

    @Test
    void rejectsCredentialedWildcard() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> CorsOrigins.parse("*"));
        assertTrue(ex.getMessage().contains("Wildcard"));
    }

    @Test
    void trimsIgnoresEmptyAndDeduplicates() {
        List<String> origins = CorsOrigins.parse(" http://localhost:5173 , , http://localhost:5173, https://app.example.com ");
        assertEquals(2, origins.size());
        assertEquals("http://localhost:5173", origins.get(0));
        assertEquals("https://app.example.com", origins.get(1));
    }

    @Test
    void blankFallsBackToLocalDefault() {
        assertEquals(List.of("http://localhost:5173"), CorsOrigins.parse("   "));
        assertEquals(List.of("http://localhost:5173"), CorsOrigins.parse(null));
    }
}
