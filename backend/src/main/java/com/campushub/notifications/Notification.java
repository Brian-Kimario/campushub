package com.campushub.notifications;

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

/** One notification for one recipient (V15). Read state is the only field that changes after creation. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID recipientId;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID projectId;

    private Instant createdAt;

    private Instant readAt;

    @Version
    private int version;

    protected Notification() {
    }

    public Notification(UUID id, UUID recipientId, UUID actorId, NotificationType type, UUID projectId, Instant createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.type = type;
        this.projectId = projectId;
        this.createdAt = createdAt;
    }

    /** Sets read_at once. A second call keeps the first time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public NotificationType getType() {
        return type;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public int getVersion() {
        return version;
    }
}
