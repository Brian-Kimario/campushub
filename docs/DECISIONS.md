# CampusHub — Architecture Decision Records

**Purpose:** Records each significant decision, its context, the alternatives considered, and its consequences, so that a reviewer can see why the system is built as it is.
**Scope:** ADR-001 to ADR-024. ADR-001 to ADR-015 record the architecture defaults from the master blueprint. ADR-016 to ADR-024 record deviations from or additions to the blueprint (INDEX). Open owner questions remain in PRODUCT §8 (OD-1 to OD-10).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [INDEX.md](INDEX.md), [PRODUCT.md](PRODUCT.md) §8, [SCOPE.md](SCOPE.md), [RISKS.md](RISKS.md), [CHANGELOG.md](CHANGELOG.md).

## 1. Conventions

- **Status** is one of *Accepted*, *Accepted (deferred implementation)*, or *Superseded by ADR-nnn*. A superseded ADR is kept, never deleted.
- **Deviation** means the decision differs from or adds to the master blueprint. The blueprint remains the source of intent (INDEX).
- A change to an accepted ADR is a new ADR that supersedes it. The change appears in CHANGELOG.
- Each ADR names its evidence: where the rule is implemented, tested, or documented.

## 2. Index

| ADR | Title | Status | Deviation |
|---|---|---|---|
| 001 | Modular monolith on Spring Boot | Accepted | No |
| 002 | MySQL 8 with forward-only Flyway migrations and real-database tests | Accepted | No |
| 003 | JWT access tokens with HS256 and 15-minute lifetime | Accepted | No |
| 004 | Refresh tokens: decided in Phase 2, implemented in P1 | Superseded by ADR-025 | No |
| 005 | Password hashing with BCrypt cost 12 | Accepted | No |
| 006 | Denial mapping: 404 for invisible objects, 403 for permitted roles failing an object condition | Accepted | No |
| 007 | No break-glass administrator access to private files in MVP | Accepted | No |
| 008 | Private file storage behind a `FileStorage` port | Accepted | No |
| 009 | Frontend state: RTK Query for server state, local state and form libraries for forms | Accepted | No |
| 010 | Caching limited to the academic catalog | Accepted | No |
| 011 | Kafka and transactional outbox are optional, Phase 8, disabled by default | Accepted | No |
| 012 | API conventions: Problem Details, offset pagination, sort allowlists | Accepted | No |
| 013 | Time handling: UTC storage, local display, calendar range of 92 days | Accepted | No |
| 014 | Hosting choice deferred to Phase 10 against defined criteria | Accepted (deferred decision) | No |
| 015 | Verification catalog consolidated into parameterized suites | Accepted | No |
| 016 | Administrators approve proposals and assign one supervisor | Accepted | Yes |
| 017 | Soft deactivation and a last-administrator guard | Accepted | Yes |
| 018 | Sealed submission versions are immutable, enforced by service and database | Accepted | Yes |
| 019 | Token revocation list and bulk revocation timestamp | Accepted | Yes |
| 020 | Lockout and login rate limits, with a uniform locked response | Accepted | Yes |
| 021 | One role per user; administrator-provisioned accounts with forced password change | Accepted | Yes |
| 022 | Team and supervisor policy: team cap of five, eligible supervisors, audited assignment | Accepted | Yes |
| 023 | Audit log is append-only and every transition is audited in the same transaction | Accepted | Yes |
| 024 | Late submission off by default, enabled per milestone by the supervisor and audited | Accepted | Yes |
| 025 | Refresh sessions in an HttpOnly cookie with rotation and family revocation (supersedes ADR-004) | Accepted | Yes |

## 3. Architecture defaults (ADR-001 to ADR-015)

### ADR-001 Modular monolith on Spring Boot

