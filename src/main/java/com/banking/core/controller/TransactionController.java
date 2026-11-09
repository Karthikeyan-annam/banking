package com.banking.core.controller;

import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
import com.banking.core.dto.request.TransactionFilterCriteria;
import com.banking.core.dto.response.TransactionResponse;
import com.banking.core.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for querying account transaction history and transaction audits.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Retrieves paginated transaction history for an account.
     *
     * @param accountId   the account UUID
     * @param page        0-based page index (default: 0)
     * @param size        page size (default: 20)
     * @param sortBy      sorting property (default: createdAt)
     * @param direction   sort direction: ASC or DESC (default: DESC)
     * @param type        optional filter by TransactionType (DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN)
     * @param status      optional filter by TransactionStatus (COMPLETED, FAILED, PENDING)
     * @param startDate   optional filter start timestamp (ISO-8601)
     * @param endDate     optional filter end timestamp (ISO-8601)
     * @return 200 OK with paginated list of TransactionResponse
     */
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
        @PathVariable UUID accountId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "DESC") String direction,
        @RequestParam(required = false) TransactionType type,
        @RequestParam(required = false) TransactionStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate
    ) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        TransactionFilterCriteria criteria = TransactionFilterCriteria.builder()
            .type(type)
            .status(status)
            .startDate(startDate)
            .endDate(endDate)
            .build();

        log.debug("Fetching transactions for account {} with criteria: {}", accountId, criteria);
        Page<TransactionResponse> result = transactionService.getAccountTransactions(accountId, criteria, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieves the details of a single transaction by its UUID.
     *
     * @param transactionId UUID of the transaction
     * @return 200 OK with TransactionResponse
     */
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable UUID transactionId) {
        TransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response);
    }
}
