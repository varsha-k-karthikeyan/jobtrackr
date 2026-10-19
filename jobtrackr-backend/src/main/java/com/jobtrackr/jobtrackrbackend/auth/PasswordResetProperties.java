package com.jobtrackr.jobtrackrbackend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// Typed view of jobtrackr.password-reset.* in application.yml, validated at startup
@Validated
@ConfigurationProperties(prefix = "jobtrackr.password-reset")
public record PasswordResetProperties(
        // Frontend base URL for the emailed link; from config only, never from the request's Host header
        @NotBlank String appUrl,
        // Sender shown in the email, e.g. "JobTrackr <no-reply@example.com>"
        @NotBlank String from,
        @NotNull Duration tokenTtl,
        @NotNull Duration resendCooldown
) {
}
