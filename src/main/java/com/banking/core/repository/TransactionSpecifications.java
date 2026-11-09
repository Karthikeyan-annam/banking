package com.banking.core.repository;

import com.banking.core.domain.entity.Transaction;
import com.banking.core.dto.request.TransactionFilterCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Specifications builder for dynamic queries on {@link Transaction}.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    /**
     * Builds a specification that retrieves transactions involving the specified account ID
     * (either as sender or receiver), dynamically applying optional filters for type,
     * status, and date bounds.
     */
    public static Specification<Transaction> withFilterCriteria(UUID accountId, TransactionFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Account involvement: (senderAccount.id = :accountId OR receiverAccount.id = :accountId)
            Predicate senderMatch = cb.equal(root.get("senderAccount").get("id"), accountId);
            Predicate receiverMatch = cb.equal(root.get("receiverAccount").get("id"), accountId);
            predicates.add(cb.or(senderMatch, receiverMatch));

            if (criteria != null) {
                // 2. Transaction Type Filter
                if (criteria.getType() != null) {
                    predicates.add(cb.equal(root.get("transactionType"), criteria.getType()));
                }

                // 3. Status Filter
                if (criteria.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
                }

                // 4. Start Date Filter (inclusive)
                if (criteria.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getStartDate()));
                }

                // 5. End Date Filter (inclusive)
                if (criteria.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.getEndDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
