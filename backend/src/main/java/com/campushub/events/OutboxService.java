package com.campushub.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes outbox rows in the caller's transaction (ADR-011). When campushub.kafka.enabled is false this is a
 * no-op, so the core workflows write nothing extra and need no broker.
 */
@Service
public class OutboxService {

    private final OutboxEventRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean enabled;

    public OutboxService(OutboxEventRepository outbox, ObjectMapper json, Clock clock,
                         @Value("${campushub.kafka.enabled:false}") boolean enabled) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
    }

    /**
     * Records one event. The payload carries IDs and the event type only. The outbox row ID doubles as the
     * event ID that consumers use for deduplication.
     */
    @Transactional
    public void record(DomainEventType type, String aggregateType, String aggregateId, Map<String, Object> fields) {
        if (!enabled) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>(fields);
        payload.put("eventId", eventId.toString());
        payload.put("type", type.wireName());
        outbox.saveAndFlush(new OutboxEvent(eventId, aggregateType, aggregateId, type.wireName(), toJson(payload),
                clock.instant()));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Outbox payload could not be serialized", ex);
        }
    }
}