- **Context.** One team, one term, one demonstration host. Microservices add deployment and failure modes the capstone cannot test thoroughly.
- **Decision.** One deployable Spring Boot 3.x application on Java 21 LTS, organized into feature modules (identity, catalog, projects, tasks, submissions, reviews, skills, dashboards, audit). Modules communicate through application services, not through each other's repositories. No microservices.
- **Consequences.** Module boundaries are checked by ArchUnit (ARCHITECTURE §4.1, TEST_STRATEGY §2). A later split would be a new ADR.
- **Evidence.** ArchUnit rule in CI_CD §3; ARCHITECTURE §4.

### ADR-002 MySQL 8 with forward-only Flyway migrations and real-database tests

- **Context.** Constraints, triggers, and row locking are central to the integrity rules. An in-memory substitute database behaves differently for these.
- **Decision.** MySQL 8 is the only supported database. Flyway migrations V1 to V8 are forward-only; an applied migration is never edited. Hibernate runs with `ddl-auto=validate`. Identifiers are `BINARY(16)` UUIDs and timestamps are `DATETIME(6)` in UTC (DATA_MODEL). Tests run against MySQL 8 through Testcontainers.
- **Consequences.** A schema correction is always a new migration. Phase 1 needs a running Docker daemon for Testcontainers (RISKS R-16).
- **Evidence.** DATA_MODEL §11; TEST_STRATEGY §2; DEPLOYMENT §5.1.

### ADR-003 JWT access tokens with HS256 and 15-minute lifetime

- **Context.** The API is stateless for most requests, and the client is a single-page application.
- **Decision.** Access tokens are HS256-signed JWTs with a 15-minute lifetime. Claims: `sub`, `iss=campushub`, `aud=campushub-api`, `iat`, `exp`, `jti`, and `role`. The API rejects tokens with a wrong algorithm, issuer, audience, or signature. The signing key is `CAMPUSHUB_JWT_SECRET`, at least 32 bytes.
- **Consequences.** Rotation of the key signs out every user (OPERATIONS §4.2). Revocation uses ADR-019.
- **Evidence.** SECURITY §3; TEST_STRATEGY §4.4 (`TokenValidationTest`).

### ADR-004 Refresh tokens: decided in Phase 2, implemented in P1

- **Context.** Refresh tokens with rotation and reuse detection are valuable but are not needed for the P0 demonstration. The blueprint asks for the decision in Phase 2.
- **Decision.** MVP issues access tokens only. A user signs in again after 15 minutes. Refresh tokens with rotation and reuse detection are designed here and implemented in P1 (SCOPE §3). The design stores refresh tokens hashed, in a separate table, and revokes the whole family on reuse.
- **Consequences.** Users are asked to sign in more often during the MVP. The risk is recorded (RISKS R-19).
- **Status.** Superseded by ADR-025. The session is restored from the refresh cookie after a reload.
- **Evidence.** SCOPE §3 and §5; SECURITY §3; TEST_STRATEGY §2 (Refresh Session cases are written in P1).

### ADR-005 Password hashing with BCrypt cost 12

- **Context.** Passwords must resist offline cracking after a database leak.
- **Decision.** Passwords are hashed with BCrypt at cost 12. Argon2id is an accepted substitute if the library is available in the chosen dependency set; the choice is a single configuration point. Plaintext passwords are never stored or logged. Temporary passwords are shown once at creation (US-ADM-01).
- **Consequences.** Cost 12 adds about 250 ms per hash on the dev host. Login rate limits (ADR-020) limit the cost of abuse.
- **Evidence.** SECURITY §3; hash-format test in TEST_STRATEGY §4.4.

### ADR-006 Denial mapping: 404 for invisible objects, 403 for permitted roles

