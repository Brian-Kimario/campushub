# CampusHub — Implementation Log

**Purpose:** The honest record of what has been built, verified, measured, and decided during implementation, with dates and commits. Entries are added as work happens and are never rewritten to look better.
**Scope:** All phases. Phase 0 is documentation only; no feature code exists yet.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [PHASE_PLAN.md](PHASE_PLAN.md), [CHANGELOG.md](CHANGELOG.md), [RISKS.md](RISKS.md), [SYLLABUS_MAPPING.md](SYLLABUS_MAPPING.md) §4 (evidence rules), [OPERATIONS.md](OPERATIONS.md) §10.

## 1. Current status

**Phase 1 complete locally.** As of 2026-11-03:

- Phase 0 documentation is written. Phase 1 code is written under `backend/` and `docker-compose.yml`. The Phase 1 review change is committed locally on `master`; nothing is pushed (there is no remote).
- Phase 1 has the skeleton, configuration guard, RFC 9457 errors, health probes, Flyway V1 baseline, and four test classes (16 tests). One CI workflow exists (`.github/workflows/ci.yml`), validated locally but not run on GitHub. No feature code and no deployments exist.
- No syllabus item is *evidenced* (SYLLABUS_MAPPING §4). All 20 rows are planned.
- No measurement has been taken. SM-6 and the render-count profile are not measured.
- The owner accepted the Phase 1 gate on 2026-11-03, with the open items recorded in §9 (CI not yet run on GitHub, R-22; secret scanning not in CI, R-23). Phase 2 is not started; it begins only on a separate instruction. The evidence and the gaps are in §9.

## 2. Phase 0 verification results

| Check | Result | Notes |
|---|---|---|
| Every required document exists in `/docs` | Pass | Checked by file listing against INDEX §2 |
| No placeholder headings | Pass | Search for TODO, TBD, placeholder, and lorem; the remaining TODO hits are task-status values, not placeholders |
| ADR numbers ADR-001 to ADR-024 each defined once | Pass | Heading count is 24 and no heading number repeats (`grep` on `### ADR-`) |
| Every ADR cited by another document exists | Pass | Citations checked; no ADR number outside 001–024 |
| Every risk ID cited by another document is defined | Pass | R-07, R-09, R-10, R-16, R-17, R-19 defined in RISKS §2–§3 |
| Section citations in DECISIONS point to existing sections | Pass after fix | Section citations corrected in nine ADRs (ADR-007, 009, 010, 011, 017, 018, 019, 021, 023); the remaining ones were confirmed against the target headings |
| Golden-path step citations in DECISIONS | Pass after fix | ADR-017 corrected from GP-3 step 9 to step 8 |
| PRODUCT SM-2 points to the negative journeys | Pass after fix | Corrected from USER_STORIES §4 to §5 |
| SYLLABUS_MAPPING governing decisions match the ADR index | Pass after fix | Rows 1–4 and 13 corrected to ADR-009 and ADR-005 |
| Blueprint §2 concept count | 20 rows | The brief said 23; recorded in SYLLABUS_MAPPING §1 |
| Relative Markdown links resolve to existing files | Pass | Checked by path resolution from each file's directory |
| Docker daemon | **Not verified** | Not run in Phase 0; required before Phase 1 (RISKS R-16) |
| Build, test, and CI runs | Not applicable | No code exists in Phase 0 |

## 3. Decisions taken during Phase 0 (independent of the owner)

These are defaults from PRODUCT §8. Each is reversible through an ADR.

| Item | Default | ADR |
|---|---|---|
| Administrator approves and assigns supervisors | Yes | ADR-016 |
| Team cap | 5 members | ADR-022 |
| Late submissions | Off unless the supervisor enables per milestone | ADR-024 |
| Institution time zone | UTC until configured | ADR-013 |
| Hosting | Deferred to Phase 10; default option A | ADR-014 |
| Kafka | Stretch, Phase 8, off by default | ADR-011 |

## 4. Rehearsals and drills

