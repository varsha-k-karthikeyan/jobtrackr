package com.jobtrackr.jobtrackrbackend.application;

import com.jobtrackr.jobtrackrbackend.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.jobtrackr.jobtrackrbackend.common.CurrentUserProvider;
import com.jobtrackr.jobtrackrbackend.user.User;
import com.jobtrackr.jobtrackrbackend.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full stack against the real Postgres: controller -> service -> repository -> SQL.
// Each test runs as a brand-new user inside a transaction that is rolled back, so no data is left behind.
@IntegrationTest
@Transactional
class ApplicationApiIntegrationTest {

    private static final String BASE = "/api/v1/applications";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;
    @MockitoBean
    private CurrentUserProvider currentUser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        User user = userRepository.save(new User("test-" + UUID.randomUUID() + "@jobtrackr.dev", "x", "Test User"));
        when(currentUser.getUserId()).thenReturn(user.getId());
    }

    @Test
    void create_returns201WithLocationAndAppendsToColumn() throws Exception {
        create("Acme", "APPLIED");

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Globex", "APPLIED")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.company").value("Globex"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_invalidBody_returns400ProblemDetailWithFieldErrors() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company": "", "role": "Engineer"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.company").exists())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void list_filtersSortsAndPaginates() throws Exception {
        create("Acme", "APPLIED");
        create("Globex", "APPLIED");
        create("Initech", "APPLIED");
        create("Acme Labs", "INTERVIEW");

        mockMvc.perform(get(BASE).param("status", "APPLIED").param("sort", "company,asc")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].company", contains("Acme", "Globex")))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        // searchText is a case-insensitive "contains" across all statuses
        mockMvc.perform(get(BASE).param("searchText", "ACME").param("sort", "company,asc"))
                .andExpect(jsonPath("$.content[*].company", contains("Acme", "Acme Labs")));

        mockMvc.perform(get(BASE).param("sort", "user.passwordHash"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_searchCoversLocationSourceAndStatus() throws Exception {
        createJson("""
                {"company": "Acme", "role": "Engineer", "status": "APPLIED", "location": "Chennai", "source": "Referral"}
                """);
        createJson("""
                {"company": "Globex", "role": "Engineer", "status": "OFFER", "location": "Pune", "source": "LinkedIn"}
                """);

        mockMvc.perform(get(BASE).param("searchText", "chenn"))
                .andExpect(jsonPath("$.content[*].company", contains("Acme")));
        mockMvc.perform(get(BASE).param("searchText", "LINKED"))
                .andExpect(jsonPath("$.content[*].company", contains("Globex")));
        // "off" matches no text field, but it is part of the status OFFER
        mockMvc.perform(get(BASE).param("searchText", "off"))
                .andExpect(jsonPath("$.content[*].company", contains("Globex")));
    }

    @Test
    void list_sortsBySalaryAndLocation_withEmptyValuesLast() throws Exception {
        createJson("""
                {"company": "Low", "role": "R", "status": "APPLIED", "salaryMinLpa": 10, "salaryMaxLpa": 15, "location": "Pune"}
                """);
        createJson("""
                {"company": "None", "role": "R", "status": "APPLIED"}
                """);
        createJson("""
                {"company": "High", "role": "R", "status": "APPLIED", "salaryMinLpa": 30, "salaryMaxLpa": 40, "location": "Bengaluru"}
                """);

        mockMvc.perform(get(BASE).param("sort", "salaryMaxLpa,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].company", contains("High", "Low", "None")));
        mockMvc.perform(get(BASE).param("sort", "salaryMinLpa,asc"))
                .andExpect(jsonPath("$.content[*].company", contains("Low", "High", "None")));
        mockMvc.perform(get(BASE).param("sort", "location,asc"))
                .andExpect(jsonPath("$.content[*].company", contains("High", "Low", "None")));
    }

    @Test
    void list_salaryRangeKeepsOverlappingJobs() throws Exception {
        createJson("""
                {"company": "Low", "role": "R", "status": "APPLIED", "salaryMinLpa": 10, "salaryMaxLpa": 15}
                """);
        createJson("""
                {"company": "Mid", "role": "R", "status": "APPLIED", "salaryMinLpa": 25, "salaryMaxLpa": 35}
                """);
        createJson("""
                {"company": "High", "role": "R", "status": "APPLIED", "salaryMinLpa": 40, "salaryMaxLpa": 50}
                """);
        createJson("""
                {"company": "MinOnly", "role": "R", "status": "APPLIED", "salaryMinLpa": 22}
                """);
        createJson("""
                {"company": "NoSalary", "role": "R", "status": "APPLIED"}
                """);

        // 20-30 overlaps Mid (25-35) and MinOnly (22); Low ends below, High starts above, NoSalary has nothing
        mockMvc.perform(get(BASE).param("salaryFrom", "20").param("salaryTo", "30").param("sort", "company,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].company", contains("Mid", "MinOnly")));
        // Only a lower bound: everything paying at least 36 at the top of its range
        mockMvc.perform(get(BASE).param("salaryFrom", "36").param("sort", "company,asc"))
                .andExpect(jsonPath("$.content[*].company", contains("High")));
        // No salary filter: all five, including the job without a salary
        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$.totalElements").value(5));
        // Invalid range
        mockMvc.perform(get(BASE).param("salaryFrom", "30").param("salaryTo", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_unknownId_returns404ProblemDetail() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void update_bumpsVersion_andStaleVersionReturns409() throws Exception {
        long id = create("Acme", "APPLIED");
        String update = """
                {"company": "Acme Corp", "role": "Engineer", "version": 0}
                """;

        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("Acme Corp"))
                .andExpect(jsonPath("$.version").value(1));

        // Same body again: version 0 is now stale
        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void update_withNewStatus_putsCardAtBottomOfThatColumn() throws Exception {
        create("A", "APPLIED");
        long b = create("B", "APPLIED");
        create("C", "APPLIED");
        create("X", "INTERVIEW");

        mockMvc.perform(put(BASE + "/{id}", b).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company": "B", "role": "Engineer", "status": "INTERVIEW", "version": 0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.version").value(1));
        sync();

        mockMvc.perform(get(BASE + "/board"))
                .andExpect(jsonPath("$.APPLIED[*].company", contains("A", "C")))
                .andExpect(jsonPath("$.APPLIED[*].position", contains(0, 1)))
                .andExpect(jsonPath("$.INTERVIEW[*].company", contains("X", "B")))
                .andExpect(jsonPath("$.INTERVIEW[*].position", contains(0, 1)));
    }

    @Test
    void move_toOtherColumn_renumbersSourceAndTarget() throws Exception {
        long a = create("A", "APPLIED");
        create("B", "APPLIED");
        create("C", "APPLIED");
        create("X", "INTERVIEW");
        create("Y", "INTERVIEW");

        move(a, "INTERVIEW", 1);

        mockMvc.perform(get(BASE + "/board"))
                .andExpect(jsonPath("$.APPLIED[*].company", contains("B", "C")))
                .andExpect(jsonPath("$.APPLIED[*].position", contains(0, 1)))
                .andExpect(jsonPath("$.INTERVIEW[*].company", contains("X", "A", "Y")))
                .andExpect(jsonPath("$.INTERVIEW[*].position", contains(0, 1, 2)))
                .andExpect(jsonPath("$.OFFER", hasSize(0)));
    }

    @Test
    void move_withinColumn_reordersInBothDirections() throws Exception {
        long a = create("A", "APPLIED");
        create("B", "APPLIED");
        create("C", "APPLIED");
        long d = create("D", "APPLIED");

        move(a, "APPLIED", 2); // down
        mockMvc.perform(get(BASE + "/board"))
                .andExpect(jsonPath("$.APPLIED[*].company", contains("B", "C", "A", "D")))
                .andExpect(jsonPath("$.APPLIED[*].position", contains(0, 1, 2, 3)));

        move(d, "APPLIED", 0); // up
        mockMvc.perform(get(BASE + "/board"))
                .andExpect(jsonPath("$.APPLIED[*].company", contains("D", "B", "C", "A")))
                .andExpect(jsonPath("$.APPLIED[*].position", contains(0, 1, 2, 3)));
    }

    @Test
    void delete_returns204AndClosesGap() throws Exception {
        create("A", "APPLIED");
        long b = create("B", "APPLIED");
        create("C", "APPLIED");

        mockMvc.perform(delete(BASE + "/{id}", b)).andExpect(status().isNoContent());
        sync();

        mockMvc.perform(get(BASE + "/board"))
                .andExpect(jsonPath("$.APPLIED[*].company", contains("A", "C")))
                .andExpect(jsonPath("$.APPLIED[*].position", contains(0, 1)));
        mockMvc.perform(get(BASE + "/{id}", b)).andExpect(status().isNotFound());
    }

    @Test
    void notes_canBeAddedListedWithApplicationAndDeleted() throws Exception {
        long id = create("Acme", "APPLIED");

        String response = mockMvc.perform(post(BASE + "/{id}/notes", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body": "Recruiter call on Friday"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long noteId = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(jsonPath("$.application.company").value("Acme"))
                .andExpect(jsonPath("$.notes[*].body", contains("Recruiter call on Friday")));

        mockMvc.perform(delete("/api/v1/notes/{id}", noteId)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/{id}", id)).andExpect(jsonPath("$.notes", hasSize(0)));

        mockMvc.perform(post(BASE + "/{id}/notes", id).contentType(MediaType.APPLICATION_JSON).content("{\"body\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stats_countsCardsPerStatusForCurrentUserOnly() throws Exception {
        create("A", "APPLIED");
        create("B", "APPLIED");
        create("C", "INTERVIEW");
        create("D", "REJECTED");

        // The grouped JPQL query runs for real here; the unit test only covers the arithmetic
        mockMvc.perform(get("/api/v1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.interview").value(1))
                .andExpect(jsonPath("$.offer").value(0))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.responseRate").value(0.5));
    }

    @Test
    void anotherUsersApplication_is404OnEveryEndpoint_andStaysUnchanged() throws Exception {
        long ownersCard = create("Acme", "APPLIED");
        Long ownerId = currentUser.getUserId();
        actAs(newUser());

        mockMvc.perform(get(BASE + "/{id}", ownersCard)).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/{id}", ownersCard).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\": \"Hacked\", \"role\": \"Engineer\", \"version\": 0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(BASE + "/{id}/move", ownersCard).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"OFFER\", \"position\": 0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/{id}/notes", ownersCard).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"not mine\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/{id}", ownersCard)).andExpect(status().isNotFound());
        // Not even visible as a list or board entry
        mockMvc.perform(get(BASE)).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(BASE + "/board")).andExpect(jsonPath("$.APPLIED", hasSize(0)));

        sync();
        actAs(ownerId);
        mockMvc.perform(get(BASE + "/{id}", ownersCard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.company").value("Acme"))
                .andExpect(jsonPath("$.application.status").value("APPLIED"))
                .andExpect(jsonPath("$.application.version").value(0))
                .andExpect(jsonPath("$.notes", hasSize(0)));
    }

    @Test
    void anotherUsersNote_cannotBeDeleted() throws Exception {
        long card = create("Acme", "APPLIED");
        String noteResponse = mockMvc.perform(post(BASE + "/{id}/notes", card).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"Phone screen booked\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long noteId = ((Number) JsonPath.read(noteResponse, "$.id")).longValue();
        Long ownerId = currentUser.getUserId();
        actAs(newUser());

        mockMvc.perform(delete("/api/v1/notes/{id}", noteId)).andExpect(status().isNotFound());

        sync();
        actAs(ownerId);
        mockMvc.perform(get(BASE + "/{id}", card)).andExpect(jsonPath("$.notes", hasSize(1)));
    }

    @Test
    void openApiDocs_areServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/applications/{id}/move']").exists());
    }

    // ---------- helpers ----------

    private long create(String company, String status) throws Exception {
        String response = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(body(company, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** A second, independent account in the same (rolled-back) transaction. */
    private Long newUser() {
        return userRepository.save(new User("other-" + UUID.randomUUID() + "@jobtrackr.dev", "x", "Other User")).getId();
    }

    /** Every following request is made as this user (what the JWT subject would say). */
    private void actAs(Long userId) {
        when(currentUser.getUserId()).thenReturn(userId);
    }

    private void createJson(String json) throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void move(long id, String status, int position) throws Exception {
        mockMvc.perform(patch(BASE + "/{id}/move", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"%s\", \"position\": %d}".formatted(status, position)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.position").value(position));
        sync();
    }

    private static String body(String company, String status) {
        return "{\"company\": \"%s\", \"role\": \"Engineer\", \"status\": \"%s\"}".formatted(company, status);
    }

    // The whole test shares one transaction and therefore one persistence context. Bulk UPDATEs bypass it,
    // so drop the cached entities to make the next request read fresh rows, as a real separate request would.
    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }
}
