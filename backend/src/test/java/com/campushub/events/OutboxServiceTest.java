package com.campushub.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Disabled-by-default behaviour (ADR-011): with the flag off, nothing is written to the outbox. */
class OutboxServiceTest {

    private static final String PROJECT = "project";

    @Test
    void recordWritesNothingWhenKafkaIsDisabled() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxService service = new OutboxService(repository, new ObjectMapper(), Clock.systemUTC(), false);

        service.record(DomainEventType.MEMBER_REMOVED, PROJECT, UUID.randomUUID().toString(), Map.of("k", "v"));

        verifyNoInteractions(repository);
    }

    @Test
    void recordWritesTypedPayloadWithEventIdWhenKafkaIsEnabled() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxService service = new OutboxService(repository, new ObjectMapper(), Clock.systemUTC(), true);

        service.record(DomainEventType.MEMBER_REMOVED, PROJECT, "p-1", Map.of("projectId", "p-1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("member.removed");
        assertThat(saved.getAggregateId()).isEqualTo("p-1");
        assertThat(saved.getPayload()).contains("\"type\":\"member.removed\"").contains("\"eventId\"");
        assertThat(saved.getPublishedAt()).isNull();
    }
}
