package com.banking.core.domain.enums;

/**
 * The nature of a financial movement in the ledger.
 *
 * <ul>
 *   <li>{@code DEBIT}         – Money leaves an account (sender leg of a transfer).</li>
 *   <li>{@code CREDIT}        – Money enters an account (receiver leg of a transfer).</li>
 *   <li>{@code TRANSFER}      – Internal P2P logical movement.</li>
 *   <li>{@code TRANSFER_OUT}  – P2P debit leg on sender account.</li>
 *   <li>{@code TRANSFER_IN}   – P2P credit leg on receiver account.</li>
 *   <li>{@code DEPOSIT}       – External funding event (e.g., cash deposit).</li>
 *   <li>{@code WITHDRAWAL}    – External money-out event (e.g., ATM withdrawal).</li>
 * </ul>
 */
public enum TransactionType {
    DEBIT,
    CREDIT,
    TRANSFER,
    TRANSFER_OUT,
    TRANSFER_IN,
    DEPOSIT,
    WITHDRAWAL
}
