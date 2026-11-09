package com.banking.core.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for depositing funds into an account.
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>{@code amount} must be present and ≥ 0.01 (zero-value deposits are
 *       meaningless business events and are rejected early).</li>
 *   <li>Precision cap ({@code @Digits}) prevents input that would overflow
 *       the {@code NUMERIC(19,4)} column before it reaches the DB.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    /** Optional payment reference or memo (e.g., "Salary - November 2024"). */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
