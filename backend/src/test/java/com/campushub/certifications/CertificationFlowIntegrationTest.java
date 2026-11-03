package com.campushub.certifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Certification slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own
 * accounts and uses its own client address, so data and rate limits never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CertificationFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CERTIFICATIONS = "/api/v1/users/me/certifications";
    private static final String VALID_BODY = """
            {"title":"AWS Cloud Practitioner","issuer":"Amazon Web Services","issuedOn":"2026-03-14",
             "credentialUrl":"https://www.credly.com/badges/example"}
            """;

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

    private UserAccount account(Role role) {
        String email = "cert-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, "Cert Tester",
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

    private ResultActions createCertification(String token, String body) throws Exception {
        return mockMvc.perform(post(CERTIFICATIONS).header(AUTHORIZATION, token)
                .contentType("application/json")
                .content(body));
    }

    private String createdCertification(String token) throws Exception {
        String body = createCertification(token, VALID_BODY)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void studentCreatesListsAndDeletesOwnCertification() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        createCertification(student, VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("AWS Cloud Practitioner"))
                .andExpect(jsonPath("$.issuer").value("Amazon Web Services"))
                .andExpect(jsonPath("$.issuedOn").value("2026-03-14"))
                .andExpect(jsonPath("$.credentialUrl").value("https://www.credly.com/badges/example"));

        mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("AWS Cloud Practitioner"));

        String id = createdCertification(student);
        mockMvc.perform(delete(CERTIFICATIONS + "/" + id).header(AUTHORIZATION, student))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, student))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listIsNewestIssuedFirst() throws Exception {
        String student = tokenFor(account(Role.STUDENT));
        createCertification(student, """
                {"title":"Older","issuer":"Issuer","issuedOn":"2020-01-01"}
                """).andExpect(status().isCreated());
        createCertification(student, """
                {"title":"Newer","issuer":"Issuer","issuedOn":"2025-06-01"}
                """).andExpect(status().isCreated());

        mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Newer"))
                .andExpect(jsonPath("$.content[1].title").value("Older"));
    }

    @Test
    void credentialUrlMustBeHttpsWhenPresent() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        for (String url : new String[] {"http://example.com/badge", "javascript:alert(1)", "ftp://example.com"}) {
            createCertification(student, """
                    {"title":"Badge","issuer":"Issuer","issuedOn":"2026-01-01","credentialUrl":"%s"}
                    """.formatted(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
        }

        createCertification(student, """
                {"title":"Badge","issuer":"Issuer","issuedOn":"2026-01-01"}
                """).andExpect(status().isCreated());
    }

    @Test
    void missingRequiredFieldsAreRejected() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        createCertification(student, """
                {"title":"","issuer":"Issuer","issuedOn":"2026-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        createCertification(student, """
                {"title":"Badge","issuer":"Issuer"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void otherStudentsCertificationsAreConcealed() throws Exception {
        String owner = tokenFor(account(Role.STUDENT));
        String other = tokenFor(account(Role.STUDENT));
        String id = createdCertification(owner);

        mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete(CERTIFICATIONS + "/" + id).header(AUTHORIZATION, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, owner))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void facultyAndAdministratorsCannotReadOrWriteCertifications() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String faculty = tokenFor(account(Role.FACULTY));
        String student = tokenFor(account(Role.STUDENT));
        String id = createdCertification(student);

        for (String token : new String[] {admin, faculty}) {
            mockMvc.perform(get(CERTIFICATIONS).header(AUTHORIZATION, token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
            createCertification(token, VALID_BODY)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
            mockMvc.perform(delete(CERTIFICATIONS + "/" + id).header(AUTHORIZATION, token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
        }
    }

    @Test
    void certificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get(CERTIFICATIONS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingACertificationDoesNotFetchTheCredentialUrl() throws Exception {
        // The server stores credentialUrl as text only (SECURITY.md, SSRF). A request that would
        // reach an unroutable host must still succeed quickly with no outbound call.
        String student = tokenFor(account(Role.STUDENT));
        String body = createCertification(student, """
                {"title":"Badge","issuer":"Issuer","issuedOn":"2026-01-01",
                 "credentialUrl":"https://10.255.255.1/unreachable"}
                """)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.credentialUrl")).isEqualTo("https://10.255.255.1/unreachable");
    }
}
