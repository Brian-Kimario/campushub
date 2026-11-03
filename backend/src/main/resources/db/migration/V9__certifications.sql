-- V9__certifications.sql
-- Owner-private certifications (DATA_MODEL §8, FR-SKILL-03). Evidence files are not part of this
-- migration: certification_files waits for the storage layer.
--   * credential_url is stored as text only. The server never fetches it (SECURITY.md, SSRF row).
--   * Every row belongs to one student. Administrators and faculty never read these (ADR-007, OD-5).
-- Rollback note: DROP TABLE certifications; (development only). Production rollback is a restore
-- (OPERATIONS §6).
CREATE TABLE certifications (
    id              BINARY(16)    NOT NULL,
    owner_id        BINARY(16)    NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    issuer          VARCHAR(200)  NOT NULL,
    issued_on       DATE          NOT NULL,
    credential_url  VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_certifications PRIMARY KEY (id),
    CONSTRAINT fk_certifications_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_certifications_title CHECK (CHAR_LENGTH(title) BETWEEN 1 AND 200),
    CONSTRAINT ck_certifications_issuer CHECK (CHAR_LENGTH(issuer) BETWEEN 1 AND 200),
    CONSTRAINT ck_certifications_url CHECK (credential_url IS NULL OR credential_url LIKE 'https://%')
);

CREATE INDEX ix_certs_owner ON certifications (owner_id, issued_on);
