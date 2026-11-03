package com.campushub.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    /** Scheduled reset of expired lockout windows (OPERATIONS §5, minute 25 UTC). */
    @Modifying
    @Query("update UserAccount u set u.lockedUntil = null, u.failedLoginCount = 0 where u.lockedUntil < :now")
    int clearExpiredLocks(@Param("now") Instant now);
}