None yet. DEMO_SCRIPT §5 and OPERATIONS §6.5 define the required entries.

| Date | Type | Host | Result | Fix recorded |
|---|---|---|---|---|
| — | — | — | — | — |

## 5. Measurements

None yet. Entries record the date, the commit, the host, the method (TEST_STRATEGY §6), and the value as measured, including misses.

| Date | Commit | Measurement | Target | Value | Method |
|---|---|---|---|---|---|
| — | — | — | — | — | — |

## 6. Operator and release log

No releases yet. Entries follow OPERATIONS §4.2 (key rotation), §5 (job changes), §6.5 (restore), and DEPLOYMENT §5 and §6 (release and rollback). Secret values are never recorded.

| Date | Action | Operator | Reason | Result |
|---|---|---|---|---|
| — | — | — | — | — |

## 7. Accepted risks and owner decisions

Owner acceptances of risks in RISKS (status *Accepted*) and answers to OD-1 to OD-10 are recorded here with the date and the name of the person who gave them. No acceptance is recorded on the team's behalf.

| Date | Item | Decision | Given by |
|---|---|---|---|
| — | R-07, R-10 acceptance | Pending | — |
| — | OD-1 to OD-10 | Pending | — |

## 8. Entry format

```
### YYYY-MM-DD — short title
- Phase:
- Commit:
- What changed:
- What was verified and how:
- What was not verified:
- Decisions, risks, or owner items affected:
```

## 9. Phase 1 entries

### 2026-11-03 — Phase 1: local infrastructure and backend skeleton
- Phase: 1
- Commit: none at the time of this entry. The review follow-up below records the local commit and its push status.
- What changed:
  - `backend/`: Spring Boot 3.5.16 on Java 21, Maven wrapper 3.9.16, package `com.campushub`. Contains the application class, `shared/errors` (RFC 9457 handler, `ErrorCode`, `ApiException`), `infrastructure/config` (`ConfigurationPolicy` and `ConfigurationGuard`), `infrastructure/storage` (storage readiness probe), profiles `application.yml`, `dev`, and `prod`, Flyway `V1__baseline_extensions.sql` (no business tables), and `Dockerfile` (multi-stage, digest-pinned, non-root).
  - `docker-compose.yml`: `mysql` (mysql:8.4 digest-pinned, health check) and `api` (built from `backend/`).
  - `docker/mysql/init/01-create-users.sh`: creates the runtime user (DML only) and the migration user (all privileges on the `campushub` schema only).
  - `.env`: created locally from `.env.example` with generated secrets. It is git-ignored and was not printed or committed.
- What was verified and how:
  - Java 21: installed with `brew install openjdk@21`. Build: `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./mvnw -B verify`, result BUILD SUCCESS with 11 tests run and 0 failures: 8 unit tests in `ConfigurationPolicyTest` and 3 tests in `MySqlIntegrationTest` against a real MySQL 8.4 Testcontainers container.
  - Docker: Docker server 29.8.2 and Compose v5.5.0. `docker compose config --quiet` passed. `docker compose up -d --build` left the `mysql` service healthy and the `api` service running.
  - Readiness: `GET http://localhost:8080/actuator/health/readiness` returned HTTP 200 with `{"status":"UP"}`. The readiness group includes the database and storage checks.
  - Migrations: `flyway_schema_history` contains version 1, "baseline extensions", with `success = 1`.
  - Least privilege: `CREATE TABLE` as the runtime user `campushub_app` returned ERROR 1142 (command denied).
  - Startup guard: `docker run --rm --env-file .env.example campushub-api` exited with status 1 and the message "Refusing to start", naming three settings: the database password, the migration password, and the JWT secret. The message did not echo any value.
- What was not verified (as of this entry; the review follow-up below updates CI, readiness, and API-docs status):
  - **CI.** No GitHub Actions workflow existed when this entry was written.
  - The CI runner's Docker support was not verified.
  - The `prod` profile has not been started.
  - The readiness DOWN path was not exercised by a test.
  - `./mvnw verify` was run with the Testcontainers MySQL module, not with the Compose database.