- **Context.** A 403 on a private project reveals that it exists. Role-only routes can safely return 403 because the role is not private information.
- **Decision.** For object-level routes, a caller who cannot see the object receives 404 (concealment). A caller whose role is permitted but who fails an object condition receives 403 `FORBIDDEN_STATE`. Role-only routes return 403 `FORBIDDEN_ROLE` for any ID, including nonexistent ones. The role check runs first (STATE_MACHINES §1).
- **Consequences.** Denial responses must be identical for existing and nonexistent IDs (TEST_STRATEGY §4.2). The UI does not explain which denial occurred (UX_UI §9).
- **Evidence.** AUTHORIZATION_MATRIX §1 and §3; NS-02 and NS-05 in USER_STORIES §5.

### ADR-007 No break-glass administrator access to private files in MVP

- **Context.** Administrators manage accounts and approve projects, but routine access to a student's private submission file is not needed for those tasks. A break-glass path needs a policy this project does not have.
- **Decision.** Administrators receive 404 for private submission files (NS-12). There is no break-glass path in MVP. The deferral is stated in the demonstration (DEMO_SCRIPT §3.5).
- **Consequences.** An administrator who must see a file for a legal or safeguarding reason needs a policy and a new ADR first (SCOPE §2 out-of-scope list).
- **Evidence.** AUTHORIZATION_MATRIX §4 (NS-12); PRODUCT §3 (administrator row).

### ADR-008 Private file storage behind a `FileStorage` port

- **Context.** Submission files must not be reachable by URL, and the storage choice may change.
- **Decision.** Files are stored on a private volume under `CAMPUSHUB_STORAGE_ROOT` through a `FileStorage` port. Storage keys are random UUIDs; client filenames are sanitized and never used in paths. Accepted types are PDF, PNG, JPEG, and DOCX, checked by magic bytes. Limits: 20 MiB per file and 5 files per version. Downloads use `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff`. An S3-compatible adapter is a documented extension, not part of MVP.
- **Consequences.** Malware scanning is not in MVP (RISKS R-07). The port allows a scanning adapter later.
- **Evidence.** SECURITY §5; ARCHITECTURE §7; TEST_STRATEGY §4.5.

### ADR-009 Frontend state: RTK Query for server state, local state for forms

- **Context.** The client has server data (projects, tasks, submissions), a session, and many forms. Mixing them in one store makes state harder to reason about (RISKS R-08).
- **Decision.** React 18+ with TypeScript, Vite, and React Router. Redux Toolkit holds the auth session and project filters; RTK Query holds server data and cache. Forms use React Hook Form with Zod schemas. Proposal drafts are stored in `sessionStorage` keyed by user ID and cleared on logout (US-STU-02, UX_UI §6).
- **Consequences.** Server data is not copied into slices. Drafts are a per-viewer convenience, never the source of truth for submitted data.
- **Evidence.** ARCHITECTURE §9; UX_UI §6; SYLLABUS_MAPPING rows 1–4.

### ADR-010 Caching limited to the academic catalog

- **Context.** Caching private data without key isolation risks leakage between users.
- **Decision.** Caffeine through Spring Cache caches only the academic catalog (departments, terms, courses). Administrator changes evict the affected entries. No private data is cached.
- **Consequences.** Other reads hit the database; the query-count tests (TEST_STRATEGY §6.1) keep them bounded.
- **Evidence.** REQUIREMENTS §3 (catalog rows); TEST_STRATEGY §6.1; CI_CD §4 item 10.

### ADR-011 Kafka and transactional outbox are optional, Phase 8, disabled by default

- **Context.** Kafka adds operational weight and failure modes. The blueprint lists it as optional and as a Phase 8 extension.
- **Decision.** The outbox pattern and Kafka are Phase 8 work, P2. `CAMPUSHUB_KAFKA_ENABLED=false` by default. When enabled, events are written to an outbox table in the same transaction and relayed to Kafka. Consumers are idempotent through `processed_events`. Phase 8 is skipped entirely if it threatens P0 delivery (PHASE_PLAN §2, Phase 8 gate).
- **Consequences.** Notifications are not part of the MVP demonstration unless Phase 8 completes.
- **Evidence.** STATE_MACHINES §9; DEPLOYMENT §3; OPERATIONS §5; SYLLABUS_MAPPING rows 14–15.

