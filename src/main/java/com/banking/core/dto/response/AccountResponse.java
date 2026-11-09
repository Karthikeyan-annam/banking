package com.banking.core.dto.response;

import com.banking.core.domain.enums.AccountStatus;
import com.banking.core.domain.enums.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection of a bank account returned by all account endpoints.
 *
 * <h3>Security Note</h3>
 * We deliberately omit the internal {@code user_id} from this response in
 * the standard view to minimise information leakage. The owning user can
 * be inferred from the authenticated context.
 *
 * <p>{@code balance} is serialised as a plain JSON number (not a string)
 * which is accurate because Jackson uses {@code BigDecimal} serialisation.
 * If client interop requires a string, add {@code @JsonSerialize(using=...)}
 * on the field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private UUID          id;

    @JsonProperty("account_number")
    private String        accountNumber;

    @JsonProperty("account_type")
    private AccountType   accountType;

    private AccountStatus status;

    private BigDecimal    balance;

    private String        currency;

    @JsonProperty("owner_id")
    private UUID          ownerId;         // included so ADMIN callers can correlate

    @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @JsonProperty("created_at")
    private Instant       createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @JsonProperty("updated_at")
    private Instant       updatedAt;
}
