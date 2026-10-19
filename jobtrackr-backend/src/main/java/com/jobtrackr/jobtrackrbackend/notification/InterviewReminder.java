package com.jobtrackr.jobtrackrbackend.notification;

import java.time.ZonedDateTime;

// Content of one "your interview starts soon" email. minutesToGo is rounded up, so 14m10s reads "15 minutes".
public record InterviewReminder(
        Long userId,
        String email,
        String recipientName,
        String company,
        String role,
        ZonedDateTime startsAt,
        long minutesToGo
) {
}
