package com.banking.core.controller;

import com.banking.core.domain.entity.User;
import com.banking.core.dto.request.CreateAccountRequest;
import com.banking.core.dto.request.DepositRequest;
import com.banking.core.dto.request.WithdrawRequest;
import com.banking.core.dto.response.AccountResponse;
import com.banking.core.dto.response.LedgerOperationResponse;
import com.banking.core.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for bank account lifecycle and ledger operations.
 *
 * <h3>Authentication & Principal Extraction</h3>
 * We inject the authenticated {@link User} entity directly via
 * {@code @AuthenticationPrincipal}. This works because:
 * <ol>
 *   <li>{@link com.banking.core.security.JwtAuthFilter} sets a
 *       {@code UsernamePasswordAuthenticationToken} whose principal IS
 *       the {@code User} entity (loaded from {@code UserDetailsService}).</li>
 *   <li>Spring's {@code @AuthenticationPrincipal} resolver then provides it
 *       directly — no manual {@code SecurityContextHolder.getContext()} needed.</li>
 * </ol>
 *
 * <h3>RBAC Delegation</h3>
 * Route-level security is declared in {@link com.banking.core.config.SecurityConfig}.
 * Resource-level ownership is enforced by {@code @PreAuthorize} annotations on
 * {@link AccountService} methods — the controller delegates entirely to the
 * service and does not duplicate those checks.
 *
 * <h3>Admin Account Creation</h3>
 * Admins can open accounts for any user by supplying a {@code ?userId=} query
 * parameter.  If omitted, the account is created for the authenticated user.
 *
 * <h3>Client IP Capture</h3>
 * The raw HTTP request is passed to deposit/withdraw so the service can record
 * the client IP in the audit trail. We prefer the {@code X-Forwarded-For}
 * header when the app is behind a load balancer/proxy.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────────────
    // POST /api/v1/accounts
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Opens a new bank account.
     *
     * <ul>
     *   <li><b>USER</b>: account is always created for themselves
     *       (the {@code userId} query param is ignored even if supplied).</li>
     *   <li><b>ADMIN</b>: may supply {@code ?userId={uuid}} to open an account
     *       on behalf of another user; defaults to self if omitted.</li>
     * </ul>
     *
     * @param currentUser authenticated principal (injected by Spring Security)
     * @param targetUserId optional override — only honoured for ADMIN callers
     * @param request      validated account creation payload
     * @return {@code 201 Created} with the new {@link AccountResponse}
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
        @AuthenticationPrincipal User currentUser,
        @RequestParam(required = false) UUID userId,
        @Valid @RequestBody CreateAccountRequest request
    ) {
        // If a userId was supplied and the caller is an admin, use it.
        // For USER-role callers, always use their own ID (security: ignore supplied userId).
        UUID ownerUserId = resolveTargetUserId(currentUser, userId);

        log.debug("Create account request: type={}, owner={}", request.getAccountType(), ownerUserId);
        AccountResponse response = accountService.createAccount(ownerUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/v1/accounts
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Lists all accounts belonging to the authenticated user.
     *
     * <p>Admins may optionally supply {@code ?userId=} to list another
     * user's accounts.
     *
     * @param currentUser  authenticated principal
     * @param targetUserId optional user override (admin only)
     * @return {@code 200 OK} with list of {@link AccountResponse}
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(
        @AuthenticationPrincipal User currentUser,
        @RequestParam(required = false) UUID userId
    ) {
        UUID ownerUserId = resolveTargetUserId(currentUser, userId);
        List<AccountResponse> accounts = accountService.getAccountsForUser(ownerUserId);
        return ResponseEntity.ok(accounts);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET /api/v1/accounts/{id}
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the details of a specific account.
     *
     * <p>The ownership check ({@code @accountSecurity.isOwner}) is enforced
     * by {@link AccountService#getAccountById} via {@code @PreAuthorize}.
     *
     * @param accountId path variable — the account UUID
     * @return {@code 200 OK} with {@link AccountResponse}, or {@code 403/404}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
        @PathVariable("id") UUID accountId
    ) {
        AccountResponse response = accountService.getAccountById(accountId);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /api/v1/accounts/{id}/deposit
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Deposits funds into an account.
     *
     * <p>Ownership check: {@link AccountService#deposit} carries
     * {@code @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")}
     * — if the caller does not own the account the request is rejected with 403
     * before the method body executes.
     *
     * @param accountId path variable — the target account UUID
     * @param request   validated deposit payload
     * @param httpReq   raw HTTP request (used to extract client IP)
     * @return {@code 200 OK} with updated {@link LedgerOperationResponse}
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<LedgerOperationResponse> deposit(
        @PathVariable("id") UUID accountId,
        @Valid @RequestBody DepositRequest request,
        HttpServletRequest httpReq
    ) {
        log.debug("Deposit {} to account {}", request.getAmount(), accountId);
        LedgerOperationResponse response = accountService.deposit(
            accountId, request, extractClientIp(httpReq)
        );
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // POST /api/v1/accounts/{id}/withdraw
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Withdraws funds from an account.
     *
     * <p>Returns {@code 422 Unprocessable Entity} if balance is insufficient
     * (thrown by {@link AccountService#withdraw} as
     * {@link com.banking.core.exception.InsufficientFundsException} and
     * translated by {@link com.banking.core.exception.GlobalExceptionHandler}).
     *
     * @param accountId path variable — the source account UUID
     * @param request   validated withdrawal payload
     * @param httpReq   raw HTTP request (used to extract client IP)
     * @return {@code 200 OK} with updated {@link LedgerOperationResponse}
     */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<LedgerOperationResponse> withdraw(
        @PathVariable("id") UUID accountId,
        @Valid @RequestBody WithdrawRequest request,
        HttpServletRequest httpReq
    ) {
        log.debug("Withdrawal {} from account {}", request.getAmount(), accountId);
        LedgerOperationResponse response = accountService.withdraw(
            accountId, request, extractClientIp(httpReq)
        );
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin-only: view any user's account
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Admin-only endpoint: list ALL accounts for any given user by their UUID.
     *
     * <p>Separated from the standard GET /accounts endpoint to have an
     * explicit route that is ADMIN-restricted at the route level in
     * {@code SecurityConfig}, providing defence-in-depth beyond the
     * {@code @PreAuthorize} check on the service method.
     *
     * @param userId the target user's UUID
     * @return {@code 200 OK} with list of {@link AccountResponse}
     */
    @GetMapping("/admin/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> listAccountsForUserAdmin(
        @PathVariable UUID userId
    ) {
        List<AccountResponse> accounts = accountService.getAccountsForUser(userId);
        return ResponseEntity.ok(accounts);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    /**
     * Determines the effective target user ID for the operation.
     *
     * <ul>
     *   <li>If the caller is an ADMIN and a {@code userId} query param is
     *       present, use the provided {@code userId}.</li>
     *   <li>Otherwise (USER role, or ADMIN without {@code userId}), use the
     *       authenticated user's own ID.</li>
     * </ul>
     *
     * <p>This prevents USER-role callers from hijacking an account creation
     * by passing someone else's UUID in the query param — even if the
     * SecurityConfig were misconfigured, the USER's own ID is always used.
     */
    private UUID resolveTargetUserId(User currentUser, UUID suppliedUserId) {
        boolean isAdmin = currentUser.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin && suppliedUserId != null) {
            log.debug("ADMIN acting on behalf of user {}", suppliedUserId);
            return suppliedUserId;
        }

        return currentUser.getId();
    }

    /**
     * Extracts the real client IP address, honouring the
     * {@code X-Forwarded-For} header when the application runs behind a
     * reverse proxy or load balancer (common in production deployments).
     *
     * <p>Always use the FIRST IP in the {@code X-Forwarded-For} chain —
     * it represents the original client. Subsequent IPs are proxy hops.
     *
     * @param request the incoming HTTP request
     * @return the best-guess real client IP string
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
