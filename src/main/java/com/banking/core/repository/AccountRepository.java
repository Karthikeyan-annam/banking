package com.banking.core.repository;

import com.banking.core.domain.entity.Account;
import com.banking.core.domain.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Account} entities.
 *
 * <h3>Pessimistic Locking</h3>
 * The {@code findByIdWithLock} method acquires a {@code SELECT … FOR UPDATE}
 * database-level lock.  This is the cornerstone of Phase 4's concurrency
 * strategy: before any balance mutation, the service layer must call this
 * method inside a {@code @Transactional} scope so that concurrent transfers
 * on the same account are serialised at the database level, eliminating
 * lost-update and dirty-read anomalies.
 *
 * <p>Both the sender and receiver accounts are locked by the transfer
 * service in a deterministic order (by UUID comparison) to prevent deadlocks
 * when two concurrent transfers involve the same pair of accounts in reverse.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Finds all accounts owned by a specific user.
     *
     * @param userId the owning user's UUID
     * @return list of accounts (may be empty; never null)
     */
    List<Account> findByUserId(UUID userId);

    /**
     * Finds an account by its human-readable account number.
     *
     * @param accountNumber the account number string
     * @return an {@link Optional} containing the account if found
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Finds all accounts with a given status (e.g., all ACTIVE accounts).
     *
     * @param status the account status filter
     * @return list of matching accounts
     */
    List<Account> findByStatus(AccountStatus status);

    /**
     * Finds an account by ID and acquires a <b>pessimistic write lock</b>
     * ({@code SELECT … FOR UPDATE}) on the row for the duration of the
     * current transaction.
     *
     * <p><b>Must be called inside an active {@code @Transactional} scope.</b>
     * Calling this outside a transaction boundary will cause the lock to be
     * immediately released and provides no protection.
     *
     * @param id the account UUID
     * @return an {@link Optional} containing the locked account if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);

    /**
     * Checks whether an account with the given account number exists.
     * Used during account creation to ensure uniqueness before persisting.
     *
     * @param accountNumber the account number to check
     * @return {@code true} if the account number is already in use
     */
    boolean existsByAccountNumber(String accountNumber);

    /**
     * Checks whether an account with the given ID exists AND is owned by
     * the given user. Used by {@link com.banking.core.security.AccountSecurityEvaluator}
     * for ownership verification without loading the full entity.
     *
     * @param id     the account UUID
     * @param userId the user UUID to match against
     * @return {@code true} if the account exists and belongs to the user
     */
    boolean existsByIdAndUserId(UUID id, UUID userId);

    /**
     * Finds an account owned by a specific user (by user ID and account ID).
     * Used to enforce ownership: a USER may only view/modify their own accounts.
     *
     * @param id     the account UUID
     * @param userId the owning user's UUID
     * @return an {@link Optional} containing the account if it exists and belongs to the user
     */
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Checks if a user is the owner of either the sender or receiver account
     * involved in a transaction.
     */
    @Query("""
        SELECT COUNT(t) > 0 FROM Transaction t
        WHERE t.id = :transactionId
          AND (
            (t.senderAccount IS NOT NULL AND t.senderAccount.user.id = :userId)
            OR
            (t.receiverAccount IS NOT NULL AND t.receiverAccount.user.id = :userId)
          )
        """)
    boolean isUserPartyToTransaction(
        @Param("transactionId") UUID transactionId,
        @Param("userId") UUID userId
    );
}
