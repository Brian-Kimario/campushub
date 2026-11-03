package com.campushub.projects;

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

/** An invitation to join a project's team (V11, STATE_MACHINES §6). */
@Entity
@Table(name = "project_invitations")
public class ProjectInvitation {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID inviteeId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID inviterId;

    @Enumerated(EnumType.STRING)
    private InvitationStatus status;

    private Instant expiresAt;

    private Instant createdAt;

    private Instant respondedAt;

    @Version
    private int version;

    protected ProjectInvitation() {
    }

    public ProjectInvitation(UUID id, UUID projectId, UUID inviteeId, UUID inviterId, Instant now, Instant expiresAt) {
        this.id = id;
        this.projectId = projectId;
        this.inviteeId = inviteeId;
        this.inviterId = inviterId;
        this.status = InvitationStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    /** The stored status, with PENDING reported as EXPIRED once the deadline has passed. */
    public InvitationStatus effectiveStatus(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isBefore(now) ? InvitationStatus.EXPIRED : status;
    }

    public void accept(Instant now) {
        this.status = InvitationStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void decline(Instant now) {
        this.status = InvitationStatus.DECLINED;
        this.respondedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getInviteeId() {
        return inviteeId;
    }

    public UUID getInviterId() {
        return inviterId;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public int getVersion() {
        return version;
    }
}