- Decisions, risks, or owner items affected:
  - Deviation, to close in Phase 10: Flyway runs in the application at startup, with the migration user, so the migration step is not separate (DEPLOYMENT §5 describes a separate step). Recorded for review.
  - Deviation: `MySqlIntegrationTest` sets the datasource and Flyway properties with `@DynamicPropertySource` instead of `@ServiceConnection`. `ConfigurationGuard` reads the environment before any bean exists, and `@ServiceConnection` sets values too late for it.
  - Dependency fix: the Testcontainers MySQL module artifact is `org.testcontainers:mysql`. The earlier name `testcontainers-mysql` does not exist on Maven Central.
  - Deferred to Phase 2: an ArchUnit rule for module boundaries. No feature modules exist yet.
  - Deferred to Phase 9: the OpenAPI snapshot comparison (API_CONTRACT §15). Endpoints do not exist yet; `/v3/api-docs` is only checked to be served.
  - Open question for Phase 2: framework errors without a CampusHub code, such as 405 Method Not Allowed, return RFC 9457 with the framework status and no `errorCode`. Decide whether they map to a documented code (API_CONTRACT §3).
  - RISKS R-16 moved to Mitigated for the development machine. The CI runner is still unverified.
  - The local stack is still running. Stop it with `docker compose down` (the volume is kept) or `docker compose down -v` (the volume is removed).

### 2026-11-03 — Phase 1 review follow-up: CI workflow, test gaps, security
- Phase: 1
- Commit: a local commit is made in the Phase 1 review change. It is not pushed. The hash is reported in the review report, not recorded here.
- What changed:
  - `.github/workflows/ci.yml`: triggers on `pull_request` and on `push` to `main` and `master`. Top-level `permissions: contents: read`. Concurrency cancels superseded runs. Job `backend` (ubuntu-24.04, 25-minute timeout) checks out with `persist-credentials: false`, sets up Temurin 21 with the Maven cache keyed on `backend/pom.xml`, and runs `./mvnw -B verify` in `backend/`. Job `image` (needs `backend`) runs `docker build` on `backend/`. Actions are pinned to full commit SHAs with the version in a comment.
  - `MySqlIntegrationTest` runs with `@ActiveProfiles("dev")`.
  - `application.yml`: springdoc API docs and Swagger UI are disabled by default. `application-dev.yml` enables them. Before this change, a run with no profile served `/v3/api-docs`.
  - New tests: `ConfigurationPolicyTest` (8), `StorageHealthIndicatorTest` (3), `ReadinessWithoutDatabaseTest` (1). `MySqlIntegrationTest` now has 4 tests, including the `/v3/api-docs` check under `dev`.
- What was verified and how:
  - `./mvnw -B verify` with the JDK 21 `JAVA_HOME` above: 16 tests, 0 failures, BUILD SUCCESS. `ReadinessWithoutDatabaseTest` stops MySQL inside the test: readiness returns 503 with `status: DOWN` and no `components`, and liveness stays UP.
  - `actionlint .github/workflows/ci.yml`: exit 0.
  - `docker compose config -q`: exit 0.
  - `docker compose up -d --build`: readiness returns 200 `{"status":"UP"}`.
  - Default profile: the `campushub-api` image was run on the Compose network with `SPRING_PROFILES_ACTIVE` empty. `/v3/api-docs`, `/swagger-ui/index.html`, and `/actuator/env` return 404. `/actuator/health` returns 200. The Compose stack sets `SPRING_PROFILES_ACTIVE: dev`, so it serves `/v3/api-docs` (200) by design.
  - Secrets: gitleaks (Homebrew build) found no leaks in the 52 files that `git ls-files -co --exclude-standard` lists. The four generated `.env` secret values match 0 times in those files. `.env`, `backend/target/`, and `infra/docker/data/` are git-ignored.
