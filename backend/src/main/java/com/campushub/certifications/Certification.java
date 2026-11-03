package com.campushub.certifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A student's own certification (DATA_MODEL §8). */
@Entity
@Table(name = "certifications")
public class Certification {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID ownerId;

    private String title;

    private String issuer;

    private LocalDate issuedOn;

    private String credentialUrl;

    private Instant createdAt;

    protected Certification() {
    }

    public Certification(UUID id, UUID ownerId, String title, String issuer, LocalDate issuedOn,
                         String credentialUrl, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.issuer = issuer;
        this.issuedOn = issuedOn;
        this.credentialUrl = credentialUrl;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getIssuer() {
        return issuer;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public String getCredentialUrl() {
        return credentialUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
