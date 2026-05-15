package com.limidus.currencyconverter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Generates and validates HS256-signed JWTs.
 *
 * <p>The subject of each token is the {@code clientId} of the authenticated API client.
 * The signing key is derived from {@code app.security.jwt-secret}. Override in production
 * via the {@code APP_SECURITY_JWT_SECRET} environment variable.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirySeconds;

    public JwtTokenProvider(ApiClientProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(
                props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expirySeconds = props.getJwtExpirySeconds();
    }

    /** Issues a signed JWT with the given clientId as the subject. */
    public String generate(String clientId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(clientId)
                .claim("type", "client_credentials")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token signature and expiry.
     * Returns the {@code clientId} (subject) if valid, or {@code null} if invalid/expired.
     */
    public String extractClientId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("[JWT] Rejected token: {}", e.getMessage());
            return null;
        }
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}
