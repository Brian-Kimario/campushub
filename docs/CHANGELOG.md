# CampusHub — Changelog

**Purpose:** A dated record of changes to the documentation set, the decisions, and the scope, so that a reviewer can see what changed, when, and why.
**Scope:** Documentation, decisions, scope, and (from Phase 1) code and releases. Entries are added in the same change that makes the modification.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md), [DECISIONS.md](DECISIONS.md), [SCOPE.md](SCOPE.md), [PHASE_PLAN.md](PHASE_PLAN.md).

Format: each entry has a date, a short title, and the affected documents. Accepted ADRs are not edited; a change is a new entry and a new ADR that supersedes the old one.

## [Phase 5B student skills] — 2026-11-03 — Skill catalog and student proficiency

**Status:** Complete locally. Not pushed.

**Affected documents:** `docs/DEMO_SCRIPT.md` (§3.8 skills walkthrough), `docs/CHANGELOG.md` (this entry). `docs/DATA_MODEL.md` §8 and `docs/API_CONTRACT.md` §3 and §11 already described this scope and were left unchanged.

**Changed (code):**
- Flyway V8 creates `skills` (unique `name_key`, `category`, `active`) and `user_skills` (PK user and skill, four-level proficiency CHECK, evidence note up to 500 characters).
- `GET /skills` (any signed-in user, paginated, `category` filter); `POST /admin/skills` (ADMIN, 201, 409 `DUPLICATE` on the normalized name).
- `GET /users/me/skills` and `PUT /users/me/skills` (STUDENT only). The PUT replaces the full set atomically, validates every entry before writing, and accepts at most 50 entries. A refused request leaves the previous set in place. Other roles get 403 `FORBIDDEN_ROLE`.
- `ErrorCode.DUPLICATE` (409) added.
- Frontend `/skills` page: catalog with pagination, admin add form with a duplicate check, and a student editor with proficiency, evidence note, a 50-skill cap, and a confirmation before removing a skill.

**Decision:** proficiency uses the four levels in DATA_MODEL §8 (BEGINNER, INTERMEDIATE, ADVANCED, EXPERT). This is the open point to confirm against the directive, which may list three.

**Verified:**
- `./mvnw -B verify`: 108 tests, 0 failures (BUILD SUCCESS). This includes 11 skills integration tests in `SkillFlowIntegrationTest`.
- `npm run build` and `npx oxlint src` (exit 0).
- Built-in browser: admin adds a skill and gets a duplicate refusal for `JAVA`; student adds two skills, sets proficiency and an evidence note, saves, reloads, and sees the values; the remove confirmation cancels. The database rows match.

**Not verified:** faculty view of `/skills` in the browser; a student's PUT with an invalid entry in the browser (backend test covers it); GitHub Actions has not run on these changes.

## [Phase 5A session restore] — 2026-11-03 — Refresh-cookie sessions and reliable sign-out

**Status:** Complete locally. Not pushed.

**Affected documents:** `docs/DECISIONS.md` (ADR-025 added; ADR-004 superseded), `docs/API_CONTRACT.md` (§2 logout and refresh), `docs/SECURITY.md` (refresh tokens, CSRF, risk note), `docs/ARCHITECTURE.md` (decision index), `docs/DEMO_SCRIPT.md` (reload and sign-out rows, troubleshooting, known limitations), `docs/DATA_MODEL.md` (V7 table), `docs/CHANGELOG.md` (this entry).

**Changed (code):**
- Flyway V7 creates `refresh_sessions` (hash of the token, family, user, expiry, used and revoked timestamps).
- Login sets an HttpOnly, SameSite=Strict, Secure-by-default cookie scoped to `/api/v1/auth`. `POST /auth/refresh` rotates it and returns a new access token. Reuse of a rotated token revokes the family and writes `auth.refresh_reuse_detected` to the audit log.
- `POST /auth/logout` works with an expired access token, revokes the refresh family, and clears the cookie. Both cookie endpoints require `X-Requested-With: CampusHub`.
- `AuthenticationFilter` no longer returns 401 for a bad bearer token on `/auth/refresh` and `/auth/logout`.
- Password change revokes every refresh family for the user.
- Hourly job purges expired refresh rows.
- Frontend restores the session once per page load (`SessionGate`, single-flight refresh), retries one request after an expired access token, and clears local state in a `finally` on sign-out.

