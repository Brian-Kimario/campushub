package com.campushub;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.junit.jupiter.api.Test;

/**
 * Starts a real MySQL 8.4 container (digest-pinned, as in docker-compose.yml), applies the
 * Flyway migrations, and checks readiness and the OpenAPI document (IMPLEMENTATION_LOG Phase 1).
 *
 * Datasource and Flyway settings come from {@link DynamicPropertySource} rather than
 * {@code @ServiceConnection}: {@link com.campushub.infrastructure.config.ConfigurationGuard}
 * validates the environment before any bean is created, so the values must be present
 * in the environment up front.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("campushub");

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("campushub.jwt.secret", () -> "integration-test-secret-of-more-than-32-bytes");
        registry.add("campushub.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("campushub.storage.root", storageRoot::toString);
        registry.add("campushub.dev-seed-enabled", () -> "true");
        registry.add("campushub.dev-seed-password", () -> "dev-seed-password-123");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readinessIsUpWhenDatabaseMigratedAndStorageWritable() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Deviation (IMPLEMENTATION_LOG §10): the authentication rule is the last matcher, so an
     * anonymous request to an unknown path gets 401, not 404. Authenticated unknown paths get 404.
     */
    @Test
    void anonymousUnknownPathIsUnauthenticatedProblem() throws Exception {
        mockMvc.perform(get("/no-such-resource"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
                .andExpect(jsonPath("$.type").value("urn:campushub:problem:unauthenticated"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/no-such-resource"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("openapi")))
                .andExpect(jsonPath("$.openapi", notNullValue()));
    }
}
