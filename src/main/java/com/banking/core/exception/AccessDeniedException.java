package com.banking.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an authenticated user attempts to access a resource they do
 * not own (e.g., viewing another user's account balance).
 *
 * <p>Returns {@code 403 Forbidden} — the user is authenticated but not
 * authorized for this specific resource instance.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException() {
        super("You do not have permission to access this resource.");
    }
}
