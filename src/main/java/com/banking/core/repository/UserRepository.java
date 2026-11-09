package com.banking.core.repository;

import com.banking.core.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>All query methods here are read-only lookups used by the authentication
 * and authorization pipeline.  Write operations (save, delete) are inherited
 * from {@link JpaRepository}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by email (case-sensitive, since the DB column is indexed
     * and uses PostgreSQL's default case-sensitive B-tree index).
     *
     * @param email the user's email address
     * @return an {@link Optional} containing the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Returns {@code true} if any user record exists with the given email.
     * Used during registration to guard against duplicate accounts.
     *
     * @param email the email address to check
     * @return {@code true} if the email is already registered
     */
    boolean existsByEmail(String email);

    /**
     * Eagerly fetches the user along with their accounts in a single JOIN
     * query to avoid an N+1 when both user and account data are needed.
     *
     * @param email the user's email address
     * @return an {@link Optional} containing the user with accounts loaded
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.accounts WHERE u.email = :email")
    Optional<User> findByEmailWithAccounts(String email);
}
