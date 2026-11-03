package com.campushub.identity.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One audit row as the administrator sees it (API_CONTRACT §13). The hashed client address is left out
 * on purpose: it is kept for correlation, not for display.
 */
public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        UUID actorId,
        String action,
        String targetType,
        String targetId,
        AuditOutcome outcome,
        String requestId,
        Map<String, Object> metadata) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getActorId(), event.getAction(),
                event.getTargetType(), event.getTargetId(), event.getOutcome(), event.getRequestId(),
                event.getMetadata());
    }
}
