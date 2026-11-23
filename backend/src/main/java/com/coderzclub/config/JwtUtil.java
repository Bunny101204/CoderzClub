package com.coderzclub.config;

import com.coderzclub.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    /**
     * Cached HMAC key. Recreating {@code Keys.hmacShaKeyFor(...)} on every request
     * caused monitor contention under browse load (see local capacity investigation).
     */
    private volatile SecretKey signingKey;

    private SecretKey signingKey() {
        SecretKey local = signingKey;
        if (local == null) {
            synchronized (this) {
                local = signingKey;
                if (local == null) {
                    local = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                    signingKey = local;
                }
            }
        }
        return local;
    }

    public String generateToken(String username, String role) {
        return generateToken(null, username, role);
    }

    public String generateToken(String userId, String username, String role) {
        var builder = Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs));
        if (userId != null && !userId.isBlank()) {
            builder.claim("uid", userId);
        }
        return builder
                .signWith(signingKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public JwtIdentity extractIdentity(String token) {
        Claims claims = extractAllClaims(token);
        return new JwtIdentity(claims.getSubject(), uidFrom(claims), claims.get("role", String.class), claims.getIssuedAt());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUserId(String token) {
        return uidFrom(extractAllClaims(token));
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        String normalizedToken = token == null ? null : token.trim();
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(normalizedToken)
                .getBody();
    }

    public boolean isTokenValid(String token, String username) {
        // Single parse: previous path re-parsed claims up to three times per request.
        Claims claims = extractAllClaims(token);
        String subject = claims.getSubject();
        Date expiration = claims.getExpiration();
        return subject != null
            && subject.equals(username)
            && expiration != null
            && expiration.after(new Date());
    }

    private static String uidFrom(Claims claims) {
        Object uid = claims.get("uid");
        if (uid == null) {
            return null;
        }
        String value = String.valueOf(uid).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * JWT {@code iat} is a NumericDate with second precision. {@code User.createdAt} may
     * include milliseconds, so a valid token issued in the same second as account creation
     * would otherwise look older than the user record.
     */
    public static final long JWT_NUMERIC_DATE_ALLOWANCE_MS = 1000L;

    public boolean legacyTokenCouldBelongToUser(JwtIdentity identity, User user) {
        if (identity == null || user == null) {
            return false;
        }
        if (identity.issuedAt() == null || user.getCreatedAt() == null) {
            return false;
        }
        return identity.issuedAt().getTime() + JWT_NUMERIC_DATE_ALLOWANCE_MS >= user.getCreatedAt().getTime();
    }

    public record JwtIdentity(String username, String userId, String role, Date issuedAt) {}
}
