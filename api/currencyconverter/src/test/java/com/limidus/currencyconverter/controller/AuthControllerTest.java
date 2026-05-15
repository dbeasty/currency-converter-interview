package com.limidus.currencyconverter.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.limidus.currencyconverter.security.ApiClientProperties;
import com.limidus.currencyconverter.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthControllerTest {

    private AuthController authController;

    @BeforeEach
    void setUp() {
        ApiClientProperties props = new ApiClientProperties();
        props.setJwtSecret("change-me-in-production-must-be-at-least-32-chars!!");
        props.setJwtExpirySeconds(3600);
        ApiClientProperties.ClientEntry client = new ApiClientProperties.ClientEntry();
        client.setClientId("default-client");
        client.setClientSecret("change-me-secret");
        props.setClients(java.util.List.of(client));
        authController = new AuthController(props, new JwtTokenProvider(props));
    }

    @Test
    void token_validClient_returnsOk() {
        var res = authController.token(new AuthController.TokenRequest("default-client", "change-me-secret"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().accessToken()).isNotBlank();
        assertThat(res.getBody().tokenType()).isEqualTo("Bearer");
    }

    @Test
    void token_invalidSecret_returnsUnauthorized() {
        var res = authController.token(new AuthController.TokenRequest("default-client", "wrong"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).isNull();
    }
}
