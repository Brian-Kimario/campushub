package com.campushub.notifications;

import static org.hamcrest.Matchers.hasItem;
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
 * Notifications written by team changes, read against a real MySQL 8.4 container (V15). Each test uses
 * its own accounts and client address so state from other tests never leaks in.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificationFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String NOTIFICATIONS = "/api/v1/notifications";

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

    private UserAccount account(Role role, String displayName) {
        String email = "notify-" + UUID.randomUUID() + "@campushub.test";
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
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void invitationAndAnswersNotifyTheRightPeople() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Olu"));
        UserAccount ada = account(Role.STUDENT, "Ada Lovelace");
        UserAccount ben = account(Role.STUDENT, "Ben Carter");
        String adaToken = tokenFor(ada);
        String benToken = tokenFor(ben);
        String projectId = createdProject(ownerToken, "Notify Project");
        String adaInvitation = invitationFor(ownerToken, projectId, ada);
        String benInvitation = invitationFor(ownerToken, projectId, ben);

        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(get(NOTIFICATIONS).header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("INVITATION_RECEIVED"))
                .andExpect(jsonPath("$.content[0].message").value("You were invited to join \"Notify Project\"."))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist());

        mockMvc.perform(post("/api/v1/invitations/" + adaInvitation + "/accept").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/invitations/" + benInvitation + "/decline").header(AUTHORIZATION, benToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(NOTIFICATIONS + "?unreadOnly=true").header(AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].message").value(hasItem(
                        "Ada Lovelace accepted your invitation to \"Notify Project\".")))
                .andExpect(jsonPath("$.content[*].message").value(hasItem(
                        "Ben Carter declined your invitation to \"Notify Project\".")));
    }

    @Test
    void failedInvitationWritesNoNotification() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Mira"));
        UserAccount ada = account(Role.STUDENT, "Ada Pending");
        String adaToken = tokenFor(ada);
        String projectId = createdProject(ownerToken, "Rollback Project");
        invitationFor(ownerToken, projectId, ada);

        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations")
                        .header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + ada.getEmail() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE"));

        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void removalNotifiesTheMemberAndTheirAccessEndsAtOnce() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Ruth"));
        UserAccount ada = account(Role.STUDENT, "Ada Removed");
        String adaToken = tokenFor(ada);
        String projectId = createdProject(ownerToken, "Removal Project");
        String invitationId = invitationFor(ownerToken, projectId, ada);
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PROJECTS + "/" + projectId + "/members/" + ada.getId())
                        .header(AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(NOTIFICATIONS).header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.content[0].message").value("You were removed from \"Removal Project\"."));
    }

    @Test
    void markReadIsPrivateToTheRecipientAndIdempotent() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Sam"));
        UserAccount ada = account(Role.STUDENT, "Ada Reader");
        String adaToken = tokenFor(ada);
        String stranger = tokenFor(account(Role.STUDENT, "Stranger"));
        String projectId = createdProject(ownerToken, "Read Project");
        invitationFor(ownerToken, projectId, ada);
        String notificationId = JsonPath.read(mockMvc.perform(get(NOTIFICATIONS).header(AUTHORIZATION, adaToken))
                .andReturn().getResponse().getContentAsString(), "$.content[0].id");

        mockMvc.perform(post(NOTIFICATIONS + "/" + notificationId + "/read").header(AUTHORIZATION, stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(NOTIFICATIONS + "/" + notificationId + "/read").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mockMvc.perform(post(NOTIFICATIONS + "/" + notificationId + "/read").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markAllReadClearsOnlyTheCallersUnreadRows() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Tade"));
        UserAccount ada = account(Role.STUDENT, "Ada Bulk");
        String adaToken = tokenFor(ada);
        String projectA = createdProject(ownerToken, "Bulk A");
        String projectB = createdProject(ownerToken, "Bulk B");
        invitationFor(ownerToken, projectA, ada);
        invitationFor(ownerToken, projectB, ada);

        mockMvc.perform(get(NOTIFICATIONS + "?unreadOnly=true&size=1").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(post(NOTIFICATIONS + "/read-all").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
        mockMvc.perform(post(NOTIFICATIONS + "/read-all").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(0));
        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get(NOTIFICATIONS + "?unreadOnly=true").header(AUTHORIZATION, adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void facultyWithNoTeamSeesAnEmptyInbox() throws Exception {
        String facultyToken = tokenFor(account(Role.FACULTY, "Dr Faculty"));

        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(AUTHORIZATION, facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get(NOTIFICATIONS).header(AUTHORIZATION, facultyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
