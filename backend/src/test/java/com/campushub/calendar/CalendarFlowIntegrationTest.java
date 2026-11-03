package com.campushub.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Calendar deadlines against a real MySQL 8.4 container (API_CONTRACT §9). Tests share one database,
 * so each assertion filters by the project it created rather than expecting an empty calendar.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CalendarFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CALENDAR = "/api/v1/calendar?from=2026-11-01&to=2026-11-30";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE).withDatabaseName("campushub");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("campushub.jwt.secret", () -> JWT_SECRET);
        registry.add("campushub.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("campushub.storage.root", storageRoot::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static RequestPostProcessor fromNewAddress() {
        String address = "10.10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, String displayName) {
        String email = "cal-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, displayName,
                passwordEncoder.encode(PASSWORD), role, false, Instant.now()));
    }

    private String tokenFor(UserAccount account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").with(fromNewAddress())
                        .contentType("application/json")
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private String projectDueOn(String token, String title, String dueDate) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"dueDate\":\"" + dueDate + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void taskDueOn(String token, String projectId, String title, String dueDate) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks").header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\",\"dueDate\":\"" + dueDate + "\"}"))
                .andExpect(status().isCreated());
    }

    private void acceptInvitation(String ownerToken, String projectId, UserAccount invitee) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(body, "$.id");
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header(AUTHORIZATION, tokenFor(invitee)))
                .andExpect(status().isOk());
    }

    /** Calendar entries for one project out of the shared calendar response. */
    private List<Object> entriesFor(String token, String projectId) throws Exception {
        String body = mockMvc.perform(get(CALENDAR).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[?(@.projectId=='" + projectId + "')]");
    }

    @Test
    void studentSeesDeadlinesOfOwnedAndJoinedProjectsOnly() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Calendar Owner");
        UserAccount member = account(Role.STUDENT, "Calendar Member");
        String ownerToken = tokenFor(owner);
        String projectId = projectDueOn(ownerToken, "Calendar Shared", "2026-11-10");
        taskDueOn(ownerToken, projectId, "Shared Task", "2026-11-12");
        String outsiderProject = projectDueOn(tokenFor(account(Role.STUDENT, "Outsider")), "Calendar Hidden",
                "2026-11-10");

        assertThat(entriesFor(ownerToken, projectId)).hasSize(2);
        assertThat(entriesFor(tokenFor(member), projectId)).isEmpty();

        acceptInvitation(ownerToken, projectId, member);

        assertThat(entriesFor(tokenFor(member), projectId)).hasSize(2);
        assertThat(entriesFor(ownerToken, outsiderProject)).isEmpty();
        assertThat(entriesFor(tokenFor(member), outsiderProject)).isEmpty();
    }

    @Test
    void facultySeesDeadlinesOfSupervisedProjectsOnly() throws Exception {
        UserAccount admin = account(Role.ADMIN, "Calendar Admin");
        UserAccount faculty = account(Role.FACULTY, "Calendar Faculty");
        String projectId = projectDueOn(tokenFor(account(Role.STUDENT, "Supervised Owner")), "Calendar Supervised",
                "2026-11-20");
        String unassigned = projectDueOn(tokenFor(account(Role.STUDENT, "Unassigned Owner")), "Calendar Unassigned",
                "2026-11-20");
        mockMvc.perform(put("/api/v1/projects/" + projectId + "/supervisor")
                        .header(AUTHORIZATION, tokenFor(admin))
                        .contentType("application/json")
                        .content("{\"supervisorId\":\"" + faculty.getId() + "\",\"version\":0}"))
                .andExpect(status().isOk());

        String facultyToken = tokenFor(faculty);
        assertThat(entriesFor(facultyToken, projectId)).hasSize(1);
        assertThat(entriesFor(facultyToken, unassigned)).isEmpty();
    }

    @Test
    void rejectsRangesThatAreReversedOrTooLong() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Range Student"));

        mockMvc.perform(get("/api/v1/calendar?from=2026-11-30&to=2026-11-01").header(AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/calendar?from=2026-01-01&to=2026-12-31").header(AUTHORIZATION, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RANGE_TOO_LARGE"));
    }

    @Test
    void calendarRequiresAuthentication() throws Exception {
        mockMvc.perform(get(CALENDAR)).andExpect(status().isUnauthorized());
    }
}
