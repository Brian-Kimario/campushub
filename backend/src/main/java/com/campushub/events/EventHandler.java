package com.campushub.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies one event exactly once per consumer (FR-EVT-03). The marker and the effect commit together. A
 * redelivered event finds its marker and is skipped. A marker insert that loses a race throws, the whole
 * transaction rolls back, and the error handler retries.
 */
@Component
@ConditionalOnProperty(name = "campushub.kafka.enabled", havingValue = "true")
public class EventHandler {

    static final String CONSUMER = "campushub-events";

    private final ProcessedEventRepository processed;
    private final EventEffect effect;
    private final ObjectMapper json;
    private final Clock clock;
    private final MeterRegistry metrics;

    public EventHandler(ProcessedEventRepository processed, EventEffect effect, ObjectMapper json, Clock clock,
                        MeterRegistry metrics) {
        this.processed = processed;
        this.effect = effect;
        this.json = json;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional
    public void handle(String message) throws JsonProcessingException {
        JsonNode payload = json.readTree(message);
        UUID eventId = UUID.fromString(payload.path("eventId").asText());
        String type = payload.path("type").asText();
        ProcessedEventKey key = new ProcessedEventKey(eventId, CONSUMER);
        if (processed.existsById(key)) {
            metrics.counter("campushub.events.duplicates", "type", type).increment();
            return;
        }
        processed.saveAndFlush(new ProcessedEvent(key, clock.instant()));
        effect.apply(eventId, type, payload);
    }
}
