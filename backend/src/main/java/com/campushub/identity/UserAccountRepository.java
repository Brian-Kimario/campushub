package com.campushub.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findByRoleAndStatusOrderByDisplayNameAsc(Role role, UserStatus status);

    boolean existsByEmail(String email);

    boolean existsByInstitutionalId(String institutionalId);

    long countByRoleAndStatus(Role role, UserStatus status);

    /** Admin directory search (API_CONTRACT §4). {@code q} is a lowercase prefix of email or display name. */
    @Query("select u from UserAccount u where (:role is null or u.role = :role)"
            + " and (:status is null or u.status = :status)"
            + " and (:q is null or lower(u.email) like concat(:q, '%') or lower(u.displayName) like concat(:q, '%'))")
    Page<UserAccount> search(@Param("role") Role role, @Param("status") UserStatus status,
                             @Param("q") String q, Pageable pageable);

    /** Scheduled reset of expired lockout windows (OPERATIONS §5, minute 25 UTC). */
    @Modifying
    @Query("update UserAccount u set u.lockedUntil = null, u.failedLoginCount = 0 where u.lockedUntil < :now")
    int clearExpiredLocks(@Param("now") Instant now);
}
