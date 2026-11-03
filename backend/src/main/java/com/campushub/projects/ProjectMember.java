package com.campushub.projects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A team member on a project (V11). The owner is not stored here; see {@link Project#isOwnedBy}. */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;

    private String role;

    private Instant joinedAt;

    protected ProjectMember() {
    }

    public ProjectMember(UUID id, UUID projectId, UUID userId, Instant joinedAt) {
        this.id = id;
        this.projectId = projectId;
        this.userId = userId;
        this.role = "MEMBER";
        this.joinedAt = joinedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
