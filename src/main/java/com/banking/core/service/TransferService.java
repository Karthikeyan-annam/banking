package com.banking.core.service;

import com.banking.core.domain.entity.Account;
import com.banking.core.domain.entity.Transaction;
import com.banking.core.domain.enums.AccountStatus;
import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
import com.banking.core.dto.request.TransferRequest;
import com.banking.core.dto.response.TransferResponse;
import com.banking.core.exception.AccessDeniedException;
import com.banking.core.exception.InsufficientFundsException;
import com.banking.core.exception.InvalidTransferException;
import com.banking.core.exception.ResourceNotFoundException;
import com.banking.core.repository.AccountRepository;
import com.banking.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating peer-to-peer (P2P) fund transfers with strict ACID guarantees,
 * deadlock prevention via ordered row-level locks, and double-entry transaction auditing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private static final int MONETARY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Executes a peer-to-peer fund transfer within a single ACID transaction.
     *
     * <h3>Concurrency & Deadlock Prevention</h3>
     * When two users execute mutual transfers concurrently (User A -> User B and User B -> User A),
     * unordered row-level locks (SELECT ... FOR UPDATE) cause a cyclic wait condition resulting in
     * database deadlocks.
     * <br><br>
     * <b>Solution:</b> We resolve the UUIDs of both accounts and acquire the pessimistic locks
     * in strict lexicographical order of their UUID strings (firstLockId < secondLockId).
     * This establishes a global lock acquisition hierarchy across all threads and completely
     * eliminates deadlock cycles.
     *
     * <h3>Rollback Guarantees</h3>
     * The method is annotated with {@code @Transactional(rollbackFor = Exception.class)}.
     * Any failure—whether balance deficiency, account state validation, or network/DB interruption—
     * triggers an immediate rollback. Neither account is partially credited or debited.
     *
     * @param request  the validated transfer request payload
     * @param clientIp client IP address for the regulatory audit trail
     * @return {@link TransferResponse} summary of the completed transfer
     */
    @PreAuthorize("@accountSecurity.isOwner(#request.sourceAccountId, authentication)")
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRED,
        rollbackFor = Exception.class
    )
    public TransferResponse transferFunds(TransferRequest request, String clientIp) {
        BigDecimal transferAmount = scale(request.getAmount());

        // 1. Resolve Target Account UUID
        UUID targetAccountId = resolveTargetAccountId(request);

        // 2. Prevent self-transfers
        if (request.getSourceAccountId().equals(targetAccountId)) {
            throw new InvalidTransferException("Source and target accounts cannot be the same account.");
        }

        // 3. Deadlock Prevention Strategy: Determine deterministic lock ordering
        UUID firstLockId;
        UUID secondLockId;
        if (request.getSourceAccountId().compareTo(targetAccountId) < 0) {
            firstLockId = request.getSourceAccountId();
            secondLockId = targetAccountId;
        } else {
            firstLockId = targetAccountId;
            secondLockId = request.getSourceAccountId();
        }

        log.debug("Acquiring pessimistic write locks in order: [{}] then [{}]", firstLockId, secondLockId);

        // 4. Acquire Pessimistic Write Locks (SELECT ... FOR UPDATE) sequentially
        Account firstLocked = accountRepository.findByIdWithLock(firstLockId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", firstLockId));
        Account secondLocked = accountRepository.findByIdWithLock(secondLockId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", secondLockId));

        // Assign to source and target references
        Account sourceAccount = firstLockId.equals(request.getSourceAccountId()) ? firstLocked : secondLocked;
        Account targetAccount = firstLockId.equals(targetAccountId) ? firstLocked : secondLocked;

        // 5. Account Lifecycle & Status Validation
        validateAccountOperational(sourceAccount, "Source");
        validateAccountOperational(targetAccount, "Target");

        // 6. Currency Compatibility Check
        if (!sourceAccount.getCurrency().equalsIgnoreCase(targetAccount.getCurrency())) {
            throw new InvalidTransferException(String.format(
                "Currency mismatch: Source account is %s but target account is %s.",
                sourceAccount.getCurrency(), targetAccount.getCurrency()
            ));
        }

        // 7. Balance Check (Application layer safety; DB CHECK (balance >= 0) is safety net 2)
        if (sourceAccount.getBalance().compareTo(transferAmount) < 0) {
            log.warn("Insufficient funds for transfer: account {} has balance {}, required {}",
                sourceAccount.getAccountNumber(), sourceAccount.getBalance(), transferAmount);
            throw new InsufficientFundsException(sourceAccount.getAccountNumber());
        }

        // 8. Atomic Balance Mutation
        BigDecimal sourceNewBalance = sourceAccount.getBalance().subtract(transferAmount);
        BigDecimal targetNewBalance = targetAccount.getBalance().add(transferAmount);

        sourceAccount.setBalance(sourceNewBalance);
        targetAccount.setBalance(targetNewBalance);

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);

        log.info("P2P Transfer of {} {} from {} (new bal: {}) to {} (new bal: {}) executed.",
            transferAmount, sourceAccount.getCurrency(),
            sourceAccount.getAccountNumber(), sourceNewBalance,
            targetAccount.getAccountNumber(), targetNewBalance);

        // 9. Double-Entry Ledger Bookkeeping (Linked by shared reference_id)
        UUID referenceCode = UUID.randomUUID();
        Instant now = Instant.now();

        // Debit leg on source account
        Transaction debitLeg = Transaction.builder()
            .referenceId(referenceCode)
            .senderAccount(sourceAccount)
            .receiverAccount(targetAccount)
            .transactionType(TransactionType.TRANSFER_OUT)
            .amount(transferAmount)
            .currency(sourceAccount.getCurrency())
            .status(TransactionStatus.COMPLETED)
            .description(request.getDescription())
            .initiatedByIp(clientIp)
            .build();

        // Credit leg on target account
        Transaction creditLeg = Transaction.builder()
            .referenceId(referenceCode)
            .senderAccount(sourceAccount)
            .receiverAccount(targetAccount)
            .transactionType(TransactionType.TRANSFER_IN)
            .amount(transferAmount)
            .currency(targetAccount.getCurrency())
            .status(TransactionStatus.COMPLETED)
            .description(request.getDescription())
            .initiatedByIp(clientIp)
            .build();

        transactionRepository.saveAll(List.of(debitLeg, creditLeg));

        // 10. Construct & Return Response
        return TransferResponse.builder()
            .transactionId(debitLeg.getId())
            .referenceCode(referenceCode)
            .sourceAccountNumber(sourceAccount.getAccountNumber())
            .targetAccountNumber(targetAccount.getAccountNumber())
            .amount(transferAmount)
            .currency(sourceAccount.getCurrency())
            .status(TransactionStatus.COMPLETED)
            .description(request.getDescription())
            .timestamp(now)
            .build();
    }

    /**
     * Looks up transfer transaction details by reference ID or transaction ID.
     */
    @Transactional(readOnly = true)
    public TransferResponse getTransferDetails(UUID transactionOrRefId) {
        Transaction txn = transactionRepository.findById(transactionOrRefId)
            .orElseGet(() -> {
                List<Transaction> byRef = transactionRepository.findByReferenceId(transactionOrRefId);
                if (byRef.isEmpty()) {
                    throw new ResourceNotFoundException("Transfer", "id/referenceId", transactionOrRefId);
                }
                return byRef.get(0);
            });

        return TransferResponse.builder()
            .transactionId(txn.getId())
            .referenceCode(txn.getReferenceId())
            .sourceAccountNumber(txn.getSenderAccount() != null ? txn.getSenderAccount().getAccountNumber() : "N/A")
            .targetAccountNumber(txn.getReceiverAccount() != null ? txn.getReceiverAccount().getAccountNumber() : "N/A")
            .amount(txn.getAmount())
            .currency(txn.getCurrency())
            .status(txn.getStatus())
            .description(txn.getDescription())
            .timestamp(txn.getCreatedAt())
            .build();
    }

    // ── Helper Routines ───────────────────────────────────────────────────

    private UUID resolveTargetAccountId(TransferRequest request) {
        if (request.getTargetAccountId() != null) {
            return request.getTargetAccountId();
        }
        if (request.getTargetAccountNumber() != null && !request.getTargetAccountNumber().isBlank()) {
            Account target = accountRepository.findByAccountNumber(request.getTargetAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "accountNumber", request.getTargetAccountNumber()));
            return target.getId();
        }
        throw new InvalidTransferException("Either targetAccountNumber or targetAccountId must be provided.");
    }

    private void validateAccountOperational(Account account, String roleName) {
        if (!account.isTransferable()) {
            throw new AccessDeniedException(String.format(
                "%s account '%s' is not active (status: %s). Transfers cannot be processed.",
                roleName, account.getAccountNumber(), account.getStatus()
            ));
        }
    }

    private BigDecimal scale(BigDecimal val) {
        return val.setScale(MONETARY_SCALE, ROUNDING);
    }
}
