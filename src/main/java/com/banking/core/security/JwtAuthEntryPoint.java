package com.banking.core.security;

import com.banking.core.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Handles all authentication failures that reach the security filter chain.
 *
 * <p>Spring Security invokes this entry point when a request hits a secured
 * resource without a valid authentication token (or with an expired/malformed
 * one that caused the {@link JwtAuthFilter} to leave the
 * {@link org.springframework.security.core.context.SecurityContextHolder} empty).
 *
 * <h3>Response Format</h3>
 * Returns a JSON {@code 401 Unauthorized} body using our standard
 * {@link ErrorResponse} envelope so API clients receive a consistent error shape:
 * <pre>
 * {
 *   "timestamp" : "2024-01-15T10:30:00Z",
 *   "status"    : 401,
 *   "error"     : "Unauthorized",
 *   "message"   : "JWT token has expired. Please log in again.",
 *   "path"      : "/api/v1/accounts"
 * }
 * </pre>
 */
@Component
@Slf4j
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public void commence(
        HttpServletRequest       request,
        HttpServletResponse      response,
        AuthenticationException  authException
    ) throws IOException {

        log.warn("Unauthorized access attempt to [{}]: {}",
            request.getRequestURI(), authException.getMessage());

        // Determine the most informative message based on the JWT exception
        // attributes set by JwtAuthFilter.
        String message = resolveMessage(request, authException);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.UNAUTHORIZED.value())
            .error("Unauthorized")
            .message(message)
            .path(request.getRequestURI())
            .build();

        OBJECT_MAPPER.writeValue(response.getOutputStream(), errorResponse);
    }

    /**
     * Inspects request attributes set by {@link JwtAuthFilter} to produce
     * a human-readable error description.
     */
    private String resolveMessage(HttpServletRequest request, AuthenticationException ex) {
        if (request.getAttribute("expired") != null) {
            return "JWT token has expired. Please log in again.";
        }
        if (request.getAttribute("invalid") != null) {
            return "JWT token is invalid or malformed.";
        }
        return ex.getMessage() != null
            ? ex.getMessage()
            : "Authentication is required to access this resource.";
    }
}
