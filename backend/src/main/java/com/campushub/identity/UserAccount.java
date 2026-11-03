package com.campushub.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Account row (DATA_MODEL §3.1). Profile fields beyond identity arrive with their features. */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String email;

    private String institutionalId;

    private String passwordHash;

    private String displayName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private boolean mustChangePassword;

    private int failedLoginCount;

    private Instant lockedUntil;

    private Instant revokedAfter;

    @Column(name = "department_id")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID departmentId;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private int version;

    protected UserAccount() {
    }

    public UserAccount(UUID id, String email, String displayName, String passwordHash, Role role,
                       boolean mustChangePassword, Instant now) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Clears an expired lock and its failure count. Returns true when a lock was cleared. */
    public boolean releaseExpiredLock(Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            lockedUntil = null;
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Counts one failed sign-in and locks the account at {@code maxAttempts}. Callers must not
     * call this while the account is already locked, so attempts during a lock never extend it (ADR-020).
     *
     * @return true when this failure locked the account
     */
    public boolean recordFailedLogin(Instant now, int maxAttempts, Duration lockout) {
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockout);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void recordSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    /** Sets the new hash, clears the forced-change flag, and revokes tokens issued at or before {@code revokedAfter}. */
    public void changePassword(String newHash, boolean mustChange, Instant revokedAfter, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
        this.revokedAfter = revokedAfter;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getInstitutionalId() {
        return institutionalId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getRevokedAfter() {
        return revokedAfter;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }
}
