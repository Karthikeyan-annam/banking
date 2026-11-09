package com.banking.core.service;

import com.banking.core.domain.entity.Transaction;
import com.banking.core.dto.request.TransactionFilterCriteria;
import com.banking.core.dto.response.TransactionResponse;
import com.banking.core.exception.ResourceNotFoundException;
import com.banking.core.repository.AccountRepository;
import com.banking.core.repository.TransactionRepository;
import com.banking.core.repository.TransactionSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service managing query operations for transaction history and auditing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    /**
     * Retrieves paginated transaction history for an account based on dynamic filter criteria.
     * Enforces that the caller owns the account or is an ADMIN.
     */
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAccountTransactions(
        UUID accountId,
        TransactionFilterCriteria criteria,
        Pageable pageable
    ) {
        // Ensure account exists
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", "id", accountId);
        }

        Specification<Transaction> spec = TransactionSpecifications.withFilterCriteria(accountId, criteria);
        Page<Transaction> page = transactionRepository.findAll(spec, pageable);

        return page.map(this::toResponse);
    }

    /**
     * Retrieves the details of an individual transaction.
     * Enforces that the caller is a party to this transaction (owner of sender or receiver account) or an ADMIN.
     */
    @PreAuthorize("@accountSecurity.canViewTransaction(#transactionId, authentication)")
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        return toResponse(transaction);
    }

    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
            .id(txn.getId())
            .referenceId(txn.getReferenceId())
            .transactionType(txn.getTransactionType())
            .amount(txn.getAmount())
            .currency(txn.getCurrency())
            .status(txn.getStatus())
            .description(txn.getDescription())
            .senderAccountId(txn.getSenderAccount() != null ? txn.getSenderAccount().getId() : null)
            .receiverAccountId(txn.getReceiverAccount() != null ? txn.getReceiverAccount().getId() : null)
            .createdAt(txn.getCreatedAt())
            .build();
    }
}
