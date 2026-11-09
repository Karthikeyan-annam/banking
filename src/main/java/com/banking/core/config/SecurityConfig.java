package com.banking.core.config;

import com.banking.core.security.JwtAccessDeniedHandler;
import com.banking.core.security.JwtAuthEntryPoint;
import com.banking.core.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>No {@code WebSecurityConfigurerAdapter} — uses the modern
 *       component-based lambda DSL introduced in Spring Security 6.</li>
 *   <li>Fully stateless: {@code STATELESS} session policy ensures no
 *       {@code HttpSession} is ever created or consulted.</li>
 *   <li>CSRF disabled — CSRF attacks require a browser session cookie;
 *       JWT Bearer tokens in headers are immune by design.</li>
 *   <li>{@code @EnableMethodSecurity} unlocks {@code @PreAuthorize} /
 *       {@code @PostAuthorize} on service and controller methods for
 *       fine-grained RBAC beyond route-level rules.</li>
 * </ul>
 *
 * <h3>RBAC Route Map</h3>
 * <pre>
 *  PUBLIC (no token required)
 *    POST /api/v1/auth/register
 *    POST /api/v1/auth/login
 *    GET  /actuator/health
 *
 *  USER + ADMIN (valid token required)
 *    GET  /api/v1/accounts/**
 *    POST /api/v1/accounts/*/deposit
 *    POST /api/v1/accounts/*/withdraw
 *    POST /api/v1/transfers/**
 *    GET  /api/v1/transactions/**
 *
 *  ADMIN only
 *    GET  /api/v1/admin/**
 * </pre>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize, @PostAuthorize, @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter          jwtAuthFilter;
    private final UserDetailsService     userDetailsService;
    private final JwtAuthEntryPoint      jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    // ── Public endpoint matchers ──────────────────────────────────────────

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/auth/**",          // register + login
        "/actuator/health",         // liveness / readiness probes
        "/actuator/info",
        "/v3/api-docs/**",          // OpenAPI / SpringDoc
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    // ── SecurityFilterChain ───────────────────────────────────────────────

    /**
     * Defines the HTTP security pipeline.
     *
     * <p>Filter insertion order:
     * <pre>
     *  … → JwtAuthFilter → UsernamePasswordAuthenticationFilter → …
     * </pre>
     * Our {@link JwtAuthFilter} runs before Spring's form-login filter so that
     * a valid Bearer token can establish the security context before any
     * form-login mechanism inspects the request.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF (stateless JWT API — no session cookies) ─────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS: delegate to MvcConfig / CorsConfigurationSource bean ─
            .cors(cors -> {})   // placeholder — configure CorsConfigurationSource in Phase 3+

            // ── Custom error handlers ─────────────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthEntryPoint)   // 401
                .accessDeniedHandler(jwtAccessDeniedHandler)   // 403
            )

            // ── Stateless sessions ────────────────────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── Route-level authorization rules ──────────────────────────
            .authorizeHttpRequests(auth -> auth

                // Public — no token required
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                // Admin-only management routes
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Account routes — authenticated users only
                // Additional ownership checks (@PreAuthorize) are enforced
                // at the service layer for USER-role callers.
                .requestMatchers(HttpMethod.GET,  "/api/v1/accounts/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/**").authenticated()

                // Transfer routes — authenticated users only
                .requestMatchers("/api/v1/transfers/**").authenticated()

                // Transaction history — authenticated users only
                .requestMatchers("/api/v1/transactions/**").authenticated()

                // Catch-all: everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── Wire in our JWT filter BEFORE the default form-login filter ─
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Beans ──────────────────────────────────────────────────────────────

    /**
     * BCrypt password encoder with the default cost factor (10 rounds).
     * Increasing to 12 adds ~300 ms per encode — acceptable for auth endpoints
     * and significantly raises the cost of offline brute-force attacks.
     *
     * <p>BCrypt automatically salts each hash — storing a salt column separately
     * is not needed.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * {@link DaoAuthenticationProvider} wires the {@link UserDetailsService}
     * (our custom one that loads from PostgreSQL) with the BCrypt encoder so
     * Spring's {@link AuthenticationManager} can authenticate login requests.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean so it can be
     * injected into {@code AuthService.login()} to trigger credential
     * verification via the configured {@link AuthenticationProvider}.
     *
     * @param config Spring's auto-configured {@link AuthenticationConfiguration}
     */
    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}
