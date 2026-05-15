package com.limidus.currencyconverter.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        ApiClientProperties props = new ApiClientProperties();
        props.setJwtSecret("change-me-in-production-must-be-at-least-32-chars!!");
        props.setJwtExpirySeconds(3600);
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateThenExtract_roundTripsClientId() {
        String jwt = provider.generate("my-client");
        assertThat(provider.extractClientId(jwt)).isEqualTo("my-client");
    }

    @Test
    void extractClientId_invalidToken_returnsNull() {
        assertThat(provider.extractClientId("not-a.jwt.token")).isNull();
    }
}
