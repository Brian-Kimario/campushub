package com.campushub.identity;

import com.campushub.identity.audit.AuditOutcome;
import com.campushub.identity.audit.AuditService;
import com.campushub.identity.security.AuthenticatedUser;
import com.campushub.identity.security.JwtTokenService;
import com.campushub.shared.errors.ApiException;
import com.campushub.shared.errors.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sign-in, sign-out, and password change (ADR-003, ADR-019, ADR-020).
 *
 * <p>Every failed sign-in gives the same {@code INVALID_CREDENTIALS} response, whether the email is
 * unknown, the account is locked or deactivated, or the password is wrong. Every path runs a BCrypt
 * comparison so timing does not reveal which case applies. The failure is written to the audit log
 * before the error is thrown. The login transaction returns an outcome instead of throwing, so the
 * audit row commits.
 */
@Service
public class AuthService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private static final String USER = "user";
    private static final String LOGIN_SUCCEEDED = "auth.login_succeeded";
    private static final String LOGIN_FAILED = "auth.login_failed";
    private static final String ACCOUNT_LOCKED = "auth.account_locked";
    private static final String LOGOUT = "auth.logout";
    private static final String PASSWORD_CHANGED = "auth.password_changed";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final RevokedTokenRepository revokedTokens;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String decoyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens,
                       RevokedTokenRepository revokedTokens, AuditService audit,
                       TransactionTemplate transactions, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.revokedTokens = revokedTokens;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(String email, String password, HttpServletRequest request) {
        Optional<LoginResponse> response = transactions.execute(status -> attemptLogin(email, password, request));
        return response.orElseThrow(AuthService::invalidCredentials);
    }

    private Optional<LoginResponse> attemptLogin(String rawEmail, String password, HttpServletRequest request) {
        Instant now = clock.instant();
        Optional<UserAccount> found = users.findByEmail(normalize(rawEmail));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, decoyHash);
            audit.record(LOGIN_FAILED, null, USER, null, AuditOutcome.DENIED, request,
                    Map.of("reason", "unknown_account"));
            return Optional.empty();
        }
        UserAccount account = found.get();
        String targetId = account.getId().toString();
        account.releaseExpiredLock(now);
        if (account.isLockedAt(now)) {
            passwordEncoder.matches(password, decoyHash);
            audit.record(LOGIN_FAILED, account.getId(), USER, targetId, AuditOutcome.DENIED, request,
                    Map.of("reason", "locked"));
            return Optional.empty();
        }
        if (account.getStatus() != UserStatus.ACTIVE) {
            passwordEncoder.matches(password, decoyHash);
            audit.record(LOGIN_FAILED, account.getId(), USER, targetId, AuditOutcome.DENIED, request,
                    Map.of("reason", "deactivated"));
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            if (account.recordFailedLogin(now, MAX_FAILED_ATTEMPTS, LOCKOUT)) {
                audit.record(ACCOUNT_LOCKED, account.getId(), USER, targetId, AuditOutcome.DENIED, request,
                        Map.of("reason", "too_many_failures"));
            }
            audit.record(LOGIN_FAILED, account.getId(), USER, targetId, AuditOutcome.DENIED, request,
                    Map.of("reason", "bad_password"));
            return Optional.empty();
        }
        account.recordSuccessfulLogin();
        JwtTokenService.IssuedToken token = tokens.issue(account.getId(), account.getRole());
        audit.record(LOGIN_SUCCEEDED, account.getId(), USER, targetId, AuditOutcome.SUCCESS, request, Map.of());
        return Optional.of(new LoginResponse(token.value(), "Bearer",
                tokens.accessTtl().toSeconds(), UserSummary.from(account)));
    }

    @Transactional(readOnly = true)
    public UserSummary currentUser(UUID userId) {
        return users.findById(userId)
                .map(UserSummary::from)
                .orElseThrow(AuthService::unauthenticated);
    }

    /** Revokes the presented token by its ID until it would have expired (ADR-019). */
    @Transactional
    public void logout(AuthenticatedUser caller, HttpServletRequest request) {
        revokedTokens.save(new RevokedToken(caller.jti(), caller.userId(), caller.expiresAt(), clock.instant()));
        audit.record(LOGOUT, caller.userId(), USER, caller.userId().toString(), AuditOutcome.SUCCESS, request, Map.of());
    }

    /**
     * Checks the current password, then the new one (API_CONTRACT §2). Tokens issued at or before
     * the change are rejected from now on, so this also signs out every other session.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, HttpServletRequest request) {
        UserAccount account = users.findById(userId).orElseThrow(AuthService::unauthenticated);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "The current password is incorrect.");
        }
        List<String> reasons = PasswordPolicy.violations(newPassword);
        if (reasons.isEmpty() && passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            reasons = List.of("must differ from the current password");
        }
        if (!reasons.isEmpty()) {
            throw ApiException.withErrors(ErrorCode.VALIDATION_FAILED,
                    "The new password does not meet the password rules.",
                    reasons.stream().map(reason -> Map.of("field", "newPassword", "reason", reason)).toList());
        }
        Instant now = clock.instant();
        account.changePassword(passwordEncoder.encode(newPassword), false, now.truncatedTo(ChronoUnit.SECONDS), now);
        audit.record(PASSWORD_CHANGED, userId, USER, userId.toString(), AuditOutcome.SUCCESS, request, Map.of());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "The email or password is incorrect.");
    }

    private static ApiException unauthenticated() {
        return new ApiException(ErrorCode.UNAUTHENTICATED, "Authentication is required.");
    }
}