### ADR-012 API conventions

- **Context.** Consistent errors and lists make the contract testable and the UI simpler.
- **Decision.** Errors follow RFC 9457 Problem Details with types `urn:campushub:problem:<code>`. Lists use offset pagination with size capped at 100 and sort fields from an allowlist per endpoint. The API is versioned under `/api/v1`.
- **Consequences.** A new error code must appear in API_CONTRACT §3 and UX_UI §9 (CI_CD §4 item 3).
- **Evidence.** API_CONTRACT §1 and §3; NFR-SEC-04; SYLLABUS_MAPPING row 9.

### ADR-013 Time handling: UTC storage, local display, 92-day calendar range

- **Context.** One institution in one time zone is assumed (A-1), but the zone is not yet confirmed (OD-6).
- **Decision.** All timestamps are stored in UTC. Display uses the user's local zone for the browser and the institution zone where a server-side value is shown. The calendar accepts ranges of 92 days or fewer. Tests use fixed instants, including a daylight-saving boundary.
- **Consequences.** Deadline display errors are a known risk until OD-6 is confirmed (RISKS R-17).
- **Evidence.** UX_UI §8; STATE_MACHINES §1; SYLLABUS_MAPPING row 7.

### ADR-014 Hosting choice deferred to Phase 10 against defined criteria

- **Context.** Budget and host preference are not known (OD-8).
- **Decision.** The team does not choose a host now. DEPLOYMENT §2 defines three options and ten pass-or-fail criteria. The default recommendation is a single VM with Docker Compose unless the budget covers a managed platform. The Phase 10 record states the choice and the evidence.
- **Consequences.** Phase 1 to 9 are written to be host-neutral. The release and rollback procedures assume one API instance (DEPLOYMENT §9).
- **Evidence.** DEPLOYMENT §2; PRODUCT A-7 and OD-8.

### ADR-015 Verification catalog consolidated into parameterized suites

- **Context.** The blueprint §23 catalog lists about 10,000 near-duplicate entries for 25 resources. Writing one test per entry would produce noise, not evidence.
- **Decision.** Each entry is classified as applicable, prohibited, redundant, or not applicable. Applicable entries with the same actor, action, and expected outcome become one parameterized case. Every entry is traceable through `backend/src/test/resources/verification-catalog/coverage.csv`. Resources that map to no MVP endpoint are marked not applicable with a reason (TEST_STRATEGY §7.2).
- **Consequences.** A reviewer checks the classification and the traceability file, not thousands of generated tests. Each excluded resource is named.
- **Evidence.** TEST_STRATEGY §7; blueprint §23 "Catalog interpretation".

## 4. Deviation ADRs (ADR-016 to ADR-024)

### ADR-016 Administrators approve proposals and assign one supervisor

- **Context.** The blueprint's student golden path says "faculty assignment/approval according to configured policy". The policy is not defined. Faculty-initiated approval adds a second approval path to test and explain (PRODUCT OD-1).
- **Decision.** An administrator approves or rejects each proposal and assigns exactly one supervisor, who must be an active faculty account (ADR-022). Faculty cannot create projects (PRODUCT OD-2, A-3). Each project has one supervisor (PRODUCT A-6).
- **Consequences.** The faculty golden path starts from an assigned project (GP-2). A multi-supervisor policy needs a new ADR.
- **Evidence.** API_CONTRACT §6 (approve, supervisor assignment); USER_STORIES US-ADM-03; GP-1 step 5.

### ADR-017 Soft deactivation and a last-administrator guard

