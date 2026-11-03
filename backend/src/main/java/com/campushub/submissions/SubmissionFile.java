package com.campushub.submissions;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Evidence attached to one submission revision (DATA_MODEL §7.3, reduced). Rows are written once and never changed. */
@Entity
@Table(name = "submission_files")
public class SubmissionFile {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID submissionId;

    private String originalName;

    private String contentType;

    private long sizeBytes;

    private String sha256;

    private String storageKey;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID uploadedBy;

    private Instant uploadedAt;

    protected SubmissionFile() {
    }

    public SubmissionFile(UUID id, UUID submissionId, String originalName, String contentType, long sizeBytes,
                          String sha256, String storageKey, UUID uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubmissionId() {
        return submissionId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
