package com.limidus.currencyconverter.config;

import com.limidus.currencyconverter.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security configuration for client-credentials token flow.
 *
 * <p>Public endpoints (no token required):
 * <ul>
 *   <li>{@code POST /auth/token} — exchange clientId + clientSecret for a JWT
 *   <li>{@code GET  /actuator/health} — liveness / readiness probe
 *   <li>{@code GET  /version} — build metadata
 * </ul>
 *
 * <p>All other endpoints require {@code Authorization: Bearer <jwt>}.
 *
 * <p>There is no {@code UserDetailsService} or {@code PasswordEncoder} — client identity is
 * validated directly in {@link com.limidus.currencyconverter.controller.AuthController} against
 * {@link com.limidus.currencyconverter.security.ApiClientProperties}, and the JWT carries the
 * clientId as its subject. This also eliminates the circular-dependency that arises when
 * {@code SecurityConfig} defines a {@code UserDetailsService} that is injected into a filter
 * that {@code SecurityConfig} itself registers.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/token").permitAll()
                .requestMatchers(HttpMethod.GET,  "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET,  "/version").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
