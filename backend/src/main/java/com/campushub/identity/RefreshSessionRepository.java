package com.campushub.identity;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {

    /** Locks the row so two concurrent refreshes with the same token cannot both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshSession r where r.tokenHash = :hash")
    Optional<RefreshSession> findForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshSession r set r.revokedAt = :now where r.familyId = :family and r.revokedAt is null")
    int revokeFamily(@Param("family") String family, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshSession r set r.revokedAt = :now where r.userId = :user and r.revokedAt is null")
    int revokeAllForUser(@Param("user") UUID user, @Param("now") Instant now);

    /** Scheduled purge of sessions that have expired anyway (OPERATIONS §5). */
    @Modifying
    @Query("delete from RefreshSession r where r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
