package com.campushub.events;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Composite key of processed_events: one row per (event, consumer). */
@Embeddable
public class ProcessedEventKey implements Serializable {

    @Column(name = "event_id")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID eventId;

    private String consumer;

    protected ProcessedEventKey() {
    }

    public ProcessedEventKey(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessedEventKey key
                && Objects.equals(eventId, key.eventId) && Objects.equals(consumer, key.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
