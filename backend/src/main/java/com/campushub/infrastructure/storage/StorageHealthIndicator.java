package com.campushub.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness check for the file volume (OPERATIONS §2). Writes and deletes a
 * small probe file in the storage root. A failure marks the instance not ready.
 */
@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final Path storageRoot;

    public StorageHealthIndicator(@Value("${campushub.storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot);
    }

    @Override
    public Health health() {
        Path probe = storageRoot.resolve(".readiness-" + UUID.randomUUID() + ".probe");
        try {
            Files.writeString(probe, "ok");
            Files.delete(probe);
            return Health.up().build();
        } catch (IOException | RuntimeException ex) {
            return Health.down(ex).build();
        }
    }
}
