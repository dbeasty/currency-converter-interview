package com.limidus.currencyconverter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on every request.
 *
 * <p>No {@link org.springframework.security.core.userdetails.UserDetailsService} is involved —
 * the clientId extracted from the token is used directly as the principal. This avoids the
 * SecurityConfig → JwtAuthFilter → UserDetailsService → SecurityConfig circular dependency.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        String clientId = jwtTokenProvider.extractClientId(token);

        if (clientId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("[JWT] Authenticated request from clientId={}", clientId);
            var auth = new UsernamePasswordAuthenticationToken(
                    clientId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(request, response);
    }
}