- **Context.** Historical submissions and audit entries reference accounts. Hard deletion would break them. A deactivated administrator could also leave the system with no one able to manage accounts.
- **Decision.** Accounts are deactivated, not deleted (`users.status`). Catalog rows referenced by history are deactivated, not deleted (US-ADM-02). Deactivation takes effect on the next request (ADR-019). The last active administrator cannot be deactivated: the request returns 422.
- **Consequences.** A user who must be removed for legal reasons needs a separate process. Deactivated users appear in history with their name.
- **Evidence.** API_CONTRACT §4 (`POST /admin/users/{id}/deactivate`); DATA_MODEL §3.1; GP-3 step 8.

### ADR-018 Sealed submission versions are immutable, enforced by service and database

- **Context.** Review decisions refer to exact versions. The blueprint requires that submitted work cannot be changed, and a service-only check can be bypassed by a future bug.
- **Decision.** Once `sealed_at` is set, a submission version and its files cannot change. The service checks it first and returns 409 `VERSION_SEALED`. A MySQL `BEFORE UPDATE` trigger rejects changes to the immutable columns and status regressions. Files on a sealed version cannot be deleted through the API.
- **Consequences.** Corrections are made in a new version. Triggers are restored from backups as part of the database dump (OPERATIONS §6.1).
- **Evidence.** STATE_MACHINES §5 and §10; DATA_MODEL §7 (trigger note); NS-10.

### ADR-019 Token revocation list and bulk revocation timestamp

- **Context.** Logout, password reset, and deactivation must take effect before a 15-minute token expires (NS-08, FR-AUTH-04). Refresh tokens are not in MVP (ADR-004).
- **Decision.** Logout adds the token's `jti` to `revoked_tokens` until its `exp`. Password reset and deactivation set `users.revoked_after`; any token issued before that timestamp is rejected. The hourly purge removes expired revocation rows (OPERATIONS §5).
- **Consequences.** Every authenticated request checks the revocation list and the user's `revoked_after`. The check is part of the security test suite.
- **Evidence.** DATA_MODEL §3.1 and §4.1; SECURITY §3; OPERATIONS §5; TEST_STRATEGY §4.6.

### ADR-020 Lockout and login rate limits, with a uniform locked response

- **Context.** Password guessing is a primary threat. A locked response that differs from a wrong-password response reveals account state.
- **Decision.** Five consecutive failures lock the account for 15 minutes. A locked account returns the same `401 INVALID_CREDENTIALS` as a wrong password. Attempts during a lock do not extend it. Login is limited to 20 attempts per minute per IP, returning 429 with `Retry-After`. Rate limits are per node (RISKS R-09).
- **Consequences.** An attacker can keep a victim locked out. This is accepted for MVP and recorded as a risk.
- **Evidence.** SECURITY §3; STATE_MACHINES §7; TEST_STRATEGY §4.6.

### ADR-021 One role per user; administrator-provisioned accounts with forced password change

- **Context.** Students, faculty, and administrators have different navigation and permissions. Multiple roles per account would multiply the authorization matrix (PRODUCT A-2, OD-7).
- **Decision.** Each user has exactly one role (STUDENT, FACULTY, or ADMIN), stored as `users.role` with a CHECK constraint. Accounts are created by administrators; there is no self-registration. A temporary password is shown once at creation and the user must change it on first sign-in.
- **Consequences.** A person who is both a faculty member and a student needs two accounts or a new ADR. Role changes by the user are refused (NS-03).
- **Evidence.** DATA_MODEL §3.1 (`role`); UX_UI §2 and §3; GP-3 steps 3–4.

### ADR-022 Team and supervisor policy: team cap of five, eligible supervisors, audited assignment

- **Context.** The blueprint leaves the team size and supervisor eligibility to the owner (OD-3). Unbounded teams make reviews and task boards hard to manage.
- **Decision.** A team has at most five members including the owner (OD-3). A supervisor must be an active FACULTY account (422 `SUPERVISOR_NOT_ELIGIBLE` otherwise). Supervisor assignment and reassignment are audited. The seventh membership returns 409 `TEAM_FULL`.
- **Consequences.** The limit is a configuration candidate later, but the MVP value is fixed in code and in the tests (TEST_STRATEGY §8).
- **Evidence.** API_CONTRACT §6 (`PUT /projects/{id}/supervisor`); PRODUCT OD-3; TEST_STRATEGY §8.

