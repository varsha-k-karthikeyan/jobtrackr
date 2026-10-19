package com.jobtrackr.jobtrackrbackend.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the reminder emails every minute (jobtrackr.reminder-emails.cron). One frequent check instead of a
// cron at 08:00 plus one timer per interview: it also covers an interview saved 15 minutes ahead, a time
// changed after its reminder went out, and catching up after the app was down. Each run is a few indexed
// queries that usually find nothing. Thin on purpose: the rules live in ReminderEmailService (unit tested).
// Runs only when scheduling is on (common/config/SchedulingConfig); tests switch it off.
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderEmailJob {

    private final ReminderEmailService reminderEmailService;

    @Scheduled(cron = "${jobtrackr.reminder-emails.cron}", zone = "${notifications.zone}")
    public void sendDueReminders() {
        // Interview reminders first: they are the time-critical ones
        int interviewReminders = reminderEmailService.sendInterviewReminders();
        int digests = reminderEmailService.sendDueDigests();
        if (interviewReminders + digests > 0) {
            log.info("Reminder emails sent: {} interview reminder(s), {} digest(s)", interviewReminders, digests);
        }
    }
}
