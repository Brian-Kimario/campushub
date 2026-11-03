package com.campushub.events;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What a consumer does with an event. CampusHub has no external downstream, so the effect is a metric and a
 * structured log line. The processed_events marker, written by {@link EventHandler}, is the durable part.
 */
@Component
public class EventEffect {

    private static final Logger log = LoggerFactory.getLogger(EventEffect.class);

    private final MeterRegistry metrics;

    public EventEffect(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    public void apply(UUID eventId, String type, JsonNode payload) {
        metrics.counter("campushub.events.processed", "type", type).increment();
        log.info("event_applied eventId={} type={} aggregateId={}", eventId, type,
                payload.path("projectId").asText(""));
    }
}
