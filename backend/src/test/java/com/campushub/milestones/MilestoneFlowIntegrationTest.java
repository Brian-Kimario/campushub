package com.campushub.milestones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Milestone slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts and projects and uses its own client address, so rate limits and data never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MilestoneFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String DUE_AT = "2026-12-10T09:00:00Z";

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
        String address = "10.5." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, String displayName) {
        String email = "user-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, displayName,
                passwordEncoder.encode(PASSWORD), role, false, Instant.now()));
    }

    private String tokenFor(UserAccount account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").with(fromNewAddress())
                        .contentType("application/json")
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String createdProject(String token) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"Milestone Host Project\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions createMilestone(String token, String projectId, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/milestones")
                .header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content("{\"title\":\"" + title + "\",\"dueAt\":\"" + DUE_AT + "\"}"));
    }

    private String createdMilestone(String token, String projectId, String title) throws Exception {
        String body = createMilestone(token, projectId, title)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** PATCH with a raw JSON body, so tests can send exactly the fields they mean. */
    private ResultActions patchMilestone(String token, String milestoneId, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/milestones/" + milestoneId).header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content(json));
    }

    private ResultActions close(String token, String milestoneId) throws Exception {
        return mockMvc.perform(post("/api/v1/milestones/" + milestoneId + "/close").header(AUTHORIZATION, bearer(token)));
    }

    private void supervisedBy(String projectId, UserAccount faculty) throws Exception {
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, faculty.getId()))
                .andExpect(status().isOk());
    }

    private void acceptedMember(String ownerToken, String projectId, UserAccount invitee) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/invitations")
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String invitationId = JsonPath.read(body, "$.id");
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header(AUTHORIZATION, bearer(tokenFor(invitee))))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCreatesMilestonesInSequenceAndListsThemInOrder() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String first = createdMilestone(token, projectId, "Proposal draft");
        createMilestone(token, projectId, "Final report")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sequence").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.allowLate").value(false))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/milestones").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].sequence").value(1))
                .andExpect(jsonPath("$[1].title").value("Final report"));
    }

    @Test
    void invalidMilestoneInputIsRejectedWithValidationFailed() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/milestones").header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"ab\",\"dueAt\":\"" + DUE_AT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/milestones").header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"Valid title\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void strangerCannotSeeMilestonesOfAProjectTheyCannotSee() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String milestoneId = createdMilestone(ownerToken, projectId, "Private milestone");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/milestones")
                        .header(AUTHORIZATION, bearer(tokenFor(account(Role.STUDENT, "Intruder")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        patchMilestone(tokenFor(account(Role.STUDENT, "Intruder")), milestoneId,
                "{\"version\":0,\"title\":\"Hijacked\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void teamMemberCanReadButNotCreateMilestones() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        createdMilestone(ownerToken, projectId, "Team milestone");
        UserAccount member = account(Role.STUDENT, "Member");
        acceptedMember(ownerToken, projectId, member);

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/milestones").header(AUTHORIZATION,
                        bearer(tokenFor(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Team milestone"));
        createMilestone(tokenFor(member), projectId, "Member milestone")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
    }

    @Test
    void ownerEditsTitleAndDueDateButNotAllowLate() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String milestoneId = createdMilestone(token, projectId, "First draft");

        patchMilestone(token, milestoneId, "{\"version\":0,\"title\":\"Revised draft\",\"dueAt\":\"2026-12-20T09:00:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Revised draft"))
                .andExpect(jsonPath("$.dueAt").value("2026-12-20T09:00:00Z"))
                .andExpect(jsonPath("$.version").value(1));
        patchMilestone(token, milestoneId, "{\"version\":1,\"allowLate\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_NOT_EDITABLE"));
    }

    @Test
    void supervisorEditsAllowLateOnlyAndIsRefusedOwnerFields() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String milestoneId = createdMilestone(ownerToken, projectId, "Supervised milestone");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        supervisedBy(projectId, faculty);
        String facultyToken = tokenFor(faculty);

        patchMilestone(facultyToken, milestoneId, "{\"version\":0,\"title\":\"Renamed\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_NOT_EDITABLE"));
        patchMilestone(facultyToken, milestoneId, "{\"version\":0,\"allowLate\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowLate").value(true))
                .andExpect(jsonPath("$.title").value("Supervised milestone"));
    }

    @Test
    void staleVersionIsRefused() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String milestoneId = createdMilestone(token, projectId, "Versioned milestone");

        patchMilestone(token, milestoneId, "{\"version\":0,\"title\":\"First edit\"}").andExpect(status().isOk());
        patchMilestone(token, milestoneId, "{\"version\":0,\"title\":\"Stale edit\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_VERSION"));
    }

    @Test
    void closingIsIrreversibleAndBlocksEdits() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String milestoneId = createdMilestone(token, projectId, "Closing milestone");

        close(token, milestoneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        close(token, milestoneId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
        patchMilestone(token, milestoneId, "{\"version\":1,\"title\":\"Too late\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void supervisorMayCloseWhileAdminAndOtherFacultyMayNot() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String milestoneId = createdMilestone(ownerToken, projectId, "Supervisor closes");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        supervisedBy(projectId, faculty);

        close(tokenFor(account(Role.ADMIN, "Ada Admin")), milestoneId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
        close(tokenFor(account(Role.FACULTY, "Other Faculty")), milestoneId)
                .andExpect(status().isNotFound());
        close(tokenFor(faculty), milestoneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void archivedProjectRefusesMilestoneWrites() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String milestoneId = createdMilestone(token, projectId, "Before archive");
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header(AUTHORIZATION, bearer(tokenFor(account(Role.ADMIN, "Ada Admin"))))
                        .contentType("application/json")
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        createMilestone(token, projectId, "After archive")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
        close(token, milestoneId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
    }

    @Test
    void openMilestoneAppearsOnTheCalendarOnItsUtcDay() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String milestoneId = createdMilestone(token, projectId, "Calendar milestone");

        String body = mockMvc.perform(get("/api/v1/calendar?from=2026-12-01&to=2026-12-31")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(body, "$[?(@.id=='" + milestoneId + "')].type");
        List<String> dates = JsonPath.read(body, "$[?(@.id=='" + milestoneId + "')].date");
        assertThat(types).containsExactly("MILESTONE_DUE");
        assertThat(dates).containsExactly("2026-12-10");
    }

    private static MockHttpServletRequestBuilder supervisorPut(String adminToken, String projectId, UUID supervisorId) {
        return put("/api/v1/projects/" + projectId + "/supervisor")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType("application/json")
                .content("{\"supervisorId\":\"" + supervisorId + "\",\"version\":0}");
    }
}
