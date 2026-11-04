package com.campushub.identity;

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
 * Own-profile slice against a real MySQL 8.4 container (API_CONTRACT §4, /users/me). Each test creates its own
 * accounts with unique emails and uses its own client address so rate limits never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProfileFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASE = "/api/v1";
    private static final String ME = BASE + "/users/me";

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
        String address = "10.7." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role) {
        String email = "profile-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, "Profile Tester",
                passwordEncoder.encode(PASSWORD), role, false, Instant.now()));
    }

    private String tokenFor(UserAccount account) throws Exception {
        String body = mockMvc.perform(post(BASE + "/auth/login").with(fromNewAddress())
                        .contentType("application/json")
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions patchMe(String token, String body) throws Exception {
        return mockMvc.perform(patch(ME).with(fromNewAddress()).header(AUTHORIZATION, token)
                .contentType("application/json")
                .content(body));
    }

    private String activeDepartment(UserAccount admin) throws Exception {
        String token = tokenFor(admin);
        String code = "P" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = mockMvc.perform(post(BASE + "/admin/catalog/departments").header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Profile Dept\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void getReturnsOwnProfileWithoutCredentials() throws Exception {
        UserAccount student = account(Role.STUDENT);

        mockMvc.perform(get(ME).with(fromNewAddress()).header(AUTHORIZATION, tokenFor(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId().toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ME).with(fromNewAddress()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void patchPersistsEditableFieldsAndTheyReadBack() throws Exception {
        UserAccount student = account(Role.STUDENT);
        String token = tokenFor(student);
        String departmentId = activeDepartment(account(Role.ADMIN));

        patchMe(token, "{\"displayName\":\"Ada Lovelace\",\"program\":\"BSc Computing\",\"cohort\":\"2026\","
                        + "\"designation\":\"Research assistant\",\"bio\":\"Interested in compilers.\","
                        + "\"departmentId\":\"" + departmentId + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.departmentId").value(departmentId));

        mockMvc.perform(get(ME).with(fromNewAddress()).header(AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("BSc Computing"))
                .andExpect(jsonPath("$.cohort").value("2026"))
                .andExpect(jsonPath("$.designation").value("Research assistant"))
                .andExpect(jsonPath("$.bio").value("Interested in compilers."));
    }

    @Test
    void blankOptionalFieldClearsIt() throws Exception {
        UserAccount student = account(Role.STUDENT);
        String token = tokenFor(student);
        patchMe(token, "{\"program\":\"BSc Computing\"}").andExpect(status().isOk());

        patchMe(token, "{\"program\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").doesNotExist());
    }

    @Test
    void protectedFieldsAreRefusedWithFieldNotEditableAndNothingChanges() throws Exception {
        UserAccount student = account(Role.STUDENT);
        String token = tokenFor(student);

        for (String field : new String[] {"role", "status", "email", "institutionalId"}) {
            patchMe(token, "{\"" + field + "\":\"X\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("FIELD_NOT_EDITABLE"));
        }

        UserAccount reloaded = users.findById(student.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getRole()).isEqualTo(Role.STUDENT);
        org.assertj.core.api.Assertions.assertThat(reloaded.getEmail()).isEqualTo(student.getEmail());
    }

    @Test
    void unknownFieldIsRejectedAsValidation() throws Exception {
        String token = tokenFor(account(Role.STUDENT));

        patchMe(token, "{\"favouriteColour\":\"blue\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("favouriteColour"));
    }

    @Test
    void invalidValuesAreReportedFieldByField() throws Exception {
        String token = tokenFor(account(Role.STUDENT));

        patchMe(token, "{\"displayName\":\"A\",\"bio\":42,\"departmentId\":\"not-a-uuid\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItems(
                        "displayName", "bio", "departmentId")));
    }

    @Test
    void departmentMustExistAndBeActive() throws Exception {
        String token = tokenFor(account(Role.STUDENT));

        patchMe(token, "{\"departmentId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("departmentId"));
    }

    @Test
    void patchOnlyChangesTheCallersOwnAccount() throws Exception {
        UserAccount first = account(Role.STUDENT);
        UserAccount second = account(Role.STUDENT);

        patchMe(tokenFor(first), "{\"program\":\"First only\"}").andExpect(status().isOk());

        mockMvc.perform(get(ME).with(fromNewAddress()).header(AUTHORIZATION, tokenFor(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.getId().toString()))
                .andExpect(jsonPath("$.program").doesNotExist());
    }
}