### ADR-023 Audit log is append-only and every transition is audited in the same transaction

- **Context.** The administrator must be able to investigate actions (US-ADM-04), and the record must not be rewritten by the application.
- **Decision.** Each state transition writes its audit event in the same database transaction as the change (STATE_MACHINES §1). The runtime database user has `INSERT` and `SELECT` on `audit_events` only. Audit metadata uses an allowlist of keys and never contains passwords, tokens, or file contents (SECURITY §8).
- **Consequences.** A transition whose audit write fails is rolled back. The audit table grows, and its capacity alert is in OPERATIONS §8.
- **Evidence.** ARCHITECTURE §6; DATA_MODEL §4.2 and privileges note; STATE_MACHINES §1 rule 4.

### ADR-024 Late submission off by default; enabled per milestone by the supervisor and audited

- **Context.** Deadlines matter for review, but students also need an explicit exception path (US-FAC-04, PRODUCT OD-4).
- **Decision.** Submissions after a milestone's due date are refused with 422 `DEADLINE_PASSED`. The supervisor can set `allowLate` on a specific milestone. The change is audited, and the submission records that it was late.
- **Consequences.** A late exception is visible in the audit log and on the submission. The exception is not granted by the student.
- **Evidence.** NS-06; STATE_MACHINES §5 (V1 preconditions); API_CONTRACT §7.

### ADR-025 Refresh sessions in an HttpOnly cookie with rotation and family revocation (supersedes ADR-004)

- **Context.** Holding the access token in memory only signed the user out on every reload. Storing a long-lived token in localStorage would expose it to any script on the page.
- **Decision.** Login and refresh set an opaque 32-byte refresh token as an HttpOnly, SameSite=Strict, Secure cookie scoped to `/api/v1/auth`, with an absolute lifetime of 8 hours. The server stores only its SHA-256 hash in `refresh_sessions` (V7). Each refresh rotates the token. Reusing a rotated token revokes its whole family and is audited as `auth.refresh_reuse_detected`. Logout revokes the family and clears the cookie. A password change revokes every family for the user. The cookie endpoints (`/auth/refresh`, `/auth/logout`) require the `X-Requested-With: CampusHub` header. The access token stays in memory and is still a 15-minute JWT (ADR-003). The frontend restores the session once per page load through a single-flight refresh.
- **Consequences.** A reload keeps the user signed in. Expired access tokens are replaced without a sign-in. The cookie is sent only to the auth path, so the refresh token is not sent on API calls. A rotation that fails returns an empty result, not an exception, so the revocation commits. Expired rows are purged hourly. The Secure flag is on by default. Browsers that refuse Secure cookies on plain-HTTP localhost need `campushub.refresh.cookie-secure=false` for that local run.
- **Evidence.** API_CONTRACT §2; SECURITY §3; `AuthFlowIntegrationTest` refresh cases; ADR-004 (superseded).

## 5. Owner decisions that remain open

ADRs do not close owner questions. The following remain in PRODUCT §8 and are listed here so the record is complete:

| Item | Status | Affects |
|---|---|---|
| OD-5 Faculty visibility of student skills and certifications | Default: not visible; confirm before P1 | US-STU-09, US-STU-10 |
| OD-6 Institution time zone | Default: UTC; provide the real zone | ADR-013 |
| OD-8 Hosting provider and budget | Open until Phase 10 | ADR-014 |
| OD-9 Licensing and code ownership | Open | CI_CD §4 item 15 |
| OD-10 Kafka required or stretch | Default: stretch | ADR-011 |
