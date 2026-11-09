package com.banking.core.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical error envelope returned by all API error responses.
 *
 * <p>Every non-2xx response from this application uses this structure,
 * ensuring API consumers have a consistent shape to parse regardless of
 * the error type.
 *
 * <h3>Example JSON</h3>
 * <pre>
 * {
 *   "timestamp" : "2024-01-15T10:30:00.000Z",
 *   "status"    : 400,
 *   "error"     : "Bad Request",
 *   "message"   : "Validation failed",
 *   "path"      : "/api/v1/auth/register",
 *   "details"   : {
 *     "email"    : "Email must be a valid email address",
 *     "password" : "Password must contain at least one uppercase letter..."
 *   }
 * }
 * </pre>
 *
 * <p>{@code details} is populated only for validation errors; it is {@code null}
 * (omitted in JSON) for all other error types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    private int     status;
    private String  error;
    private String  message;
    private String  path;

    /** Field-level validation errors, keyed by field name. Null for non-validation errors. */
    private Map<String, String> details;
}
