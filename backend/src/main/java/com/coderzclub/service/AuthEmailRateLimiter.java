package com.coderzclub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Best-effort in-process throttle for outbound auth email endpoints.
 * Limits by client IP and by normalized email/username key.
 */
@Service
public class AuthEmailRateLimiter {
    public enum Action {
        REGISTER,
        RESEND_VERIFICATION,
        PASSWORD_RESET
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMs;

    public AuthEmailRateLimiter(
        @Value("${app.auth-email.rate-limit.max-attempts:5}") int maxAttempts,
        @Value("${app.auth-email.rate-limit.window-ms:900000}") long windowMs
    ) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowMs = Math.max(1_000L, windowMs);
    }

    public void check(Action action, String clientIp, String identity) {
        long now = System.currentTimeMillis();
        enforce(key(action, "ip", normalizeIp(clientIp)), now);
        if (StringUtils.hasText(identity)) {
            enforce(key(action, "id", identity.trim().toLowerCase(Locale.ROOT)), now);
        }
    }

    private void enforce(String key, long now) {
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.startedAtMs >= windowMs) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (window.count.get() > maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again later.");
        }
    }

    private static String key(Action action, String kind, String value) {
        return action.name() + ":" + kind + ":" + value;
    }

    private static String normalizeIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        String first = clientIp.split(",")[0].trim();
        return first.isEmpty() ? "unknown" : first;
    }

    private static final class Window {
        private final long startedAtMs;
        private final AtomicInteger count = new AtomicInteger(1);

        private Window(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }
    }
}
