package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.IntegrationTest;
import com.jobtrackr.jobtrackrbackend.application.ApplicationRepository;
import com.jobtrackr.jobtrackrbackend.user.DemoAccount;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

// The real reminder queries and "mark as sent" updates against PostgreSQL. The mailer is a mock, so nothing is
// sent. The queries see every user in the database (the job has no signed-in user), so the assertions only
// look at this test's own user; the rollback undoes all of it.
@IntegrationTest
@Transactional
class ReminderEmailIntegrationTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired
    private ReminderEmailService reminderEmailService;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReminderEmailProperties properties;
    @Autowired
    private NotificationProperties notificationProperties;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private ReminderMailer mailer;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = createUser();
    }

    @Test
    void interviewReminder_coversOnlyTheNextThirtyMinutes_andIsSentOncePerInterviewTime() throws Exception {
        Instant now = Instant.now();
        createInterview("In15Minutes", "APPLIED", now.plus(Duration.ofMinutes(15)));
        createInterview("In45Minutes", "INTERVIEW", now.plus(Duration.ofMinutes(45)));
        createInterview("StartedAlready", "INTERVIEW", now.minus(Duration.ofMinutes(5)));
        createInterview("Rejected", "REJECTED", now.plus(Duration.ofMinutes(10)));
        long demoUserId = jdbc.queryForObject("select id from users where email = ?", Long.class, DemoAccount.EMAIL);
        createInterview(demoUserId, "DemoCard", "INTERVIEW", now.plus(Duration.ofMinutes(10)));

        reminderEmailService.sendInterviewReminders();
        assertThat(remindedCompanies()).containsExactly("In15Minutes");
        assertThat(remindedCompanies(demoUserId)).isEmpty();

        clearInvocations(mailer);
        reminderEmailService.sendInterviewReminders();
        assertThat(remindedCompanies()).isEmpty();
    }

    // The owner's example: an interview at 7:30 pm whose time is saved at 7:15 pm is already inside the
    // 30-minute window, so the next run (within a minute) sends it. Moving it again re-arms the reminder.
    @Test
    void interviewMovedIntoTheWindow_isRemindedOnTheNextRun_andMovingItAgainRemindsAgain() throws Exception {
        Instant now = Instant.now();
        long applicationId = createInterview("Freshworks", "INTERVIEW", now.plus(Duration.ofHours(2)));

        reminderEmailService.sendInterviewReminders();
        assertThat(remindedCompanies()).isEmpty();

        // "It is 7:15 and the interview is now at 7:30"
        moveInterview(applicationId, now.plus(Duration.ofMinutes(15)));
        reminderEmailService.sendInterviewReminders();
        assertThat(remindedCompanies()).containsExactly("Freshworks");

        clearInvocations(mailer);
        moveInterview(applicationId, now.plus(Duration.ofMinutes(25)));
        reminderEmailService.sendInterviewReminders();
        assertThat(remindedCompanies()).containsExactly("Freshworks");
    }

    // The switch is read on every run: turned off, the very next run sends nothing to this user
    @Test
    void userWhoSwitchedRemindersOff_getsNoInterviewReminderAndNoDigest() throws Exception {
        LocalDate today = LocalDate.now(INDIA);
        Instant nineAm = ZonedDateTime.of(today, LocalTime.of(9, 0), INDIA).toInstant();
        ReminderEmailService atNine = new ReminderEmailService(applicationRepository, userRepository, mailer,
                properties, notificationProperties, Clock.fixed(nineAm, ZoneOffset.UTC));
        createInterview("SoonButOff", "INTERVIEW", Instant.now().plus(Duration.ofMinutes(10)));
        createFollowUp("DueButOff", today);
        jdbc.update("update users set email_reminders_enabled = false where id = ?", userId);

        reminderEmailService.sendInterviewReminders();
        atNine.sendDueDigests();

        assertThat(remindedCompanies()).isEmpty();
        assertThat(digestsFor(userId)).isEmpty();
        // Not marked either: switched back on later the same day, the digest still goes out
        assertThat(digestSentOn(userId)).isNull();
    }

    @Test
    void markingTheReminder_doesNotBumpTheCardVersion() throws Exception {
        long applicationId = createInterview("Acme", "APPLIED", Instant.now().plus(Duration.ofMinutes(10)));
        Integer before = version(applicationId);

        reminderEmailService.sendInterviewReminders();

        assertThat(version(applicationId)).isEqualTo(before);
    }

    @Test
    void digest_listsDueFollowUpsAndTheRestOfTodaysInterviews_onceADay() throws Exception {
        LocalDate today = LocalDate.now(INDIA);
        // A service whose clock says 09:00 India time today, so the test does not depend on when it runs
        Instant nineAm = ZonedDateTime.of(today, LocalTime.of(9, 0), INDIA).toInstant();
        ReminderEmailService atNine = new ReminderEmailService(applicationRepository, userRepository, mailer,
                properties, notificationProperties, Clock.fixed(nineAm, ZoneOffset.UTC));
        createFollowUp("DueToday", today);
        createFollowUp("Overdue", today.minusDays(2));
        createFollowUp("NextWeek", today.plusDays(7));
        createInterview("Afternoon", "INTERVIEW", nineAm.plus(Duration.ofHours(6)));
        createInterview("EarlyMorning", "INTERVIEW", nineAm.minus(Duration.ofMinutes(90)));
        createInterview("Tomorrow", "INTERVIEW", nineAm.plus(Duration.ofDays(1)));
        long quietUserId = createUser();

        atNine.sendDueDigests();

        ReminderDigest digest = digestsFor(userId).getFirst();
        assertThat(digest.followUps()).extracting(ReminderDigest.FollowUpLine::company).containsExactly("Overdue", "DueToday");
        assertThat(digest.interviews()).extracting(ReminderDigest.InterviewLine::company).containsExactly("Afternoon");
        // Nothing to report: no email, but the day still counts as handled
        assertThat(digestsFor(quietUserId)).isEmpty();
        assertThat(digestSentOn(quietUserId)).isEqualTo(today);

        clearInvocations(mailer);
        atNine.sendDueDigests();
        assertThat(digestsFor(userId)).isEmpty();
    }

    private List<String> remindedCompanies() throws Exception {
        return remindedCompanies(userId);
    }

    private List<String> remindedCompanies(long ownerId) throws Exception {
        ArgumentCaptor<InterviewReminder> sent = ArgumentCaptor.forClass(InterviewReminder.class);
        verify(mailer, atLeast(0)).sendInterviewReminder(sent.capture());
        return sent.getAllValues().stream().filter(reminder -> reminder.userId() == ownerId).map(InterviewReminder::company).toList();
    }

    private List<ReminderDigest> digestsFor(long ownerId) throws Exception {
        ArgumentCaptor<ReminderDigest> sent = ArgumentCaptor.forClass(ReminderDigest.class);
        verify(mailer, atLeast(0)).sendDigest(sent.capture());
        return sent.getAllValues().stream().filter(digest -> digest.userId() == ownerId).toList();
    }

    private long createUser() {
        return jdbc.queryForObject("insert into users (email, password_hash, full_name) values (?, 'x', 'Reminder Test') returning id",
                Long.class, "reminder-" + UUID.randomUUID() + "@test.dev");
    }

    private long createInterview(String company, String status, Instant interviewAt) {
        return createInterview(userId, company, status, interviewAt);
    }

    private long createInterview(long ownerId, String company, String status, Instant interviewAt) {
        return jdbc.queryForObject("""
                insert into applications (user_id, company, role, status, position, interview_at)
                values (?, ?, 'Engineer', ?, 0, ?) returning id
                """, Long.class, ownerId, company, status, Timestamp.from(interviewAt.truncatedTo(ChronoUnit.MICROS)));
    }

    private void createFollowUp(String company, LocalDate followUpOn) {
        jdbc.update("insert into applications (user_id, company, role, status, position, follow_up_on) values (?, ?, 'Engineer', 'APPLIED', 0, ?)",
                userId, company, followUpOn);
    }

    // Like the user saving a new time. The whole test is one transaction, so the cached entity is dropped to make
    // the next query read the new time (in the app, each job run works on fresh entities anyway).
    private void moveInterview(long applicationId, Instant interviewAt) {
        entityManager.clear();
        jdbc.update("update applications set interview_at = ? where id = ?", Timestamp.from(interviewAt.truncatedTo(ChronoUnit.MICROS)), applicationId);
    }

    private Integer version(long applicationId) {
        return jdbc.queryForObject("select version from applications where id = ?", Integer.class, applicationId);
    }

    private LocalDate digestSentOn(long ownerId) {
        return jdbc.queryForObject("select digest_sent_on from users where id = ?", LocalDate.class, ownerId);
    }
}
