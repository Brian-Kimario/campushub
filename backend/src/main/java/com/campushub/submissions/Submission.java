package com.campushub.submissions;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One revision of a project's submission (DATA_MODEL §7.1, §7.2 and §7.4 merged, reduced; see IMPLEMENTATION_LOG §11). */
@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    private int revisionNo;

    private String title;

    private String description;

    private String link;

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID submittedBy;

    private Instant submittedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID reviewedBy;

    private Instant reviewedAt;

    private String feedback;

    @Version
    private int version;

    protected Submission() {
    }

    public Submission(UUID id, UUID projectId, int revisionNo, String title, String description, String link,
                      UUID submittedBy, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.revisionNo = revisionNo;
        this.title = title;
        this.description = description;
        this.link = link;
        this.status = SubmissionStatus.SUBMITTED;
        this.submittedBy = submittedBy;
        this.submittedAt = now;
    }

    /** SUBMITTED → UNDER_REVIEW. The reviewer is recorded now so the row's review CHECK holds. */
    public void startReview(UUID reviewerId) {
        this.status = SubmissionStatus.UNDER_REVIEW;
        this.reviewedBy = reviewerId;
    }

    /** UNDER_REVIEW → ACCEPTED or CHANGES_REQUESTED. The deciding supervisor is recorded, not the one who started. */
    public void decide(ReviewDecision decision, UUID reviewerId, String reviewFeedback, Instant now) {
        this.status = decision.status();
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.feedback = reviewFeedback;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getRevisionNo() {
        return revisionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLink() {
        return link;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getFeedback() {
        return feedback;
    }

    public int getVersion() {
        return version;
    }
}
