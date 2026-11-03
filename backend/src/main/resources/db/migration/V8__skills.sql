-- V8__skills.sql
-- Skill catalog and student skills (DATA_MODEL §8, FR-SKILL-01 and FR-SKILL-02).
--   * name_key is the normalized name (trimmed, inner whitespace collapsed, lowercased). It is unique,
--     so "Java" and " java " are one catalog entry.
--   * proficiency takes the four documented levels. Each row belongs to one student (ADR-007 keeps
--     these hidden from administrators and faculty; OD-5 default).
-- Rollback note: DROP TABLE user_skills; DROP TABLE skills; (development only). Production rollback is
-- a restore (OPERATIONS §6).
CREATE TABLE skills (
    id        BINARY(16)   NOT NULL,
    name      VARCHAR(80)  NOT NULL,
    name_key  VARCHAR(80)  NOT NULL,
    category  VARCHAR(60)  NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT uq_skills_name_key UNIQUE (name_key),
    CONSTRAINT ck_skills_name CHECK (CHAR_LENGTH(name) BETWEEN 2 AND 80),
    CONSTRAINT ck_skills_category CHECK (CHAR_LENGTH(category) BETWEEN 1 AND 60)
);

CREATE INDEX ix_skills_category_name ON skills (category, name_key);

CREATE TABLE user_skills (
    user_id        BINARY(16)    NOT NULL,
    skill_id       BINARY(16)    NOT NULL,
    proficiency    VARCHAR(16)   NOT NULL,
    evidence_note  VARCHAR(500)  NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_user_skills PRIMARY KEY (user_id, skill_id),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT ck_user_skills_proficiency CHECK (proficiency IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    CONSTRAINT ck_user_skills_evidence CHECK (evidence_note IS NULL OR CHAR_LENGTH(evidence_note) <= 500)
);
