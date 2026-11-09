package com.banking.core.dto.request;

import com.banking.core.domain.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request payload for opening a new bank account.
 *
 * <p>The {@code initialDeposit} is optional — an account can be created with
 * a zero balance.  If provided it must be ≥ 0.01 and must not exceed
 * 15 integer digits + 4 decimal places (matching the DB column precision).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRequest {

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    /**
     * Opening deposit amount.  Optional; if omitted the account starts at zero.
     * <p>
     * {@code @Digits(integer=15, fraction=4)} prevents values that would
     * overflow the {@code NUMERIC(19,4)} database column.
     */
    @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
    @Digits(integer = 15, fraction = 4,
            message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Builder.Default
    private BigDecimal initialDeposit = BigDecimal.ZERO;

    /**
     * ISO 4217 currency code.  Defaults to USD.
     * Phase 4 will add cross-currency transfer validation.
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO 4217 code")
    @Builder.Default
    private String currency = "USD";

    /** Optional free-text description / nickname for the account. */
    @Size(max = 200, message = "Description must not exceed 200 characters")
    private String description;
}