**Verified:**
- `./mvnw -B verify`: 97 tests, 0 failures, including 7 new refresh-cookie cases in `AuthFlowIntegrationTest`.
- `npm run build` and `npm run lint` (exit 0).
- Built-in browser: sign-in, reload keeps the session, sign-out returns 204 and lands on `/login`, and `/projects` after sign-out redirects to login.

**Not verified:** reuse detection, a deactivated account's refresh, and logout with an expired token in the browser (backend tests cover the first and third; the deactivated-account case is implemented but has no test yet). GitHub Actions has not run on these commits.

## [MVP password minimum] — 2026-11-03 — Minimum lowered to 8 characters

**Status:** Complete locally. Not pushed.

**Affected documents:** `docs/SECURITY.md` and `docs/API_CONTRACT.md` (password policy), `docs/DEMO_SCRIPT.md` (change-password step), `docs/IMPLEMENTATION_LOG.md` (§11 entry).

**Changed (code):** `PasswordPolicy.MIN_LENGTH` and the dev seed minimum (`ConfigurationPolicy`) lowered from 12 to 8. The maximum stays at 72 UTF-8 bytes (BCrypt input limit). The frontend change-password form uses the same minimum. The common-password list gained short entries (for example `password`, `12345678`) because an 8-character minimum would otherwise admit them.

**Changed (local data only):** the four demo accounts were set to a shared local password through the change-password endpoint. The value is not recorded in any document or commit.

**Verified:** `./mvnw -B verify` (90 tests, 0 failures, BUILD SUCCESS). `npm run build` passes. `npm run lint` exits 0. All four demo accounts log in through `/api/v1`.

## [MVP Stage 4 follow-up] — 2026-11-03 — `/api/v1` migration, lint, browser verification, demo script

**Status:** Complete locally. Committed locally in four commits (`/api/v1` and identity, MVP backend slices, frontend, docs); not pushed. See `git log`.

**Affected documents:** `docs/IMPLEMENTATION_LOG.md` (§11 Stage 4 follow-up entry), `docs/DEMO_SCRIPT.md` (rewritten for the local MVP), `docs/CHANGELOG.md` (this entry).

**Changed (code):**
- All REST controllers are served under `/api/v1` through `backend/src/main/java/com/campushub/shared/web/ApiVersionConfig.java`. Actuator and springdoc stay outside the prefix.
- Security matchers, the password-change exemptions, and the backend integration tests use `/api/v1` paths.
- `frontend/src/api/baseApi.ts` uses `/api/v1`. `frontend/vite.config.ts` forwards `/api` unchanged to `CAMPUSHUB_API_TARGET` (default `http://localhost:8080`).
- Lint fixes: lazy page exports moved to `frontend/src/app/lazyPages.ts`, so `only-export-components` passes; `watch` replaced with `useWatch` in five forms, so `incompatible-library` passes.

**Resolved from the Stage 4 entry:** the root-route deviation (controllers at `/auth`, `/projects`, `/users`) is resolved by the migration above. The stale Compose `api` container is rebuilt from current source.

**Verified:**
- `./mvnw -B verify`: 89 tests, 0 failures, BUILD SUCCESS.
- `npm run build` passes. `npm run lint` exits 0 with no warnings reported (was 0 errors and 11 warnings).
- Compose `api` rebuilt from current source; readiness reports `UP`. The four seeded accounts log in through `/api/v1`. No volume was removed and the database was not reset.
- Latest GitHub Actions run on `98e9b7b` (Phase 2 commit) succeeded, 3m10s. This run predates the Stage 4 follow-up changes, which have not been pushed.
- Built-in browser pane, React UI: login (correct and wrong password), empty-field validation, `/projects` redirect without a session, student project create, task create and `IN_PROGRESS` transition, submissions for revision 1 and revision 2, admin supervisor assignment, faculty review start, changes requested with feedback, student sees the feedback, password change (old rejected, new accepted, original restored), session end on reload.

**Not verified in the browser:** student attempts at admin actions and cross-user access; task transitions beyond `IN_PROGRESS`; `BLOCKED` and reset flows; project archive; faculty acceptance of revision 2; pagination past 50 tasks. The backend suite covers the rules behind several of these; the UI paths are not clicked through.

**Open:**
- Sign-out sometimes needs a second attempt in the built-in browser pane. Not investigated.
- Stage 6 (full E2E run) is not started.

## [MVP Stage 4] — 2026-11-03 — React frontend

