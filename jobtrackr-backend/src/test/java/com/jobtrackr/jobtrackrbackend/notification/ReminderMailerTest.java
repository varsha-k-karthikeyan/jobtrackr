package com.jobtrackr.jobtrackrbackend.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The JavaMailSender is a mock: messages are built for real but never leave the test (no SMTP server)
class ReminderMailerTest {

    private static final String BOARD = "http://localhost:4200/board";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private final ReminderDigest digest = new ReminderDigest(5L, "varsha@test.dev", "Varsha", DAY,
            List.of(new ReminderDigest.FollowUpLine("Zoho", "Angular Developer", DAY, 0),
                    new ReminderDigest.FollowUpLine("Postman", "Frontend Engineer", DAY.minusDays(3), 3)),
            List.of(new ReminderDigest.InterviewLine("Freshworks", "Senior Frontend Engineer", at(14, 30)),
                    new ReminderDigest.InterviewLine("Swiggy", "UI Engineer", at(22, 15))));

    private final InterviewReminder reminder = new InterviewReminder(5L, "varsha@test.dev", "Varsha",
            "Freshworks", "Senior Frontend Engineer", at(22, 15), 17);

    @Test
    void sendsTheDigestToTheUser_asPlainTextAndHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        ReminderEmailProperties properties = new ReminderEmailProperties(LocalTime.of(8, 0), Duration.ofMinutes(30),
                "http://localhost:4200", "JobTrackr <no-reply@test.dev>");

        new ReminderMailer(sender, properties).sendDigest(digest);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).extracting(Object::toString).containsExactly("varsha@test.dev");
        assertThat(sent.getValue().getFrom()[0].toString()).isEqualTo("JobTrackr <no-reply@test.dev>");
        assertThat(sent.getValue().getSubject()).isEqualTo("Your JobTrackr day, Tue 20 Oct: 2 interviews and 2 follow-ups");
    }

    @Test
    void digest_listsInterviewsWithTheirTimes_andFollowUpsWithHowOverdueTheyAre() {
        assertThat(ReminderMailer.digestText(digest, BOARD))
                .contains("Interviews today:", "2:30 PM  Freshworks (Senior Frontend Engineer)", "10:15 PM  Swiggy (UI Engineer)")
                .contains("Follow-ups due:", "Zoho (Angular Developer), due today", "Postman (Frontend Engineer), overdue by 3 days (Sat 17 Oct)")
                .contains(BOARD);
        assertThat(ReminderMailer.digestHtml(digest, BOARD)).contains("<strong>2:30 PM</strong>", "href=\"" + BOARD + "\"");
    }

    @Test
    void digestSubject_namesOnlyWhatIsThere() {
        ReminderDigest onlyFollowUp = new ReminderDigest(5L, "varsha.k.karthikeyan@test.dev", null, DAY, digest.followUps().subList(0, 1), List.of());

        assertThat(ReminderMailer.digestSubject(onlyFollowUp)).isEqualTo("Your JobTrackr day, Tue 20 Oct: 1 follow-up");
        assertThat(ReminderMailer.digestText(onlyFollowUp, BOARD)).startsWith("Hi varsha.k.karthikeyan,").doesNotContain("Interviews today");
    }

    @Test
    void interviewReminder_saysWhenAndHowSoon() {
        assertThat(ReminderMailer.interviewSubject(reminder)).isEqualTo("Starting soon: Freshworks interview at 10:15 PM");
        assertThat(ReminderMailer.interviewText(reminder, BOARD))
                .startsWith("Hi Varsha,")
                .contains("Your interview with Freshworks (Senior Frontend Engineer) starts at 10:15 PM, in 17 minutes.");
    }

    @Test
    void html_escapesTheUserTypedCompanyAndRole() {
        InterviewReminder hostile = new InterviewReminder(5L, "x@test.dev", "<i>Eve</i>", "<script>x</script>", "A & B",
                at(22, 15), 1);

        assertThat(ReminderMailer.interviewHtml(hostile, BOARD))
                .contains("&lt;script&gt;x&lt;/script&gt;", "A &amp; B", "&lt;i&gt;Eve&lt;/i&gt;", "in 1 minute.")
                .doesNotContain("<script>", "<i>Eve");
    }

    private static ZonedDateTime at(int hour, int minute) {
        return ZonedDateTime.of(DAY, LocalTime.of(hour, minute), INDIA);
    }
}
