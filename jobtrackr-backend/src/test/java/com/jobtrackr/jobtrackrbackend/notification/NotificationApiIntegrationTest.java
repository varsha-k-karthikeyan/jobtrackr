package com.jobtrackr.jobtrackrbackend.notification;

import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real follow-up queries against PostgreSQL, through the controller. Each test runs as brand-new
// users inside a transaction that is rolled back. "Today" is the real date in the configured zone, so the
// dates below are relative to it.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationApiIntegrationTest {

    private static final String BASE = "/api/v1/notifications";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private CurrentUserProvider currentUser;

    private MockMvc mockMvc;
    private long userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        userId = createUser();
        when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void list_showsDueAndUpcomingOpenFollowUps_ofTheCallerOnly() throws Exception {
        createApplication(userId, "Overdue", "INTERVIEW", TODAY.minusDays(3));
        createApplication(userId, "DueToday", "APPLIED", TODAY);
        createApplication(userId, "InTwoDays", "APPLIED", TODAY.plusDays(2));
        createApplication(userId, "NextMonth", "APPLIED", TODAY.plusDays(30));
        createApplication(userId, "NoDate", "APPLIED", null);
        createApplication(userId, "Offer", "OFFER", TODAY);
        createApplication(userId, "Rejected", "REJECTED", TODAY);
        createApplication(createUser(), "SomeoneElse", "APPLIED", TODAY);

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(TODAY.toString()))
                .andExpect(jsonPath("$.due[*].company", contains("Overdue", "DueToday")))
                .andExpect(jsonPath("$.due[0].daysUntilDue").value(-3))
                .andExpect(jsonPath("$.due[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$.upcoming[*].company", contains("InTwoDays")))
                .andExpect(jsonPath("$.upcoming[0].daysUntilDue").value(2));
    }

    @Test
    void dismiss_hidesTheNotification_withoutBumpingTheCardVersion() throws Exception {
        long id = createApplication(userId, "Acme", "APPLIED", TODAY);

        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent()); // idempotent

        mockMvc.perform(get(BASE)).andExpect(jsonPath("$.due", empty()));
        // An edit form opened before the dismiss must still save without a 409
        assertThat(jdbc.queryForObject("select version from applications where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void dismiss_cardOfAnotherUser_is404() throws Exception {
        long othersCard = createApplication(createUser(), "Acme", "APPLIED", TODAY);

        mockMvc.perform(delete(BASE + "/" + othersCard))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertThat(jdbc.queryForObject("select reminder_dismissed_on from applications where id = ?", LocalDate.class, othersCard))
                .isNull();
    }

    @Test
    void dismissAll_hidesEveryDueFollowUp_butKeepsUpcoming() throws Exception {
        createApplication(userId, "Overdue", "APPLIED", TODAY.minusDays(1));
        createApplication(userId, "DueToday", "INTERVIEW", TODAY);
        createApplication(userId, "Tomorrow", "APPLIED", TODAY.plusDays(1));

        mockMvc.perform(delete(BASE)).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$.due", empty()))
                .andExpect(jsonPath("$.upcoming[*].company", contains("Tomorrow")));
    }

    @Test
    void newFollowUpDate_bringsADismissedNotificationBack() throws Exception {
        long id = createApplication(userId, "Acme", "APPLIED", TODAY.minusDays(2));
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());

        // The user reschedules the follow-up to today through the normal edit endpoint
        mockMvc.perform(put("/api/v1/applications/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company": "Acme", "role": "Engineer", "followUpOn": "%s", "version": 0}
                                """.formatted(TODAY)))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE)).andExpect(jsonPath("$.due[*].company", contains("Acme")));
    }

    // ---------- helpers ----------

    private long createUser() {
        return jdbc.queryForObject("insert into users (email, password_hash, full_name) values (?, 'x', 'Notification Test') returning id",
                Long.class, "notification-" + UUID.randomUUID() + "@test.dev");
    }

    private long createApplication(long ownerId, String company, String status, LocalDate followUpOn) {
        Integer position = jdbc.queryForObject("select count(*) from applications where user_id = ? and status = ?",
                Integer.class, ownerId, status);
        return jdbc.queryForObject("""
                        insert into applications (user_id, company, role, status, position, follow_up_on)
                        values (?, ?, 'Engineer', ?, ?, ?) returning id""",
                Long.class, ownerId, company, status, position, followUpOn);
    }
}
