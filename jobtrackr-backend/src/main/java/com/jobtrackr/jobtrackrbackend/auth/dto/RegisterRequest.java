package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes of a password, so cap the length there
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 120) String fullName
) {
}
