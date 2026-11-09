package com.banking.core.domain.enums;

/**
 * Processing state of a financial transaction.
 *
 * <ul>
 *   <li>{@code PENDING}   – Received; awaiting processing.</li>
 *   <li>{@code COMPLETED} – Both legs (debit + credit) booked successfully.</li>
 *   <li>{@code FAILED}    – Attempt rolled back (e.g., insufficient funds,
 *                           database error). No money moved.</li>
 *   <li>{@code REVERSED}  – A previously COMPLETED transaction that has been
 *                           unwound (refunded / reversed).</li>
 * </ul>
 */
public enum TransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REVERSED
}
