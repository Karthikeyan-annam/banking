package com.banking.core.dto.response;

import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
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
 * Read-only projection of a single transaction ledger entry.
 *
 * <p>Returned by deposit and withdraw operations (along with the updated
 * {@link AccountResponse}) so the client has an immediate record of the
 * event without needing a separate transaction-history query.
 *
 * <p>Also reused in Phase 5 for paginated transaction history responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private UUID              id;

    @JsonProperty("reference_id")
    private UUID              referenceId;

    @JsonProperty("transaction_type")
    private TransactionType   transactionType;

    private BigDecimal        amount;

    private String            currency;

    private TransactionStatus status;

    private String            description;

    @JsonProperty("sender_account_id")
    private UUID              senderAccountId;

    @JsonProperty("receiver_account_id")
    private UUID              receiverAccountId;

    @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @JsonProperty("created_at")
    private Instant           createdAt;
}
