package com.campushub.projects;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Project slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts and uses its own client address, so rate limits and projects never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProjectFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";

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
        String address = "10.2." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
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

    private ResultActions createProject(String token, String title) throws Exception {
        return mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content("{\"title\":\"" + title + "\"}"));
    }

    private String createdId(String token, String title) throws Exception {
        String body = createProject(token, title).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions assignSupervisor(String adminToken, String projectId, UUID supervisorId, int version)
            throws Exception {
        return mockMvc.perform(put("/api/v1/projects/" + projectId + "/supervisor").header(AUTHORIZATION, bearer(adminToken))
                .contentType("application/json")
                .content("{\"supervisorId\":\"" + supervisorId + "\",\"version\":" + version + "}"));
    }

    @Test
    void studentCreatesAnActiveProjectOwnedByThemselves() throws Exception {
        UserAccount student = account(Role.STUDENT, "Ada Student");
        String token = tokenFor(student);

        createProject(token, "Capstone Compiler")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Capstone Compiler"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.owner.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.supervisor").doesNotExist());
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Ada Student"));

        createProject(token, "ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        createProject(token, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"title\":\"Valid title\",\"startDate\":\"2026-05-10\",\"dueDate\":\"2026-05-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    @Test
    void facultyCannotCreateProjects() throws Exception {
        String token = tokenFor(account(Role.FACULTY, "Fran Faculty"));

        createProject(token, "Not allowed")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void studentListsOnlyTheirOwnProjects() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        UserAccount other = account(Role.STUDENT, "Other");
        String ownerToken = tokenFor(owner);
        String ownProject = createdId(ownerToken, "Owner Project");
        createdId(tokenFor(other), "Someone Else Project");

        mockMvc.perform(get("/api/v1/projects").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownProject))
                .andExpect(jsonPath("$.sort[0]").value("createdAt,desc"));
    }

    @Test
    void otherStudentGetsTheSameNotFoundAsAMissingProject() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(ownerToken, "Private Project");
        String intruderToken = tokenFor(account(Role.STUDENT, "Intruder"));

        String existing = mockMvc.perform(get("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(existing, "$.errorCode")).isEqualTo("NOT_FOUND");
        assertThat(JsonPath.<String>read(existing, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
    }

    @Test
    void malformedProjectIdIsNotFound() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Ada Student"));

        mockMvc.perform(get("/api/v1/projects/not-a-uuid").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void supervisorSeesTheProjectOnlyAfterAdminAssignsThem() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(ownerToken, "Supervised Project");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        String facultyToken = tokenFor(faculty);
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));

        mockMvc.perform(get("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(facultyToken)))
                .andExpect(status().isNotFound());

        assignSupervisor(adminToken, projectId, faculty.getId(), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supervisor.id").value(faculty.getId().toString()))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(facultyToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header(AUTHORIZATION, bearer(facultyToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyActiveFacultyCanBeAssignedAsSupervisor() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(ownerToken, "Needs Supervisor");
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        UserAccount student = account(Role.STUDENT, "Not Faculty");

        assignSupervisor(adminToken, projectId, student.getId(), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SUPERVISOR_NOT_ELIGIBLE"));
        assignSupervisor(tokenFor(account(Role.FACULTY, "Fran")), projectId, student.getId(), 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void ownerUpdatesWithCurrentVersionAndStaleVersionIsRefused() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(token, "Original Title");

        mockMvc.perform(patch("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"version\":0,\"title\":\"Revised Title\",\"subject\":\"Databases\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Revised Title"))
                .andExpect(jsonPath("$.subject").value("Databases"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"version\":0,\"title\":\"Late Write\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_VERSION"));
    }

    @Test
    void supervisorCannotEditProjectDetails() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(ownerToken, "Owner Only");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        assignSupervisor(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, faculty.getId(), 0)
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(tokenFor(faculty)))
                        .contentType("application/json")
                        .content("{\"version\":1,\"title\":\"Faculty Edit\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
    }

    @Test
    void archivedProjectIsReadOnlyAndOnlyAdminsCanArchive() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdId(ownerToken, "To Be Archived");
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive").header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive").header(AUTHORIZATION, bearer(adminToken))
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(patch("/api/v1/projects/" + projectId).header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"version\":1,\"title\":\"After Archive\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
    }

    @Test
    void listRejectsUnknownStatusAndOversizedPages() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Ada Student"));

        mockMvc.perform(get("/api/v1/projects?status=NOPE").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/projects?size=500").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void facultyDirectoryIsAdminOnly() throws Exception {
        UserAccount faculty = account(Role.FACULTY, "Directory Faculty");

        mockMvc.perform(get("/api/v1/users/faculty").header(AUTHORIZATION, bearer(tokenFor(account(Role.STUDENT, "Ada")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get("/api/v1/users/faculty").header(AUTHORIZATION, bearer(tokenFor(account(Role.ADMIN, "Ada Admin")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + faculty.getId() + "')].displayName").value("Directory Faculty"));
    }
}
