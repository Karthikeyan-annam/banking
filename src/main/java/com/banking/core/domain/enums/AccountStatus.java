package com.banking.core.domain.enums;

/**
 * Lifecycle states for a bank account.
 *
 * <ul>
 *   <li>{@code ACTIVE}    – Normal operating state; transfers permitted.</li>
 *   <li>{@code INACTIVE}  – No recent activity; can be reactivated.</li>
 *   <li>{@code SUSPENDED} – Frozen due to fraud/compliance review; no transfers.</li>
 *   <li>{@code CLOSED}    – Permanently closed; zero balance required before closing.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    CLOSED
}
