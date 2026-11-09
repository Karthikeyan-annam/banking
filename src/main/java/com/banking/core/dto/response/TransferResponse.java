package com.banking.core.dto.response;

import com.banking.core.domain.enums.TransactionStatus;
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
 * Response payload returned upon completion of a P2P fund transfer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {

    @JsonProperty("transaction_id")
    private UUID transactionId;

    @JsonProperty("reference_code")
    private UUID referenceCode;

    @JsonProperty("source_account_number")
    private String sourceAccountNumber;

    @JsonProperty("target_account_number")
    private String targetAccountNumber;

    private BigDecimal amount;

    private String currency;

    private TransactionStatus status;

    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;
}
