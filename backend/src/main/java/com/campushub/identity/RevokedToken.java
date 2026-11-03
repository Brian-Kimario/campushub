package com.campushub.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A logged-out access token, kept until it would have expired anyway (DATA_MODEL §4, ADR-019). */
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private String jti;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    private Instant expiresAt;

    private Instant revokedAt;

    protected RevokedToken() {
    }

    public RevokedToken(String jti, UUID userId, Instant expiresAt, Instant revokedAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }
}
