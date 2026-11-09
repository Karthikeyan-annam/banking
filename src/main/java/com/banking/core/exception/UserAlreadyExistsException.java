package com.banking.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a user attempts to register with an email address that
 * already exists in the system.
 *
 * <p>Maps to {@code 409 Conflict} — the resource (user with that email)
 * already exists; it is not a client input error (400) or server error (500).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("A user with email '" + email + "' already exists.");
    }
}
