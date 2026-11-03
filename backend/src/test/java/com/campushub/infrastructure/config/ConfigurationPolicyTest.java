package com.campushub.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigurationPolicyTest {

    private static final String STRONG_SECRET = "a-local-test-secret-of-more-than-32-bytes";

    @Test
    void completeConfigurationHasNoViolations() {
        List<String> violations = ConfigurationPolicy.violations(validSettings()::get, Set.of("dev"));

        assertTrue(violations.isEmpty(), () -> "expected no violations but found " + violations);
    }

    @Test
    void placeholderSecretIsRefusedWithoutEchoingTheValue() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.jwt.secret", "replace-with-64-char-random-value");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.jwt.secret holds a placeholder value"), violations);
        assertFalse(violations.get(0).contains("replace-with"));
    }

    @Test
    void unchangedChangeMePasswordIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("spring.datasource.password", "change-me-local-only");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("spring.datasource.password holds a placeholder value"), violations);
    }

    @Test
    void secretShorterThan32BytesIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.jwt.secret", "too-short");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.jwt.secret must be at least 32 bytes"), violations);
    }

    @Test
    void missingRequiredSettingIsRefused() {
        Map<String, String> settings = validSettings();
        settings.remove("spring.flyway.password");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("spring.flyway.password is missing"), violations);
    }

    @Test
    void wildcardCorsOriginIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.cors.allowed-origins", "*");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.cors.allowed-origins must not contain a wildcard"), violations);
    }

    @Test
    void devSeedInProdProfileIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.dev-seed-enabled", "true");
        settings.put("campushub.dev-seed-password", "a-local-dev-seed-password-123");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("prod"));

        assertEquals(List.of("campushub.dev-seed-enabled must be false in the prod profile"), violations);
    }

    @Test
    void devSeedWithoutPasswordIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.dev-seed-enabled", "true");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.dev-seed-password is missing"), violations);
    }

    @Test
    void shortDevSeedPasswordIsRefusedWithoutEchoingTheValue() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.dev-seed-enabled", "true");
        settings.put("campushub.dev-seed-password", "short-pw");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.dev-seed-password must be at least 12 characters"), violations);
        assertFalse(violations.get(0).contains("short-pw"));
    }

    @Test
    void invalidTimeZoneIsRefused() {
        Map<String, String> settings = validSettings();
        settings.put("campushub.institution-timezone", "Mars/Olympus");

        List<String> violations = ConfigurationPolicy.violations(settings::get, Set.of("dev"));

        assertEquals(List.of("campushub.institution-timezone is not a valid IANA time zone"), violations);
    }

    private static Map<String, String> validSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("spring.datasource.username", "campushub_app");
        settings.put("spring.datasource.password", "local-app-password-1");
        settings.put("spring.flyway.user", "campushub_migrator");
        settings.put("spring.flyway.password", "local-migrator-password-1");
        settings.put("campushub.jwt.secret", STRONG_SECRET);
        settings.put("campushub.cors.allowed-origins", "http://localhost:5173");
        settings.put("campushub.storage.root", "/var/campushub/storage");
        settings.put("campushub.institution-timezone", "UTC");
        settings.put("campushub.dev-seed-enabled", "false");
        return settings;
    }
}
