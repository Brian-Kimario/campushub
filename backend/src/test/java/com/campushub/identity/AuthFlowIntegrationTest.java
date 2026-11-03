package com.campushub.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campushub.identity.audit.AuditEvent;
import com.campushub.identity.audit.AuditEventRepository;
import com.campushub.identity.security.JwtTokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end authentication against a real MySQL 8.4 container (TEST_STRATEGY §4.6). Each test
 * creates its own accounts and uses its own client address, so the per-address rate limit and
 * account lockouts never leak between tests.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(ProbeController.class)
class AuthFlowIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName
            .parse("mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902")
            .asCompatibleSubstituteFor("mysql");
    private static final String JWT_SECRET = "integration-test-secret-of-more-than-32-bytes";
    private static final String DEV_SEED_PASSWORD = "dev-seed-password-123";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String REFRESH_COOKIE = "campushub_refresh";
    private static final String CLIENT_HEADER = "X-Requested-With";
    private static final String CLIENT_VALUE = "CampusHub";

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
        registry.add("campushub.dev-seed-enabled", () -> "true");
        registry.add("campushub.dev-seed-password", () -> DEV_SEED_PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEventRepository auditEvents;

    @Autowired
    private JwtTokenService tokens;

    /** A fresh client address per call, so rate limits are isolated between tests and calls. */
    private static RequestPostProcessor fromNewAddress() {
        String address = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private UserAccount account(Role role, boolean mustChangePassword) {
        String email = "user-" + UUID.randomUUID() + "@campushub.test";
        UserAccount account = new UserAccount(UUID.randomUUID(), email, "Test User",
                passwordEncoder.encode(PASSWORD), role, mustChangePassword, Instant.now());
        return users.save(account);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(fromNewAddress())
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String tokenFor(UserAccount account) throws Exception {
        return login(account.getEmail(), PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void loginReturnsTokenAndSummaryWithoutCredentials() throws Exception {
        login("student@campushub.local", DEV_SEED_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("student@campushub.local"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.failedLoginCount").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameResponse() throws Exception {
        UserAccount known = account(Role.STUDENT, false);

        login(known.getEmail(), "not-the-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect."));
        login("nobody-" + UUID.randomUUID() + "@campushub.test", "not-the-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("The email or password is incorrect."));
    }

    @Test
    void meReturnsTheCallerForAValidToken() throws Exception {
        UserAccount account = account(Role.STUDENT, false);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tokenFor(account))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId().toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void requestWithoutTokenIsUnauthenticatedWithChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void expiredTokenIsUnauthenticated() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        SignedJWT expired = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(account.getId().toString())
                .issuer("campushub")
                .audience("campushub-api")
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(1800)))
                .jwtID(UUID.randomUUID().toString())
                .claim("role", "STUDENT")
                .build());
        expired.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(expired.serialize())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutRevokesTheCurrentToken() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        String token = tokenFor(account);

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token))
                        .header(CLIENT_HEADER, CLIENT_VALUE))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordRevokesTokensIssuedBeforeTheChange() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        String before = tokenFor(account);
        Thread.sleep(1100); // revoked_after has one-second precision (ADR-019)

        mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(before))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"a-brand-new-passphrase\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(before)))
                .andExpect(status().isUnauthorized());
        login(account.getEmail(), "a-brand-new-passphrase").andExpect(status().isOk());
    }

    @Test
    void changePasswordRejectsAWeakReplacement() throws Exception {
        UserAccount account = account(Role.STUDENT, false);

        mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(tokenFor(account)))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    void changePasswordRejectsAReplacementLongerThanBcryptCanHold() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        String seventyThreeBytes = "a".repeat(73);

        mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(tokenFor(account)))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + seventyThreeBytes + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
    }

    @Test
    void forcedPasswordChangeBlocksOtherEndpointsUntilChanged() throws Exception {
        UserAccount account = account(Role.STUDENT, true);
        String token = tokenFor(account);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/v1/probe/ping").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void studentIsForbiddenFromAdminEndpoints() throws Exception {
        UserAccount student = account(Role.STUDENT, false);

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", bearer(tokenFor(student))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ROLE"));
    }

    @Test
    void adminCanReachAdminEndpoints() throws Exception {
        UserAccount admin = account(Role.ADMIN, false);

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", bearer(tokenFor(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void anotherUsersRecordIsNotFoundNotForbidden() throws Exception {
        UserAccount owner = account(Role.STUDENT, false);
        UserAccount other = account(Role.STUDENT, false);

        mockMvc.perform(get("/api/v1/probe/records/" + owner.getId()).header("Authorization", bearer(tokenFor(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/probe/records/" + owner.getId()).header("Authorization", bearer(tokenFor(owner))))
                .andExpect(status().isOk());
    }

    @Test
    void fifthFailureLocksTheAccountAndTheCorrectPasswordThenFails() throws Exception {
        UserAccount account = account(Role.STUDENT, false);

        for (int i = 0; i < 5; i++) {
            login(account.getEmail(), "wrong-password-123").andExpect(status().isUnauthorized());
        }
        login(account.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));

        List<AuditEvent> locks = auditEvents.findAll().stream()
                .filter(event -> "auth.account_locked".equals(event.getAction())).toList();
        assertThat(locks).isNotEmpty();
    }

    @Test
    void twentyFirstAttemptFromOneAddressIsRateLimitedWithRetryAfter() throws Exception {
        RequestPostProcessor address = fromNewAddress();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login").with(address).contentType("application/json")
                            .content("{\"email\":\"x-" + i + "@campushub.test\",\"password\":\"wrong-password-123\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(address).contentType("application/json")
                        .content("{\"email\":\"x@campushub.test\",\"password\":\"wrong-password-123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }

    @Test
    void devSeedCreatesOneAccountPerRoleWithoutForcedChange() throws Exception {
        for (String email : List.of("admin@campushub.local", "faculty@campushub.local",
                "student@campushub.local", "student2@campushub.local")) {
            assertThat(users.findByEmail(email)).hasValueSatisfying(account ->
                    assertThat(account.isMustChangePassword()).isFalse());
        }
    }

    private MvcResult loginResult(UserAccount account) throws Exception {
        return login(account.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie;
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewAddress())
                .cookie(cookie).header(CLIENT_HEADER, CLIENT_VALUE));
    }

    @Test
    void loginSetsAnHttpOnlyRefreshCookieScopedToTheAuthPath() throws Exception {
        MvcResult result = loginResult(account(Role.STUDENT, false));

        Cookie cookie = refreshCookie(result);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict");
    }

    @Test
    void refreshRotatesTheCookieAndReturnsAUsableAccessToken() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        Cookie first = refreshCookie(loginResult(account));

        MvcResult rotated = refresh(first).andExpect(status().isOk()).andReturn();
        Cookie second = refreshCookie(rotated);
        String access = rotated.getResponse().getContentAsString()
                .replaceAll("(?s).*\"accessToken\":\"([^\"]+)\".*", "$1");

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(access)))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        Cookie first = refreshCookie(loginResult(account));
        Cookie second = refreshCookie(refresh(first).andExpect(status().isOk()).andReturn());

        refresh(first).andExpect(status().isUnauthorized());
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutTheClientHeaderIsRejected() throws Exception {
        Cookie cookie = refreshCookie(loginResult(account(Role.STUDENT, false)));

        mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewAddress()).cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshWithoutACookieIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewAddress())
                        .header(CLIENT_HEADER, CLIENT_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSessionEvenWhenTheAccessTokenIsExpired() throws Exception {
        Cookie cookie = refreshCookie(loginResult(account(Role.STUDENT, false)));

        mockMvc.perform(post("/api/v1/auth/logout").header(CLIENT_HEADER, CLIENT_VALUE)
                        .header("Authorization", bearer("not-a-valid-token")).cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordEndsEveryRefreshSession() throws Exception {
        UserAccount account = account(Role.STUDENT, false);
        Cookie cookie = refreshCookie(loginResult(account));
        String token = tokenFor(account);

        mockMvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"another-new-passphrase\"}"))
                .andExpect(status().isNoContent());

        refresh(cookie).andExpect(status().isUnauthorized());
    }
}
