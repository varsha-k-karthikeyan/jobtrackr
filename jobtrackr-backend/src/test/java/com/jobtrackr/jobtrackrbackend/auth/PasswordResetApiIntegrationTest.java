package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The whole "forgot password" flow through the real security filters and database. The mailer is a mock
// (no SMTP). NOT @Transactional, unlike the other integration tests: the email is sent only AFTER COMMIT,
// and a test transaction that rolls back never commits. So data really is written, and each test deletes
// its own user at the end (ON DELETE CASCADE removes its tokens).
@IntegrationTest
class PasswordResetApiIntegrationTest {

    private static final String PASSWORD = "S3cret@Pass";
    private static final String NEW_PASSWORD = "N3w@Secret";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private PasswordResetMailer mailer;

    private MockMvc mockMvc;
    private String email;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = "reset-test-" + UUID.randomUUID() + "@test.dev";
    }

    @AfterEach
    void deleteTestUser() {
        jdbc.update("delete from users where email = ?", email);
    }

    @Test
    void fullFlow_emailedLinkSetsTheNewPassword_endsOldSessions_andWorksOnlyOnce() throws Exception {
        String oldCookie = register();

        forgotPassword(email);
        String token = tokenFromEmail();

        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(token, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        login(NEW_PASSWORD).andExpect(status().isOk());
        login(PASSWORD).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, oldCookie)))
                .andExpect(status().isUnauthorized());

        // Single use: the same link a second time is a 400 with a message, not a 401
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody(token, "An0ther@Pass")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(PasswordResetService.INVALID_LINK));
    }

    @Test
    void unknownEmail_getsTheSameAnswer_andNoEmail() throws Exception {
        forgotPassword("nobody-" + UUID.randomUUID() + "@test.dev");

        verify(mailer, after(500).never()).send(any());
    }

    @Test
    void secondRequestWithinTheCooldown_sendsOnlyOneEmail() throws Exception {
        register();

        forgotPassword(email);
        forgotPassword(email);

        verify(mailer, after(500).times(1)).send(any());
    }

    @Test
    void storesOnlyTheHashOfTheToken() throws Exception {
        register();
        forgotPassword(email);
        String token = tokenFromEmail();

        String stored = jdbc.queryForObject("""
                select t.token_hash from password_reset_tokens t join users u on u.id = t.user_id where u.email = ?""",
                String.class, email);
        assertThat(stored).isEqualTo(OpaqueTokens.hash(token)).isNotEqualTo(token);
    }

    @Test
    void madeUpToken_is400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("not-a-real-token", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(PasswordResetService.INVALID_LINK));
    }

    @Test
    void weakNewPassword_is400OnThatField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("any-token", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    // ---------- helpers ----------

    /** Registers the test user; returns its refresh cookie value. */
    private String register() throws Exception {
        String setCookie = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"Reset Test\"}".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private void forgotPassword(String address) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(address)))
                .andExpect(status().isNoContent());
    }

    /** Waits for the (asynchronous, after-commit) email and pulls the token out of its link. */
    private String tokenFromEmail() throws Exception {
        ArgumentCaptor<PasswordResetRequested> sent = ArgumentCaptor.forClass(PasswordResetRequested.class);
        verify(mailer, timeout(5000)).send(sent.capture());
        String link = sent.getValue().resetLink();
        assertThat(link).startsWith("http://localhost:4200/reset-password?token=");
        return link.substring(link.indexOf("token=") + 6);
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private static String resetBody(String token, String newPassword) {
        return "{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, newPassword);
    }
}
