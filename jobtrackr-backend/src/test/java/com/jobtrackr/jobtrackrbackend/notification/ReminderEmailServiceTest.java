package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.application.Application;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.user.DemoAccount;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Pure unit test: repositories and mailer are mocks, time is a fixed Clock. India is UTC+05:30.
@ExtendWith(MockitoExtension.class)
class ReminderEmailServiceTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReminderMailer mailer;

    private ReminderEmailService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        ReminderEmailProperties properties = new ReminderEmailProperties(LocalTime.of(8, 0), Duration.ofMinutes(30),
                "http://localhost:4200", "JobTrackr <no-reply@test.dev>");
        return new ReminderEmailService(applicationRepository, userRepository, mailer, properties,
                new NotificationProperties(INDIA, 7), clock);
    }

    // ---- Morning digest ----

    @Test
    void digest_beforeEightIndiaTime_doesNothing() {
        // 02:29 UTC = 07:59 in India
        assertThat(serviceAt("2026-10-20T02:29:00Z").sendDueDigests()).isZero();

        verifyNoInteractions(userRepository, applicationRepository, mailer);
    }

    @Test
    void digest_fromEightOn_sendsDueFollowUpsAndTheRestOfTodaysInterviews_thenMarksTheDay() throws Exception {
        // 03:00 UTC = 08:30 in India; a backend started late still sends the day's digest
        Instant now = Instant.parse("2026-10-20T03:00:00Z");
        User user = user(5L);
        Application followUp = followUp("Zoho", DAY.minusDays(2));
        Application interview = interview("Freshworks", ZonedDateTime.of(DAY, LocalTime.of(22, 15), INDIA).toInstant());
        when(userRepository.findDueForDigest(DAY, DemoAccount.EMAIL)).thenReturn(List.of(user));
        when(applicationRepository.findOpenFollowUps(5L, NotificationService.NOTIFIED_STATUSES, DAY)).thenReturn(List.of(followUp));
        // From "now" (an interview at 07:30 is already over) to midnight India time
        when(applicationRepository.findInterviewsBetween(5L, ReminderEmailService.INTERVIEW_STATUSES, now, Instant.parse("2026-10-20T18:30:00Z")))
                .thenReturn(List.of(interview));

        assertThat(serviceAt("2026-10-20T03:00:00Z").sendDueDigests()).isEqualTo(1);

        ArgumentCaptor<ReminderDigest> sent = ArgumentCaptor.forClass(ReminderDigest.class);
        verify(mailer).sendDigest(sent.capture());
        assertThat(sent.getValue().day()).isEqualTo(DAY);
        assertThat(sent.getValue().followUps()).extracting(ReminderDigest.FollowUpLine::company, ReminderDigest.FollowUpLine::daysOverdue)
                .containsExactly(tuple("Zoho", 2L));
        assertThat(sent.getValue().interviews()).extracting(line -> line.startsAt().toLocalTime())
                .containsExactly(LocalTime.of(22, 15));
        verify(userRepository).markDigestSent(5L, DAY);
    }

    @Test
    void digest_withNothingToReport_isNotSent_butTheDayIsHandled() throws Exception {
        // Mocks are built first: creating them inside when(...).thenReturn(...) would be nested stubbing
        User user = user(5L);
        when(userRepository.findDueForDigest(DAY, DemoAccount.EMAIL)).thenReturn(List.of(user));
        when(applicationRepository.findOpenFollowUps(any(), any(), any())).thenReturn(List.of());
        when(applicationRepository.findInterviewsBetween(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(serviceAt("2026-10-20T03:00:00Z").sendDueDigests()).isZero();

        verify(mailer, never()).sendDigest(any());
        verify(userRepository).markDigestSent(5L, DAY);
    }

    @Test
    void digest_thatFailsToSend_staysDueForTheNextRun_andOtherUsersStillGetTheirs() throws Exception {
        User failing = user(5L);
        User fine = user(6L);
        Application followUp = followUp("Zoho", DAY);
        when(userRepository.findDueForDigest(DAY, DemoAccount.EMAIL)).thenReturn(List.of(failing, fine));
        when(applicationRepository.findOpenFollowUps(anyLong(), any(), any())).thenReturn(List.of(followUp));
        when(applicationRepository.findInterviewsBetween(anyLong(), any(), any(), any())).thenReturn(List.of());
        doThrow(new MessagingException("SMTP down")).when(mailer).sendDigest(argThat(digest -> digest.userId() == 5L));

        assertThat(serviceAt("2026-10-20T03:00:00Z").sendDueDigests()).isEqualTo(1);

        verify(userRepository, never()).markDigestSent(5L, DAY);
        verify(userRepository).markDigestSent(6L, DAY);
    }

    // ---- Interview starting soon ----

    @Test
    void interviewReminders_lookThirtyMinutesAhead_andAreMarkedForTheTimeTheyWereSentFor() throws Exception {
        Instant now = Instant.parse("2026-10-20T16:28:00Z"); // 21:58 in India
        // Saved at 21:58 for 22:15: already inside the 30 minutes, so the very next run sends it
        Instant interviewAt = Instant.parse("2026-10-20T16:45:00Z");
        Application interview = interview("Freshworks", interviewAt);
        when(interview.getId()).thenReturn(42L);
        User owner = user(5L);
        when(interview.getUser()).thenReturn(owner);
        when(applicationRepository.findInterviewsDueForReminder(ReminderEmailService.INTERVIEW_STATUSES, now, now.plus(Duration.ofMinutes(30)), DemoAccount.EMAIL))
                .thenReturn(List.of(interview));

        assertThat(serviceAt("2026-10-20T16:28:00Z").sendInterviewReminders()).isEqualTo(1);

        ArgumentCaptor<InterviewReminder> sent = ArgumentCaptor.forClass(InterviewReminder.class);
        verify(mailer).sendInterviewReminder(sent.capture());
        assertThat(sent.getValue().minutesToGo()).isEqualTo(17);
        assertThat(sent.getValue().startsAt().toLocalTime()).isEqualTo(LocalTime.of(22, 15));
        assertThat(sent.getValue().userId()).isEqualTo(5L);
        verify(applicationRepository).markInterviewReminderSent(42L, interviewAt);
    }

    @Test
    void interviewReminder_roundsTheMinutesUp() throws Exception {
        Application interview = interview("Zoho", Instant.parse("2026-10-20T16:42:10Z"));
        User owner = user(5L);
        when(interview.getUser()).thenReturn(owner);
        when(applicationRepository.findInterviewsDueForReminder(any(), any(), any(), any())).thenReturn(List.of(interview));

        serviceAt("2026-10-20T16:28:00Z").sendInterviewReminders();

        verify(mailer).sendInterviewReminder(argThat(reminder -> reminder.minutesToGo() == 15));
    }

    @Test
    void interviewReminder_thatFailsToSend_isNotMarked_soTheNextRunRetriesIt() throws Exception {
        Application failing = interview("Zoho", Instant.parse("2026-10-20T16:40:00Z"));
        Application fine = interview("Freshworks", Instant.parse("2026-10-20T16:50:00Z"));
        when(failing.getId()).thenReturn(1L);
        when(fine.getId()).thenReturn(2L);
        User owner = user(5L);
        when(failing.getUser()).thenReturn(owner);
        when(fine.getUser()).thenReturn(owner);
        when(applicationRepository.findInterviewsDueForReminder(any(), any(), any(), any())).thenReturn(List.of(failing, fine));
        doThrow(new MessagingException("SMTP down")).when(mailer).sendInterviewReminder(argThat(reminder -> reminder.company().equals("Zoho")));

        assertThat(serviceAt("2026-10-20T16:28:00Z").sendInterviewReminders()).isEqualTo(1);

        verify(applicationRepository, never()).markInterviewReminderSent(eq(1L), any());
        verify(applicationRepository).markInterviewReminderSent(2L, Instant.parse("2026-10-20T16:50:00Z"));
    }

    private static User user(Long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }

    private static Application followUp(String company, LocalDate followUpOn) {
        Application application = mock(Application.class);
        when(application.getCompany()).thenReturn(company);
        when(application.getFollowUpOn()).thenReturn(followUpOn);
        return application;
    }

    private static Application interview(String company, Instant interviewAt) {
        Application application = mock(Application.class);
        when(application.getCompany()).thenReturn(company);
        when(application.getInterviewAt()).thenReturn(interviewAt);
        return application;
    }
}
