package com.banking.core.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite response returned by deposit and withdraw operations.
 *
 * <p>Bundles both the updated account snapshot and the newly created
 * transaction ledger entry in a single response body, so clients don't
 * need two separate API calls to reconcile a balance change.
 *
 * <h3>Example JSON</h3>
 * <pre>
 * {
 *   "account": { "id": "...", "balance": 1500.00, ... },
 *   "transaction": { "id": "...", "transaction_type": "DEPOSIT", "amount": 500.00, ... }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerOperationResponse {

    /** Updated account state after the operation. */
    @JsonProperty("account")
    private AccountResponse account;

    /** The ledger entry recorded for this operation. */
    @JsonProperty("transaction")
    private TransactionResponse transaction;
}
