package com.campushub.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One issued refresh token, stored as a hash (ADR-025). Rotation marks the row used and issues a
 * successor in the same family. The family ends at the original {@code expiresAt}.
 */
@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    private String familyId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    private Instant issuedAt;

    private Instant expiresAt;

    private Instant usedAt;

    private Instant revokedAt;

    protected RefreshSession() {
    }

    public RefreshSession(String tokenHash, String familyId, UUID userId, Instant issuedAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
