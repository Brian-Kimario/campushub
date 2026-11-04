-- V13__milestones.sql
-- Milestones per project (DATA_MODEL §6.1, reduced). The owner creates and edits title and due
-- date; the supervisor edits allow_late; either may close (STATE_MACHINES §4). Closing is
-- irreversible in the MVP. Sequence is assigned by the service under the project row lock.
-- Rollback note: DROP TABLE milestones; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE milestones (
    id          BINARY(16)    NOT NULL,
    project_id  BINARY(16)    NOT NULL,
    sequence    INT           NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    due_at      DATETIME(6)   NOT NULL,
    allow_late  BOOLEAN       NOT NULL DEFAULT FALSE,
    status      VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    version     INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_milestones PRIMARY KEY (id),
    CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT uq_milestones_project_sequence UNIQUE (project_id, sequence),
    CONSTRAINT ck_milestones_title CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 200),
    CONSTRAINT ck_milestones_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_milestones_sequence CHECK (sequence >= 1)
);

CREATE INDEX ix_milestones_project_due ON milestones (project_id, due_at);
CREATE INDEX ix_milestones_due ON milestones (due_at);
