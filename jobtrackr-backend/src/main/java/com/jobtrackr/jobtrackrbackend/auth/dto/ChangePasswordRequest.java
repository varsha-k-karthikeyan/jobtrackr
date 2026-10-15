package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Body of PUT /me/password. The current password has no format rules (like login: it only has to match);
// the new one follows the same policy as registration.
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.COMPOSITION_REGEX, message = PasswordPolicy.COMPOSITION_MESSAGE)
        String newPassword
) {
}
