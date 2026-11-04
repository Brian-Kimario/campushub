-- V5__tasks.sql
-- MVP task slice (DATA_MODEL §6.2, reduced). Deviations, recorded in IMPLEMENTATION_LOG §11:
--   * no milestone_id and no assignee_id: the project owner is the only actor, so every task
--     transition in STATE_MACHINES §3.2 is performed by the owner (the "assignee" role is absent);
--   * completed_at is kept as a column so that DONE carries its completion time (T6).
-- Rollback note: DROP TABLE tasks; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE tasks (
    id             BINARY(16)    NOT NULL,
    project_id     BINARY(16)    NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    description    TEXT          NULL,
    status         VARCHAR(16)   NOT NULL,
    priority       VARCHAR(8)    NOT NULL DEFAULT 'MEDIUM',
    blocked_reason VARCHAR(500)  NULL,
    due_date       DATE          NULL,
    completed_at   DATETIME(6)   NULL,
    created_by     BINARY(16)    NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    version        INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_tasks_title CHECK (CHAR_LENGTH(title) BETWEEN 3 AND 200),
    CONSTRAINT ck_tasks_description CHECK (description IS NULL OR CHAR_LENGTH(description) <= 5000),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'BLOCKED', 'DONE')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_tasks_blocked CHECK (status <> 'BLOCKED' OR blocked_reason IS NOT NULL),
    CONSTRAINT ck_tasks_completed CHECK (status = 'DONE' OR completed_at IS NULL)
);

CREATE INDEX ix_tasks_project_status_due ON tasks (project_id, status, due_date);
