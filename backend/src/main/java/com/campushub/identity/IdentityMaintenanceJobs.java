package com.campushub.identity;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Scheduled identity housekeeping (OPERATIONS §5). Runs in UTC. Single-instance only (risk R-02). */
@Component
public class IdentityMaintenanceJobs {

    private final RevokedTokenRepository revokedTokens;
    private final RefreshSessionRepository refreshSessions;
    private final UserAccountRepository users;
    private final Clock clock;

    public IdentityMaintenanceJobs(RevokedTokenRepository revokedTokens, RefreshSessionRepository refreshSessions,
                                   UserAccountRepository users, Clock clock) {
        this.revokedTokens = revokedTokens;
        this.refreshSessions = refreshSessions;
        this.users = users;
        this.clock = clock;
    }

    /** Drops revocation rows for tokens that have expired anyway. */
    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredRevokedTokens() {
        revokedTokens.deleteExpired(clock.instant());
    }

    /** Drops refresh sessions past their absolute end (ADR-025). */
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredRefreshSessions() {
        refreshSessions.deleteExpired(clock.instant());
    }

    /** Clears lockout windows that have ended. */
    @Scheduled(cron = "0 25 * * * *", zone = "UTC")
    @Transactional
    public void clearExpiredLocks() {
        users.clearExpiredLocks(clock.instant());
    }
}