- What was not verified:
  - CI on GitHub. There is no remote, so the workflow has not run. The runner's Docker support, which Testcontainers needs on the runner, is unverified.
  - gitleaks is not in CI.
  - Startup and shutdown behaviour beyond the Spring test context was not measured.
  - Runtime database privileges were checked by hand (ERROR 1142 on `CREATE`), not by an automated test.
  - The `prod` profile has not been started.
- Decisions, risks, or owner items affected:
  - RISKS R-22 (CI has never run on GitHub) and R-23 (no secret scanning in CI) were added.
  - The CI and readiness-DOWN items in the earlier "not verified" list are closed by this entry. The CI item is closed only for local validation.

### 2026-11-03 — Phase 2: identity, roles, and security
- Phase: 2
- Commit: a local commit is made at the end of this change. It is not pushed. The hash is reported in the handoff, not recorded here.
- What changed:
  - Migrations `V2__identity_catalog.sql` (`users`, without the `departments` foreign key) and `V3__auth_audit.sql` (`revoked_tokens`, `audit_events` (append-only by intent), `users.revoked_after`).
  - `identity/`: `UserAccount`, `Role`, `UserStatus`, `PasswordPolicy`, `AuthService`, `AuthController`, `LoginRateLimiter`, `IdentityMaintenanceJobs` (UTC cron: purge revoked tokens at minute 05, clear expired locks at minute 25), `DevAccountSeeder` (only when `campushub.dev-seed-enabled=true`).
  - `identity/security/`: `JwtTokenService` (Nimbus, HS256 only, 15-minute TTL, issuer and audience checked), `AuthenticationFilter` (revocation, account status, `revoked_after` at one-second precision, role-claim match, forced-change gate), `SecurityConfig` (stateless; BCrypt cost 12), `ProblemResponder`.
  - `identity/audit/`: `AuditService` with a metadata allowlist and a keyed (HMAC-SHA256) IP hash.
  - Errors: `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `FORBIDDEN_ROLE`, `FORBIDDEN_STATE`, `PASSWORD_CHANGE_REQUIRED`, `RATE_LIMITED` added to `ErrorCode`. `ProblemFactory` shares the RFC 9457 body between the MVC handler and the filter chain.
  - `ConfigurationPolicy` refuses a dev seed without a password of at least 12 characters, and the message never echoes the value.
  - Dependency: `nimbus-jose-jwt` 10.4.2, pinned in `pom.xml` because the Boot 3.5 parent does not manage it.
- What was verified and how:
  - `./mvnw -B verify` with the JDK 21 `JAVA_HOME`: 43 tests, 0 failures, BUILD SUCCESS.
  - `TokenValidationTest` (10, no database): round trip; malformed tokens; `alg: none`; HS512; wrong signature; wrong issuer; wrong audience; expired; missing `jti`; unknown role.
  - `AuthFlowIntegrationTest` (15, MySQL 8.4 container): login with summary and no credentials; identical 401 for wrong password and unknown email; `/auth/me`; no token gives 401 with `WWW-Authenticate: Bearer`; expired token gives 401; logout revokes; change password revokes older tokens and accepts the new password; weak replacement gives 400; forced change gives 403 `PASSWORD_CHANGE_REQUIRED` except on exempt paths; student on `/admin/**` gives 403 `FORBIDDEN_ROLE`; admin gets 200; another user's record gives 404; fifth failure locks, and the correct password then gives 401; 21st attempt from one address gives 429 with `Retry-After`; the dev seed creates four accounts without a forced change.
  - `ConfigurationPolicyTest` (10): adds the dev-seed password rules.
  - `MySqlIntegrationTest` (4): unknown anonymous path now gives 401 (see deviations).
- What was not verified:
  - The Compose stack with the Phase 2 image. Not run in this change.
  - Lockout does not extend during a lock. The code path exists (`AuthService` returns before `recordFailedLogin` while locked), but no test asserts the stored `locked_until` is unchanged.
  - Logs contain no password, token, or email. There is no log-capture test yet.
  - The dev seed is idempotent across restarts. The test checks the accounts exist with no forced change, not that a re-run creates no duplicates.
  - The scheduled jobs (`IdentityMaintenanceJobs`) have no test, and no run has observed them firing on their cron.
  - BCrypt input is limited to 72 bytes. The password policy allows 128 characters. Behaviour above 72 bytes was not checked (see deviations).
  - CI on GitHub (still no remote run; R-22).
- Deviations (each one is a decision, not a silent gap):
  - Unknown path, anonymous: 401 `UNAUTHENTICATED`, not 404. The authentication rule is the last matcher, so an anonymous caller learns nothing about route existence. Authenticated unknown paths still give 404.
  - `/actuator/env` anonymous: 401, not 404. Same reason.
  - `departments` foreign key on `users.department_id` deferred until the department catalog exists.
  - Audit INSERT-only is not enforced at the database grant level yet. The application code has no update or delete path.
  - Request IDs are random per error and per audit row. They are not yet correlated across the two.
  - Rate limit is per node, in memory (R-09).
  - Scheduled jobs run without a database lock, so they assume a single instance (R-02).
  - Common-password list is a short starter list (documented gap in `PasswordPolicy`).
  - Admin account creation and password reset endpoints are deferred. Refresh tokens are deferred (ADR-004).
  - A token issued in the same second as a password change is refused (fail-closed, ADR-019), so the user signs in again after changing the password.
- Decisions, risks, or owner items affected:
  - ADR-003 (HS256, 15-minute TTL, strict validation), ADR-005 (BCrypt 12), ADR-006 (resource policy), ADR-019 (revocation and `revoked_after`), ADR-020 (lockout 5 failures, 15 minutes, 20 per minute per address), ADR-021 (roles), ADR-023 (audit) are implemented as written above.
  - Not yet updated in this change: PHASE_PLAN status, RISKS evidence (R-02, R-03, R-05), CHANGELOG (no file exists yet), API_CONTRACT §3 (new codes need adding there, per ADR-012), and DATA_MODEL deviation notes. These are open items for the Phase 2 review.

## 10. Phase 2 review follow-ups (BCrypt limit)

Earlier documents cite this section for the Phase 2 deviations and the password limit. The Phase 2 deviations themselves are listed in the Phase 2 entry under §9.

- **BCrypt 72-byte limit (Stage 0 fix).** BCrypt reads only the first 72 bytes of input, so the old 128-character maximum could silently truncate a password. `PasswordPolicy` now requires 12 to 72 UTF-8 bytes. `AuthService.matchesPassword` refuses any candidate longer than 72 bytes before comparing, so an over-long value cannot match on its first 72 bytes.
- **Verified:** `PasswordPolicyTest` (4 unit tests); `AuthFlowIntegrationTest.changePasswordRejectsAReplacementLongerThanBcryptCanHold` (400 `VALIDATION_FAILED` on `newPassword`).
- **Not verified:** a login attempt with a 73-byte password against a 72-byte prefix of a stored hash, through the HTTP layer. The guard is unit-level only.
- **Outstanding:** the Compose API image was built before these changes and has not been rebuilt or smoke-tested (Stage 0 remainder).

## 11. MVP vertical slice: Stage 1 projects (reduced)

### 2026-11-03 — Stage 1: project create, read, update, supervisor, archive

- **Built:** migration `V4__projects.sql` (written; applied only in Testcontainers runs, so no shared environment has it yet); `Project`, `ProjectStatus`, `ProjectRepository`, `ProjectService`, `ProjectController`; request and response records; `PageResponse` and `Pagination` in `shared/web`; `UserDirectoryController` with `GET /users/faculty`; `ErrorCode` additions `STALE_VERSION`, `PROJECT_ARCHIVED`, `SUPERVISOR_NOT_ELIGIBLE`; `GlobalExceptionHandler` mappings for a bad path or query type (404 for a path value, 400 for a query value) and for optimistic-lock failures (409 `STALE_VERSION`).
- **Verified:** `./mvnw -B verify` on JDK 21: 61 tests, 0 failures, BUILD SUCCESS. `ProjectFlowIntegrationTest` (13 tests, MySQL 8.4 via Testcontainers) covers: student create returns ACTIVE with owner set; title "ab" and whitespace-only title return 400; due date before start date returns field `dueDate`; faculty create returns 403 `FORBIDDEN_ROLE`; student list shows only own projects; another student's GET returns the same 404 body as a missing id; faculty sees a project only after admin assigns them; admin assigns an active faculty supervisor; a student as supervisor returns 422 `SUPERVISOR_NOT_ELIGIBLE`; non-admin assign returns 403 `FORBIDDEN_ROLE`; owner PATCH with the current version increments `version`; PATCH with a stale version returns 409 `STALE_VERSION`; supervisor PATCH returns 403 `FORBIDDEN_STATE`; only admin can archive; PATCH on an archived project returns 422 `PROJECT_ARCHIVED`; `status=NOPE` and `size=500` return 400; `/users/faculty` returns 403 for a student and 200 with the active faculty for an admin.
- **Deviations** (also stated in API_CONTRACT §6):
  - Project status is `ACTIVE` or `ARCHIVED` only. The DRAFT, PROPOSED, APPROVED, REJECTED, and COMPLETED lifecycle and its endpoints are deferred.
  - No `termId`, `courseId`, members, invitations, milestones, or `role` and `supervisorId` list filters. `subject` is added as an optional field.
  - `PATCH` is allowed only while the project is `ACTIVE` and only for the owner. It uses full-field semantics: an omitted optional field is cleared.
  - Archive moves `ACTIVE` to `ARCHIVED`, not the contract's COMPLETED or REJECTED sources, because those states do not exist yet.
  - `GET /users/faculty` is not in the full contract. It is admin-only and unpaginated, to support choosing a supervisor. Pagination for it is deferred.
  - A malformed `{id}` such as `not-a-uuid` returns 404 `NOT_FOUND`, the same as a missing one. The contract does not state this case.
- **Not verified:**
  - Concurrent writes. The stale-version case is tested sequentially. The `ObjectOptimisticLockingFailureException` mapping is compiled and wired, but no test forces a real race.
  - The Compose stack with this build. The image is not rebuilt.
  - The GitHub CI run (R-22).

### 2026-11-03 — Stage 2: tasks (reduced)

- **Built:** migration `V5__tasks.sql` (written; applied only in Testcontainers runs); `Task`, `TaskStatus`, `TaskPriority`, `TaskTransitions` (legal moves and their audit actions), `TaskRepository`, `TaskService`, `TaskController`; request and response records. Audit actions: `task.created`, `task.updated`, and the transition actions (`task.started`, `task.blocked`, `task.review_requested`, `task.reset`, `task.completed`, `task.returned`, `task.unblocked`, `task.reopened`).
- **Verified:** `./mvnw -B verify` on JDK 21: 76 tests, 0 failures, BUILD SUCCESS (suite includes `TaskFlowIntegrationTest`, 15 tests, MySQL 8.4 via Testcontainers). The test covers: owner create returns TODO with MEDIUM priority and `createdBy`; `title` "ab" and `priority` "URGENT" return 400 `VALIDATION_FAILED`; faculty create returns 403 `FORBIDDEN_STATE`; a non-visible project's task list returns 404; the list is ordered by creation time, `status` filters, and `status=NOPE` returns 400; supervisor reads the list and a task; a hidden task returns the same 404 body as a missing one; `/tasks/not-a-uuid` returns 404; PATCH with `status` returns 400 `FIELD_NOT_EDITABLE`, and a valid PATCH increments `version`; the happy path TODO→IN_PROGRESS→IN_REVIEW→DONE sets `completedAt`, and DONE→IN_PROGRESS clears it; BLOCKED without a reason returns 400 on `blockedReason`, and unblocking clears the reason; TODO→DONE and same-state moves return 409 `INVALID_TRANSITION`; a stale version returns 409 `STALE_VERSION` before the transition is checked; supervisor transition returns 403 `FORBIDDEN_STATE`; an archived project refuses create and transition with 422 `PROJECT_ARCHIVED` and leaves the task unchanged.
- **Deviations** (also stated in API_CONTRACT §8 and DATA_MODEL §6.2):
  - No `milestone_id` and no `assignee_id`. The owner is the only actor, so every transition in STATE_MACHINES §3.2 is performed by the owner, and the assignee role is absent.
  - The due column is `due_date DATE`, not `due_at`. `completed_at` is added.
  - The archived-project refusal uses `PROJECT_ARCHIVED` (422), not the contract's `PROJECT_NOT_EDITABLE`, because the project status is `ACTIVE` or `ARCHIVED` only.
  - Create, PATCH, and transitions are owner-only. A visible non-owner gets 403 `FORBIDDEN_STATE`. An invisible task or project gets 404.
  - Order of checks for writes: visibility, owner, archived, version, then transition legality, then the BLOCKED reason check.
  - Task list filters are `status` only. `dueBefore`, `assigneeId`, `milestoneId`, and the comment endpoints are not built.
- **Not verified:**
  - The audit rows written for `task.created`, `task.updated`, and the transition actions. The tests check API responses, not audit content.
  - Concurrent writes. The stale-version case is tested sequentially.
  - The Compose stack with this build. The image is not rebuilt.
  - The GitHub CI run (R-22).
  - Submissions and review (V6 is written but not applied or tested), and the React frontend.

### 2026-11-03 — Stage 3: submissions and review (reduced)

- **Built:** migration `V6__submissions.sql` (written; applied only in Testcontainers runs); `SubmissionStatus`, `ReviewDecision`, `Submission`, `SubmissionRepository`, `SubmissionService`, `SubmissionController`; request and response records. `ErrorCode` additions `REVIEW_EXISTS` (409) and `FEEDBACK_REQUIRED` (422), both also in API_CONTRACT §3. Audit actions: `submission.created`, `submission.review_started`, `submission.accepted`, `submission.changes_requested`.
- **Verified:** `./mvnw -B verify` on JDK 21: 89 tests, 0 failures, BUILD SUCCESS. `SubmissionFlowIntegrationTest` (13 tests, MySQL 8.4 via Testcontainers) covers: owner creates revision 1 as SUBMITTED with no reviewer and no feedback, and `version` 0; an `http://` link returns 400 `VALIDATION_FAILED`; a non-visible project's submission list returns 404; a hidden submission returns the same 404 body as a missing one; faculty create returns 403 `FORBIDDEN_STATE`; the supervisor reads the submission and list, and another faculty member gets 404; admin review returns 403 `FORBIDDEN_ROLE`; review start moves SUBMITTED to UNDER_REVIEW and records the reviewer, and a second start returns 409 `INVALID_TRANSITION`; a decision before the review starts returns 409 `INVALID_TRANSITION`; CHANGES_REQUESTED with blank feedback returns 422 `FEEDBACK_REQUIRED`; the decision is recorded with feedback and reviewer, a second decision returns 409 `REVIEW_EXISTS`, the owner sees the feedback, the next revision is 2, and a create while SUBMITTED returns 409 `INVALID_TRANSITION`; ACCEPTED is final and blocks a new revision; an archived project refuses create and review start with 422 `PROJECT_ARCHIVED` and leaves the submission SUBMITTED.
- **Deviations** (also stated in API_CONTRACT §10 and DATA_MODEL §7):
  - One `submissions` row per revision. No DRAFT, no `submission_versions`, no `submission_files`, no `reviews` table. The decision lives on the row.
  - The link is an `https://` URL. There is no file upload, download, or storage. The `submission-files` endpoints are not built.
  - Routes are project-scoped (`/projects/{id}/submissions`, `/submissions/{id}`, `/submissions/{id}/review*`), not milestone-scoped. There is no milestone and no `/versions` endpoint.
  - Check order for writes: role (403 `FORBIDDEN_ROLE`) first for review actions, then visibility (404), then owner for create (403 `FORBIDDEN_STATE`), then archived (422), then state (409), then the feedback check (422). Review start and decision carry no `version`, so no 409 `STALE_VERSION` is produced.
  - The reviewer is recorded at review start and reassigned to the caller who records the decision.
  - The submission list is unpaginated and ordered by `revisionNo` ascending.
- **Not verified:**
  - Concurrent duplicate revisions. Two simultaneous creates for the same `revision_no` should hit `uk_submissions_revision`, but no test forces the race. The exception may surface as 500 rather than 409 and is untested.
  - Audit row content for the submission actions. The tests check API responses, not the audit table.
  - The Compose stack with this build. The image is not rebuilt.
  - The GitHub CI run (R-22).
  - The React frontend (Stage 4).

### 2026-11-03 — Stage 4: React frontend (partial verification)

- **Built:** `frontend/` with API client on RTK Query (`src/api/`), in-memory auth slice, protected routes, and feature folders for auth, projects, tasks, and submissions. Task panel: list (first 50, with a hint when more exist), create, edit, the ten transitions from STATE_MACHINES §3.2, `blockedReason` 3–500 characters for `BLOCKED`. Submission panel: list, create revision (owner, only when none exists or changes were requested), review start, and decision with feedback required for `CHANGES_REQUESTED`.
- **Verified:** `npm run build` passes. `npm run lint` shows 0 errors and 11 warnings (see CHANGELOG `MVP Stage 4`). Backend smoke flow through the Vite proxy on the current source, port 8081: see CHANGELOG `MVP Stage 4` for the list of status codes checked.
- **Not verified:** UI click-through of forms, dialogs, stale-version recovery, and reload. No E2E run (Stage 6).
- **Deviations:**
  - Task list shows the first 50 tasks, with no paging control. The API supports `page` and `size`.
  - Backend controllers are mounted at the root, not `/api/v1` as API_CONTRACT and DECISIONS state. The frontend calls `/api` and the dev proxy strips the prefix. Open decision recorded in CHANGELOG.
- **Environment note:** the Compose `api` container on 8080 predates the current code. Local verification used `./mvnw spring-boot:run` with the `dev` profile on 8081 against the Compose MySQL.

### 2026-11-03 — Stage 4 follow-up: `/api/v1`, lint, browser verification, demo script

- **Built:** all REST routes moved under `/api/v1` (`shared/web/ApiVersionConfig.java`); security matchers, password-change exemptions, and backend tests updated to match. Frontend base URL is `/api/v1`; the Vite proxy forwards `/api` unchanged. `docs/DEMO_SCRIPT.md` rewritten for the local MVP.
- **Lint:** lazy page exports moved to `src/app/lazyPages.ts`; React Hook Form `watch` replaced with `useWatch` in five forms. `npm run lint` exits 0 with no warnings reported.
- **Verified:** `./mvnw -B verify` (89 tests, 0 failures). `npm run build` passes. Compose `api` rebuilt from current source; readiness `UP`; the four seeded accounts log in through `/api/v1`. No Docker volume removed; database not reset.
- **Verified in the built-in browser pane, React UI:** login (correct and wrong password), empty-field validation, redirect without session, student project and task create with `IN_PROGRESS` transition, revisions 1 and 2 submitted, admin supervisor assignment, faculty review start with changes requested and feedback, student sees the feedback, password change and restore, session end on reload.
- **Not verified in the browser:** student attempts at admin actions and cross-user access; transitions beyond `IN_PROGRESS`; `BLOCKED` and reset flows; project archive; faculty acceptance of revision 2; pagination past 50 tasks. Listed in `docs/DEMO_SCRIPT.md` §4.
- **Resolved deviation:** the root-route mapping recorded in the Stage 4 entry above is closed by the `/api/v1` migration.
- **Open:** sign-out sometimes needs a second attempt in the browser pane (not investigated). Stage 6 E2E run not started.
- **CI:** latest GitHub Actions run on `98e9b7b` succeeded (run 41591794252). The follow-up changes are not pushed, so they have no CI run yet.
