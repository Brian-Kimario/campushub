-- V10__certification_edit.sql
-- Certification editing (API_CONTRACT §11, Phase 9.1). V9 is already applied, so the new columns
-- arrive in a new migration instead of an edit to V9.
--   * expires_on is optional. When present it cannot come before issued_on.
--   * credential_id is the issuer's identifier for the credential. It is stored as text only.
--   * version is the optimistic-lock counter. Existing rows start at 0.
-- Rollback note: ALTER TABLE certifications DROP COLUMN version, DROP COLUMN credential_id,
-- DROP COLUMN expires_on (development only). Production rollback is a restore (OPERATIONS §6).
ALTER TABLE certifications
    ADD COLUMN expires_on DATE NULL,
    ADD COLUMN credential_id VARCHAR(100) NULL,
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_certifications_expiry CHECK (expires_on IS NULL OR expires_on >= issued_on),
    ADD CONSTRAINT ck_certifications_credential_id CHECK (credential_id IS NULL OR CHAR_LENGTH(credential_id) BETWEEN 1 AND 100);
