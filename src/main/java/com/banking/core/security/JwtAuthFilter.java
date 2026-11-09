package com.banking.core.security;

import com.banking.core.exception.TokenException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter that runs exactly once per HTTP request.
 *
 * <h3>Processing Flow</h3>
 * <pre>
 *  Incoming Request
 *       │
 *       ▼
 *  Extract "Authorization: Bearer &lt;token&gt;" header
 *       │── missing / no "Bearer " prefix ──▶ skip (let Spring Security handle as anonymous)
 *       │
 *       ▼
 *  JwtService.extractUsername(token)
 *       │── ExpiredJwtException  ──▶ delegate to JwtAuthEntryPoint (401)
 *       │── MalformedJwtException ──▶ delegate to JwtAuthEntryPoint (401)
 *       │
 *       ▼
 *  UserDetailsService.loadUserByUsername(email)
 *       │
 *       ▼
 *  JwtService.isTokenValid(token, userDetails)
 *       │── false ──▶ skip (SecurityContext remains empty → 401 downstream)
 *       │
 *       ▼
 *  Set UsernamePasswordAuthenticationToken in SecurityContextHolder
 *       │
 *       ▼
 *  Continue filter chain
 * </pre>
 *
 * <h3>Why {@link OncePerRequestFilter}?</h3>
 * In Servlet containers with async dispatch or error forwarding, a plain
 * {@code Filter} can be invoked multiple times per logical request.
 * {@code OncePerRequestFilter} guarantees the token is validated exactly once.
 *
 * <h3>Thread Safety</h3>
 * Spring creates this bean as a singleton. All state is immutable or confined
 * to the method stack — the filter is fully thread-safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER      = "Authorization";
    private static final String BEARER_PREFIX     = "Bearer ";
    private static final int    BEARER_PREFIX_LEN = BEARER_PREFIX.length();

    private final JwtService          jwtService;
    private final UserDetailsService  userDetailsService;

    /**
     * Core filter method.  Runs once per request.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     */
    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest  request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        // ── 1. Extract the Authorization header ──────────────────────────
        final String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // No JWT present — treat as anonymous and let Spring Security
            // decide whether the endpoint requires authentication.
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(BEARER_PREFIX_LEN);

        // ── 2. Parse the token and extract the subject (email) ───────────
        final String userEmail;
        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired for request [{}]: {}", request.getRequestURI(), ex.getMessage());
            // Store the exception so JwtAuthEntryPoint can build a detailed 401 body
            request.setAttribute("expired", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        } catch (MalformedJwtException | JwtException ex) {
            log.warn("Malformed/invalid JWT for request [{}]: {}",
                request.getRequestURI(), ex.getMessage());
            request.setAttribute("invalid", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // ── 3. Only authenticate if no auth is already set in the context ─
        //       (Avoids redundant DB lookups on re-processed async dispatches)
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // ── 4. Validate token integrity + expiry + subject match ──────
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── 5. Build the Spring Security authentication token ─────
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,                           // credentials are null for token auth
                        userDetails.getAuthorities()    // populates roles for RBAC decisions
                    );

                // Attach request metadata (remote IP, session ID) to the auth token
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // ── 6. Publish to SecurityContext ─────────────────────────
                // From this point, Spring Security treats the request as authenticated.
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user [{}] via JWT for [{}]",
                    userEmail, request.getRequestURI());
            } else {
                log.warn("JWT failed validation for user [{}] on request [{}]",
                    userEmail, request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skip JWT processing entirely for the authentication endpoints themselves
     * (login and register do not carry a token).
     * This is an optimisation — the filter would handle them correctly even
     * without this override, but skipping saves a header parse.
     *
     * @param request the incoming request
     * @return {@code true} if the filter should be skipped
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/");
    }
}
