-- V18__academic_catalog.sql
-- Academic catalog (DATA_MODEL §3.2-3.4, FR-CAT-01..03) and the project term and course links (FR-PROJ-01).
-- Deviation from V2: users.department_id gets its foreign key here, now that departments exist.
-- Existing projects keep term_id NULL; the service requires a term for new proposals only.
-- Rollback note: development only. Production rollback is a restore (OPERATIONS §6).

CREATE TABLE departments (
    id          BINARY(16)   NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uk_departments_code UNIQUE (code),
    CONSTRAINT ck_departments_name CHECK (CHAR_LENGTH(name) BETWEEN 2 AND 150)
);

CREATE INDEX ix_departments_active ON departments (active, name);

CREATE TABLE academic_terms (
    id          BINARY(16)   NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    starts_on   DATE         NOT NULL,
    ends_on     DATE         NOT NULL,
    status      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_academic_terms PRIMARY KEY (id),
    CONSTRAINT uk_academic_terms_code UNIQUE (code),
    CONSTRAINT ck_academic_terms_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_academic_terms_dates CHECK (ends_on > starts_on)
);

CREATE INDEX ix_academic_terms_status_starts ON academic_terms (status, starts_on);

CREATE TABLE courses (
    id            BINARY(16)   NOT NULL,
    code          VARCHAR(20)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    department_id BINARY(16)   NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT fk_courses_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT
);

CREATE INDEX ix_courses_department ON courses (department_id, active);

ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id) ON DELETE RESTRICT;

ALTER TABLE projects
    ADD COLUMN term_id BINARY(16) NULL,
    ADD COLUMN course_id BINARY(16) NULL,
    ADD CONSTRAINT fk_projects_term FOREIGN KEY (term_id) REFERENCES academic_terms (id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_projects_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE RESTRICT;
