package com.banking.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for JWT-specific errors that are detected OUTSIDE the filter chain
 * (e.g., a manually decoded token in a service method).
 *
 * <p>Maps to {@code 401 Unauthorized}.
 *
 * <p>Errors detected INSIDE the filter chain are handled by
 * {@link com.banking.core.security.JwtAuthEntryPoint} directly — this exception
 * exists for service-layer usage.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenException extends RuntimeException {

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
