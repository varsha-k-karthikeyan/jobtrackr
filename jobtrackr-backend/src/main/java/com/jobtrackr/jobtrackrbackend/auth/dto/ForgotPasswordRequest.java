package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body of POST /auth/forgot-password. The answer is the same 204 whether or not the email is registered.
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
}
