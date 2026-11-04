-- V6__submissions.sql
-- MVP submission and review slice (DATA_MODEL §7.1, §7.2, §7.4, reduced). Deviations, recorded in
-- IMPLEMENTATION_LOG §11 (Stage 3):
--   * one row per revision, attached to the project directly (no milestones yet), so a revision
--     is the unit that faculty review;
--   * no DRAFT state: the revision is sealed when it is created, because the content is one link;
--   * the review decision is stored on the revision row rather than in a separate reviews table
--     (one decision per revision is then enforced by the row itself);
--   * no files: the MVP accepts a link (repository or document, https only).
-- Rollback note: DROP TABLE submissions; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE submissions (
    id            BINARY(16)    NOT NULL,
    project_id    BINARY(16)    NOT NULL,
    revision_no   INT           NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   TEXT          NULL,
    link          VARCHAR(500)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    submitted_by  BINARY(16)    NOT NULL,
    submitted_at  DATETIME(6)   NOT NULL,
    reviewed_by   BINARY(16)    NULL,
    reviewed_at   DATETIME(6)   NULL,
    feedback      TEXT          NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_submissions PRIMARY KEY (id),
    CONSTRAINT uk_submissions_revision UNIQUE (project_id, revision_no),
    CONSTRAINT fk_submissions_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_submissions_submitted_by FOREIGN KEY (submitted_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_submissions_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_submissions_revision CHECK (revision_no >= 1),
    CONSTRAINT ck_submissions_title CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 200),
    CONSTRAINT ck_submissions_link CHECK (link LIKE 'https://%'),
    CONSTRAINT ck_submissions_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'CHANGES_REQUESTED', 'ACCEPTED')),
    CONSTRAINT ck_submissions_review CHECK (
        (status = 'SUBMITTED' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status = 'UNDER_REVIEW' AND reviewed_by IS NOT NULL AND reviewed_at IS NULL)
        OR (status IN ('CHANGES_REQUESTED', 'ACCEPTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_submissions_feedback CHECK (status <> 'CHANGES_REQUESTED' OR feedback IS NOT NULL)
);

CREATE INDEX ix_submissions_project_revision ON submissions (project_id, revision_no);
