package com.campushub.certifications;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    private LocalDate expiresOn;

    private String credentialId;

    private String credentialUrl;

    private Instant createdAt;

    @Version
    private int version;

    protected Certification() {
    }

    public Certification(UUID id, UUID ownerId, String title, String issuer, LocalDate issuedOn,
                         LocalDate expiresOn, String credentialId, String credentialUrl, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.issuer = issuer;
        this.issuedOn = issuedOn;
        this.expiresOn = expiresOn;
        this.credentialId = credentialId;
        this.credentialUrl = credentialUrl;
        this.createdAt = createdAt;
    }

    /** Replaces the editable fields. The owner, id and creation time never change. */
    public void update(String title, String issuer, LocalDate issuedOn, LocalDate expiresOn,
                       String credentialId, String credentialUrl) {
        this.title = title;
        this.issuer = issuer;
        this.issuedOn = issuedOn;
        this.expiresOn = expiresOn;
        this.credentialId = credentialId;
        this.credentialUrl = credentialUrl;
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

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public String getCredentialUrl() {
        return credentialUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
