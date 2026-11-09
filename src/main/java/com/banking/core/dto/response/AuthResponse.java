package com.banking.core.dto.response;

import com.banking.core.domain.enums.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response payload returned after a successful registration or login.
 *
 * <h3>Security Note</h3>
 * We deliberately do NOT return the user's password hash or internal
 * database IDs that could aid enumeration.  The {@code userId} UUID is
 * included because clients need it to construct subsequent account API calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /** The signed JWT access token to be sent as {@code Authorization: Bearer <token>}. */
    @JsonProperty("access_token")
    private String accessToken;

    /** Long-lived refresh token (can be used to obtain a new access token). */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** Token type — always "Bearer" for our implementation. */
    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    /** Access token lifetime in seconds (matches JWT {@code exp - iat}). */
    @JsonProperty("expires_in")
    private long expiresIn;

    // ── User info summary ─────────────────────────────────────────────────

    @JsonProperty("user_id")
    private UUID userId;

    private String email;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private Role role;
}
