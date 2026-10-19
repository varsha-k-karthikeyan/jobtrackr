package com.jobtrackr.jobtrackrbackend.notification.dto;

import java.time.LocalDate;
import java.util.List;

// Body of GET /notifications. "due" (today or overdue, most overdue first) can be dismissed; "upcoming" is for
// information only. "interviews" are the ones still ahead in the same window, earliest first; they leave the
// list by themselves once they start, so they are never dismissed. The badge counts due follow-ups plus
// today's interviews.
public record NotificationsResponse(
        LocalDate today,
        List<FollowUpNotification> due,
        List<FollowUpNotification> upcoming,
        List<InterviewNotification> interviews
) {
}
