package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Body of POST /auth/reset-password: the token from the emailed link plus the new password, which follows
// the same policy as registration. The token is in the body, not the URL, so it never reaches access logs.
public record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        @Pattern(regexp = PasswordPolicy.COMPOSITION_REGEX, message = PasswordPolicy.COMPOSITION_MESSAGE)
        String newPassword
) {
}
