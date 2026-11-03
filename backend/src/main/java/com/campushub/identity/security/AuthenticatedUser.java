package com.campushub.identity.security;

import com.campushub.identity.Role;
import java.time.Instant;
import java.util.UUID;

/** The authenticated caller, placed in the security context by {@link AuthenticationFilter}. */
public record AuthenticatedUser(
        UUID userId,
        Role role,
        String jti,
        Instant expiresAt,
        boolean mustChangePassword) {
}
