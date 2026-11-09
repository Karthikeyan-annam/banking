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
 * Request payload for withdrawing funds from an account.
 *
 * <p>Identical validation shape to {@link DepositRequest} — both represent
 * a positive monetary delta; the direction (credit/debit) is determined by
 * which service method handles the request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequest {

    @NotNull(message = "Withdrawal amount is required")
    @DecimalMin(value = "0.01", message = "Withdrawal amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits and 4 decimal places")
    private BigDecimal amount;

    /** Optional memo for the withdrawal (e.g., "ATM withdrawal - Main St"). */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}
