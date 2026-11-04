package com.campushub.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One pending or published event (V16). Only publishing state changes after creation. */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;

    private String aggregateType;

    private String aggregateId;

    private String eventType;

    @Column(length = 2000)
    private String payload;

    private Instant createdAt;

    private Instant publishedAt;

    private int publishAttempts;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType, String payload,
                       Instant createdAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant now) {
        this.publishedAt = now;
    }

    public void recordFailedAttempt() {
        this.publishAttempts++;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }
}
