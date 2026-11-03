package com.campushub.tasks;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Task row (DATA_MODEL §6.2, reduced for the MVP; see IMPLEMENTATION_LOG §11). */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    private String blockedReason;

    private LocalDate dueDate;

    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private int version;

    protected Task() {
    }

    public Task(UUID id, UUID projectId, UUID createdBy, String title, String description,
                TaskPriority priority, LocalDate dueDate, Instant now) {
        this.id = id;
        this.projectId = projectId;
        this.createdBy = createdBy;
        this.status = TaskStatus.TODO;
        this.createdAt = now;
        this.updatedAt = now;
        updateDetails(title, description, priority, dueDate, now);
    }

    public void updateDetails(String title, String description, TaskPriority priority, LocalDate dueDate,
                              Instant now) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    /** Sets the status and keeps the dependent columns consistent with the table's CHECK constraints. */
    public void moveTo(TaskStatus next, String reason, Instant now) {
        this.status = next;
        this.blockedReason = next == TaskStatus.BLOCKED ? reason : null;
        this.completedAt = next == TaskStatus.DONE ? now : null;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
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
