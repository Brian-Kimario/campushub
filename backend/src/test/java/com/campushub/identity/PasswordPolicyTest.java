package com.campushub.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsAPassphraseAtTheByteLimit() {
        assertTrue(PasswordPolicy.violations("a".repeat(PasswordPolicy.MAX_BYTES)).isEmpty());
    }

    @Test
    void rejectsAPassphraseOneByteOverTheLimit() {
        assertEquals(List.of("must be at most 72 bytes when UTF-8 encoded"),
                PasswordPolicy.violations("a".repeat(PasswordPolicy.MAX_BYTES + 1)));
    }

    @Test
    void countsMultiByteCharactersByTheirUtf8Length() {
        // 25 characters of 3 bytes each is 75 bytes: under 72 characters, over the BCrypt limit.
        String candidate = "€".repeat(25);
        assertEquals(List.of("must be at most 72 bytes when UTF-8 encoded"), PasswordPolicy.violations(candidate));
    }

    @Test
    void rejectsShortAndCommonPasswords() {
        assertEquals(List.of("must be at least 8 characters"), PasswordPolicy.violations("Test@12"));
        assertEquals(List.of("is too common"), PasswordPolicy.violations("Password1234"));
        assertEquals(List.of("is too common"), PasswordPolicy.violations("PASSWORD"));
    }

    @Test
    void acceptsAnEightCharacterPassword() {
        assertTrue(PasswordPolicy.violations("Test@1234").isEmpty());
    }
}
