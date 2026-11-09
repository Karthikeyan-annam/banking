package com.banking.core.service;

import com.banking.core.domain.entity.User;
import com.banking.core.domain.enums.Role;
import com.banking.core.dto.request.LoginRequest;
import com.banking.core.dto.request.RegisterRequest;
import com.banking.core.dto.response.AuthResponse;
import com.banking.core.exception.UserAlreadyExistsException;
import com.banking.core.repository.UserRepository;
import com.banking.core.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user registration and authentication.
 *
 * <h3>Registration Flow</h3>
 * <pre>
 *  1. Check email uniqueness (fail fast with 409 if duplicate)
 *  2. BCrypt-encode the plaintext password
 *  3. Persist the User entity via UserRepository
 *  4. Generate access + refresh tokens for the new user
 *  5. Return AuthResponse (token + profile summary)
 * </pre>
 *
 * <h3>Login Flow</h3>
 * <pre>
 *  1. Delegate credential validation to AuthenticationManager
 *     (which uses DaoAuthenticationProvider → UserDetailsService → BCrypt compare)
 *     ↳ Throws BadCredentialsException on failure → GlobalExceptionHandler → 401
 *  2. Load the User entity from the DB
 *  3. Generate fresh access + refresh tokens
 *  4. Return AuthResponse
 * </pre>
 *
 * <h3>Transaction Boundaries</h3>
 * {@code register()} is {@code @Transactional} because it writes to the DB.
 * {@code login()} is {@code @Transactional(readOnly = true)} — it only reads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtService          jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpirationMs;

    // ── Registration ──────────────────────────────────────────────────────

    /**
     * Registers a new customer (USER role) account.
     *
     * @param request validated registration payload
     * @return an {@link AuthResponse} containing tokens and user info
     * @throws UserAlreadyExistsException if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // ── 1. Email uniqueness guard ─────────────────────────────────────
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(request.getEmail());
        }

        // ── 2. Build and persist the user ─────────────────────────────────
        User user = User.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail().toLowerCase().trim())   // normalise to lowercase
            .password(passwordEncoder.encode(request.getPassword()))
            .phoneNumber(request.getPhoneNumber())
            .role(Role.USER)
            .enabled(true)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        // ── 3. Generate tokens ────────────────────────────────────────────
        String accessToken  = jwtService.generateAccessToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);

        return buildAuthResponse(savedUser, accessToken, refreshToken);
    }

    // ── Login ─────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and returns a fresh pair of JWT tokens.
     *
     * <p>The {@link AuthenticationManager} handles:
     * <ul>
     *   <li>Credential verification (BCrypt comparison)</li>
     *   <li>Account status checks (disabled, locked, expired)</li>
     *   <li>Throwing {@code BadCredentialsException} on any failure</li>
     * </ul>
     * We call it BEFORE touching the DB for the User entity, so a wrong
     * password results in a 401 before we ever issue a SELECT.
     *
     * @param request validated login payload (email + password)
     * @return an {@link AuthResponse} containing tokens and user info
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // ── 1. Authenticate — throws BadCredentialsException if wrong ─────
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail().toLowerCase().trim(),
                request.getPassword()
            )
        );

        // ── 2. Load the full user entity ──────────────────────────────────
        // We know the user exists at this point because authentication passed.
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
            .orElseThrow(() -> new IllegalStateException(
                "User authenticated but not found in DB — this should never happen"));

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());

        // ── 3. Issue tokens ───────────────────────────────────────────────
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    // ── Token Refresh ─────────────────────────────────────────────────────

    /**
     * Issues a new access token given a valid, non-expired refresh token.
     *
     * @param refreshToken the existing refresh token from the client
     * @return an {@link AuthResponse} with a new access token
     */
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(String refreshToken) {
        final String userEmail = jwtService.extractUsername(refreshToken);

        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalStateException("User not found for refresh token"));

        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new com.banking.core.exception.TokenException("Refresh token is invalid or expired.");
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        log.info("Access token refreshed for user: {}", userEmail);

        return buildAuthResponse(user, newAccessToken, refreshToken);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .tokenType("Bearer")
            .expiresIn(jwtExpirationMs / 1000)   // convert ms → seconds for the client
            .userId(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .role(user.getRole())
            .build();
    }
}
