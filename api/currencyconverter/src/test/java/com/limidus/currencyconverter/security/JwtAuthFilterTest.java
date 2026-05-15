package com.limidus.currencyconverter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeader_doesNotAuthenticate() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        JwtAuthFilter filter = new JwtAuthFilter(provider);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, res, chain);
        verify(chain).doFilter(req, res);
        org.mockito.Mockito.verifyNoInteractions(provider);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformedAuthorization_doesNotAuthenticate() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        JwtAuthFilter filter = new JwtAuthFilter(provider);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Basic xyz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, res, chain);
        verify(provider, org.mockito.Mockito.never()).extractClientId(org.mockito.ArgumentMatchers.any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerToken_valid_setsAuthentication() throws Exception {
        ApiClientProperties props = new ApiClientProperties();
        props.setJwtSecret("change-me-in-production-must-be-at-least-32-chars!!");
        JwtTokenProvider provider = new JwtTokenProvider(props);
        JwtAuthFilter filter = new JwtAuthFilter(provider);
        String token = provider.generate("client-a");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("client-a");
    }

    @Test
    void bearerToken_whenContextAlreadyAuthenticated_skipsOverwrite() throws Exception {
        ApiClientProperties props = new ApiClientProperties();
        props.setJwtSecret("change-me-in-production-must-be-at-least-32-chars!!");
        JwtTokenProvider provider = new JwtTokenProvider(props);
        JwtAuthFilter filter = new JwtAuthFilter(provider);
        String token = provider.generate("client-b");
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "existing", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("existing");
    }

    @Test
    void bearerToken_invalidJwt_doesNotAuthenticate() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        when(provider.extractClientId("bad")).thenReturn(null);
        JwtAuthFilter filter = new JwtAuthFilter(provider);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
