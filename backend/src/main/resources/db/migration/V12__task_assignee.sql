-- V12__task_assignee.sql
-- Task assignment (FR-TASK-02, FR-TEAM-03). NULL means unassigned. The application enforces that an
-- assignee is the project owner or a current team member, and removing a member clears their open
-- assignments. Assignee is a plain reference, so the composite index serves "my tasks in project" lookups.
-- Rollback note: ALTER TABLE tasks DROP FOREIGN KEY fk_tasks_assignee, DROP INDEX ix_tasks_project_assignee,
-- DROP COLUMN assignee_id (development only). Production rollback is a restore (OPERATIONS §6).
ALTER TABLE tasks
    ADD COLUMN assignee_id BINARY(16) NULL,
    ADD CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id) ON DELETE RESTRICT;

CREATE INDEX ix_tasks_project_assignee ON tasks (project_id, assignee_id);
