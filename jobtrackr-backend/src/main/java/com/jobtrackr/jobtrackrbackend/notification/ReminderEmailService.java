package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.user.DemoAccount;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Decides which reminder emails are due and sends them; ReminderEmailJob calls it every minute.
// - Morning digest: once per user per day, from digestTime on (so a late start still sends it): the bell's
//   due follow-ups plus the rest of today's interviews. Nothing to report = no email, but the day is handled.
// - Interview reminder: every interview starting within the lead time whose reminder was not sent for its
//   current time. Saving a time 15 minutes ahead is therefore emailed within a minute; a past start never is.
// - Only to users whose switch is on (users.email_reminders_enabled, PUT /me/email-reminders). Both queries
//   read it on every run, so switching off stops the emails from the next minute on.
// - Never to the public demo account: anyone can use it, and its address is not an inbox we own (with a real
//   provider the email would go to an outside domain). The password-reset flow skips it for the same reason.
// Each email is marked as sent only after the send succeeded, so a failure is retried on the next run. One
// user's failure does not stop the others. Not @Transactional on purpose: a slow SMTP call must not hold a
// database transaction open; each repository call commits on its own.
// Logs carry user and application ids only, never addresses or card content.
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderEmailService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ReminderMailer mailer;
    private final ReminderEmailProperties properties;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    /** Returns how many digests were sent. */
    public int sendDueDigests() {
        ZoneId zone = notificationProperties.zone();
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        if (now.toLocalTime().isBefore(properties.digestTime())) {
            return 0;
        }
        LocalDate today = now.toLocalDate();
        Instant endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant();
        int sent = 0;
        for (User user : userRepository.findDueForDigest(today, DemoAccount.EMAIL)) {
            try {
                ReminderDigest digest = digestFor(user, today, now.toInstant(), endOfToday);
                if (!digest.isEmpty()) {
                    mailer.sendDigest(digest);
                    sent++;
                }
                userRepository.markDigestSent(user.getId(), today);
            } catch (Exception exception) {
                log.warn("Reminder digest for user {} could not be sent: {}", user.getId(), exception.getClass().getSimpleName());
            }
        }
        return sent;
    }

    /** Returns how many interview reminders were sent. */
    public int sendInterviewReminders() {
        Instant now = clock.instant();
        Instant until = now.plus(properties.interviewLeadTime());
        int sent = 0;
        for (Application application : applicationRepository.findInterviewsDueForReminder(NotificationService.INTERVIEW_STATUSES, now, until, DemoAccount.EMAIL)) {
            try {
                mailer.sendInterviewReminder(reminderFor(application, now));
                applicationRepository.markInterviewReminderSent(application.getId(), application.getInterviewAt());
                sent++;
            } catch (Exception exception) {
                log.warn("Interview reminder for application {} of user {} could not be sent: {}",
                        application.getId(), application.getUser().getId(), exception.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private ReminderDigest digestFor(User user, LocalDate today, Instant now, Instant endOfToday) {
        ZoneId zone = notificationProperties.zone();
        // The bell's "due" list (today and overdue, not dismissed), so the email and the app agree
        List<ReminderDigest.FollowUpLine> followUps = applicationRepository
                .findOpenFollowUps(user.getId(), NotificationService.NOTIFIED_STATUSES, today).stream()
                .map(application -> new ReminderDigest.FollowUpLine(application.getCompany(), application.getRole(),
                        application.getFollowUpOn(), ChronoUnit.DAYS.between(application.getFollowUpOn(), today)))
                .toList();
        // Only interviews still ahead: one at 07:30 is already over when the 08:00 digest goes out
        List<ReminderDigest.InterviewLine> interviews = applicationRepository
                .findInterviewsBetween(user.getId(), NotificationService.INTERVIEW_STATUSES, now, endOfToday).stream()
                .map(application -> new ReminderDigest.InterviewLine(application.getCompany(), application.getRole(),
                        application.getInterviewAt().atZone(zone)))
                .toList();
        return new ReminderDigest(user.getId(), user.getEmail(), user.getFullName(), today, followUps, interviews);
    }

    private InterviewReminder reminderFor(Application application, Instant now) {
        User owner = application.getUser();
        Duration toGo = Duration.between(now, application.getInterviewAt());
        // Rounded up: 14m10s left reads "in 15 minutes", never "in 0 minutes"
        long minutesToGo = (toGo.toSeconds() + 59) / 60;
        return new InterviewReminder(owner.getId(), owner.getEmail(), owner.getFullName(), application.getCompany(),
                application.getRole(), application.getInterviewAt().atZone(notificationProperties.zone()), minutesToGo);
    }
}
