package com.campushub.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campushub.identity.Role;
import com.campushub.identity.UserAccount;
import com.campushub.identity.UserAccountRepository;
import com.campushub.identity.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The optional Kafka extension against a real MySQL 8.4 and a real Kafka broker (ADR-011, FR-EVT-01 to 04,
 * NFR-REL-01). Covers the outbox write, relay, idempotent consumption, rollback atomicity, transient retry and
 * the dead-letter topic.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class KafkaFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final DockerImageName KAFKA_IMAGE = DockerImageName
            .parse("apache/kafka:3.9.1@sha256:4ceccc577f03f51f6af8dbfda55194d0d892f4fa7913ffbded567ce3895622ed")
            .asCompatibleSubstituteFor("apache/kafka");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String AUTHORIZATION = "Authorization";
    private static final String PROJECTS = "/api/v1/projects";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE).withDatabaseName("campushub");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

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
        registry.add("campushub.kafka.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("campushub.kafka.relay-interval-ms", () -> "200");
        registry.add("campushub.kafka.retry.initial-interval-ms", () -> "100");
        registry.add("campushub.kafka.retry.max-elapsed-ms", () -> "3000");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private ProcessedEventRepository processed;

    @Autowired
    private EventHandler handler;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @MockitoSpyBean
    private EventEffect effect;

    @MockitoSpyBean
    private AuditService audit;

    @Test
    void invitationIsRelayedAndAppliedExactlyOnce() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Kofi"));
        UserAccount invitee = account(Role.STUDENT, "Ada Outbox");
        String projectId = createdProject(ownerToken, "Outbox happy path");
        invitationFor(ownerToken, projectId, invitee);

        await().atMost(WAIT).until(() -> eventFor(projectId, DomainEventType.INVITATION_RECEIVED) != null);
        OutboxEvent event = eventFor(projectId, DomainEventType.INVITATION_RECEIVED);

        await().atMost(WAIT).until(() -> outbox.findById(event.getId()).orElseThrow().getPublishedAt() != null);
        await().atMost(WAIT).until(() -> processed.existsById(
                new ProcessedEventKey(event.getId(), EventHandler.CONSUMER)));

        // A redelivered message is recognised by its marker and skipped (FR-EVT-03).
        handler.handle(event.getPayload());
        verify(effect, times(1)).apply(eq(event.getId()), any(), any());
        assertThat(event.getPayload()).contains("\"projectId\":\"" + projectId + "\"");
    }

    @Test
    void failedWriteRollsBackTheOutboxRowWithTheChange() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Ruth"));
        UserAccount invitee = account(Role.STUDENT, "Ben Rollback");
        String projectId = createdProject(ownerToken, "Outbox rollback");
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit).record(eq("project.invitation_created"), any(), any(), any(), any(), any(), any());

        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations").header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().is5xxServerError());

        // The outbox row was written before the audit failure, so it must roll back with the invitation.
        assertThat(outbox.findAll()).noneMatch(event -> projectId.equals(event.getAggregateId()));
    }

    @Test
    void transientConsumerFailureIsRetriedUntilItApplies() throws Exception {
        String ownerToken = tokenFor(account(Role.STUDENT, "Owner Mira"));
        UserAccount invitee = account(Role.STUDENT, "Ada Retry");
        String projectId = createdProject(ownerToken, "Outbox retry");
        AtomicInteger injectedFailures = new AtomicInteger();
        doAnswer(invocation -> {
            JsonNode payload = invocation.getArgument(2);
            if (projectId.equals(payload.path("projectId").asText()) && injectedFailures.get() == 0) {
                injectedFailures.incrementAndGet();
                throw new IllegalStateException("transient effect failure");
            }
            return invocation.callRealMethod();
        }).when(effect).apply(any(), any(), any());

        invitationFor(ownerToken, projectId, invitee);

        await().atMost(WAIT).until(() -> eventFor(projectId, DomainEventType.INVITATION_RECEIVED) != null);
        OutboxEvent event = eventFor(projectId, DomainEventType.INVITATION_RECEIVED);
        await().atMost(WAIT).until(() -> processed.existsById(
                new ProcessedEventKey(event.getId(), EventHandler.CONSUMER)));
        // The first attempt failed, the retry applied it: the marker exists only because the retry succeeded.
        assertThat(injectedFailures.get()).isEqualTo(1);
    }

    @Test
    void unparseableMessageGoesToTheDeadLetterTopic() throws Exception {
        String poison = "not-json-" + UUID.randomUUID();
        kafka.send(EventTopics.EVENTS, "poison", poison).get();

        assertThat(deadLetterContains(poison)).isTrue();
    }

    private OutboxEvent eventFor(String aggregateId, DomainEventType type) {
        return outbox.findAll().stream()
                .filter(event -> aggregateId.equals(event.getAggregateId())
                        && type.wireName().equals(event.getEventType()))
                .findFirst()
                .orElse(null);
    }

    private boolean deadLetterContains(String value) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(EventTopics.DEAD_LETTER));
            await().atMost(WAIT).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (value.equals(record.value())) {
                        return true;
                    }
                }
                return false;
            });
            return true;
        }
    }

    private static RequestPostProcessor fromNewAddress() {
        String address = "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, String displayName) {
        String email = "kafka-" + UUID.randomUUID() + "@campushub.test";
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

    private void invitationFor(String ownerToken, String projectId, UserAccount invitee) throws Exception {
        mockMvc.perform(post(PROJECTS + "/" + projectId + "/invitations").header(AUTHORIZATION, ownerToken)
                        .contentType("application/json")
                        .content("{\"inviteeEmail\":\"" + invitee.getEmail() + "\"}"))
                .andExpect(status().isCreated());
    }
}
