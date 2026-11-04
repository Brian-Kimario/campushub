package com.campushub.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Writes and reads uploaded bytes under the storage root (OPERATIONS §2). Keys are random UUIDs chosen
 * here, never taken from a client. Every path is checked to stay under the root.
 */
@Component
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path root;

    public FileStorage(@Value("${campushub.storage.root}") String storageRoot) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    /** What was written: the key the caller must record, the byte count, and the SHA-256 of the content in lowercase hex. */
    public record Stored(String key, long sizeBytes, String sha256) {
    }

    /** Streams the content to a new file. A partly written file is removed before the failure is rethrown. */
    public Stored write(InputStream content) throws IOException {
        String key = UUID.randomUUID().toString();
        MessageDigest digest = sha256();
        long size;
        try {
            Files.createDirectories(root);
            try (InputStream in = new DigestInputStream(content, digest)) {
                size = Files.copy(in, resolve(key));
            }
        } catch (IOException | RuntimeException ex) {
            delete(key);
            throw ex;
        }
        return new Stored(key, size, HexFormat.of().formatHex(digest.digest()));
    }

    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    /** Best effort. A file that cannot be removed is logged, because the row that named it is already gone. */
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not remove stored file {}", key, ex);
        }
    }

    /** A key must parse as a UUID, and the resolved path must stay under the root. */
    private Path resolve(String key) {
        Path path = root.resolve(UUID.fromString(key).toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Storage path escapes the storage root.");
        }
        return path;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime.", ex);
        }
    }
}
