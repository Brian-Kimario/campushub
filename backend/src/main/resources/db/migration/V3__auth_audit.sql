-- V3__auth_audit.sql
-- Phase 2 authentication revocation and append-only audit (DATA_MODEL §4, §11; ADR-019, ADR-023).
-- Rollback note: DROP TABLE audit_events; DROP TABLE revoked_tokens; ALTER TABLE users DROP COLUMN revoked_after;
-- (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE revoked_tokens (
    jti        CHAR(36)    NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_revoked_tokens PRIMARY KEY (jti),
    CONSTRAINT fk_revoked_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_revoked_expires ON revoked_tokens (expires_at);

CREATE TABLE audit_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6)  NOT NULL,
    actor_id    BINARY(16)   NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(40)  NULL,
    target_id   VARCHAR(36)  NULL,
    outcome     VARCHAR(16)  NOT NULL,
    request_id  VARCHAR(36)  NULL,
    ip_hash     CHAR(64)     NULL,
    metadata    JSON         NULL,
    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'DENIED')),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_audit_occurred ON audit_events (occurred_at);
CREATE INDEX ix_audit_actor ON audit_events (actor_id, occurred_at);
CREATE INDEX ix_audit_target ON audit_events (target_type, target_id);
CREATE INDEX ix_audit_action ON audit_events (action, occurred_at);

ALTER TABLE users ADD COLUMN revoked_after DATETIME(6) NULL;
