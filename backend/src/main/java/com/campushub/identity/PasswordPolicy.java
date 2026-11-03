package com.campushub.identity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Length and common-password rules (API_CONTRACT §2, SECURITY §3). The "must differ from the
 * last password" rule needs the stored hash, so the service checks it.
 *
 * <p>Gap: the common-password list below is a short starter list, not the full published list.
 * The complete list is not yet in the repository (IMPLEMENTATION_LOG §10).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    /**
     * BCrypt reads only the first 72 bytes of its input, so a longer password would silently
     * ignore its tail. The limit is therefore measured in UTF-8 bytes, not characters.
     */
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password1234",
            "password12345",
            "123456789012",
            "qwerty123456",
            "iloveyou1234",
            "welcome12345",
            "letmein12345",
            "monkey123456",
            "dragon123456",
            "football1234",
            "passw0rd1234",
            "changeme1234",
            "campushub123",
            "admin1234567");

    private PasswordPolicy() {
    }

    /** @return one reason per rule the candidate breaks; empty when it is acceptable */
    public static List<String> violations(String candidate) {
        List<String> reasons = new ArrayList<>();
        if (candidate == null || candidate.length() < MIN_LENGTH) {
            reasons.add("must be at least " + MIN_LENGTH + " characters");
        } else if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            reasons.add("must be at most " + MAX_BYTES + " bytes when UTF-8 encoded");
        } else if (COMMON_PASSWORDS.contains(candidate.toLowerCase(Locale.ROOT))) {
            reasons.add("is too common");
        }
        return reasons;
    }
}
