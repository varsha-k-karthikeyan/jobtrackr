package com.jobtrackr.jobtrackrbackend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// Typed view of the jobtrackr.jwt.* settings. @Validated makes the app refuse to start with a weak key.
@Validated
@ConfigurationProperties(prefix = "jobtrackr.jwt")
public record JwtProperties(
        // HS256 needs a key of at least 256 bits = 32 bytes
        @NotBlank @Size(min = 32, message = "must be at least 32 characters for HS256") String secret,
        @NotNull Duration accessTokenTtl,
        // How long a refresh token (and so a "remembered" session) lasts without use
        @NotNull Duration refreshTokenTtl,
        // Secure cookies are only sent over HTTPS: false for http://localhost, true when deployed
        boolean refreshCookieSecure
) {
}
