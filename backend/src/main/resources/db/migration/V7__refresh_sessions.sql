-- V7__refresh_sessions.sql
-- Refresh sessions for the HttpOnly cookie session (ADR-025, which supersedes the access-token-only
-- MVP decision in ADR-004). Only a SHA-256 hash of each refresh token is stored. A row is marked used
-- when it is rotated; presenting a used row revokes the whole family (reuse detection).
-- Rollback note (development only): DROP TABLE refresh_sessions;
CREATE TABLE refresh_sessions (
    token_hash CHAR(64)    NOT NULL,
    family_id  CHAR(36)    NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    issued_at  DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT pk_refresh_sessions PRIMARY KEY (token_hash),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX ix_refresh_family ON refresh_sessions (family_id);
CREATE INDEX ix_refresh_user ON refresh_sessions (user_id);
CREATE INDEX ix_refresh_expires ON refresh_sessions (expires_at);
