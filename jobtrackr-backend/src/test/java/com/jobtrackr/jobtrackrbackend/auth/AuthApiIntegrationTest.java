package com.jobtrackr.jobtrackrbackend.auth;

import com.jobtrackr.jobtrackrbackend.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import com.jobtrackr.jobtrackrbackend.auth.dto.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs requests through the real security filter chain: real tokens, real BCrypt, real database (rolled back)
@IntegrationTest
@Transactional
class AuthApiIntegrationTest {

    private static final String PASSWORD = "S3cret@Pass";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;
    private String email;

    @BeforeEach
    void setUp() {
        // springSecurity() adds the security filters, which a plain MockMvc would skip
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        email = "auth-test-" + UUID.randomUUID() + "@jobtrackr.dev";
    }

    @Test
    void register_returns201WithToken_andDuplicateEmailReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_invalidInput_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void register_passwordWithoutUppercaseOrSpecialCharacter_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "lowercase1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value(PasswordPolicy.COMPOSITION_MESSAGE));
    }

    @Test
    void login_returnsTokenThatIdentifiesTheUser() throws Exception {
        register(email);

        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void login_wrongPasswordOrUnknownEmail_returns401() throws Exception {
        register(email);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("nobody-" + email, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/applications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withTamperedToken_returns401() throws Exception {
        String token = register(email);

        // Change the signature's first character: the signature no longer matches
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/v1/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applications_areIsolatedPerUser() throws Exception {
        String tokenA = register(email);
        String tokenB = register("other-" + email);

        mockMvc.perform(post("/api/v1/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company": "Acme", "role": "Engineer", "status": "APPLIED"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(jsonPath("$.content[*].company", contains("Acme")));
        mockMvc.perform(get("/api/v1/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void publicDemoAccount_canLogIn() throws Exception {
        // Guards the hash written by V4__demo_user_data.sql: the README and the frontend publish these credentials
        String token = login("demo@jobtrackr.dev", "Demo@1234");

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Demo User"));
    }

    @Test
    void login_setsAnHttpOnlyStrictRefreshCookieScopedToAuth() throws Exception {
        register(email);

        String setCookie = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .startsWith(RefreshTokenCookie.NAME + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Max-Age=1209600");
        // The refresh token must never appear in the JSON body
        assertThat(refreshTokenIn(setCookie)).hasSizeGreaterThanOrEqualTo(43);
    }

    @Test
    void refresh_rotatesTheCookie_returnsAWorkingAccessToken_andRejectsTheOldCookie() throws Exception {
        String firstCookie = refreshTokenIn(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));

        var refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, firstCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();
        String secondCookie = refreshTokenIn(refreshed.getHeader(HttpHeaders.SET_COOKIE));
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        String newAccessToken = accessToken(refreshed.getContentAsString());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // The first cookie was used up by the rotation: 401, and the response deletes the cookie
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, firstCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // The second one still works
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, secondCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Your session has expired. Please log in again."));
    }

    @Test
    void logout_revokesTheRefreshTokenAndDeletesTheCookie() throws Exception {
        String cookie = refreshTokenIn(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(RefreshTokenCookie.NAME, cookie)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returnsAFreshSession_endsTheOldOne_andTheNewPasswordLogsIn() throws Exception {
        var registered = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andReturn().getResponse();
        String oldCookie = refreshTokenIn(registered.getHeader(HttpHeaders.SET_COOKIE));
        String accessToken = accessToken(registered.getContentAsString());

        var changed = mockMvc.perform(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, "N3w@Secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn().getResponse();
        String newCookie = refreshTokenIn(changed.getHeader(HttpHeaders.SET_COOKIE));

        // Every earlier session is over. Its row is deleted, not revoked: a revoked token replayed after the
        // grace period would count as theft and end the fresh session too
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                RefreshTokenService.hash(oldCookie))).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, oldCookie)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(RefreshTokenCookie.NAME, newCookie)))
                .andExpect(status().isOk());

        login(email, "N3w@Secret");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_wrongCurrentPassword_is400OnThatField_not401() throws Exception {
        String accessToken = register(email);

        mockMvc.perform(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange("Wrong@Pass1", "N3w@Secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Current password is incorrect"));
    }

    @Test
    void changePassword_weakNewPassword_is400() throws Exception {
        String accessToken = register(email);

        mockMvc.perform(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    @Test
    void changePassword_demoAccount_is403() throws Exception {
        String accessToken = login("demo@jobtrackr.dev", "Demo@1234");

        mockMvc.perform(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange("Demo@1234", "N3w@Secret")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        login("demo@jobtrackr.dev", "Demo@1234"); // unchanged
    }

    @Test
    void changePassword_withoutToken_is401() throws Exception {
        mockMvc.perform(put("/api/v1/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content(passwordChange(PASSWORD, "N3w@Secret")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerDocs_arePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private String register(String newEmail) throws Exception {
        return accessToken(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(newEmail, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String login(String loginEmail, String password) throws Exception {
        return accessToken(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(loginEmail, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** The token value out of a Set-Cookie header: "jobtrackr_refresh=<value>; Path=...". */
    private static String refreshTokenIn(String setCookieHeader) {
        assertThat(setCookieHeader).isNotNull();
        return setCookieHeader.substring(setCookieHeader.indexOf('=') + 1, setCookieHeader.indexOf(';'));
    }

    private static String accessToken(String responseBody) {
        return JsonPath.read(responseBody, "$.accessToken");
    }

    private static String passwordChange(String currentPassword, String newPassword) {
        return "{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(currentPassword, newPassword);
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"Test User\"}".formatted(email, password);
    }
}
