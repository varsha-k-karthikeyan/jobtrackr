package com.jobtrackr.jobtrackrbackend.notification.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;

import java.time.Instant;

// One upcoming interview in the notification menu. interviewAt is the exact instant (the browser shows it in
// its own zone); daysUntil is computed on the server in the configured zone, like FollowUpNotification's
// daysUntilDue: 0 = today, 1 = tomorrow.
public record InterviewNotification(
        Long applicationId,
        String company,
        String role,
        ApplicationStatus status,
        Instant interviewAt,
        long daysUntil
) {
}
