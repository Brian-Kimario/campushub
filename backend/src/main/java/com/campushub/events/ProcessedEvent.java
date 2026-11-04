package com.campushub.events;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Marker that a consumer has applied an event (V16). Written in the same transaction as the effect. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventKey key;

    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(ProcessedEventKey key, Instant processedAt) {
        this.key = key;
        this.processedAt = processedAt;
    }
}
