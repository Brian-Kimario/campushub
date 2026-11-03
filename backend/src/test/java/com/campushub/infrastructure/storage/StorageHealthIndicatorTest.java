package com.campushub.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;

class StorageHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void writableStorageRootIsUpAndLeavesNoProbeBehind() throws IOException {
        StorageHealthIndicator indicator = new StorageHealthIndicator(tempDir.toString());

        assertEquals(Status.UP, indicator.health().getStatus());

        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count(), "the readiness probe file must be deleted");
        }
    }

    @Test
    void storageRootThatIsNotADirectoryIsDown() throws IOException {
        Path notADirectory = Files.writeString(tempDir.resolve("plain-file"), "x");
        StorageHealthIndicator indicator = new StorageHealthIndicator(notADirectory.toString());

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void missingStorageRootIsDown() {
        StorageHealthIndicator indicator = new StorageHealthIndicator(tempDir.resolve("missing").toString());

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