**Status:** Frontend slice built and smoke-tested locally. Not committed, not pushed, not deployed. Interactive UI flows are not yet clicked through end to end. Stage 5 and the full E2E run (Stage 6) are not started.

**Affected documents:** `docs/IMPLEMENTATION_LOG.md` (§11 Stage 4 entry).

**Added (code):** `frontend/` (Vite 8, React 19, TypeScript, Redux Toolkit with RTK Query, React Router, React Hook Form with Zod 4). Screens: login, forced password change, project list, project create and edit, project detail, admin supervisor assignment and archive, tasks (list, create, edit, transitions with `blockedReason`), submissions (list, create revision, faculty review start and decision). Access token held in memory only.

**Changed (code):** `frontend/vite.config.ts` reads `CAMPUSHUB_API_TARGET` (default `http://localhost:8080`) so the dev proxy can target another local backend port.

**Verified:**
- `npm run build` passes (`tsc -b` and `vite build`).
- `npm run lint` reports 0 errors and 11 warnings: `only-export-components` on the lazy route constants in `src/app/router.tsx`, and `incompatible-library` on React Hook Form `watch` calls. Not yet addressed.
- Against the current backend source on port 8081 through the Vite `/api` proxy: login returns 401 for a wrong password and 200 for the three seeded roles; project create 201 and list 200; task create and `TODO`→`IN_PROGRESS` transition 200; `BLOCKED` without a reason 400; submission create 201; admin supervisor assignment 200; faculty review start 200; `CHANGES_REQUESTED` without feedback 422; with feedback 201; student revision 2 after changes requested 201; second decision 409; admin task create 403.
- `/login` renders in the built-in browser with no console errors.

**Not verified:** Form submissions, dialogs, stale-version recovery, and session reload through the React UI. Password change through the UI.

**Open:**
- The API contract base path is `/api/v1` (API_CONTRACT header and DECISIONS ADR on errors and versioning). The backend controllers are mounted at the root (`/auth`, `/projects`, `/users`). The frontend uses `/api` and the proxy strips it. This is a contract deviation that needs a decision: change the backend mapping to `/api/v1`, or record the root mapping as a deviation.
- The Compose `api` container on port 8080 is stale. It returns 404 for `/auth/login`, so it does not run the current code. It was not stopped or rebuilt.

## [MVP Stage 3] — 2026-11-03 — Submissions and review

**Status:** Backend slice complete locally, verified by `./mvnw -B verify` (89 tests, 0 failures, BUILD SUCCESS). Not committed, not pushed, not deployed. The React frontend is not started.

**Affected documents:** `docs/IMPLEMENTATION_LOG.md` (§11 Stage 3 entry), `docs/API_CONTRACT.md` (§10 MVP-subset note), `docs/DATA_MODEL.md` (§7 deviation note).

**Added (code):** `V6__submissions.sql` (written; applied only in Testcontainers runs); submission package (create revision, list, get, review start, decision); `ErrorCode` additions `REVIEW_EXISTS` and `FEEDBACK_REQUIRED`.

**Tests:** `SubmissionFlowIntegrationTest` (13), on MySQL 8.4 via Testcontainers.

**Deviations:** recorded in IMPLEMENTATION_LOG §11 and API_CONTRACT §10.

## [MVP Stages 1–2] — 2026-11-03 — Projects and tasks

**Status:** Backend slice complete locally, verified by `./mvnw -B verify` (76 tests, 0 failures, BUILD SUCCESS). Not committed, not pushed, not deployed. Submissions, review, and the React frontend are not started.

**Affected documents:** `docs/IMPLEMENTATION_LOG.md` (§11), `docs/API_CONTRACT.md` (§6 and §8 MVP-subset notes), `docs/DATA_MODEL.md` (§6.2 deviation note), `docs/SECURITY.md` (password-policy wording corrected).

**Added (code):** `V4__projects.sql` and `V5__tasks.sql` (written; applied only in Testcontainers runs); project package (create, list, get, owner PATCH, admin supervisor assignment, admin archive); task package (create, list, get, owner PATCH, owner transitions with audit actions); `GET /users/faculty` (admin only); `ErrorCode` additions `FIELD_NOT_EDITABLE`, `INVALID_TRANSITION`.

**Tests:** `ProjectFlowIntegrationTest` (13) and `TaskFlowIntegrationTest` (15), both on MySQL 8.4 via Testcontainers.

**Deviations:** recorded in IMPLEMENTATION_LOG §11.

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
