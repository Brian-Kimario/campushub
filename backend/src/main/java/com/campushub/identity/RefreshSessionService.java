package com.campushub.identity;

import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes refresh tokens (ADR-025). The token travels only in an HttpOnly
 * cookie scoped to the auth path. The database holds its SHA-256 hash.
 *
 * <p>Rotation returns an empty result rather than throwing for a refused token. A thrown exception
 * would roll back the family revocation that reuse detection just made.
 */
@Service
public class RefreshSessionService {

    static final String COOKIE_NAME = "campushub_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";
    static final String CLIENT_HEADER = "X-Requested-With";
    static final String CLIENT_HEADER_VALUE = "CampusHub";
    private static final String USER = "user";
    private static final String REUSE_DETECTED = "auth.refresh_reuse_detected";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshSessionRepository sessions;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final Clock clock;
    private final Duration lifetime;
    private final boolean cookieSecure;

    public RefreshSessionService(RefreshSessionRepository sessions, UserAccountRepository users,
                                 AuditService audit, Clock clock,
                                 @Value("${campushub.refresh.lifetime:PT8H}") Duration lifetime,
                                 @Value("${campushub.refresh.cookie-secure:true}") boolean cookieSecure) {
        this.sessions = sessions;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
        this.lifetime = lifetime;
        this.cookieSecure = cookieSecure;
    }

    /** Starts a new family at sign-in. The family ends after {@code lifetime}, with no sliding renewal. */
    @Transactional
    public IssuedRefresh start(UUID userId) {
        Instant now = clock.instant();
        return issue(userId, UUID.randomUUID().toString(), now.plus(lifetime), now);
    }

    /**
     * Exchanges a valid token for its successor in the same family. Returns empty when the token
     * is unknown, expired, revoked, or belongs to an inactive account. A reused token revokes the
     * whole family and writes an audit row.
     */
    @Transactional
    public Optional<Rotation> rotate(String rawToken, HttpServletRequest request) {
        Instant now = clock.instant();
        Optional<RefreshSession> found = sessions.findForUpdate(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshSession current = found.get();
        if (current.getUsedAt() != null) {
            sessions.revokeFamily(current.getFamilyId(), now);
            audit.record(REUSE_DETECTED, current.getUserId(), USER, current.getUserId().toString(),
                    AuditOutcome.DENIED, request, Map.of());
            return Optional.empty();
        }
        if (current.getRevokedAt() != null || !now.isBefore(current.getExpiresAt())) {
            return Optional.empty();
        }
        Optional<UserAccount> account = users.findById(current.getUserId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> !wasIssuedBeforeRevocation(current, user.getRevokedAfter()));
        if (account.isEmpty()) {
            return Optional.empty();
        }
        current.markUsed(now);
        IssuedRefresh next = issue(current.getUserId(), current.getFamilyId(), current.getExpiresAt(), now);
        return Optional.of(new Rotation(next, account.get()));
    }

    /** Ends the family of a presented token. Unknown tokens are ignored so logout always succeeds. */
    @Transactional
    public Optional<UUID> revokeFamilyOf(String rawToken) {
        Instant now = clock.instant();
        return sessions.findById(hash(rawToken)).map(session -> {
            sessions.revokeFamily(session.getFamilyId(), now);
            return session.getUserId();
        });
    }

    /** Ends every family for the user, for example after a password change. */
    @Transactional
    public void revokeAllFor(UUID userId) {
        sessions.revokeAllForUser(userId, clock.instant());
    }

    public ResponseCookie cookie(IssuedRefresh refresh) {
        return base(refresh.token())
                .maxAge(Duration.between(clock.instant(), refresh.expiresAt()))
                .build();
    }

    public ResponseCookie clearedCookie() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }

    private IssuedRefresh issue(UUID userId, String familyId, Instant expiresAt, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new RefreshSession(hash(raw), familyId, userId, now, expiresAt));
        return new IssuedRefresh(raw, expiresAt);
    }

    /** Same whole-second rule as the access-token filter (ADR-019). */
    private static boolean wasIssuedBeforeRevocation(RefreshSession session, Instant revokedAfter) {
        return revokedAfter != null
                && session.getIssuedAt().getEpochSecond() <= revokedAfter.getEpochSecond();
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }

    /** A raw refresh token and its absolute end. The raw value is never stored. */
    public record IssuedRefresh(String token, Instant expiresAt) {
    }

    public record Rotation(IssuedRefresh refresh, UserAccount account) {
    }
}
