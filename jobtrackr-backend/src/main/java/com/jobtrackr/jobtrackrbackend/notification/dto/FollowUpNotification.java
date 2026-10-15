package com.jobtrackr.jobtrackrbackend.notification.dto;

import com.jobtrackr.jobtrackrbackend.application.ApplicationStatus;

import java.time.LocalDate;

// One follow-up in the notification menu. daysUntilDue is computed on the server, in the configured zone,
// so the client does no date arithmetic: 0 = due today, -3 = three days overdue, 2 = due in two days.
public record FollowUpNotification(
        Long applicationId,
        String company,
        String role,
        ApplicationStatus status,
        LocalDate followUpOn,
        long daysUntilDue
) {
}
