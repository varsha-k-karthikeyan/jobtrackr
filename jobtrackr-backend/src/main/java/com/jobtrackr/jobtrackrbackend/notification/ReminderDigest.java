package com.jobtrackr.jobtrackrbackend.notification;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

// Content of one user's morning email: what ReminderEmailService found, ready for ReminderMailer.
// Times are already in the configured zone, so the mailer only formats them.
public record ReminderDigest(
        Long userId,
        String email,
        String recipientName,
        LocalDate day,
        List<FollowUpLine> followUps,
        List<InterviewLine> interviews
) {

    // A due follow-up; daysOverdue is 0 when it is due today
    public record FollowUpLine(String company, String role, LocalDate followUpOn, long daysOverdue) {
    }

    public record InterviewLine(String company, String role, ZonedDateTime startsAt) {
    }

    public boolean isEmpty() {
        return followUps.isEmpty() && interviews.isEmpty();
    }
}
