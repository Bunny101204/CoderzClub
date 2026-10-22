package com.coderzclub.config;

import com.coderzclub.model.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {
    private static final String SECRET = "coderzclub-test-hs512-secret-key-that-is-at-least-sixty-four-bytes-long!!";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", 3_600_000L);
    }

    @Test
    void generatedTokenContainsUidUsernameAndRole() {
        String token = jwtUtil.generateToken("user-1", "alice", "USER");
        JwtUtil.JwtIdentity identity = jwtUtil.extractIdentity(token);
        assertEquals("alice", identity.username());
        assertEquals("user-1", identity.userId());
        assertEquals("USER", identity.role());
        assertEquals("alice", jwtUtil.extractUsername(token));
        assertEquals("user-1", jwtUtil.extractUserId(token));
        assertEquals("USER", jwtUtil.extractRole(token));
        assertTrue(jwtUtil.isTokenValid(token, "alice"));
        assertNotNull(identity.issuedAt());
    }

    @Test
    void twoArgGeneratorOmitsUidForLegacyShape() {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        JwtUtil.JwtIdentity identity = jwtUtil.extractIdentity(token);
        assertNull(identity.userId());
        assertNull(jwtUtil.extractUserId(token));
        assertEquals("alice", jwtUtil.extractUsername(token));
        assertEquals("ADMIN", jwtUtil.extractRole(token));
        assertNotNull(identity.issuedAt());
    }

    @Test
    void legacyTokenIssuedAfterUserCreationBelongsToUser() {
        Date createdAt = new Date(System.currentTimeMillis() - 60_000);
        Date issuedAt = new Date();
        User user = userCreatedAt(createdAt);
        JwtUtil.JwtIdentity identity = new JwtUtil.JwtIdentity("alice", null, "USER", issuedAt);
        assertTrue(jwtUtil.legacyTokenCouldBelongToUser(identity, user));
    }

    @Test
    void legacyTokenIssuedBeforeReplacementUserDoesNotBelong() {
        Date issuedAt = new Date(System.currentTimeMillis() - 86_400_000L);
        Date createdAt = new Date();
        User replacement = userCreatedAt(createdAt);
        JwtUtil.JwtIdentity identity = new JwtUtil.JwtIdentity("alice", null, "USER", issuedAt);
        assertFalse(jwtUtil.legacyTokenCouldBelongToUser(identity, replacement));
    }

    @Test
    void legacyTokenAllowsOneSecondNumericDatePrecision() {
        long secondFloor = (System.currentTimeMillis() / 1000L) * 1000L;
        Date issuedAt = new Date(secondFloor);
        Date createdAt = new Date(secondFloor + 400L);
        User user = userCreatedAt(createdAt);
        JwtUtil.JwtIdentity identity = new JwtUtil.JwtIdentity("alice", null, "USER", issuedAt);
        assertTrue(jwtUtil.legacyTokenCouldBelongToUser(identity, user));
    }

    @Test
    void legacyTokenWithoutIssuedAtIsRejected() {
        User user = userCreatedAt(new Date());
        JwtUtil.JwtIdentity identity = new JwtUtil.JwtIdentity("alice", null, "USER", null);
        assertFalse(jwtUtil.legacyTokenCouldBelongToUser(identity, user));
    }

    @Test
    void extractIdentityReadsIssuedAtFromVerifiedClaims() {
        Date issuedAt = new Date((System.currentTimeMillis() / 1000L) * 1000L);
        String token = Jwts.builder()
            .setSubject("alice")
            .claim("role", "USER")
            .setIssuedAt(issuedAt)
            .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
            .compact();
        JwtUtil.JwtIdentity identity = jwtUtil.extractIdentity(token);
        assertNull(identity.userId());
        assertEquals(issuedAt.getTime() / 1000L, identity.issuedAt().getTime() / 1000L);
    }

    private static User userCreatedAt(Date createdAt) {
        User user = new User();
        user.setUsername("alice");
        user.setCreatedAt(createdAt);
        return user;
    }

    @Test
    void invalidSignatureFails() {
        String token = jwtUtil.generateToken("user-1", "alice", "USER");
        String otherSecret = "another-hs512-secret-key-that-is-also-at-least-sixty-four-bytes-long!!";
        assertThrows(SignatureException.class, () -> Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseClaimsJws(token));
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", otherSecret);
        ReflectionTestUtils.setField(other, "jwtExpirationMs", 3_600_000L);
        assertThrows(SignatureException.class, () -> other.extractIdentity(token));
    }

    @Test
    void expiredTokenFails() {
        String token = Jwts.builder()
            .setSubject("alice")
            .claim("uid", "user-1")
            .claim("role", "USER")
            .setIssuedAt(new Date(System.currentTimeMillis() - 10_000))
            .setExpiration(new Date(System.currentTimeMillis() - 5_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS512)
            .compact();
        assertThrows(ExpiredJwtException.class, () -> jwtUtil.extractIdentity(token));
        assertFalse(isValidIgnoringParseErrors(token, "alice"));
    }

    @Test
    void malformedTokenFailsSafely() {
        assertThrows(Exception.class, () -> jwtUtil.extractIdentity("not-a-jwt"));
        assertFalse(isValidIgnoringParseErrors("not-a-jwt", "alice"));
    }

    private boolean isValidIgnoringParseErrors(String token, String username) {
        try {
            return jwtUtil.isTokenValid(token, username);
        } catch (Exception ignored) {
            return false;
        }
    }
}
