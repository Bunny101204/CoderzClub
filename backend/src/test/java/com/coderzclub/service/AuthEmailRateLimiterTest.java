package com.coderzclub.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthEmailRateLimiterTest {

    @Test
    void allowsConfiguredBurstThenRejects() {
        AuthEmailRateLimiter limiter = new AuthEmailRateLimiter(3, 60_000);
        assertDoesNotThrow(() -> limiter.check(AuthEmailRateLimiter.Action.PASSWORD_RESET, "10.0.0.1", "a@b.c"));
        assertDoesNotThrow(() -> limiter.check(AuthEmailRateLimiter.Action.PASSWORD_RESET, "10.0.0.1", "a@b.c"));
        assertDoesNotThrow(() -> limiter.check(AuthEmailRateLimiter.Action.PASSWORD_RESET, "10.0.0.1", "a@b.c"));
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> limiter.check(AuthEmailRateLimiter.Action.PASSWORD_RESET, "10.0.0.1", "a@b.c")
        );
        assertEquals(429, ex.getStatusCode().value());
    }
}
