package com.campushub.submissions;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * File types a submission may carry (API_CONTRACT §10). The type comes from the leading bytes, never from
 * the declared content type. A name with an extension must also match the detected type, so a PDF named
 * {@code photo.png} is refused.
 */
public enum EvidenceType {

    PDF("application/pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}, "pdf"),
    PNG("image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, "png"),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "jpg", "jpeg");

    private final String contentType;
    private final byte[] signature;
    private final Set<String> extensions;

    EvidenceType(String contentType, byte[] signature, String... extensions) {
        this.contentType = contentType;
        this.signature = signature;
        this.extensions = Set.of(extensions);
    }

    public String contentType() {
        return contentType;
    }

    /** The first extension in the declaration, used when a stored file needs a name. */
    public String preferredExtension() {
        return extensions.stream().sorted().findFirst().orElseThrow();
    }

    /** A name without an extension is accepted; a name with one must use an extension of this type. */
    public boolean accepts(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return true;
        }
        return extensions.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** How many leading bytes must be read to match every signature. */
    public static int probeLength() {
        return Arrays.stream(values()).mapToInt(type -> type.signature.length).max().orElseThrow();
    }

    /** The type whose signature starts the content, or empty when none matches. */
    public static Optional<EvidenceType> detect(byte[] head) {
        return Arrays.stream(values())
                .filter(type -> startsWith(head, type.signature))
                .findFirst();
    }

    private static boolean startsWith(byte[] head, byte[] signature) {
        return head.length >= signature.length && Arrays.equals(head, 0, signature.length, signature, 0, signature.length);
    }
}
