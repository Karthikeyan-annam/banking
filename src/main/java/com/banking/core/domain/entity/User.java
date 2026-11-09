package com.banking.core.domain.entity;

import com.banking.core.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity representing a system user (customer or admin).
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>Implements {@link UserDetails} so Spring Security can use this entity
 *       directly — no separate {@code UserPrincipal} wrapper needed.</li>
 *   <li>UUID primary key prevents integer-based enumeration attacks on the API.</li>
 *   <li>Password is stored as a BCrypt hash; the plaintext is never retained.</li>
 *   <li>{@code @EntityListeners(AuditingEntityListener.class)} wires the
 *       Spring Data JPA auditing that populates {@code createdAt}/{@code updatedAt}.</li>
 * </ul>
 *
 * <h3>Lombok Notes</h3>
 * We deliberately use {@code @Getter}/{@code @Setter} instead of {@code @Data}
 * to avoid the auto-generated {@code equals}/{@code hashCode} that breaks
 * Hibernate identity semantics (entities should use surrogate-key identity).
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_email", columnNames = "email")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * BCrypt-hashed password.  Named {@code passwordHash} in the domain;
     * mapped to column {@code password_hash}.  The {@link UserDetails} contract
     * requires a method called {@code getPassword()} which Lombok generates.
     */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    // ── Spring Security account-status flags ──────────────────────────────

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "is_account_non_expired", nullable = false)
    @Builder.Default
    private boolean accountNonExpired = true;

    @Column(name = "is_account_non_locked", nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    @Column(name = "is_credentials_non_expired", nullable = false)
    @Builder.Default
    private boolean credentialsNonExpired = true;

    // ── Auditing ──────────────────────────────────────────────────────────

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relationship ──────────────────────────────────────────────────────

    /**
     * Lazy-loaded for performance: most user lookups don't need account data.
     * FetchType.LAZY is the default for @OneToMany but stated explicitly for clarity.
     * CascadeType.ALL is intentionally NOT used — accounts have their own lifecycle.
     */
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Account> accounts;

    // ── UserDetails contract ───────────────────────────────────────────────

    /**
     * Returns a singleton list containing a {@link SimpleGrantedAuthority}
     * for the user's role, prefixed with "ROLE_" as Spring Security expects.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * Spring Security uses {@code getUsername()} as the unique principal identifier.
     * We use the email address as the username.
     */
    @Override
    public String getUsername() {
        return email;
    }

    // Explicit overrides for boolean flags (Lombok generates these but
    // UserDetails demands them — explicit keeps IDE inspection happy).
    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public boolean isAccountNonExpired() { return accountNonExpired; }

    @Override
    public boolean isAccountNonLocked() { return accountNonLocked; }

    @Override
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }

    // ── equals / hashCode ─────────────────────────────────────────────────
    // Based ONLY on the surrogate key (id), consistent with Hibernate's
    // recommendation for entities with a generated primary key.

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Constant hashCode for new (transient) instances; stable after persist.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', role=" + role + "}";
    }
}
