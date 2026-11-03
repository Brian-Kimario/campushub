package com.campushub.submissions;

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
 * Submission and review slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts and projects and uses its own client address, so rate limits and data never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SubmissionFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String LINK = "https://example.org/report";

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
        String address = "10.4." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
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
                        .content("{\"title\":\"Submission Host Project\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions createSubmission(String token, String projectId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/" + projectId + "/submissions").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content(body));
    }

    private String createdSubmission(String token, String projectId) throws Exception {
        String body = createSubmission(token, projectId,
                "{\"title\":\"Week one report\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions startReview(String token, String submissionId) throws Exception {
        return mockMvc.perform(post("/api/v1/submissions/" + submissionId + "/review/start")
                .header(AUTHORIZATION, bearer(token)));
    }

    private ResultActions decide(String token, String submissionId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/submissions/" + submissionId + "/review").header(AUTHORIZATION, bearer(token))
                .contentType("application/json")
                .content(body));
    }

    private void archive(String adminToken, String projectId, int version) throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive").header(AUTHORIZATION, bearer(adminToken))
                        .contentType("application/json").content("{\"version\":" + version + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCreatesRevisionOneAsSubmittedAndTheRowIsSealed() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        String token = tokenFor(owner);
        String projectId = createdProject(token);

        createSubmission(token, projectId, "{\"title\":\"Week one report\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionNo").value(1))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedBy").value(owner.getId().toString()))
                .andExpect(jsonPath("$.reviewedBy").doesNotExist())
                .andExpect(jsonPath("$.feedback").doesNotExist())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void nonHttpsLinkIsRejectedWithFieldErrors() throws Exception {
        String token = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(token);

        createSubmission(token, projectId, "{\"title\":\"Week one report\",\"link\":\"http://example.org/report\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void supervisorCannotCreateSubmissionAndGetsForbiddenState() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, faculty.getId()))
                .andExpect(status().isOk());

        createSubmission(tokenFor(faculty), projectId, "{\"title\":\"Faculty upload\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_STATE"));
    }

    @Test
    void otherStudentCannotListSubmissionsOfAProjectTheyCannotSee() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        createdSubmission(ownerToken, projectId);

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/submissions")
                        .header(AUTHORIZATION, bearer(tokenFor(account(Role.STUDENT, "Intruder")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void hiddenSubmissionLooksExactlyLikeAMissingSubmission() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));
        String intruderToken = tokenFor(account(Role.STUDENT, "Intruder"));

        String hidden = mockMvc.perform(get("/api/v1/submissions/" + submissionId).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(get("/api/v1/submissions/" + UUID.randomUUID()).header(AUTHORIZATION, bearer(intruderToken)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(hidden, "$.errorCode")).isEqualTo("NOT_FOUND");
        assertThat(JsonPath.<String>read(hidden, "$.detail"))
                .isEqualTo(JsonPath.<String>read(missing, "$.detail"));
    }

    @Test
    void supervisorReadsSubmissionsOfTheirProjectButAnotherFacultyGetsNotFound() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/submissions/" + submissionId).header(AUTHORIZATION, bearer(tokenFor(supervisor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submissionId));
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/submissions")
                        .header(AUTHORIZATION, bearer(tokenFor(supervisor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(submissionId));

        UserAccount otherFaculty = account(Role.FACULTY, "Otto Faculty");
        mockMvc.perform(get("/api/v1/submissions/" + submissionId).header(AUTHORIZATION, bearer(tokenFor(otherFaculty))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void adminCannotReviewAndGetsForbiddenRole() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));

        startReview(adminToken, submissionId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        decide(adminToken, submissionId, "{\"decision\":\"ACCEPTED\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void startingAReviewMovesSubmittedToUnderReviewAndRecordsTheReviewer() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());

        startReview(tokenFor(supervisor), submissionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewedBy").value(supervisor.getId().toString()));
        startReview(tokenFor(supervisor), submissionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void decisionBeforeTheReviewStartsIsInvalidTransition() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());

        decide(tokenFor(supervisor), submissionId, "{\"decision\":\"ACCEPTED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void changesRequestedWithoutFeedbackIsRefused() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        String supervisorToken = tokenFor(supervisor);
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());
        startReview(supervisorToken, submissionId).andExpect(status().isOk());

        decide(supervisorToken, submissionId, "{\"decision\":\"CHANGES_REQUESTED\",\"feedback\":\"   \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FEEDBACK_REQUIRED"));
    }

    @Test
    void secondDecisionIsReviewExistsAndNextRevisionFollowsChangesRequested() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String firstId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        String supervisorToken = tokenFor(supervisor);
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());
        startReview(supervisorToken, firstId).andExpect(status().isOk());

        decide(supervisorToken, firstId, "{\"decision\":\"CHANGES_REQUESTED\",\"feedback\":\"Cite your sources\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.feedback").value("Cite your sources"))
                .andExpect(jsonPath("$.reviewedBy").value(supervisor.getId().toString()))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        decide(supervisorToken, firstId, "{\"decision\":\"ACCEPTED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REVIEW_EXISTS"));

        mockMvc.perform(get("/api/v1/submissions/" + firstId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.feedback").value("Cite your sources"));

        createSubmission(ownerToken, projectId, "{\"title\":\"Revised report\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revisionNo").value(2))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        createSubmission(ownerToken, projectId, "{\"title\":\"Third try\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));

        mockMvc.perform(get("/api/v1/projects/" + projectId + "/submissions").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionNo").value(1))
                .andExpect(jsonPath("$[1].revisionNo").value(2));
    }

    @Test
    void reviewDecisionsNotifyTheSubmitterWithTheDecisionType() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Notified Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Noah Notifier");
        String supervisorToken = tokenFor(supervisor);
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());
        startReview(supervisorToken, submissionId).andExpect(status().isOk());

        decide(supervisorToken, submissionId, "{\"decision\":\"CHANGES_REQUESTED\",\"feedback\":\"Add a summary\"}")
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("SUBMISSION_CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.content[0].projectId").value(projectId));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(supervisorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void acceptedRevisionIsFinalAndCannotBeFollowedByANewRevision() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        String supervisorToken = tokenFor(supervisor);
        mockMvc.perform(supervisorPut(tokenFor(account(Role.ADMIN, "Ada Admin")), projectId, supervisor.getId()))
                .andExpect(status().isOk());
        startReview(supervisorToken, submissionId).andExpect(status().isOk());

        decide(supervisorToken, submissionId, "{\"decision\":\"ACCEPTED\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        createSubmission(ownerToken, projectId, "{\"title\":\"After acceptance\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TRANSITION"));
    }

    @Test
    void archivedProjectRefusesSubmissionsAndReviews() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        UserAccount supervisor = account(Role.FACULTY, "Fran Faculty");
        String supervisorToken = tokenFor(supervisor);
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        mockMvc.perform(supervisorPut(adminToken, projectId, supervisor.getId())).andExpect(status().isOk());
        archive(adminToken, projectId, 1);

        createSubmission(ownerToken, projectId, "{\"title\":\"Too late\",\"link\":\"" + LINK + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
        startReview(supervisorToken, submissionId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
        mockMvc.perform(get("/api/v1/submissions/" + submissionId).header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    /** Admin request that assigns a supervisor to a project still at version 0. */
    private static MockHttpServletRequestBuilder supervisorPut(String adminToken, String projectId, UUID supervisorId) {
        return put("/api/v1/projects/" + projectId + "/supervisor")
                .header(AUTHORIZATION, bearer(adminToken))
                .contentType("application/json")
                .content("{\"supervisorId\":\"" + supervisorId + "\",\"version\":0}");
    }
}
