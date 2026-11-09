package com.banking.core.security;

import com.banking.core.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Handles authorization failures — requests where the user IS authenticated
 * but does NOT have the required role/authority for the endpoint.
 *
 * <p>Example: a USER-role token hitting an ADMIN-only endpoint returns a
 * {@code 403 Forbidden} rather than a generic {@code 401}.
 *
 * <h3>Why not just use the entry point?</h3>
 * Spring Security distinguishes two failure modes:
 * <ul>
 *   <li><b>Not authenticated</b> → {@link JwtAuthEntryPoint} → 401</li>
 *   <li><b>Authenticated but no permission</b> → {@link JwtAccessDeniedHandler} → 403</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public void handle(
        HttpServletRequest   request,
        HttpServletResponse  response,
        AccessDeniedException accessDeniedException
    ) throws IOException {

        log.warn("Access denied to [{}]: {}", request.getRequestURI(),
            accessDeniedException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error("Forbidden")
            .message("You do not have permission to access this resource.")
            .path(request.getRequestURI())
            .build();

        OBJECT_MAPPER.writeValue(response.getOutputStream(), errorResponse);
    }
}
