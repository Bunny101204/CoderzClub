package com.coderzclub.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedRequestHostResolverTest {

    @Test
    void ignoresForwardedHostWhenTrustDisabled() {
        TrustedRequestHostResolver resolver = new TrustedRequestHostResolver(false, "127.0.0.1");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("evil.example.test");
        when(request.getHeader("Host")).thenReturn("coderzclub.site");
        assertEquals("coderzclub.site", resolver.resolveTrustedHostname(request));
    }

    @Test
    void ignoresForwardedHostFromUntrustedPeerEvenWhenFlagEnabled() {
        TrustedRequestHostResolver resolver = new TrustedRequestHostResolver(true, "10.0.0.5");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("evil.example.test");
        when(request.getHeader("Host")).thenReturn("app.coderzclub.site");
        assertEquals("app.coderzclub.site", resolver.resolveTrustedHostname(request));
    }

    @Test
    void acceptsForwardedHostOnlyFromTrustedProxyPeer() {
        TrustedRequestHostResolver resolver = new TrustedRequestHostResolver(true, "10.0.0.5,10.0.0.6");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("college.example.test, spoof.example.test");
        when(request.getHeader("Host")).thenReturn("internal.proxy");
        assertEquals("college.example.test", resolver.resolveTrustedHostname(request));
    }

    @Test
    void fallsBackToServerNameWhenHostMissing() {
        TrustedRequestHostResolver resolver = new TrustedRequestHostResolver(false, "");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Host")).thenReturn(null);
        when(request.getServerName()).thenReturn("fallback.example.test");
        assertEquals("fallback.example.test", resolver.resolveTrustedHostname(request));
    }

    @Test
    void nullRequestYieldsNull() {
        assertNull(new TrustedRequestHostResolver(false, "").resolveTrustedHostname(null));
    }
}
