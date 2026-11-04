package com.campushub.milestones;

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

/** Milestone row (DATA_MODEL §6.1). Sequence is fixed at creation and orders the project's milestones. */
@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    private int sequence;

    private String title;

    private Instant dueAt;

    private boolean allowLate;

    @Enumerated(EnumType.STRING)
    private MilestoneStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private int version;

    protected Milestone() {
    }

    public Milestone(UUID id, UUID projectId, int sequence, String title, Instant dueAt, boolean allowLate,
                     Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.sequence = sequence;
        this.title = title;
        this.dueAt = dueAt;
        this.allowLate = allowLate;
        this.status = MilestoneStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Owner edit: title and due date. Null leaves a field as it is. */
    public void updateOwnerFields(String title, Instant dueAt, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (dueAt != null) {
            this.dueAt = dueAt;
        }
        this.updatedAt = now;
    }

    /** Supervisor edit: late-submission policy only. */
    public void updateAllowLate(boolean allowLate, Instant now) {
        this.allowLate = allowLate;
        this.updatedAt = now;
    }

    public void close(Instant now) {
        this.status = MilestoneStatus.CLOSED;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status == MilestoneStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getSequence() {
        return sequence;
    }

    public String getTitle() {
        return title;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public boolean isAllowLate() {
        return allowLate;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }
}
