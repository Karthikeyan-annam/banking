package com.banking.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stateless JWT service responsible for token generation, claim extraction,
 * and signature/expiry validation.
 *
 * <h3>Algorithm</h3>
 * We use {@code HS512} (HMAC-SHA-512). It requires a secret of at least
 * 512 bits (64 bytes). The key is read from the environment variable
 * {@code JWT_SECRET} via Spring's {@code @Value}. Never hard-code it.
 *
 * <h3>Claims Layout</h3>
 * <pre>
 * {
 *   "sub"   : "alice@bank.com",         // subject = username/email
 *   "roles" : ["ROLE_USER"],            // granted authorities
 *   "iat"   : 1700000000,              // issued-at (epoch seconds)
 *   "exp"   : 1700086400               // expiry   (iat + expiration ms)
 * }
 * </pre>
 *
 * <h3>Thread Safety</h3>
 * All methods are stateless and thread-safe; no instance state is mutated.
 */
@Service
@Slf4j
public class JwtService {

    // ── Configuration (injected from application.yml / env vars) ─────────

    @Value("${application.security.jwt.secret-key}")
    private String secretKeyString;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpirationMs;             // default: 86 400 000 (24 h)

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpirationMs;         // default: 604 800 000 (7 d)

    // ── Token Generation ──────────────────────────────────────────────────

    /**
     * Generates an access JWT for the given {@link UserDetails} principal.
     * Embeds the user's granted authorities as the {@code "roles"} claim.
     *
     * @param userDetails the authenticated principal
     * @return a signed, compact JWT string
     */
    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("roles", roles);

        return buildToken(extraClaims, userDetails, jwtExpirationMs);
    }

    /**
     * Generates a long-lived refresh token (no roles embedded — it is only
     * used to obtain a new access token, not to authorize actions directly).
     *
     * @param userDetails the authenticated principal
     * @return a signed, compact refresh JWT string
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshExpirationMs);
    }

    // ── Claim Extraction ──────────────────────────────────────────────────

    /**
     * Extracts the {@code sub} (subject) claim, which we set to the user's email.
     *
     * @param token a compact JWT string
     * @return the email/username stored in the token's subject claim
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the token's expiry timestamp.
     *
     * @param token a compact JWT string
     * @return the {@link Date} when the token expires
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Generic claim extractor using a {@link Function} resolver.
     *
     * @param token          a compact JWT string
     * @param claimsResolver a function that maps the parsed {@link Claims} to
     *                       the desired value
     * @param <T>            the return type
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ── Validation ────────────────────────────────────────────────────────

    /**
     * Validates that the token:
     * <ol>
     *   <li>Has not been tampered with (HMAC signature check).</li>
     *   <li>Has not expired.</li>
     *   <li>Belongs to the given {@code userDetails} principal
     *       (subject matches username).</li>
     * </ol>
     *
     * @param token       the compact JWT string to validate
     * @param userDetails the principal to match against the token subject
     * @return {@code true} if the token is valid for the given user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Checks whether the token's {@code exp} claim is in the past.
     *
     * @param token a compact JWT string
     * @return {@code true} if the token has expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Internal Helpers ──────────────────────────────────────────────────

    /**
     * Core builder that constructs and signs a JWT with the given payload.
     *
     * @param extraClaims  additional claims to embed (e.g., roles)
     * @param userDetails  principal whose username becomes the subject
     * @param expirationMs token lifetime in milliseconds
     * @return signed, compact JWT string
     */
    private String buildToken(
        Map<String, Object> extraClaims,
        UserDetails userDetails,
        long expirationMs
    ) {
        Instant now = Instant.now();
        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(getSigningKey(), Jwts.SIG.HS512)
            .compact();
    }

    /**
     * Parses the JWT and returns all claims.
     * Any signature or format error propagates as a {@link JwtException}
     * subtype, which the filter translates into a 401 response.
     *
     * @param token a compact JWT string
     * @return the parsed {@link Claims} payload
     * @throws ExpiredJwtException    if the token has expired
     * @throws MalformedJwtException  if the token structure is invalid
     * @throws SignatureException     if the HMAC signature does not match
     * @throws UnsupportedJwtException if the JWT format is not supported
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Derives a {@link SecretKey} from the configured secret string.
     * The string is UTF-8 encoded into bytes, then passed to JJWT's
     * {@link Keys#hmacShaKeyFor(byte[])} which validates the key length
     * for the target algorithm (HS512 requires ≥ 512 bits).
     *
     * @return an HMAC-SHA SecretKey
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
