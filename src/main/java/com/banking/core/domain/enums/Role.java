package com.banking.core.domain.enums;

/**
 * System-wide roles used by Spring Security for RBAC.
 *
 * <p>These values map directly to the {@code user_role} PostgreSQL enum
 * defined in the Flyway migration script.
 *
 * <ul>
 *   <li>{@code USER}  – standard customer account; may only access their own data.</li>
 *   <li>{@code ADMIN} – back-office operator; has elevated read/write access.</li>
 * </ul>
 */
public enum Role {
    USER,
    ADMIN
}
