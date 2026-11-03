# CampusHub — Changelog

**Purpose:** A dated record of changes to the documentation set, the decisions, and the scope, so that a reviewer can see what changed, when, and why.
**Scope:** Documentation, decisions, scope, and (from Phase 1) code and releases. Entries are added in the same change that makes the modification.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md), [DECISIONS.md](DECISIONS.md), [SCOPE.md](SCOPE.md), [PHASE_PLAN.md](PHASE_PLAN.md).

Format: each entry has a date, a short title, and the affected documents. Accepted ADRs are not edited; a change is a new entry and a new ADR that supersedes the old one.

## [Phase 1] — 2026-11-03 — Local infrastructure and backend skeleton

**Status:** Complete locally. Committed locally, not pushed. CI workflow validated locally, not run on GitHub (no remote). Stopped for approval before Phase 2.

**Affected documents:** `docs/IMPLEMENTATION_LOG.md` (§1 status, §9 Phase 1 entries), `docs/PHASE_PLAN.md` (§1 status, §4 unchanged), `docs/RISKS.md` (R-16 Mitigated; R-22 and R-23 added), `README.md` (status line, CI note).

**Added (code and configuration):**
- `backend/`: Spring Boot 3.5.16 on Java 21 with the Maven wrapper; application class; RFC 9457 error handling; configuration guard; storage readiness probe; `application.yml`, `dev`, and `prod` profiles; Flyway V1 baseline; multi-stage `Dockerfile`.
- `docker-compose.yml` with digest-pinned `mysql:8.4` and the `api` service.
- `docker/mysql/init/01-create-users.sh` for least-privilege database users.
- `.github/workflows/ci.yml`: backend job (`./mvnw -B verify`, Java 21, least-privilege token, pinned action SHAs) and image job (Docker build).
- Tests: `ConfigurationPolicyTest` (8), `StorageHealthIndicatorTest` (3), `MySqlIntegrationTest` (4), `ReadinessWithoutDatabaseTest` (1).

**Changed in the review:**
- Springdoc API docs and Swagger UI are off by default and enabled only in the `dev` profile, so a missing profile cannot expose the API description.

**Verified:** `./mvnw -B verify` (16 tests, 0 failures, BUILD SUCCESS, including MySQL 8.4 via Testcontainers and the readiness-DOWN path after MySQL stops); `actionlint` on the workflow (exit 0); `docker compose config -q` (exit 0); Compose stack rebuilt and readiness UP; default profile returns 404 for `/v3/api-docs`, Swagger UI, and `/actuator/env`; gitleaks found no leaks in the 52 committable files. Details in IMPLEMENTATION_LOG §9.

**Deviations recorded:** Flyway runs in-app with the migration user (DEPLOYMENT §5); Testcontainers test uses `@DynamicPropertySource`; ArchUnit deferred to Phase 2; OpenAPI snapshot deferred to Phase 9.

## [Phase 0] — 2026-11-03 — Planning baseline

**Status:** Planning only. Not committed. No application code.

### Added

- `docs/PRODUCT.md`: audience, problems, users, success metrics SM-1 to SM-6, exclusions, assumptions A-1 to A-7, owner items OD-1 to OD-10.
- `docs/REQUIREMENTS.md`: functional and nonfunctional requirements with priorities and acceptance criteria.
- `docs/USER_STORIES.md`: role-specific stories, golden paths GP-1 to GP-3, negative journeys NS-01 to NS-12.
- `docs/SCOPE.md`: P0, P1, and P2 scope and change control.
- `docs/ARCHITECTURE.md`, `docs/DATA_MODEL.md`, `docs/ERD.md`, `docs/API_CONTRACT.md`, `docs/STATE_MACHINES.md`, `docs/AUTHORIZATION_MATRIX.md`, `docs/SECURITY.md`, `docs/UX_UI.md`.
- `docs/TEST_STRATEGY.md`, `docs/CI_CD.md`, `docs/DEPLOYMENT.md`, `docs/OPERATIONS.md`.
- `docs/SYLLABUS_MAPPING.md`, `docs/DEMO_SCRIPT.md`.
- `docs/DECISIONS.md`: ADR-001 to ADR-024, of which ADR-016 to ADR-024 are deviations.
- `docs/RISKS.md`: baseline risks from blueprint §19 and risks identified in Phase 0.
- `docs/PHASE_PLAN.md`, `docs/IMPLEMENTATION_LOG.md`, `docs/CHANGELOG.md`.
- `docs/INDEX.md` linking all canonical documents; root `README.md`; `.gitignore`; `.editorconfig`; `.env.example` (placeholders only).

### Changed

- Within Phase 0 drafting, corrected the following before this baseline: API_CONTRACT §8 wording; activate, complete, and owner-removal rows to 422 `PRECONDITION_NOT_MET`; DATA_MODEL `revoked_after` added; Task status CHECK restored to include `IN_REVIEW`; STATE_MACHINES §1 evaluation order places the role check first; AUTHORIZATION_MATRIX milestone field rows use 400; membership creation is invitation-only; STATE_MACHINES §10 pair arithmetic corrected.
- SYLLABUS_MAPPING governing decisions corrected to match the ADR index (rows 1–4 use ADR-009; row 13 cites ADR-005 for BCrypt).
- Phase 0 cross-check: section citations corrected in DECISIONS (ADR-007, 009, 010, 011, 017, 018, 019, 021, 023); ADR-017 golden-path step corrected to GP-3 step 8; PRODUCT SM-2 corrected to USER_STORIES §5; IMPLEMENTATION_LOG §2 rewritten to state what was checked.
- PHASE_PLAN §4 replaced with the full Phase 0 review (OD status, roadmap, verification summary, readiness verdict).

### Decided

- ADR-001 to ADR-024 recorded (see DECISIONS §2).
- Phase 0 defaults for OD-1 to OD-10 applied, pending owner confirmation (PRODUCT §8).

### Not done

- No commit.
- No code, migrations, tests, or pipelines.
- Docker availability not verified (RISKS R-16).
