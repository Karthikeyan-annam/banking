package com.banking.core.repository;

import com.banking.core.domain.entity.Transaction;
import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Transaction} entities.
 * Supports dynamic filtering and pagination via JpaSpecificationExecutor.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /**
     * Retrieves paginated transaction history for an account.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.senderAccount.id   = :accountId
           OR t.receiverAccount.id = :accountId
        """)
    Page<Transaction> findAllByAccountId(
        @Param("accountId") UUID accountId,
        Pageable pageable
    );

    /**
     * Looks up all transaction legs sharing a referenceId.
     */
    List<Transaction> findByReferenceId(UUID referenceId);

    /**
     * Counts transactions for an account with a specific status.
     */
    @Query("""
        SELECT COUNT(t) FROM Transaction t
        WHERE (t.senderAccount.id   = :accountId
           OR  t.receiverAccount.id = :accountId)
          AND t.status = :status
        """)
    long countByAccountIdAndStatus(
        @Param("accountId") UUID accountId,
        @Param("status")    TransactionStatus status
    );
}
