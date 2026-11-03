-- V2__identity_catalog.sql
-- Phase 2 identity catalog (DATA_MODEL §3.1, ADR-021): the users table only.
-- Deviation: departments, academic_terms and courses are not created here. users.department_id is
-- stored without a foreign key until the department catalog arrives with the features that need it
-- (recorded in IMPLEMENTATION_LOG §10).
-- Rollback note: DROP TABLE users; (development only). Production rollback is a restore (OPERATIONS §6).
CREATE TABLE users (
    id                   BINARY(16)   NOT NULL,
    email                VARCHAR(254) NOT NULL,
    institutional_id     VARCHAR(32)  NULL,
    password_hash        VARCHAR(100) NOT NULL,
    display_name         VARCHAR(100) NOT NULL,
    role                 VARCHAR(32)  NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count   INT          NOT NULL DEFAULT 0,
    locked_until         DATETIME(6)  NULL,
    department_id        BINARY(16)   NULL,
    program              VARCHAR(120) NULL,
    cohort               VARCHAR(20)  NULL,
    designation          VARCHAR(120) NULL,
    bio                  VARCHAR(1000) NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_institutional_id UNIQUE (institutional_id),
    CONSTRAINT ck_users_role CHECK (role IN ('STUDENT', 'FACULTY', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    CONSTRAINT ck_users_failed_login CHECK (failed_login_count >= 0)
);

CREATE INDEX ix_users_role_status ON users (role, status);
