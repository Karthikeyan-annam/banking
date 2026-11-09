package com.banking.core.service;

import com.banking.core.domain.entity.Account;
import com.banking.core.domain.entity.Transaction;
import com.banking.core.domain.entity.User;
import com.banking.core.domain.enums.AccountStatus;
import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
import com.banking.core.dto.request.CreateAccountRequest;
import com.banking.core.dto.request.DepositRequest;
import com.banking.core.dto.request.WithdrawRequest;
import com.banking.core.dto.response.AccountResponse;
import com.banking.core.dto.response.LedgerOperationResponse;
import com.banking.core.dto.response.TransactionResponse;
import com.banking.core.exception.AccessDeniedException;
import com.banking.core.exception.InsufficientFundsException;
import com.banking.core.exception.ResourceNotFoundException;
import com.banking.core.repository.AccountRepository;
import com.banking.core.repository.TransactionRepository;
import com.banking.core.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for all account lifecycle and ledger operations.
 *
 * <h3>Transaction Isolation</h3>
 * <ul>
 *   <li>{@code createAccount}, {@code deposit}, {@code withdraw} use
 *       {@code READ_COMMITTED} isolation (the PostgreSQL default). This is
 *       sufficient here because we acquire a row-level
 *       {@code SELECT … FOR UPDATE} lock via
 *       {@link AccountRepository#findByIdWithLock} before mutating balance —
 *       the lock prevents dirty reads and lost updates at a lower overhead
 *       than a higher isolation level would impose.</li>
 *   <li>Read-only lookups ({@code getAccountById}, {@code getAccountsForUser})
 *       use {@code readOnly = true} which signals Hibernate to skip dirty
 *       checking and flush, and signals the JDBC driver to use a read
 *       replica if one is configured.</li>
 * </ul>
 *
 * <h3>BigDecimal Rounding</h3>
 * All monetary amounts are scaled to 4 decimal places using
 * {@link RoundingMode#HALF_EVEN} (banker's rounding) before being stored.
 * This eliminates accumulated rounding bias across large transaction volumes.
 *
 * <h3>Ownership Security</h3>
 * Resource-level ownership checks are enforced at the method level via
 * {@code @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")}
 * which runs BEFORE the method body executes. ADMINs bypass this check.
 * Service methods do not need to repeat the check internally.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final int    MONETARY_SCALE   = 4;
    private static final RoundingMode ROUNDING   = RoundingMode.HALF_EVEN;

    private final AccountRepository      accountRepository;
    private final TransactionRepository  transactionRepository;
    private final UserRepository         userRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    // ── Account Creation ──────────────────────────────────────────────────

    /**
     * Opens a new bank account for the given user.
     *
     * <p>If {@code initialDeposit > 0}, a {@code DEPOSIT} transaction entry
     * is created atomically within the same transaction so the ledger is
     * accurate from account inception.
     *
     * @param userId  the UUID of the owning user
     * @param request validated creation payload
     * @return the newly created account as an {@link AccountResponse}
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        BigDecimal initialBalance = scale(
            request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO
        );

        Account account = Account.builder()
            .user(owner)
            .accountNumber(accountNumberGenerator.generate())
            .accountType(request.getAccountType())
            .status(AccountStatus.ACTIVE)
            .balance(initialBalance)
            .currency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "USD")
            .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: {} for user {}", saved.getAccountNumber(), userId);

        // Record opening deposit in the ledger if initial balance > 0
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            recordLedgerEntry(
                null,              // no sender (external funding)
                saved,
                TransactionType.DEPOSIT,
                initialBalance,
                saved.getCurrency(),
                "Account opening deposit",
                null
            );
        }

        return toAccountResponse(saved);
    }

    // ── Account Queries ───────────────────────────────────────────────────

    /**
     * Returns the details of a single account.
     *
     * <p>The {@code @PreAuthorize} check runs BEFORE this method body —
     * if the caller does not own the account and is not an admin,
     * Spring Security throws an {@code AccessDeniedException} (403) and
     * this code is never reached.
     *
     * @param accountId UUID of the requested account
     * @return account details as {@link AccountResponse}
     */
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        return toAccountResponse(account);
    }

    /**
     * Returns all accounts owned by a specific user.
     *
     * <p>The caller must be the target user or an ADMIN — enforced via
     * {@code @PreAuthorize("@accountSecurity.isSelfOrAdmin(#userId, authentication)")}.
     *
     * @param userId UUID of the user whose accounts to list
     * @return list of account responses (empty if the user has no accounts)
     */
    @PreAuthorize("@accountSecurity.isSelfOrAdmin(#userId, authentication)")
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForUser(UUID userId) {
        // Verify the user exists (guards against stale UUIDs in URLs)
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        return accountRepository.findByUserId(userId)
            .stream()
            .map(this::toAccountResponse)
            .collect(Collectors.toList());
    }

    // ── Deposit ───────────────────────────────────────────────────────────

    /**
     * Credits funds to an account and writes a {@code DEPOSIT} ledger entry.
     *
     * <h3>Locking Strategy</h3>
     * We acquire a {@code SELECT … FOR UPDATE} row-level lock on the account
     * row ({@link AccountRepository#findByIdWithLock}) before reading or
     * modifying the balance. This serialises concurrent deposits on the same
     * account at the database level, preventing lost updates.
     *
     * <h3>Atomicity</h3>
     * The balance update and the ledger INSERT are wrapped in a single
     * {@code @Transactional} scope. If the ledger INSERT fails (e.g., DB
     * constraint), the balance update is rolled back automatically — money
     * is never credited without a corresponding ledger record.
     *
     * @param accountId UUID of the target account
     * @param request   validated deposit payload
     * @param clientIp  caller's IP address for the audit trail
     * @return composite response with updated account and ledger entry
     */
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerOperationResponse deposit(
        UUID accountId,
        DepositRequest request,
        String clientIp
    ) {
        BigDecimal amount = scale(request.getAmount());

        // ── Acquire row lock before reading or mutating balance ───────────
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        validateAccountIsActive(account);

        // ── Credit the balance ────────────────────────────────────────────
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);

        log.info("Deposit of {} {} to account {} | new balance: {}",
            amount, account.getCurrency(), account.getAccountNumber(), newBalance);

        // ── Write ledger entry atomically ─────────────────────────────────
        Transaction txn = recordLedgerEntry(
            null, saved,
            TransactionType.DEPOSIT,
            amount, saved.getCurrency(),
            request.getDescription(),
            clientIp
        );

        return LedgerOperationResponse.builder()
            .account(toAccountResponse(saved))
            .transaction(toTransactionResponse(txn))
            .build();
    }

    // ── Withdrawal ────────────────────────────────────────────────────────

    /**
     * Debits funds from an account and writes a {@code WITHDRAWAL} ledger entry.
     *
     * <h3>Insufficient Funds</h3>
     * The service checks {@code balance - amount >= 0} BEFORE mutating the
     * entity.  If the check fails, {@link InsufficientFundsException} is
     * thrown and the transaction is rolled back — no ledger entry is written,
     * and no balance is deducted.
     *
     * <p>The PostgreSQL {@code CHECK (balance >= 0)} constraint is a second
     * line of defence: even if a bug bypasses this application check, the
     * DB will reject the UPDATE with a constraint violation, triggering the
     * same transaction rollback.
     *
     * @param accountId UUID of the source account
     * @param request   validated withdrawal payload
     * @param clientIp  caller's IP address for the audit trail
     * @return composite response with updated account and ledger entry
     */
    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerOperationResponse withdraw(
        UUID accountId,
        WithdrawRequest request,
        String clientIp
    ) {
        BigDecimal amount = scale(request.getAmount());

        // ── Acquire row lock ──────────────────────────────────────────────
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        validateAccountIsActive(account);

        // ── Sufficient funds check ────────────────────────────────────────
        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Insufficient funds: account {} balance={} attempted withdrawal={}",
                account.getAccountNumber(), account.getBalance(), amount);
            throw new InsufficientFundsException(account.getAccountNumber());
        }

        // ── Debit the balance ─────────────────────────────────────────────
        account.setBalance(newBalance);
        Account saved = accountRepository.save(account);

        log.info("Withdrawal of {} {} from account {} | new balance: {}",
            amount, account.getCurrency(), account.getAccountNumber(), newBalance);

        // ── Write ledger entry atomically ─────────────────────────────────
        Transaction txn = recordLedgerEntry(
            saved, null,
            TransactionType.WITHDRAWAL,
            amount, saved.getCurrency(),
            request.getDescription(),
            clientIp
        );

        return LedgerOperationResponse.builder()
            .account(toAccountResponse(saved))
            .transaction(toTransactionResponse(txn))
            .build();
    }

    // ── Shared Ledger Helper ──────────────────────────────────────────────

    /**
     * Creates and persists a {@link Transaction} ledger entry.
     *
     * <p>This helper is called inside the same {@code @Transactional} scope
     * as the balance mutation, guaranteeing atomicity.  If this method throws
     * (e.g., a DB constraint is violated), the entire transaction — including
     * the balance change — is rolled back.
     *
     * @param sender    the debited account (null for deposits from external)
     * @param receiver  the credited account (null for withdrawals to external)
     * @param type      the nature of the transaction
     * @param amount    the monetary amount (pre-scaled)
     * @param currency  ISO 4217 currency code
     * @param note      free-text description
     * @param clientIp  client IP for the audit trail
     * @return the persisted {@link Transaction}
     */
    Transaction recordLedgerEntry(
        Account sender,
        Account receiver,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String note,
        String clientIp
    ) {
        Transaction txn = Transaction.builder()
            .referenceId(UUID.randomUUID())
            .senderAccount(sender)
            .receiverAccount(receiver)
            .transactionType(type)
            .amount(amount)
            .currency(currency)
            .status(TransactionStatus.COMPLETED)
            .description(note)
            .initiatedByIp(clientIp)
            .build();

        return transactionRepository.save(txn);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    /**
     * Enforces that an account is in {@code ACTIVE} status before any
     * balance mutation. Suspended, closed, or inactive accounts cannot
     * participate in financial operations.
     */
    private void validateAccountIsActive(Account account) {
        if (!account.isTransferable()) {
            throw new AccessDeniedException(
                "Account '" + account.getAccountNumber() + "' is not active " +
                "(status: " + account.getStatus() + "). No transactions can be processed."
            );
        }
    }

    /**
     * Scales a {@link BigDecimal} to {@value #MONETARY_SCALE} decimal places
     * using {@link RoundingMode#HALF_EVEN} (banker's rounding).
     *
     * <p>Always call this before storing or comparing monetary values to
     * avoid scale-mismatch comparisons ({@code 1.00 != 1.0000} in BigDecimal
     * without scale normalisation).
     */
    private BigDecimal scale(BigDecimal value) {
        return value.setScale(MONETARY_SCALE, ROUNDING);
    }

    // ── Entity → DTO Mappers ──────────────────────────────────────────────

    /**
     * Maps an {@link Account} entity to an {@link AccountResponse} DTO.
     * Intentionally a manual mapping to avoid MapStruct coupling for simple
     * field copies — MapStruct will be used for more complex mappings in
     * Phase 4/5.
     */
    public AccountResponse toAccountResponse(Account account) {
        return AccountResponse.builder()
            .id(account.getId())
            .accountNumber(account.getAccountNumber())
            .accountType(account.getAccountType())
            .status(account.getStatus())
            .balance(account.getBalance())
            .currency(account.getCurrency())
            .ownerId(account.getUser() != null ? account.getUser().getId() : null)
            .createdAt(account.getCreatedAt())
            .updatedAt(account.getUpdatedAt())
            .build();
    }

    /**
     * Maps a {@link Transaction} entity to a {@link TransactionResponse} DTO.
     */
    private TransactionResponse toTransactionResponse(Transaction txn) {
        return TransactionResponse.builder()
            .id(txn.getId())
            .referenceId(txn.getReferenceId())
            .transactionType(txn.getTransactionType())
            .amount(txn.getAmount())
            .currency(txn.getCurrency())
            .status(txn.getStatus())
            .description(txn.getDescription())
            .senderAccountId(txn.getSenderAccount() != null
                ? txn.getSenderAccount().getId() : null)
            .receiverAccountId(txn.getReceiverAccount() != null
                ? txn.getReceiverAccount().getId() : null)
            .createdAt(txn.getCreatedAt())
            .build();
    }
}
