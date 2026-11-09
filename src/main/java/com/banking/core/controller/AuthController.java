package com.banking.core.controller;

import com.banking.core.dto.request.LoginRequest;
import com.banking.core.dto.request.RegisterRequest;
import com.banking.core.dto.response.AuthResponse;
import com.banking.core.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *  POST /api/v1/auth/register  — create a new user account
 *  POST /api/v1/auth/login     — authenticate and receive JWT tokens
 *  POST /api/v1/auth/refresh   — exchange a refresh token for a new access token
 * </pre>
 *
 * <p>All endpoints in this controller are publicly accessible (no token required)
 * as configured in {@link com.banking.core.config.SecurityConfig}.
 *
 * <p>{@code @Valid} on method parameters triggers Bean Validation before the
 * method body executes; constraint violations are caught by
 * {@link com.banking.core.exception.GlobalExceptionHandler#handleValidation}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new customer account.
     *
     * <p>Returns {@code 201 Created} on success with the JWT token pair
     * so the client can immediately make authenticated calls without
     * requiring a separate login step.
     *
     * @param request validated registration payload
     * @return {@code 201} with {@link AuthResponse} body
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        log.debug("Registration request received for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates a user and issues a JWT token pair.
     *
     * <p>Returns {@code 200 OK} with the tokens on success.
     * On failure, {@link org.springframework.security.authentication.BadCredentialsException}
     * propagates to {@link com.banking.core.exception.GlobalExceptionHandler}
     * which returns a {@code 401} with a deliberately vague error message
     * (prevents user enumeration).
     *
     * @param request validated login payload (email + password)
     * @return {@code 200} with {@link AuthResponse} body
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request
    ) {
        log.debug("Login request received for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Issues a new access token using a valid refresh token.
     *
     * <p>The refresh token is supplied in the {@code Authorization: Bearer <token>}
     * header to follow the same transport convention as access tokens.
     *
     * @param authHeader the {@code Authorization} header containing the refresh token
     * @return {@code 200} with a new {@link AuthResponse} body
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
        @RequestHeader("Authorization") String authHeader
    ) {
        // Strip "Bearer " prefix — same convention as the JWT filter
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String refreshToken = authHeader.substring(7);
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
}
