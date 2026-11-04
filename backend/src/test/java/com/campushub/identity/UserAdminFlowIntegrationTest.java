package com.campushub.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Administrator user management against a real MySQL 8.4 container (API_CONTRACT §4, /admin/users).
 * Each test creates its own accounts with unique emails and its own client address.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserAdminFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASE = "/api/v1";
    private static final String ADMIN_USERS = BASE + "/admin/users";

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
        String address = "10.8." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role) {
        String email = "admin-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, "Admin Tester",
                passwordEncoder.encode(PASSWORD), role, false, Instant.now()));
    }

    private String tokenFor(UserAccount account) throws Exception {
        return tokenFor(account.getEmail(), PASSWORD);
    }

    private String tokenFor(String email, String password) throws Exception {
        String body = mockMvc.perform(post(BASE + "/auth/login").with(fromNewAddress())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions createUser(String token, String email, String role) throws Exception {
        return mockMvc.perform(post(ADMIN_USERS).with(fromNewAddress()).header(AUTHORIZATION, token)
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"displayName\":\"New Person\",\"role\":\"" + role + "\"}"));
    }

    private String uniqueEmail() {
        return "managed-" + UUID.randomUUID() + "@campushub.test";
    }

    private String createdId(String token, String email) throws Exception {
        String body = createUser(token, email, "STUDENT").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions postAction(String token, String id, String action) throws Exception {
        return mockMvc.perform(post(ADMIN_USERS + "/" + id + "/" + action).with(fromNewAddress())
                .header(AUTHORIZATION, token));
    }

    @Test
    void nonAdminCannotReachAdminUserDirectory() throws Exception {
        String token = tokenFor(account(Role.STUDENT));

        mockMvc.perform(get(ADMIN_USERS).with(fromNewAddress()).header(AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    void listFiltersByRoleAndSearchesByEmailPrefix() throws Exception {
        String token = tokenFor(account(Role.ADMIN));
        String email = "searchable-" + UUID.randomUUID() + "@campushub.test";
        createdId(token, email);

        mockMvc.perform(get(ADMIN_USERS).with(fromNewAddress()).header(AUTHORIZATION, token)
                        .param("role", "STUDENT").param("q", email.substring(0, 20).toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(email))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].temporaryPassword").doesNotExist());

        mockMvc.perform(get(ADMIN_USERS).with(fromNewAddress()).header(AUTHORIZATION, token)
                        .param("role", "FACULTY").param("q", email.substring(0, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].email").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(email))));
    }

    @Test
    void createReturnsTemporaryPasswordThatSignsInAndForcesChange() throws Exception {
        String token = tokenFor(account(Role.ADMIN));
        String email = uniqueEmail();

        String body = createUser(token, email, "FACULTY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FACULTY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andReturn().getResponse().getContentAsString();
        String temporary = JsonPath.read(body, "$.temporaryPassword");

        tokenFor(email, temporary);
    }

    @Test
    void duplicateEmailIsRefusedWithDuplicate() throws Exception {
        String token = tokenFor(account(Role.ADMIN));
        String email = uniqueEmail();
        createUser(token, email, "STUDENT").andExpect(status().isCreated());

        createUser(token, email.toUpperCase(), "STUDENT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE"));
    }

    @Test
    void createRejectsInvalidInputAsValidation() throws Exception {
        String token = tokenFor(account(Role.ADMIN));

        mockMvc.perform(post(ADMIN_USERS).with(fromNewAddress()).header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"displayName\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void adminCannotDeactivateOwnAccount() throws Exception {
        UserAccount admin = account(Role.ADMIN);
        String token = tokenFor(admin);

        postAction(token, admin.getId().toString(), "deactivate")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_NOT_MET"));

        assertThat(users.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deactivatedUserLosesAccessImmediatelyAndReactivationRestoresIt() throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN));
        String email = uniqueEmail();
        String id = createdId(adminToken, email);
        String studentToken = tokenFor(email, resetTemporary(adminToken, id));

        postAction(adminToken, id, "deactivate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));

        mockMvc.perform(get(BASE + "/users/me").with(fromNewAddress()).header(AUTHORIZATION, studentToken))
                .andExpect(status().isUnauthorized());

        postAction(adminToken, id, "reactivate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        tokenFor(email, resetTemporary(adminToken, id));
    }

    /** Resets the password, so the returned temporary value is the current one. */
    private String resetTemporary(String adminToken, String id) throws Exception {
        String body = postAction(adminToken, id, "reset-password").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.temporaryPassword");
    }

    @Test
    void resetPasswordRevokesExistingTokensAndIssuesNewTemporary() throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN));
        String email = uniqueEmail();
        String id = createdId(adminToken, email);
        String oldToken = tokenFor(email, resetTemporary(adminToken, id));

        String body = postAction(adminToken, id, "reset-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.temporaryPassword");

        mockMvc.perform(get(BASE + "/users/me").with(fromNewAddress()).header(AUTHORIZATION, oldToken))
                .andExpect(status().isUnauthorized());
        tokenFor(email, fresh);
    }

    @Test
    void roleChangeIsRefusedWithRoleChangeNotSupported() throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN));
        String id = createdId(adminToken, uniqueEmail());

        mockMvc.perform(patch(ADMIN_USERS + "/" + id).with(fromNewAddress()).header(AUTHORIZATION, adminToken)
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ROLE_CHANGE_NOT_SUPPORTED"));

        assertThat(users.findById(UUID.fromString(id)).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void patchUpdatesProfileFieldsAndKeepsRoleWhenSameRoleIsSent() throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN));
        String id = createdId(adminToken, uniqueEmail());

        mockMvc.perform(patch(ADMIN_USERS + "/" + id).with(fromNewAddress()).header(AUTHORIZATION, adminToken)
                        .contentType("application/json")
                        .content("{\"role\":\"STUDENT\",\"displayName\":\"Renamed Person\",\"cohort\":\"2027\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed Person"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        String adminToken = tokenFor(account(Role.ADMIN));

        postAction(adminToken, UUID.randomUUID().toString(), "deactivate")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }
}
