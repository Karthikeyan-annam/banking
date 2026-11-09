package com.banking.core.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — translates every application exception into
 * a consistent {@link ErrorResponse} JSON body.
 *
 * <h3>Handler Priority</h3>
 * Spring processes {@code @ExceptionHandler} methods in declaration order
 * when multiple match. More specific exceptions are declared first.
 *
 * <h3>What is NOT handled here</h3>
 * JWT exceptions thrown INSIDE the filter chain ({@link JwtAuthFilter}) are
 * caught by {@link com.banking.core.security.JwtAuthEntryPoint} and
 * {@link com.banking.core.security.JwtAccessDeniedHandler} — those execute
 * before the dispatcher servlet, so {@code @RestControllerAdvice} never sees them.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 400 Bad Request ──────────────────────────────────────────────────

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransfer(
        InvalidTransferException ex,
        HttpServletRequest request
    ) {
        log.warn("Invalid transfer request [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(
        org.springframework.web.bind.MissingServletRequestParameterException ex,
        HttpServletRequest request
    ) {
        String msg = String.format("Missing required request parameter: '%s'", ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
        org.springframework.http.converter.HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        log.warn("Malformed request payload for [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request body.", request, null);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
        org.springframework.dao.DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        log.error("Data integrity violation for [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT,
            "Database constraint violation. Operation cannot be completed.", request, null);
    }

    /**
     * Handles {@code @Valid} / {@code @Validated} failures on request bodies.
     * Returns a 400 response with a {@code details} map of field → error message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        Map<String, String> details = new HashMap<>();
        String firstErrorMessage = null;

        for (org.springframework.validation.ObjectError error : ex.getBindingResult().getAllErrors()) {
            String fieldName = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            details.put(fieldName, errorMessage);
            if (firstErrorMessage == null) {
                firstErrorMessage = errorMessage;
            }
        }

        String summaryMessage = firstErrorMessage != null ? firstErrorMessage : "Validation failed";
        log.warn("Validation failed for [{}]: {}", request.getRequestURI(), details);

        return buildResponse(HttpStatus.BAD_REQUEST, summaryMessage, request, details);
    }

    /**
     * Handles type mismatch in path variables or request params
     * (e.g., passing a string where a UUID is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        String message = String.format("Parameter '%s' has an invalid value: '%s'",
            ex.getName(), ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST, message, request, null);
    }

    // ── Authentication Errors (401) ───────────────────────────────────────

    /**
     * Handles Spring Security's {@link BadCredentialsException} thrown when
     * the AuthenticationManager rejects the provided email/password combination.
     * We deliberately return a vague message to prevent user enumeration.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
        BadCredentialsException ex,
        HttpServletRequest request
    ) {
        log.warn("Failed login attempt for request [{}]", request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED,
            "Invalid email or password.", request, null);
    }

    /**
     * Handles authentication against a disabled user account
     * ({@code is_enabled = false} in the DB).
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledAccount(
        DisabledException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
            "Your account has been disabled. Please contact support.", request, null);
    }

    /**
     * Handles authentication against a locked user account
     * ({@code is_account_non_locked = false}).
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLockedAccount(
        LockedException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
            "Your account is temporarily locked. Please try again later.", request, null);
    }

    /**
     * Handles JWT errors that bubble up from service-layer manual parsing
     * (not from the filter — those are handled by {@code JwtAuthEntryPoint}).
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorResponse> handleExpiredJwt(
        ExpiredJwtException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
            "JWT token has expired. Please log in again.", request, null);
    }

    @ExceptionHandler({MalformedJwtException.class, SignatureException.class, JwtException.class})
    public ResponseEntity<ErrorResponse> handleInvalidJwt(
        JwtException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED,
            "JWT token is invalid or malformed.", request, null);
    }

    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ErrorResponse> handleTokenException(
        TokenException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    // ── 409 Conflict ─────────────────────────────────────────────────────

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
        UserAlreadyExistsException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────

    @ExceptionHandler({ResourceNotFoundException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(
        RuntimeException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────

    /**
     * Handles our domain-level {@link AccessDeniedException} (e.g., account
     * not active, or ownership check failed in service).
     * Note: Spring Security's own AccessDeniedException is handled separately
     * by {@link com.banking.core.security.JwtAccessDeniedHandler} at the
     * filter-chain level before it reaches this handler.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
        AccessDeniedException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    /**
     * Handles Spring Security's own {@code AccessDeniedException} when it
     * surfaces inside a controller (e.g., thrown by {@code @PreAuthorize}
     * on a service method) rather than at the filter level.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(
        org.springframework.security.access.AccessDeniedException ex,
        HttpServletRequest request
    ) {
        log.warn("Spring Security access denied for [{}]: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN,
            "You do not have permission to access this resource.", request, null);
    }

    // ── 422 Unprocessable Entity ───────────────────────────────────────────

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
        InsufficientFundsException ex,
        HttpServletRequest request
    ) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null);
    }

    // ── 500 Catch-All ─────────────────────────────────────────────────────

    /**
     * Last-resort handler for any unhandled exception.
     * Logs the full stack trace but returns a vague message to the client —
     * internal implementation details must never leak in API responses.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
        Exception ex,
        HttpServletRequest request
    ) {
        log.error("Unhandled exception for request [{}]:", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.", request, null);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(
        HttpStatus         status,
        String             message,
        HttpServletRequest request,
        Map<String, String> details
    ) {
        ErrorResponse body = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .path(request.getRequestURI())
            .details(details)
            .build();
        return new ResponseEntity<>(body, status);
    }
}
