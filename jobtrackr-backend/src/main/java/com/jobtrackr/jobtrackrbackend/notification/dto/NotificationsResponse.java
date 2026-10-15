package com.jobtrackr.jobtrackrbackend.notification.dto;

import java.time.LocalDate;
import java.util.List;

// Body of GET /notifications. "due" (today or overdue, most overdue first) drives the bell's badge and can
// be dismissed; "upcoming" is for information only.
public record NotificationsResponse(
        LocalDate today,
        List<FollowUpNotification> due,
        List<FollowUpNotification> upcoming
) {
}
