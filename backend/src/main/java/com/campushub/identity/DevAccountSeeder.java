package com.campushub.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one account per role for local development, only when {@code campushub.dev-seed-enabled}
 * is true (ADR-021). Passwords come from {@code campushub.dev-seed-password} and are never logged.
 * Accounts are created with no forced password change so local sign-in works immediately.
 */
@Component
@ConditionalOnProperty(prefix = "campushub", name = "dev-seed-enabled", havingValue = "true")
public class DevAccountSeeder implements ApplicationRunner {

    private static final String DOMAIN = "@campushub.local";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String password;

    public DevAccountSeeder(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock,
                            @Value("${campushub.dev-seed-password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin", "Dev Administrator", Role.ADMIN);
        seed("faculty", "Dev Faculty", Role.FACULTY);
        seed("student", "Dev Student", Role.STUDENT);
        seed("student2", "Dev Student Two", Role.STUDENT);
    }

    private void seed(String localPart, String displayName, Role role) {
        String email = localPart + DOMAIN;
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        // Deterministic ID per email, so re-running the seed never creates a second account.
        UUID id = UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
        users.save(new UserAccount(id, email, displayName, passwordEncoder.encode(password), role, false,
                clock.instant()));
    }
}
