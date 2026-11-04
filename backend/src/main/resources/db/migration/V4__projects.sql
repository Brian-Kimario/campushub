-- V4__projects.sql
-- MVP project slice (DATA_MODEL §5.1, reduced). Deviations, recorded in IMPLEMENTATION_LOG §11:
--   * no term_id / course_id (academic_terms and courses are not built yet);
--   * no project_members or invitations: the owner is the only student who acts on a project;
--   * status is ACTIVE or ARCHIVED only; the proposal and approval states are not in the MVP;
--   * supervisor_id is a single column, not a membership row.
-- Rollback note: DROP TABLE projects; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE projects (
    id            BINARY(16)    NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   TEXT          NULL,
    subject       VARCHAR(120)  NULL,
    owner_id      BINARY(16)    NOT NULL,
    supervisor_id BINARY(16)    NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    start_date    DATE          NULL,
    due_date      DATE          NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    version       INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_projects_supervisor FOREIGN KEY (supervisor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_projects_title CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 200),
    CONSTRAINT ck_projects_description CHECK (description IS NULL OR CHAR_LENGTH(description) <= 5000),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_projects_dates CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date)
);

CREATE INDEX ix_projects_owner_status ON projects (owner_id, status);
CREATE INDEX ix_projects_supervisor_status ON projects (supervisor_id, status);
