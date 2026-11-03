-- V1__baseline_extensions.sql
-- Phase 1 baseline (DATA_MODEL §11): character set and collation defaults only; no business tables.
-- Tables arrive with the features that need them, starting with V2__identity_catalog.sql.
-- Rollback note: none. This migration only sets defaults for the current database. To revert in
-- development, drop and recreate the database. Production rollback is a restore (OPERATIONS §6).
ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
