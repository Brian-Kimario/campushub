package com.campushub.events;

/** Topic names for the optional Kafka extension (ADR-011). */
public final class EventTopics {

    public static final String EVENTS = "campushub.events";
    public static final String DEAD_LETTER = "campushub.events.DLT";

    private EventTopics() {
    }
}
