package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;

// No format or length rules on purpose: every wrong value must end in the same 401, not in a 400
// that reveals the password policy.
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
