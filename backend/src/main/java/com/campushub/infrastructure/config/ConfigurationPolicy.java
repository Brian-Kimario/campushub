package com.campushub.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Startup refusals from DEPLOYMENT §3 and SECURITY §9. Messages name the setting,
 * never its value, so a refusal cannot leak a secret into logs.
 */
public final class ConfigurationPolicy {

    static final int MIN_JWT_SECRET_BYTES = 32;
    static final int MIN_DEV_SEED_PASSWORD_LENGTH = 12;

    private static final List<String> PLACEHOLDER_PREFIXES = List.of("replace-with-", "change-me");

    private static final List<String> REQUIRED_KEYS = List.of(
            "spring.datasource.username",
            "spring.flyway.user",
            "campushub.cors.allowed-origins",
            "campushub.storage.root");

    private static final List<String> SECRET_KEYS = List.of(
            "spring.datasource.password",
            "spring.flyway.password",
            "campushub.jwt.secret");

    private ConfigurationPolicy() {
    }

    /**
     * @param settings lookup for a setting by its property name; returns null or blank when absent
     * @param activeProfiles the active Spring profiles
     * @return one message per violation; an empty list means the configuration may start
     */
    public static List<String> violations(Function<String, String> settings, Set<String> activeProfiles) {
        List<String> violations = new ArrayList<>();

        for (String key : REQUIRED_KEYS) {
            checkPresentAndNotPlaceholder(key, settings.apply(key), violations);
        }
        for (String key : SECRET_KEYS) {
            checkPresentAndNotPlaceholder(key, settings.apply(key), violations);
        }

        String secret = settings.apply("campushub.jwt.secret");
        if (isUsable(secret) && secret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            violations.add("campushub.jwt.secret must be at least " + MIN_JWT_SECRET_BYTES + " bytes");
        }

        String origins = settings.apply("campushub.cors.allowed-origins");
        if (isUsable(origins) && origins.contains("*")) {
            violations.add("campushub.cors.allowed-origins must not contain a wildcard");
        }

        String zone = settings.apply("campushub.institution-timezone");
        if (isUsable(zone) && !isValidZone(zone)) {
            violations.add("campushub.institution-timezone is not a valid IANA time zone");
        }

        boolean devSeed = Boolean.parseBoolean(settings.apply("campushub.dev-seed-enabled"));
        if (devSeed && activeProfiles.contains("prod")) {
            violations.add("campushub.dev-seed-enabled must be false in the prod profile");
        }
        if (devSeed) {
            checkDevSeedPassword(settings.apply("campushub.dev-seed-password"), violations);
        }

        return violations;
    }

    private static void checkPresentAndNotPlaceholder(String key, String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(key + " is missing");
        } else if (PLACEHOLDER_PREFIXES.stream().anyMatch(value::startsWith)) {
            violations.add(key + " holds a placeholder value");
        }
    }

    private static void checkDevSeedPassword(String value, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add("campushub.dev-seed-password is missing");
        } else if (PLACEHOLDER_PREFIXES.stream().anyMatch(value::startsWith)) {
            violations.add("campushub.dev-seed-password holds a placeholder value");
        } else if (value.length() < MIN_DEV_SEED_PASSWORD_LENGTH) {
            violations.add("campushub.dev-seed-password must be at least " + MIN_DEV_SEED_PASSWORD_LENGTH + " characters");
        }
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank()
                && PLACEHOLDER_PREFIXES.stream().noneMatch(value::startsWith);
    }

    private static boolean isValidZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }
}
