package com.campushub.submissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
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
 * Evidence file slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts, projects and client address. The framework multipart ceiling (25 MB) is not exercised here:
 * MockMvc bypasses it, so the policy limit (20 MiB) is what these tests can reach.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SubmissionFileFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final byte[] PDF = "%PDF-1.4\n% evidence\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

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
        String address = "10.6." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
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

    private String createdProject(String ownerToken) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects").header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"title\":\"Evidence Host Project\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String createdSubmission(String ownerToken, String projectId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/projects/" + projectId + "/submissions")
                        .header(AUTHORIZATION, bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"title\":\"Capstone report\",\"description\":\"Final draft\","
                                + "\"link\":\"https://example.org/report\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void supervisedBy(String projectId, UserAccount faculty) throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        mockMvc.perform(put("/api/v1/projects/" + projectId + "/supervisor").with(fromNewAddress())
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"supervisorId\":\"" + faculty.getId() + "\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    private ResultActions upload(String token, String submissionId, String name, String contentType, byte[] bytes)
            throws Exception {
        return mockMvc.perform(multipart("/api/v1/submissions/" + submissionId + "/files")
                .file(new MockMultipartFile("file", name, contentType, bytes))
                .header(AUTHORIZATION, bearer(token)));
    }

    private String uploaded(String token, String submissionId, String name, String contentType, byte[] bytes)
            throws Exception {
        String body = upload(token, submissionId, name, contentType, bytes)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void ownerUploadsEvidenceThatListsAndDownloadsUnchanged() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));

        String fileId = uploaded(ownerToken, submissionId, "report.pdf", "application/pdf", PDF);

        mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/files").header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fileId))
                .andExpect(jsonPath("$[0].originalName").value("report.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$[0].sha256").value(sha256Hex(PDF)))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());

        byte[] downloaded = mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/files/" + fileId + "/content")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
    }

    @Test
    void pngAndJpegAreDetectedFromTheirBytes() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));

        upload(ownerToken, submissionId, "diagram.png", "image/png", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
        upload(ownerToken, submissionId, "photo.jpeg", "image/jpeg", JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    @Test
    void contentThatDoesNotMatchItsNameOrSignatureIsRefused() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));

        upload(ownerToken, submissionId, "notes.pdf", "application/pdf", "plain text, not a PDF".getBytes(StandardCharsets.US_ASCII))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FILE_TYPE_NOT_ALLOWED"));
        upload(ownerToken, submissionId, "photo.png", "image/png", PDF)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FILE_TYPE_NOT_ALLOWED"));
        upload(ownerToken, submissionId, "script.exe", "application/octet-stream", PDF)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FILE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void fileOverTheSizeLimitIsRefusedWith413() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));
        byte[] oversized = new byte[20 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, oversized, 0, PDF.length);

        upload(ownerToken, submissionId, "thesis.pdf", "application/pdf", oversized)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"));
    }

    @Test
    void sixthFileOnOneRevisionIsRefused() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));
        for (int i = 1; i <= 5; i++) {
            uploaded(ownerToken, submissionId, "part-" + i + ".pdf", "application/pdf", PDF);
        }

        upload(ownerToken, submissionId, "part-6.pdf", "application/pdf", PDF)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("FILE_LIMIT_EXCEEDED"));
    }

    @Test
    void filesCannotBeAddedOnceReviewHasStarted() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        String projectId = createdProject(ownerToken);
        supervisedBy(projectId, faculty);
        String submissionId = createdSubmission(ownerToken, projectId);
        String facultyToken = tokenFor(faculty);
        mockMvc.perform(post("/api/v1/submissions/" + submissionId + "/review/start")
                        .with(fromNewAddress()).header(AUTHORIZATION, bearer(facultyToken)))
                .andExpect(status().isOk());

        upload(ownerToken, submissionId, "late.pdf", "application/pdf", PDF)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_SEALED"));
    }

    @Test
    void facultyCannotUploadButTheSupervisorCanReadTheFiles() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount faculty = account(Role.FACULTY, "Fran Faculty");
        String projectId = createdProject(ownerToken);
        supervisedBy(projectId, faculty);
        String submissionId = createdSubmission(ownerToken, projectId);
        String fileId = uploaded(ownerToken, submissionId, "report.pdf", "application/pdf", PDF);
        String facultyToken = tokenFor(faculty);

        upload(facultyToken, submissionId, "mine.pdf", "application/pdf", PDF)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/files/" + fileId + "/content")
                        .header(AUTHORIZATION, bearer(facultyToken)))
                .andExpect(status().isOk());
    }

    @Test
    void strangersAndAdminsGetNotFoundForFiles() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String submissionId = createdSubmission(ownerToken, createdProject(ownerToken));
        String fileId = uploaded(ownerToken, submissionId, "report.pdf", "application/pdf", PDF);

        for (String token : new String[] {tokenFor(account(Role.STUDENT, "Intruder")), tokenFor(account(Role.ADMIN, "Ada"))}) {
            mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/files").header(AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
            mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/files/" + fileId + "/content")
                            .header(AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void fileIdFromAnotherSubmissionIsNotFound() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String first = createdSubmission(ownerToken, createdProject(ownerToken));
        String second = createdSubmission(ownerToken, createdProject(ownerToken));
        String fileId = uploaded(ownerToken, first, "report.pdf", "application/pdf", PDF);

        mockMvc.perform(get("/api/v1/submissions/" + second + "/files/" + fileId + "/content")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedProjectRefusesNewFiles() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken);
        String submissionId = createdSubmission(ownerToken, projectId);
        String adminToken = tokenFor(account(Role.ADMIN, "Ada Admin"));
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive").with(fromNewAddress())
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType("application/json")
                        .content("{\"version\":0}"))
                .andExpect(status().isOk());

        upload(ownerToken, submissionId, "report.pdf", "application/pdf", PDF)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PROJECT_ARCHIVED"));
    }
}
