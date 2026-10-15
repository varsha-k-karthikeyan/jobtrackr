package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // Length and composition are separate constraints so each failure gets its own clear message.
        // 8 to 20 characters by product decision (well inside BCrypt's 72-byte input limit).
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.COMPOSITION_REGEX, message = PasswordPolicy.COMPOSITION_MESSAGE)
        String password,
        @Size(max = 120) String fullName
) {
}
