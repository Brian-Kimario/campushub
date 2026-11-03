package com.campushub.identity.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One append-only audit row (DATA_MODEL §11, ADR-023). Rows are only ever inserted. Metadata holds
 * allowlisted keys, and the client IP is stored only as a keyed hash.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID actorId;

    private String action;

    private String targetType;

    private String targetId;

    @Enumerated(EnumType.STRING)
    private AuditOutcome outcome;

    private String requestId;

    @JdbcTypeCode(SqlTypes.CHAR)
    private String ipHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    protected AuditEvent() {
    }

    public AuditEvent(Instant occurredAt, UUID actorId, String action, String targetType, String targetId,
                      AuditOutcome outcome, String requestId, String ipHash, Map<String, Object> metadata) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.outcome = outcome;
        this.requestId = requestId;
        this.ipHash = ipHash;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }
}
