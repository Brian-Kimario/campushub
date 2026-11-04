-- V14__submission_files.sql
-- Evidence files attached to a submission revision (DATA_MODEL §7.3, reduced). The bytes live on the
-- storage volume under a random key. The row records the type found in the leading bytes, never the
-- declared content type. Deviations, recorded in CHANGELOG (Phase E.3):
--   * files attach while the revision is SUBMITTED, before review starts, because the MVP revision is
--     sealed on creation and has no DRAFT state;
--   * there is no delete endpoint, and rows are never updated.
-- Rollback note: DROP TABLE submission_files; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE submission_files (
    id             BINARY(16)    NOT NULL,
    submission_id  BINARY(16)    NOT NULL,
    original_name  VARCHAR(255)  NOT NULL,
    content_type   VARCHAR(100)  NOT NULL,
    size_bytes     BIGINT        NOT NULL,
    sha256         VARCHAR(64)   NOT NULL,
    storage_key    VARCHAR(36)   NOT NULL,
    uploaded_by    BINARY(16)    NOT NULL,
    uploaded_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_submission_files PRIMARY KEY (id),
    CONSTRAINT uk_submission_files_key UNIQUE (storage_key),
    CONSTRAINT fk_submission_files_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_submission_files_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_submission_files_size CHECK (size_bytes >= 1),
    CONSTRAINT ck_submission_files_type CHECK (content_type IN ('application/pdf', 'image/png', 'image/jpeg'))
);

CREATE INDEX ix_submission_files_submission ON submission_files (submission_id, uploaded_at);
