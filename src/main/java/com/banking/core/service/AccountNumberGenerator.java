package com.banking.core.service;

import com.banking.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique, human-readable bank account numbers.
 *
 * <h3>Format</h3>
 * {@code 10-digit numeric string}, zero-padded.
 * Example: {@code "1000045823"}
 *
 * <h3>Uniqueness Strategy</h3>
 * We use a {@link SecureRandom} source (not {@code Math.random()}) to generate
 * a 10-digit candidate, then check for collision against the database.
 * Retry up to {@value #MAX_RETRIES} times before throwing.
 *
 * <p>With a 10-digit space (10 billion possible values) and a realistic
 * account count well below 100 million, the collision probability per
 * generation is < 0.001% — retries are essentially never triggered.
 *
 * <h3>Why Not UUID?</h3>
 * Account numbers are customer-facing identifiers used in correspondence,
 * wire transfers, and support tickets. A short numeric string is far more
 * usable than a UUID in those contexts.
 *
 * <h3>Thread Safety</h3>
 * {@link SecureRandom} is thread-safe. The retry loop is stateless.
 */
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private static final int MAX_RETRIES   = 10;
    private static final long MIN_VALUE    = 1_000_000_000L;   // 10 digits: 1,000,000,000
    private static final long RANGE        = 9_000_000_000L;   // → 9,999,999,999

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    /**
     * Generates a unique 10-digit account number that does not collide with
     * any existing account in the database.
     *
     * @return a unique 10-character numeric string
     * @throws IllegalStateException if a unique number cannot be found within
     *                               {@value #MAX_RETRIES} attempts (should never happen)
     */
    public String generate() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String candidate = String.valueOf(MIN_VALUE + (long)(SECURE_RANDOM.nextDouble() * RANGE));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
            "Failed to generate a unique account number after " + MAX_RETRIES + " attempts. " +
            "This indicates an unexpectedly high collision rate — investigate DB capacity.");
    }
}
