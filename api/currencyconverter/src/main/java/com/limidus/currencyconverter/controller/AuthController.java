package com.limidus.currencyconverter.controller;

import com.limidus.currencyconverter.security.ApiClientProperties;
import com.limidus.currencyconverter.security.JwtTokenProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2-style client credentials token endpoint.
 *
 * <p>Clients exchange their {@code clientId} + {@code clientSecret} for a short-lived JWT.
 * The JWT is then passed as {@code Authorization: Bearer <token>} on every subsequent request.
 *
 * <pre>
 * POST /auth/token
 * { "clientId": "my-service", "clientSecret": "supersecret" }
 *
 * 200 OK
 * { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600 }
 *
 * 401 Unauthorized  — unknown clientId or wrong secret
 * </pre>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ApiClientProperties clientProperties;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(ApiClientProperties clientProperties, JwtTokenProvider jwtTokenProvider) {
        this.clientProperties = clientProperties;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        boolean valid = clientProperties.getClients().stream()
                .anyMatch(c -> c.getClientId().equals(request.clientId())
                        && c.getClientSecret().equals(request.clientSecret()));

        if (!valid) {
            log.warn("[AUTH] Token request rejected for clientId={}", request.clientId());
            return ResponseEntity.status(401).build();
        }

        String token = jwtTokenProvider.generate(request.clientId());
        log.info("[AUTH] Token issued for clientId={} expiresIn={}s",
                request.clientId(), jwtTokenProvider.getExpirySeconds());
        return ResponseEntity.ok(
                new TokenResponse(token, "Bearer", jwtTokenProvider.getExpirySeconds()));
    }

    public record TokenRequest(
            @NotBlank String clientId,
            @NotBlank String clientSecret) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
