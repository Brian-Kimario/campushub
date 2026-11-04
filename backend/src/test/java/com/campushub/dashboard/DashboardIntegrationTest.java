package com.campushub.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Dashboard aggregates against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test uses its own
 * accounts and client address, so counts from other tests never leak in.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DashboardIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String STUDENT_DASHBOARD = "/api/v1/users/me/dashboard/student";
    private static final String FACULTY_DASHBOARD = "/api/v1/users/me/dashboard/faculty";
    private static final String ADMIN_DASHBOARD = "/api/v1/users/me/dashboard/admin";

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

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode readJson(String body) throws Exception {
        return JSON.readTree(body);
    }

    private static RequestPostProcessor fromNewAddress() {
        String address = "10.8." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, String displayName) {
        String email = "dash-" + UUID.randomUUID() + "@campushub.test";
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

    private String createdProject(String token, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createdTask(String token, String projectId, String title) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void transition(String token, String taskId, String to, int version) throws Exception {
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/transitions").header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"to\":\"" + to + "\",\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void studentWithoutTasksSeesNoProgressInsteadOfComplete() throws Exception {
        UserAccount student = account(Role.STUDENT, "Empty Student");
        String token = tokenFor(student);
        createdProject(token, "Empty Project");

        mockMvc.perform(get(STUDENT_DASHBOARD).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectsTotal").value(1))
                .andExpect(jsonPath("$.tasksTotal").value(0))
                .andExpect(jsonPath("$.progressPercent").doesNotExist())
                .andExpect(jsonPath("$.recentProjects[0].title").value("Empty Project"))
                .andExpect(jsonPath("$.recentProjects[0].progressPercent").doesNotExist());
    }

    @Test
    void studentDashboardCountsTaskStatesAndProgress() throws Exception {
        UserAccount student = account(Role.STUDENT, "Progress Student");
        String token = tokenFor(student);
        String projectId = createdProject(token, "Progress Project");
        String finished = createdTask(token, projectId, "Finished");
        createdTask(token, projectId, "Pending");
        transition(token, finished, "IN_PROGRESS", 0);
        transition(token, finished, "IN_REVIEW", 1);
        transition(token, finished, "DONE", 2);

        mockMvc.perform(get(STUDENT_DASHBOARD).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksTotal").value(2))
                .andExpect(jsonPath("$.tasksDone").value(1))
                .andExpect(jsonPath("$.tasksPending").value(1))
                .andExpect(jsonPath("$.progressPercent").value(50))
                .andExpect(jsonPath("$.recentProjects[0].taskTotal").value(2))
                .andExpect(jsonPath("$.recentProjects[0].progressPercent").value(50));
    }

    @Test
    void studentDashboardIgnoresOtherStudentsWork() throws Exception {
        String mine = tokenFor(account(Role.STUDENT, "Mine"));
        String theirs = tokenFor(account(Role.STUDENT, "Theirs"));
        createdProject(mine, "My Project");
        String otherProject = createdProject(theirs, "Their Project");
        createdTask(theirs, otherProject, "Their Task");

        mockMvc.perform(get(STUDENT_DASHBOARD).header(AUTHORIZATION, mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectsTotal").value(1))
                .andExpect(jsonPath("$.tasksTotal").value(0));
    }

    @Test
    void facultyDashboardCountsOnlyProjectsTheySupervise() throws Exception {
        UserAccount admin = account(Role.ADMIN, "Admin");
        UserAccount faculty = account(Role.FACULTY, "Faculty");
        String adminToken = tokenFor(admin);
        String facultyToken = tokenFor(faculty);
        String supervised = createdProject(tokenFor(account(Role.STUDENT, "Supervised Student")), "Supervised");
        createdProject(tokenFor(account(Role.STUDENT, "Unassigned Student")), "Unassigned");

        mockMvc.perform(put("/api/v1/projects/" + supervised + "/supervisor").header(AUTHORIZATION, adminToken)
                        .contentType("application/json")
                        .content("{\"supervisorId\":\"" + faculty.getId() + "\",\"version\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(FACULTY_DASHBOARD).header(AUTHORIZATION, facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedProjects").value(1))
                .andExpect(jsonPath("$.submissionsAwaitingReview").value(0))
                .andExpect(jsonPath("$.projects[0].title").value("Supervised"));
    }

    @Test
    void adminDashboardCountsActiveAccountsAndProjectsByRoleAndStatus() throws Exception {
        String admin = tokenFor(account(Role.ADMIN, "Dashboard Admin"));
        JsonNode before = readJson(mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        account(Role.FACULTY, "Added Faculty");
        createdProject(tokenFor(account(Role.STUDENT, "Project Owner")), "Admin Count Project");

        mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsersByRole.STUDENT").isNumber())
                .andExpect(jsonPath("$.activeUsersByRole.FACULTY").isNumber())
                .andExpect(jsonPath("$.activeUsersByRole.ADMIN").isNumber())
                .andExpect(jsonPath("$.projectsByStatus.ACTIVE").isNumber())
                .andExpect(jsonPath("$.projectsByStatus.ARCHIVED").isNumber())
                .andExpect(jsonPath("$.activeUsersByRole.FACULTY")
                        .value(before.at("/activeUsersByRole/FACULTY").asLong() + 1))
                .andExpect(jsonPath("$.projectsByStatus.ACTIVE")
                        .value(before.at("/projectsByStatus/ACTIVE").asLong() + 1));
    }

    @Test
    void adminDashboardExcludesDeactivatedAccounts() throws Exception {
        String admin = tokenFor(account(Role.ADMIN, "Deactivation Admin"));
        long before = readJson(mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, admin))
                .andReturn().getResponse().getContentAsString())
                .at("/activeUsersByRole/STUDENT").asLong();

        UserAccount student = account(Role.STUDENT, "Soon Deactivated");
        mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, admin))
                .andExpect(jsonPath("$.activeUsersByRole.STUDENT").value(before + 1));

        jdbc.update("UPDATE users SET status = 'DEACTIVATED' WHERE email = ?", student.getEmail());

        mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeUsersByRole.STUDENT").value(before));
    }

    @Test
    void adminDashboardIsRefusedToOtherRoles() throws Exception {
        String faculty = tokenFor(account(Role.FACULTY, "Not Admin Faculty"));
        String student = tokenFor(account(Role.STUDENT, "Not Admin Student"));

        mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, faculty))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get(ADMIN_DASHBOARD).header(AUTHORIZATION, student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void dashboardsAreRoleScopedAndRequireAuthentication() throws Exception {
        String student = tokenFor(account(Role.STUDENT, "Student"));
        String faculty = tokenFor(account(Role.FACULTY, "Faculty"));

        mockMvc.perform(get(FACULTY_DASHBOARD).header(AUTHORIZATION, student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get(STUDENT_DASHBOARD).header(AUTHORIZATION, faculty))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get(STUDENT_DASHBOARD))
                .andExpect(status().isUnauthorized());
    }
}
