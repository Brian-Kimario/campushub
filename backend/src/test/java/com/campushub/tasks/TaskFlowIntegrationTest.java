package com.campushub.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Task slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts and projects and uses its own client address, so rate limits and data never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TaskFlowIntegrationTest {

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
        String address = "10.3." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
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
                        .content("{\"title\":\"Task Host Project\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions createTask(String token, String projectId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content(body));
    }

    private String createdTask(String token, String projectId, String title) throws Exception {
        String body = createTask(token, projectId, "{\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions transition(String token, String taskId, String to, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks/" + taskId + "/transitions").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content("{\"to\":\"" + to + "\",\"version\":" + version + "}"));
    }

    private ResultActions transitionWithReason(String token, String taskId, String to, int version, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/v1/tasks/" + taskId + "/transitions").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content("{\"to\":\"" + to + "\",\"version\":" + version + ",\"blockedReason\":\"" + reason + "\"}"));
    }

    @Test
    void ownerCreatesATodoTaskWithMediumPriorityByDefault() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        String token = tokenFor(owner);
        String projectId = createdProject(token);

        createTask(token, projectId, "{\"title\":\"Write the literature review\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdBy").value(owner.getId().toString()))
                .andExpect(jsonPath("$.blockedReason").doesNotExist());
    }

    @Test
    void invalidTaskInputIsRejectedWithFieldErrors() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);

        createTask(token, projectId, "{\"title\":\"ab\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        createTask(token, projectId, "{\"title\":\"Valid title\",\"priority\":\"URGENT\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void supervisorCannotCreateTasksAndGetsForbiddenStateNotNotFound() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        mockMvc.perform(supervisorPut(adminToken, projectId, faculty.getId()))
                .andExpect(status().isOk());

        createTask(tokenFor(faculty), projectId, "{\"title\":\"Faculty task\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
    }

    @Test
    void otherStudentCannotListTasksOfAProjectTheyCannotSee() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        createdTask(ownerToken, projectId, "Private task");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").header(AUTHORIZATION,
                        bearer(tokenFor(account(Role.STUDENT, "Intruder")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void ownerListsTasksInCreationOrderAndFiltersByStatus() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);
        String first = createdTask(token, projectId, "First task");
        createdTask(token, projectId, "Second task");

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.sort[0]").value("createdAt,asc"));
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks?status=DONE").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks?status=NOPE").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void supervisorCanReadTasksOfTheirProject() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String taskId = createdTask(ownerToken, projectId, "Readable task");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(
                        tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, faculty.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks").header(AUTHORIZATION, bearer(tokenFor(faculty))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(tokenFor(faculty))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId));
    }

    @Test
    void hiddenTaskLooksExactlyLikeAMissingTask() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(ownerToken, createdProject(ownerToken), "Hidden task");
        String intruderToken = tokenFor(account(Role.STUDENT, "Intruder"));

        String hidden = mockMvc.perform(get("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/tasks/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(hidden, "$.errorCode")).isEqualTo("NOT_FOUND");
        assertThat(JsonPath.<String>read(hidden, "$.detail")).isEqualTo(JsonPath.<String>read(missing, "$.detail"));
    }

    @Test
    void malformedTaskIdIsNotFound() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));

        mockMvc.perform(get("/api/v1/tasks/not-a-uuid").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void patchWithAStatusIsRefusedAndOtherFieldsUpdate() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(token, createdProject(token), "Original task");

        mockMvc.perform(patch("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"version\":0,\"title\":\"Original task\",\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FIELD_NOT_EDITABLE"));

        mockMvc.perform(patch("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(token))
                        .contentType("application/json")
                        .content("{\"version\":0,\"title\":\"Renamed task\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed task"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void ownerWalksTheHappyPathAndCompletionIsRecordedAndCleared() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(token, createdProject(token), "Happy path task");

        transition(token, taskId, "IN_PROGRESS", 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));
        transition(token, taskId, "IN_REVIEW", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        transition(token, taskId, "DONE", 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        transition(token, taskId, "IN_PROGRESS", 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void blockedNeedsAReasonAndUnblockingClearsIt() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(token, createdProject(token), "Blockable task");

        transition(token, taskId, "BLOCKED", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("blockedReason"));

        transitionWithReason(token, taskId, "BLOCKED", 0, "Waiting on the dataset")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.blockedReason").value("Waiting on the dataset"));

        transition(token, taskId, "TODO", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.blockedReason").doesNotExist());
    }

    @Test
    void illegalAndSameStateTransitionsAreRefused() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(token, createdProject(token), "Guarded task");

        transition(token, taskId, "DONE", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
        transition(token, taskId, "TODO", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void staleVersionIsRefusedBeforeTheTransitionIsChecked() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String taskId = createdTask(token, createdProject(token), "Versioned task");

        transition(token, taskId, "IN_PROGRESS", 7)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_VERSION"));
    }

    @Test
    void supervisorCannotTransitionTasks() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String taskId = createdTask(ownerToken, projectId, "Owner-only transitions");
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(
                        tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, faculty.getId()))
                .andExpect(status().isOk());

        transition(tokenFor(faculty), taskId, "IN_PROGRESS", 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
    }

    @Test
    void archivedProjectRefusesTaskWritesWithoutTouchingTheTask() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String taskId = createdTask(ownerToken, projectId, "Frozen task");
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive").header(AUTHORIZATION, bearer(adminToken))
                        .contentType("application/json").content("{\"version\":0}"))
                .andExpect(status().isOk());

        createTask(ownerToken, projectId, "{\"title\":\"Too late\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
        transition(ownerToken, taskId, "IN_PROGRESS", 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
        mockMvc.perform(get("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.version").value(0));
    }

    /** Invites the student and accepts on their behalf, so they are a team member when this returns. */
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

    /** Full-field PATCH that sets the assignee; a null assignee unassigns. */
    private ResultActions assign(String token, String taskId, int version, String title, UUID assigneeId)
            throws Exception {
        String assignee = assigneeId == null ? "null" : "\"" + assigneeId + "\"";
        return mockMvc.perform(patch("/api/v1/tasks/" + taskId).header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content("{\"version\":" + version + ",\"title\":\"" + title + "\",\"assigneeId\":" + assignee + "}"));
    }

    @Test
    void assigneeMustBeTheOwnerOrAnActiveMemberAndNullUnassigns() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        String ownerToken = tokenFor(owner);
        String projectId = createdProject(ownerToken);
        UserAccount member = account(Role.STUDENT, "Member");
        acceptedMember(ownerToken, projectId, member);
        UserAccount outsider = account(Role.STUDENT, "Outsider");

        createTask(ownerToken, projectId, "{\"title\":\"Given at birth\",\"assigneeId\":\"" + member.getId() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeId").value(member.getId().toString()));
        createTask(ownerToken, projectId, "{\"title\":\"Not on the team\",\"assigneeId\":\"" + outsider.getId() + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_NOT_MET"));

        String taskId = createdTask(ownerToken, projectId, "Assigned later");
        assign(ownerToken, taskId, 0, "Assigned later", outsider.getId())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_NOT_MET"));
        assign(ownerToken, taskId, 0, "Assigned later", owner.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(owner.getId().toString()));
        assign(ownerToken, taskId, 1, "Assigned later", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(nullValue()));
    }

    @Test
    void removingAMemberClearsOnlyTheirOpenAssignments() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        String ownerToken = tokenFor(owner);
        String projectId = createdProject(ownerToken);
        UserAccount member = account(Role.STUDENT, "Member");
        acceptedMember(ownerToken, projectId, member);

        String openTaskId = createdTask(ownerToken, projectId, "Still open");
        assign(ownerToken, openTaskId, 0, "Still open", member.getId()).andExpect(status().isOk());

        String doneTaskId = createdTask(ownerToken, projectId, "Already finished");
        assign(ownerToken, doneTaskId, 0, "Already finished", member.getId()).andExpect(status().isOk());
        transition(ownerToken, doneTaskId, "IN_PROGRESS", 1).andExpect(status().isOk());
        transition(ownerToken, doneTaskId, "IN_REVIEW", 2).andExpect(status().isOk());
        transition(ownerToken, doneTaskId, "DONE", 3).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + projectId + "/members/" + member.getId())
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/" + openTaskId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(nullValue()))
                .andExpect(jsonPath("$.version").value(2));
        mockMvc.perform(get("/api/v1/tasks/" + doneTaskId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(member.getId().toString()))
                .andExpect(jsonPath("$.version").value(4));
    }

    /** Admin request that assigns a supervisor to a project still at version 0. */
    private static MockHttpServletRequestBuilder supervisorPut(String adminToken, String projectId, UUID supervisorId) {
        return put("/api/v1/projects/" + projectId + "/supervisor")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType("application/json")
                .content("{\"supervisorId\":\"" + supervisorId + "\",\"version\":0}");
    }
}
