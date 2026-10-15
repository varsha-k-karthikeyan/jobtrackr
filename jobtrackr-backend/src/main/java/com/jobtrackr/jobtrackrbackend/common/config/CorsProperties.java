package com.jobtrackr.jobtrackrbackend.common.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// Typed view of jobtrackr.cors.*. The origins come from CORS_ALLOWED_ORIGINS (comma-separated),
// so a deployed frontend URL can be added without a code change.
@Validated
@ConfigurationProperties(prefix = "jobtrackr.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
