package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.common.EmailGreeting;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

// Builds and sends the two reminder emails (morning digest, interview starting soon), each with a
// plain-text and an HTML version. Locally they land in Mailpit (http://localhost:8025); tests mock this class.
// Company and role are user-typed, so the HTML version escapes them.
@Component
@RequiredArgsConstructor
public class ReminderMailer {

    // Locale.ENGLISH: the JVM's default locale must not turn "PM" into something else on another machine
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final ReminderEmailProperties properties;

    public void sendDigest(ReminderDigest digest) throws MessagingException {
        send(digest.email(), digestSubject(digest), digestText(digest, boardLink()), digestHtml(digest, boardLink()));
    }

    public void sendInterviewReminder(InterviewReminder reminder) throws MessagingException {
        send(reminder.email(), interviewSubject(reminder), interviewText(reminder, boardLink()), interviewHtml(reminder, boardLink()));
    }

    private void send(String to, String subject, String text, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text, html);
        mailSender.send(message);
    }

    private String boardLink() {
        return properties.appUrl() + "/board";
    }

    // ---- Morning digest ----

    static String digestSubject(ReminderDigest digest) {
        String interviews = count(digest.interviews().size(), "interview");
        String followUps = count(digest.followUps().size(), "follow-up");
        String summary = digest.interviews().isEmpty() ? followUps
                : digest.followUps().isEmpty() ? interviews
                : interviews + " and " + followUps;
        return "Your JobTrackr day, " + DAY.format(digest.day()) + ": " + summary;
    }

    static String digestText(ReminderDigest digest, String boardLink) {
        StringBuilder text = new StringBuilder(EmailGreeting.of(digest.recipientName(), digest.email())).append("\n\n");
        if (!digest.interviews().isEmpty()) {
            text.append("Interviews today:\n");
            digest.interviews().forEach(line -> text.append("- ").append(TIME.format(line.startsAt())).append("  ")
                    .append(line.company()).append(" (").append(line.role()).append(")\n"));
            text.append('\n');
        }
        if (!digest.followUps().isEmpty()) {
            text.append("Follow-ups due:\n");
            digest.followUps().forEach(line -> text.append("- ").append(line.company()).append(" (").append(line.role())
                    .append("), ").append(dueText(line)).append('\n'));
            text.append('\n');
        }
        return text.append("Open your board: ").append(boardLink).append('\n')
                .append(footer()).toString();
    }

    static String digestHtml(ReminderDigest digest, String boardLink) {
        StringBuilder html = new StringBuilder("<p>").append(HtmlUtils.htmlEscape(EmailGreeting.of(digest.recipientName(), digest.email()))).append("</p>");
        if (!digest.interviews().isEmpty()) {
            html.append("<h3>Interviews today</h3><ul>")
                    .append(digest.interviews().stream()
                            .map(line -> "<li><strong>" + TIME.format(line.startsAt()) + "</strong> " + escaped(line.company(), line.role()) + "</li>")
                            .collect(Collectors.joining()))
                    .append("</ul>");
        }
        if (!digest.followUps().isEmpty()) {
            html.append("<h3>Follow-ups due</h3><ul>")
                    .append(digest.followUps().stream()
                            .map(line -> "<li>" + escaped(line.company(), line.role()) + ", " + dueText(line) + "</li>")
                            .collect(Collectors.joining()))
                    .append("</ul>");
        }
        return html.append("<p><a href=\"").append(boardLink).append("\">Open your board</a></p>")
                .append("<p style=\"color:#666;font-size:12px\">").append(footer()).append("</p>").toString();
    }

    private static String dueText(ReminderDigest.FollowUpLine line) {
        if (line.daysOverdue() == 0) {
            return "due today";
        }
        return "overdue by " + count(line.daysOverdue(), "day") + " (" + DAY.format(line.followUpOn()) + ")";
    }

    // ---- Interview starting soon ----

    static String interviewSubject(InterviewReminder reminder) {
        return "Starting soon: " + reminder.company() + " interview at " + TIME.format(reminder.startsAt());
    }

    static String interviewText(InterviewReminder reminder, String boardLink) {
        return """
                %s

                Your interview with %s (%s) starts at %s, in %s.

                Good luck!

                Open your board: %s
                %s
                """.formatted(EmailGreeting.of(reminder.recipientName(), reminder.email()), reminder.company(), reminder.role(),
                TIME.format(reminder.startsAt()), count(reminder.minutesToGo(), "minute"), boardLink, footer());
    }

    static String interviewHtml(InterviewReminder reminder, String boardLink) {
        return """
                <p>%s</p>
                <p>Your interview with %s starts at <strong>%s</strong>, in %s.</p>
                <p>Good luck!</p>
                <p><a href="%s">Open your board</a></p>
                <p style="color:#666;font-size:12px">%s</p>
                """.formatted(HtmlUtils.htmlEscape(EmailGreeting.of(reminder.recipientName(), reminder.email())), escaped(reminder.company(), reminder.role()),
                TIME.format(reminder.startsAt()), count(reminder.minutesToGo(), "minute"), boardLink, footer());
    }

    // ---- Shared pieces ----

    private static String escaped(String company, String role) {
        return "<strong>" + HtmlUtils.htmlEscape(company) + "</strong> (" + HtmlUtils.htmlEscape(role) + ")";
    }

    private static String count(long amount, String noun) {
        return amount + " " + noun + (amount == 1 ? "" : "s");
    }


    private static String footer() {
        return "You get this email because reminder emails are on for your JobTrackr account.";
    }
}
