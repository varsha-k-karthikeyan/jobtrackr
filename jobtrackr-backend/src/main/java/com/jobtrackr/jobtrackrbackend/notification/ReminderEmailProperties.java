package com.jobtrackr.jobtrackrbackend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.LocalTime;

// Typed view of jobtrackr.reminder-emails.* in application.yml, validated at startup. The zone for "08:00"
// and "today" is notifications.zone, shared with the bell, so both agree on which follow-ups are due.
// (The job's cron is read directly by @Scheduled in ReminderEmailJob.)
@Validated
@ConfigurationProperties(prefix = "jobtrackr.reminder-emails")
public record ReminderEmailProperties(
        // Local time from which the day's digest is sent; later the same day if the app was down at that time
        @NotNull LocalTime digestTime,
        // How long before an interview its reminder goes out
        @NotNull Duration interviewLeadTime,
        // Frontend base URL for the "open your board" link; from config only, like the reset link
        @NotBlank String appUrl,
        @NotBlank String from
) {
}
