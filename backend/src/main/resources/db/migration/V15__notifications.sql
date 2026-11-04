-- V15__notifications.sql
-- Database-backed notifications with read state (release brief item 6; not in REQUIREMENTS.md).
--   * Rows are written in the same transaction as the event that causes them (team invitations and
--     removals), so a rolled-back change leaves no notification behind.
--   * Message text is not stored. The service renders it from type, project title and actor name, so a
--     project rename shows the current title and no free text is kept in the table.
--   * read_at NULL means unread. Marking read is idempotent and never clears read_at.
-- Rollback note: DROP TABLE notifications; (development only).
CREATE TABLE notifications (
    id            BINARY(16)   NOT NULL,
    recipient_id  BINARY(16)   NOT NULL,
    actor_id      BINARY(16)   NULL,
    type          VARCHAR(40)  NOT NULL,
    project_id    BINARY(16)   NULL,
    created_at    DATETIME(6)  NOT NULL,
    read_at       DATETIME(6)  NULL,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notifications_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'INVITATION_RECEIVED', 'INVITATION_ACCEPTED', 'INVITATION_DECLINED', 'MEMBER_REMOVED'))
);

CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_id, created_at, id);
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_id, read_at);
