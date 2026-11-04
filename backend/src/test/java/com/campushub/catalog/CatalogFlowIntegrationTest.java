package com.campushub.catalog;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Catalog slice against a real MySQL 8.4 container (TEST_STRATEGY §4, FR-CAT-01..04). Each test creates its own
 * rows with unique codes and uses its own client address, so data and rate limits never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class CatalogFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BASE = "/api/v1";
    private static final String DEPARTMENTS = BASE + "/catalog/departments";
    private static final String TERMS = BASE + "/catalog/terms";
    private static final String COURSES = BASE + "/catalog/courses";

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
        String email = "catalog-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, "Catalog Tester",
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

    /** A unique code that fits VARCHAR(20). */
    private static String code() {
        return "C" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private ResultActions createDepartment(String token, String code, String name) throws Exception {
        return mockMvc.perform(post(BASE + "/admin/catalog/departments").header(AUTHORIZATION, token)
                .contentType("application/json")
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"));
    }

    private String createdDepartment(String token) throws Exception {
        String body = createDepartment(token, code(), "Dept " + code())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions createTerm(String token, String startsOn, String endsOn) throws Exception {
        return mockMvc.perform(post(BASE + "/admin/catalog/terms").header(AUTHORIZATION, token)
                .contentType("application/json")
                .content("{\"code\":\"" + code() + "\",\"name\":\"Term\",\"startsOn\":\"" + startsOn
                        + "\",\"endsOn\":\"" + endsOn + "\"}"));
    }

    private ResultActions createCourse(String token, String departmentId) throws Exception {
        return mockMvc.perform(post(BASE + "/admin/catalog/courses").header(AUTHORIZATION, token)
                .contentType("application/json")
                .content("{\"code\":\"" + code() + "\",\"title\":\"Course\",\"departmentId\":\""
                        + departmentId + "\"}"));
    }

    @Test
    void departmentsAreReadableByAnySignedInUserAndFilterByActive() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String id = createdDepartment(admin);

        mockMvc.perform(get(DEPARTMENTS).with(fromNewAddress()).header(AUTHORIZATION, student)
                        .param("active", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].active").value(hasItem(true)));
    }

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get(DEPARTMENTS).with(fromNewAddress()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void readAfterWriteShowsTheNewValueNotACachedOne() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String viewer = tokenFor(account(Role.STUDENT));
        String id = createdDepartment(admin);
        String before = "Before " + code();
        String after = "After " + code();

        // Warm the cache with the same request, then write, then read it again.
        mockMvc.perform(get(DEPARTMENTS).header(AUTHORIZATION, viewer).param("size", "100"))
                .andExpect(status().isOk());
        patchDepartment(id, admin, "{\"name\":\"" + before + "\"}").andExpect(status().isOk());
        mockMvc.perform(get(DEPARTMENTS).header(AUTHORIZATION, viewer).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].name").value(hasItem(before)));

        patchDepartment(id, admin, "{\"name\":\"" + after + "\"}").andExpect(status().isOk());
        mockMvc.perform(get(DEPARTMENTS).header(AUTHORIZATION, viewer).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].name").value(hasItem(after)))
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')].name").value(not(hasItem(before))));
    }

    private ResultActions patchDepartment(String id, String token, String body) throws Exception {
        return mockMvc.perform(patch(BASE + "/admin/catalog/departments/" + id).header(AUTHORIZATION, token)
                .contentType("application/json")
                .content(body));
    }

    @Test
    void adminAddsADepartmentAndDuplicateCodeIsRefused() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String code = code();

        createDepartment(admin, code, "Computing").andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.active").value(true));

        createDepartment(admin, code, "Other").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE"));
    }

    @Test
    void nonAdminCannotWriteToTheCatalog() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        createDepartment(student, code(), "Blocked").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void departmentNameBelowTheSchemaMinimumIsRejected() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));

        createDepartment(admin, code(), "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void termEndsMustBeAfterItStarts() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));

        createTerm(admin, "2030-06-30", "2030-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("endsOn"));
    }

    @Test
    void termUpdateChecksTheMergedDates() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String body = createTerm(admin, "2030-01-01", "2030-06-30")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        // Moving only endsOn before the stored startsOn must fail, even though the body has one date.
        mockMvc.perform(patch(BASE + "/admin/catalog/terms/" + id).header(AUTHORIZATION, admin)
                        .contentType("application/json")
                        .content("{\"endsOn\":\"2029-12-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void courseNeedsAKnownDepartment() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));

        createCourse(admin, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("departmentId"));
    }

    @Test
    void referencedDepartmentIsNotDeletedButAnUnreferencedOneIs() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String departmentId = createdDepartment(admin);
        createCourse(admin, departmentId).andExpect(status().isCreated());

        mockMvc.perform(delete(BASE + "/admin/catalog/departments/" + departmentId).header(AUTHORIZATION, admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_ITEM_IN_USE"));

        String unused = createdDepartment(admin);
        mockMvc.perform(delete(BASE + "/admin/catalog/departments/" + unused).header(AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void coursesFilterByDepartment() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String departmentId = createdDepartment(admin);
        String body = createCourse(admin, departmentId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(body, "$.id");

        mockMvc.perform(get(COURSES).header(AUTHORIZATION, student).param("departmentId", departmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(courseId));
    }

    @Test
    void unknownSortFieldIsRejectedWithAFieldError() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        mockMvc.perform(get(DEPARTMENTS).header(AUTHORIZATION, student).param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void termListFiltersByStatus() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        createTerm(admin, "2031-01-01", "2031-06-30").andExpect(status().isCreated());

        mockMvc.perform(get(TERMS).header(AUTHORIZATION, student).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", hasItem("ACTIVE")));
    }
}
