package com.jobtrackr.jobtrackrbackend.auth;

import com.jayway.jsonpath.JsonPath;
import com.jobtrackr.jobtrackrbackend.auth.dto.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs requests through the real security filter chain: real tokens, real BCrypt, real database (rolled back)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthApiIntegrationTest {

    private static final String PASSWORD = "S3cret@Pass";

    @Autowired
    private WebApplicationContext context;

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

    private static String accessToken(String responseBody) {
        return JsonPath.read(responseBody, "$.accessToken");
    }

    private static String credentials(String email, String password) {
        return "{\"email\": \"%s\", \"password\": \"%s\", \"fullName\": \"Test User\"}".formatted(email, password);
    }
}
