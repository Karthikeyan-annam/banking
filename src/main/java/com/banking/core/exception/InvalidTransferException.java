package com.banking.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when transfer parameters violate business rules (e.g. self-transfer,
 * currency mismatch, or missing recipient info).
 *
 * <p>Maps to {@code 400 Bad Request}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
