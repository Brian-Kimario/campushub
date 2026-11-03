package com.campushub.projects;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Team membership and invitations against a real MySQL 8.4 container (STATE_MACHINES §6). Each test
 * uses its own accounts and client address so state from other tests never leaks in.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MembershipFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String MINE = "/api/v1/invitations/mine";

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
        String address = "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, String displayName) {
        String email = "team-" + UUID.randomUUID() + "@campushub.test";
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
        String body = mockMvc.perform(post(PROJECTS).header(AUTHORIZATION, token)
                        .contentType("application/json")
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String invitationFor(String ownerToken, String projectId, UserAccount invitee) throws Exception {
        String body = mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void acceptedInvitationAddsMemberWhoCanSeeTheProject() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount invitee = account(Role.STUDENT, "Invitee");
        String inviteeToken = tokenFor(invitee);
        String projectId = createdProject(ownerToken, "Shared Project");

        String invitationId = invitationFor(ownerToken, projectId, invitee);

        mockMvc.perform(get(MINE).header(AUTHORIZATION, inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectTitle").value("Shared Project"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept").header(AUTHORIZATION, inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get(PROJECTS + "/" + projectId).header(AUTHORIZATION, inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Shared Project"));

        mockMvc.perform(get(PROJECTS + "/" + projectId + "/members").header(AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].displayName").value("Invitee"))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test
    void declinedInvitationGrantsNoAccess() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount invitee = account(Role.STUDENT, "Decliner");
        String inviteeToken = tokenFor(invitee);
        String projectId = createdProject(ownerToken, "Declined Project");
        String invitationId = invitationFor(ownerToken, projectId, invitee);

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/decline").header(AUTHORIZATION, inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(get(PROJECTS + "/" + projectId).header(AUTHORIZATION, inviteeToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheInviteeCanAnswerAnInvitation() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount invitee = account(Role.STUDENT, "Target");
        String projectId = createdProject(ownerToken, "Guarded Project");
        String invitationId = invitationFor(ownerToken, projectId, invitee);

        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header(AUTHORIZATION, tokenFor(account(Role.STUDENT, "Stranger"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOwnerCanInviteAndDuplicatePendingIsRejected() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount invitee = account(Role.STUDENT, "Candidate");
        String projectId = createdProject(ownerToken, "Owned Project");
        invitationFor(ownerToken, projectId, invitee);

        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, tokenFor(account(Role.STUDENT, "Outsider")))
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void facultyCannotBeInvitedAsTeamMembers() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        UserAccount faculty = account(Role.FACULTY, "Faculty Member");
        String projectId = createdProject(ownerToken, "Student Only Project");

        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + faculty.getEmail() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void teamCannotExceedFiveIncludingTheOwner() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner"));
        String projectId = createdProject(ownerToken, "Full Project");
        for (int i = 1; i <= 4; i++) {
            UserAccount member = account(Role.STUDENT, "Member " + i);
            String invitationId = invitationFor(ownerToken, projectId, member);
            mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                            .header(AUTHORIZATION, tokenFor(member)))
                    .andExpect(status().isOk());
        }

        UserAccount late = account(Role.STUDENT, "Late Comer");
        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + late.getEmail() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TEAM_FULL"));
    }

    @Test
    void removingAMemberRevokesAccessImmediatelyAndOwnerCannotBeRemoved() throws Exception {
        UserAccount owner = account(Role.STUDENT, "Owner");
        String ownerToken = tokenFor(owner);
        UserAccount member = account(Role.STUDENT, "Former Member");
        String memberToken = tokenFor(member);
        String projectId = createdProject(ownerToken, "Revocation Project");
        String invitationId = invitationFor(ownerToken, projectId, member);
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept").header(AUTHORIZATION, memberToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PROJECTS + "/" + projectId + "/members/" + member.getId())
                        .header(AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PROJECTS + "/" + projectId).header(AUTHORIZATION, memberToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(PROJECTS + "/" + projectId + "/members/" + owner.getId())
                        .header(AUTHORIZATION, ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }
}
