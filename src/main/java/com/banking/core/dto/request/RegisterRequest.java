package com.banking.core.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for the user registration endpoint.
 *
 * <p>All validation is performed by Bean Validation (Hibernate Validator)
 * before the request reaches the service layer, so the service never receives
 * an invalid state.
 *
 * <h3>Password Policy</h3>
 * The {@code @Pattern} on {@code password} enforces:
 * <ul>
 *   <li>At least 8 characters</li>
 *   <li>At least one uppercase letter</li>
 *   <li>At least one lowercase letter</li>
 *   <li>At least one number/digit</li>
 *   <li>Allows any standard character (including any special characters/symbols)</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$",
        message = "Password must contain at least 8 characters, with at least one uppercase letter, one lowercase letter, and one number"
    )
    private String password;

    @Pattern(
        regexp = "^\\+?[0-9\\- ]{7,20}$",
        message = "Phone number must be a valid phone number"
    )
    private String phoneNumber;   // optional
}
