-- V11__project_membership.sql
-- Project team membership and invitations (STATE_MACHINES §6, API_CONTRACT §6.1).
--   * The owner stays in projects.owner_id and is not a membership row; the team cap of five
--     counts the owner plus the MEMBER rows.
--   * Only MEMBER is stored as a role. Faculty supervision stays on projects.supervisor_id.
--   * pending_key makes "one PENDING invitation per project and invitee" a database rule.
--     It is NULL for every non-pending row, and MySQL allows many NULLs in a UNIQUE index.
-- Rollback note: DROP TABLE project_invitations; DROP TABLE project_members; (development only).
CREATE TABLE project_members (
    id          BINARY(16)   NOT NULL,
    project_id  BINARY(16)   NOT NULL,
    user_id     BINARY(16)   NOT NULL,
    role        VARCHAR(16)  NOT NULL DEFAULT 'MEMBER',
    joined_at   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_project_members PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_members_role CHECK (role IN ('MEMBER'))
);

CREATE INDEX ix_project_members_user ON project_members (user_id);

CREATE TABLE project_invitations (
    id            BINARY(16)   NOT NULL,
    project_id    BINARY(16)   NOT NULL,
    invitee_id    BINARY(16)   NOT NULL,
    inviter_id    BINARY(16)   NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    expires_at    DATETIME(6)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    responded_at  DATETIME(6)  NULL,
    pending_key   VARCHAR(64)  GENERATED ALWAYS AS (
                      CASE WHEN status = 'PENDING' THEN CONCAT(HEX(project_id), HEX(invitee_id)) END
                  ) STORED,
    version       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_project_invitations PRIMARY KEY (id),
    CONSTRAINT uk_project_invitations_pending UNIQUE (pending_key),
    CONSTRAINT fk_project_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_invitee FOREIGN KEY (invitee_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_project_invitations_inviter FOREIGN KEY (inviter_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_project_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    CONSTRAINT ck_project_invitations_not_self CHECK (invitee_id <> inviter_id)
);

CREATE INDEX ix_project_invitations_invitee_status ON project_invitations (invitee_id, status);
CREATE INDEX ix_project_invitations_project_status ON project_invitations (project_id, status);
