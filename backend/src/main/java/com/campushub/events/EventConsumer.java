package com.campushub.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Listens on the events topic. Failures go through the error handler in {@link KafkaConfig}. */
@Component
@ConditionalOnProperty(name = "campushub.kafka.enabled", havingValue = "true")
public class EventConsumer {

    private final EventHandler handler;

    public EventConsumer(EventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = EventTopics.EVENTS, groupId = "${campushub.kafka.consumer-group:campushub}")
    public void onEvent(String message) throws JsonProcessingException {
        handler.handle(message);
    }
}
