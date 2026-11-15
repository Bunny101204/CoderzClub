package com.coderzclub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts a hostname for optional custom-domain tenant resolution.
 *
 * <p>Trusted reverse-proxy boundary:
 * <ul>
 *   <li>By default only {@code Host} / {@code request.getServerName()} is consulted.</li>
 *   <li>{@code X-Forwarded-Host} is ignored unless {@code app.custom-domains.trust-forwarded-host=true}
 *       and the direct peer ({@code remoteAddr}) is listed in
 *       {@code app.custom-domains.trusted-proxy-cidrs} (exact IPv4 match or {@code *} for local labs).</li>
 *   <li>Cloudflare / edge must strip client-supplied forwarded host headers before the app.</li>
 * </ul>
 *
 * <p>Public custom-domain routing stays off until DNS ownership, TLS, and edge config are verified.
 */
@Component
public class TrustedRequestHostResolver {
    private final boolean trustForwardedHost;
    private final Set<String> trustedProxyPeers;

    public TrustedRequestHostResolver(
        @Value("${app.custom-domains.trust-forwarded-host:false}") boolean trustForwardedHost,
        @Value("${app.custom-domains.trusted-proxy-cidrs:}") String trustedProxyCidrs
    ) {
        this.trustForwardedHost = trustForwardedHost;
        this.trustedProxyPeers = parsePeers(trustedProxyCidrs);
    }

    public String resolveTrustedHostname(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustForwardedHost && isTrustedProxy(request.getRemoteAddr())) {
            String forwarded = firstHost(request.getHeader("X-Forwarded-Host"));
            if (StringUtils.hasText(forwarded)) {
                return forwarded;
            }
        }
        // Never fall through to X-Forwarded-Host for untrusted peers.
        String hostHeader = firstHost(request.getHeader("Host"));
        if (StringUtils.hasText(hostHeader)) {
            return hostHeader;
        }
        return firstHost(request.getServerName());
    }

    public boolean trustsForwardedHost() {
        return trustForwardedHost;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyPeers.isEmpty()) {
            return false;
        }
        String peer = remoteAddr.trim().toLowerCase(Locale.ROOT);
        if (trustedProxyPeers.contains("*")) {
            return true;
        }
        return trustedProxyPeers.contains(peer);
    }

    static String firstHost(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma).trim();
        }
        return value.isEmpty() ? null : value;
    }

    private static Set<String> parsePeers(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(v -> v.toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
