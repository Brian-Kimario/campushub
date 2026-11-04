-- V16__outbox_and_processed_events.sql
-- Transactional outbox and idempotent consumers for the optional Kafka extension (ADR-011, FR-EVT-01 to 04).
--   * outbox_events rows are written in the same transaction as the business change that caused them, and
--     only when campushub.kafka.enabled is true. A rolled-back change leaves no event behind.
--   * The relay publishes unpublished rows and stamps published_at. Delivery is at least once, so
--     consumers must be idempotent.
--   * processed_events records each (event, consumer) pair once. The consumer inserts this marker in the same
--     transaction as its effect, so a redelivered event is detected and skipped.
--   * The payload is a small JSON document held in VARCHAR. It carries IDs and type only, never free text.
-- Rollback note: DROP TABLE processed_events; DROP TABLE outbox_events; (development only).
CREATE TABLE outbox_events (
    id              BINARY(16)    NOT NULL,
    aggregate_type  VARCHAR(40)   NOT NULL,
    aggregate_id    VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(60)   NOT NULL,
    payload         VARCHAR(2000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    published_at    DATETIME(6)   NULL,
    publish_attempts INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_type CHECK (event_type IN (
        'invitation.received', 'invitation.accepted', 'invitation.declined',
        'member.removed', 'submission.reviewed'))
);

CREATE INDEX ix_outbox_events_unpublished ON outbox_events (published_at, created_at, id);

CREATE TABLE processed_events (
    event_id      BINARY(16)   NOT NULL,
    consumer      VARCHAR(80)  NOT NULL,
    processed_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer)
);
