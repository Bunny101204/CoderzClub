package com.coderzclub.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailBrandContextTest {
    @Test
    void stripsUnsafeCharactersFromDisplayName() {
        assertEquals("Alpha College", EmailBrandContext.sanitizeDisplayName("  Alpha <script> College  "));
        assertNull(EmailBrandContext.sanitizeDisplayName(""));
        assertNull(EmailBrandContext.sanitizeDisplayName("x".repeat(81)));
        assertEquals("CoderzClub", EmailBrandContext.of(null).productName());
    }
}
