package com.campushub.skills;

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
import java.util.Collections;
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
 * Skill slice against a real MySQL 8.4 container (TEST_STRATEGY §4). Each test creates its own accounts
 * and skill names, and uses its own client address, so data and rate limits never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SkillFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String SKILLS = "/api/v1/users/me/skills";

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
        String address = "10.5." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role) {
        String email = "skill-" + UUID.randomUUID() + "@campushub.test";
        return users.save(new UserAccount(UUID.randomUUID(), email, "Skill Tester",
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

    private ResultActions createSkill(String token, String name, String category) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/skills").header(AUTHORIZATION, token)
                .contentType("application/json")
                .content("{\"name\":\"" + name + "\",\"category\":\"" + category + "\"}"));
    }

    private String createdSkill(String token, String name, String category) throws Exception {
        String body = createSkill(token, name, category)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String uniqueName() {
        return "Skill " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String entry(String skillId, String proficiency) {
        return "{\"skillId\":\"" + skillId + "\",\"proficiency\":\"" + proficiency + "\"}";
    }

    private ResultActions replaceSkills(String token, String body) throws Exception {
        return mockMvc.perform(put(SKILLS).header(AUTHORIZATION, token)
                .contentType("application/json")
                .content(body));
    }

    @Test
    void catalogIsReadableByAnySignedInUserAndFiltersByCategory() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String category = "Cat " + UUID.randomUUID().toString().substring(0, 8);
        String id = createdSkill(admin, uniqueName(), category);

        mockMvc.perform(get("/api/v1/skills").with(fromNewAddress()).header(AUTHORIZATION, student)
                        .param("category", category))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].category").value(category));
    }

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/skills").with(fromNewAddress()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void adminAddsASkillWithNormalizedNameAndRefusesADuplicate() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String category = "Norm " + UUID.randomUUID().toString().substring(0, 8);
        String base = uniqueName();

        createSkill(admin, "  " + base.replace(" ", "   ") + "  ", category)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(base));

        createSkill(admin, base.toLowerCase(), category)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE"));
    }

    @Test
    void nonAdminCannotAddToTheCatalog() throws Exception {
        String student = tokenFor(account(Role.STUDENT));

        createSkill(student, uniqueName(), "Programming")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void invalidSkillNameIsRejected() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));

        createSkill(admin, "x", "Programming")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void studentReplacesOwnSkillsAsOneSet() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String java = createdSkill(admin, uniqueName(), "Programming");
        String sql = createdSkill(admin, uniqueName(), "Data");

        replaceSkills(student, "[" + entry(java, "ADVANCED") + "," + entry(sql, "BEGINNER") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        replaceSkills(student, "[" + entry(sql, "EXPERT") + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skillId").value(sql))
                .andExpect(jsonPath("$[0].proficiency").value("EXPERT"));

        mockMvc.perform(get(SKILLS).header(AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].skillId").value(sql));
    }

    @Test
    void invalidReplacementLeavesThePreviousSetInPlace() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String java = createdSkill(admin, uniqueName(), "Programming");
        replaceSkills(student, "[" + entry(java, "INTERMEDIATE") + "]").andExpect(status().isOk());

        replaceSkills(student, "[" + entry(java, "ADVANCED") + "," + entry(java, "EXPERT") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        mockMvc.perform(get(SKILLS).header(AUTHORIZATION, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].proficiency").value("INTERMEDIATE"));
    }

    @Test
    void replacementRefusesUnknownSkillsMissingProficiencyAndLongEvidence() throws Exception {
        String student = tokenFor(account(Role.STUDENT));
        String unknown = UUID.randomUUID().toString();
        String longNote = "x".repeat(501);

        replaceSkills(student, "[" + entry(unknown, "BEGINNER") + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills[0].skillId"));

        String admin = tokenFor(account(Role.ADMIN));
        String java = createdSkill(admin, uniqueName(), "Programming");
        replaceSkills(student, "[{\"skillId\":\"" + java + "\",\"proficiency\":null}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills[0].proficiency"));

        replaceSkills(student, "[{\"skillId\":\"" + java + "\",\"proficiency\":\"BEGINNER\",\"evidenceNote\":\""
                        + longNote + "\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills[0].evidenceNote"));
    }

    @Test
    void replacementRefusesMoreThanFiftyEntries() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String student = tokenFor(account(Role.STUDENT));
        String skillId = createdSkill(admin, uniqueName(), "Programming");
        String many = String.join(",", Collections.nCopies(51, entry(skillId, "BEGINNER")));

        replaceSkills(student, "[" + many + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void studentSkillsAreOwnerOnly() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String owner = tokenFor(account(Role.STUDENT));
        String other = tokenFor(account(Role.STUDENT));
        String java = createdSkill(admin, uniqueName(), "Programming");
        replaceSkills(owner, "[" + entry(java, "ADVANCED") + "]").andExpect(status().isOk());

        mockMvc.perform(get(SKILLS).header(AUTHORIZATION, other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void facultyAndAdministratorsCannotReadOrWriteStudentSkills() throws Exception {
        String admin = tokenFor(account(Role.ADMIN));
        String faculty = tokenFor(account(Role.FACULTY));
        String java = createdSkill(admin, uniqueName(), "Programming");

        mockMvc.perform(get(SKILLS).header(AUTHORIZATION, faculty))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));

        replaceSkills(admin, "[" + entry(java, "EXPERT") + "]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }
}
