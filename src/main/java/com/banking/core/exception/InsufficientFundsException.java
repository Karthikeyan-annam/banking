package com.banking.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a fund transfer or withdrawal is requested but the source
 * account does not have sufficient funds.
 *
 * <p>Maps to {@code 422 Unprocessable Entity} — the request is well-formed
 * and the account exists, but the business rule prevents execution.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber) {
        super("Account '" + accountNumber + "' has insufficient funds for this transaction.");
    }
}
