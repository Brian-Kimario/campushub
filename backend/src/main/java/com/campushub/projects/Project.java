package com.campushub.projects;

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

/** Project row (DATA_MODEL §5.1, reduced for the MVP; see IMPLEMENTATION_LOG §11). */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String title;

    private String description;

    private String subject;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID ownerId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID supervisorId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID termId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    private LocalDate startDate;

    private LocalDate dueDate;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private int version;

    protected Project() {
    }

    public Project(UUID id, UUID ownerId, String title, String description, String subject,
                   LocalDate startDate, LocalDate dueDate, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.status = ProjectStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        updateDetails(title, description, subject, startDate, dueDate, now);
    }

    public void updateDetails(String title, String description, String subject,
                              LocalDate startDate, LocalDate dueDate, Instant now) {
        this.title = title;
        this.description = description;
        this.subject = subject;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    public void assignSupervisor(UUID supervisorId, Instant now) {
        this.supervisorId = supervisorId;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = ProjectStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public boolean isArchived() {
        return status == ProjectStatus.ARCHIVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return userId.equals(ownerId);
    }

    public boolean isSupervisedBy(UUID userId) {
        return userId.equals(supervisorId);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSubject() {
        return subject;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getSupervisorId() {
        return supervisorId;
    }

    public UUID getTermId() {
        return termId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
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
