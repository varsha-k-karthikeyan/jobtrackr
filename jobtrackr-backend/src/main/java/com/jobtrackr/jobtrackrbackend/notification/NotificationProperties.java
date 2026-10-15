package com.jobtrackr.jobtrackrbackend.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.ZoneId;

// Typed view of notifications.* in application.yml, validated at startup
@Validated
@ConfigurationProperties(prefix = "notifications")
public record NotificationProperties(
        // The zone in which "today" is decided. The JVM runs in UTC, but a follow-up date is a calendar
        // date in the user's zone: at 01:00 in India it is still yesterday in UTC.
        @NotNull ZoneId zone,
        // How many days ahead a follow-up counts as "upcoming"
        @Min(0) @Max(30) int upcomingDays
) {
}
