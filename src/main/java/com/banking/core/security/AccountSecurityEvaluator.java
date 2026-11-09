package com.banking.core.security;

import com.banking.core.domain.entity.User;
import com.banking.core.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Custom Spring Security evaluator bean used in {@code @PreAuthorize} SpEL
 * expressions throughout the service layer.
 *
 * <h3>Usage Pattern</h3>
 * <pre>
 * // On a service or controller method:
 * {@literal @}PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
 * public AccountResponse getAccountById(UUID accountId) { ... }
 * </pre>
 *
 * <h3>Why a Separate Component?</h3>
 * Embedding ownership SQL inside {@code @PreAuthorize} expressions (e.g., using
 * {@code @Query} beans) makes the expressions opaque and hard to test.  A named
 * Spring bean with a clear Java method is:
 * <ul>
 *   <li>Unit-testable in isolation with mocks.</li>
 *   <li>Debuggable — the method can be stepped through in an IDE.</li>
 *   <li>Extendable — new ownership rules (e.g., joint accounts) are added here,
 *       not scattered across dozens of {@code @PreAuthorize} strings.</li>
 * </ul>
 *
 * <h3>ADMIN Bypass</h3>
 * Any principal holding the {@code ROLE_ADMIN} authority passes all ownership
 * checks automatically — admins have unrestricted read/write access.
 *
 * <h3>Thread Safety</h3>
 * All methods are stateless and thread-safe; {@link AccountRepository} is a
 * Spring-managed proxy that is inherently thread-safe.
 */
@Component("accountSecurity")  // bean name used in @PreAuthorize SpEL
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityEvaluator {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    private final AccountRepository accountRepository;

    /**
     * Checks whether the authenticated principal owns the given account,
     * or holds the ADMIN role (which bypasses ownership restrictions).
     *
     * <p>This check is performed via a single indexed DB query
     * ({@code WHERE id = ? AND user_id = ?}) — no full account load is needed.
     *
     * @param accountId      UUID of the account being accessed
     * @param authentication the current Spring Security authentication token
     * @return {@code true} if the principal owns the account or is an admin
     */
    public boolean isOwner(UUID accountId, Authentication authentication) {
        if (accountId == null || authentication == null || !authentication.isAuthenticated()) {
            log.warn("isOwner check failed: null accountId or unauthenticated request");
            return false;
        }

        // ── Admin bypass ──────────────────────────────────────────────────
        boolean isAdmin = authentication.getAuthorities()
            .contains(new SimpleGrantedAuthority(ADMIN_ROLE));
        if (isAdmin) {
            log.debug("ADMIN bypass: access granted to account {}", accountId);
            return true;
        }

        // ── Ownership check ───────────────────────────────────────────────
        // The principal is our User entity (set by JwtAuthFilter)
        if (!(authentication.getPrincipal() instanceof User user)) {
            log.warn("Unexpected principal type: {}", authentication.getPrincipal().getClass());
            return false;
        }

        UUID userId = user.getId();
        boolean owns = accountRepository.existsByIdAndUserId(accountId, userId);

        if (!owns) {
            log.warn("Ownership check FAILED: user {} attempted to access account {}",
                userId, accountId);
        }

        return owns;
    }

    /**
     * Checks whether the authenticated principal IS the given user, or
     * holds the ADMIN role.  Used to guard {@code /users/{userId}/accounts}
     * style endpoints.
     *
     * @param targetUserId   UUID of the user resource being accessed
     * @param authentication the current authentication token
     * @return {@code true} if the authenticated user matches or is an admin
     */
    public boolean isSelfOrAdmin(UUID targetUserId, Authentication authentication) {
        if (targetUserId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities()
            .contains(new SimpleGrantedAuthority(ADMIN_ROLE));
        if (isAdmin) return true;

        if (!(authentication.getPrincipal() instanceof User user)) return false;

        return user.getId().equals(targetUserId);
    }

    /**
     * Checks whether the authenticated principal owns either the sender or receiver
     * account associated with a specific transaction, or holds the ADMIN role.
     *
     * @param transactionId  UUID of the transaction to inspect
     * @param authentication the current authentication token
     * @return {@code true} if authorized to view this transaction
     */
    public boolean canViewTransaction(UUID transactionId, Authentication authentication) {
        if (transactionId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities()
            .contains(new SimpleGrantedAuthority(ADMIN_ROLE));
        if (isAdmin) return true;

        if (!(authentication.getPrincipal() instanceof User user)) return false;

        return accountRepository.isUserPartyToTransaction(transactionId, user.getId());
    }
}
