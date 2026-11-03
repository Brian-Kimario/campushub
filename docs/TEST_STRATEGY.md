# CampusHub — Test Strategy

**Purpose:** Defines what is tested, at which layer, with which tools, which gates block a change, and how the 10,000-entry verification catalog in the blueprint (§23) becomes a bounded set of automated suites.
**Scope:** Backend (Spring Boot), frontend (React), database migrations, the HTTP contract, accessibility, performance, and the security suite. Applies to every phase from Phase 1 onward.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md) (source of the security suite), [CI_CD.md](CI_CD.md) (where gates run), [SECURITY.md](SECURITY.md) §12, [USER_STORIES.md](USER_STORIES.md) (golden paths), [PRODUCT.md](PRODUCT.md) §5 (success measures), [DECISIONS.md](DECISIONS.md) ADR-015.

## 1. Principles

1. **Every security rule has a test that can fail.** A rule without a test is not a rule. The authorization matrix is executable, not descriptive.
2. **Test the behavior, not the layer count.** Prefer a test at the lowest layer that proves the behavior. Use the database layer when the behavior depends on MySQL (constraints, triggers, locking).
3. **Real MySQL, not an H2 substitute.** Persistence, Flyway migrations, triggers, and locking are tested against MySQL 8 through Testcontainers (ADR-002).
4. **Deterministic by default.** No test depends on wall-clock time, network access to the internet, or test order. Time is injected through a `Clock` bean.
5. **Skipping a security test is a failure.** A test marked disabled, skipped, or tagged as known-failing in the security suite fails the pipeline (SM-5).
6. **A percentage does not hide a gap.** Coverage is a floor for review, not a goal in itself (blueprint §15). Missing critical cases fail review even when coverage is high.

## 2. Test layers

| Layer | Tool | What it proves | Runs in | Blocks |
|---|---|---|---|---|
| Unit | JUnit 5, AssertJ | State machine guards (STATE_MACHINES), validation rules, policy functions, DTO mapping rules | PR | PR merge |
| Web slice | Spring MockMvc with security filter chain | Authentication, role checks, request validation, Problem Details shape, status codes per API_CONTRACT | PR | PR merge |
| Persistence and migration | JUnit 5 + Testcontainers MySQL 8 | Flyway V1–V8 apply on an empty database and on the previous version; Hibernate `validate` passes; constraints (duplicate membership, duplicate review, duplicate submission) hold; triggers block sealed-version changes | PR | PR merge |
| Transaction and concurrency | JUnit 5 + Testcontainers, parallel threads | Submission and review transactions are atomic; duplicate actions produce one winner; `@Version` conflicts return `STALE_VERSION` | PR | PR merge |
| Architecture | ArchUnit | Module dependency rules (ARCHITECTURE §4.1); no cycles between feature modules; controllers do not reach repositories | PR | PR merge |
| Contract | OpenAPI snapshot diff; generated client compile | Served OpenAPI matches the committed snapshot; every endpoint in API_CONTRACT has a route and a security test (API_CONTRACT §15) | PR | PR merge |
| Security suite | JUnit 5 parameterized from the matrix, plus file and token tests | Every matrix row returns the status and `code` it declares (§4) | PR | PR merge |
| Frontend unit and component | Vitest, React Testing Library, MSW | Forms and Zod schemas; status and role-specific navigation; the five states of each screen (UX_UI §4); error-code copy coverage (§5.4) | PR | PR merge |
| End-to-end | Playwright against the packaged app and MySQL | Golden paths GP-1 to GP-3 (USER_STORIES); forbidden and not-found paths; file upload and download | PR (smoke subset), main (full) | PR merge (smoke); main gate |
| Accessibility | axe-core through Playwright; keyboard scripts | NFR-A11Y-01 on every primary screen (§5.2) | PR (smoke), main (full) | PR merge |
| Performance | k6 against the seed dataset; Hibernate statistics in integration tests; `EXPLAIN` snapshots | SM-6 latency; query-count limits (NFR-PERF-01); bounded plans (§6) | Phase 9 measurement; query-count and plan tests on PR | Query-count and plan tests block PR; SM-6 is measured, not gated per PR |
| Static and dependency | SpotBugs with find-sec-bugs, ESLint security rules, CodeQL, OWASP Dependency-Check, `npm audit --omit=dev`, gitleaks | SECURITY §10 and §12 | PR (except CodeQL on main) | Per CI_CD §3 |

Tests run through a single local entry point, `scripts/verify`, created in Phase 1. It runs the same steps as the PR pipeline in CI_CD §2, in the same order, so a developer machine and the CI runner give the same answer (NFR-TEST-01).

## 3. Coverage and quality gates

| Gate | Threshold | Scope | Enforcement |
|---|---|---|---|
| Line coverage, backend | ≥ 80% | Packages `*.application` and `*.domain` in every feature module | JaCoCo rule in `mvn verify` |
| Branch coverage, backend policy code | ≥ 90% | Packages `*.policy` | JaCoCo rule; the matrix rows must also pass (§4) |
| Line coverage, frontend | ≥ 70% | `src/features/**` excluding generated code | Vitest coverage threshold |
| Matrix rows | 100% of rows in AUTHORIZATION_MATRIX §3 that have a test | Security suite | Build fails if a row has no test (the test names are listed in §4.3) |
| NS journeys | 12 of 12 (NS-01 to NS-12) | AUTHORIZATION_MATRIX §4 | Each has a named test |
| Skipped or disabled tests | 0 in the security suite | Tests tagged `security` | CI rejects `@Disabled` or `test.skip` on tagged tests |
| Critical or high static or dependency findings | 0 without an accepted exception | SECURITY §10 | CI_CD §3 |

Coverage is reported per module and per critical path in the CI summary, not only as a global number (blueprint §15).

## 4. Security suite

### 4.1 Source and shape

- The suite is generated from the rows of AUTHORIZATION_MATRIX §3. Each row lists the endpoint, the actors, the expected denial, and the expected `code`.
- The implementation is one parameterized JUnit test per matrix section, with a data source that reads the rows from a versioned CSV at `backend/src/test/resources/security/authorization-matrix.csv` (committed; generated from the markdown table by a script in Phase 9 and checked by the build). Keeping the data in one file means a matrix change and a test change arrive in the same pull request (AUTHORIZATION_MATRIX §6).
- Each actor is a fixture: `SO`, `SM`, `SI`, `SX`, `FS`, `FX`, `AD`, plus `ANON` (no token), `EXPIRED`, `REVOKED`, and `DEACTIVATED` (AUTHORIZATION_MATRIX §2).
- Fixtures create real rows in the Testcontainers database. The suite does not mock the policy layer.

### 4.2 Assertions for every row

1. Status code equals the matrix value (`200`, `201`, `401`, `403`, `404`, `409`, `422`, and so on).
2. Problem Details `code` equals the matrix value; `type` is `urn:campushub:problem:<code lowercase>` (API_CONTRACT §3).
3. For denials that are 403R, the response is the same for every ID, including IDs that do not exist.
4. For 404 on private objects, the response body (after removing `instance` and `timestamp`) is byte-identical to the response for a nonexistent ID of the same kind, and no field names or counts differ.
5. No side effect: for denied writes, the row count and the `updated_at` and `version` of the target are unchanged, and no audit row is written for the denied attempt except where SECURITY §8 requires one.

### 4.3 Concealment timing

Timing is checked for the 404 concealment rule (AUTHORIZATION_MATRIX §6). For each concealed route:

- Measure 100 requests for an existing private object the caller cannot see and 100 for a nonexistent ID, after 20 warm-up requests.
- Pass when the difference of the medians is ≤ 10 ms and the difference of the 95th percentiles is ≤ 25 ms.
- A failing run is repeated once. Two failing runs fail the build and must be investigated; they are not retried further and the tolerance is not widened without an ADR.

The tolerance is set for a shared CI runner. It is recorded here so that a later reviewer can judge a change to it.

### 4.4 Minimum security cases

The blueprint's minimum list (§15) maps to the suite as follows.

| Blueprint minimum case | Covered by |
|---|---|
| No token → 401 | Matrix §1 step 1; every route, `ANON` actor |
| Malformed token → 401 | `TokenValidationTest`: malformed, `alg=none`, wrong `iss`, wrong `aud`, wrong signature |
| Expired token → 401 | `TokenValidationTest`: `exp` in the past |
| Wrong role → 403 | Matrix role-only rows, `403R` |
| Right role but wrong project → 403 or 404 per concealment | Matrix rows with `404` and `403S`; NS-02, NS-05 |
| Student attempts faculty review → denied | NS-04 pattern; review rows for `STUDENT` actors |
| Faculty reviews unassigned project → denied | NS-05 |
| Filename path traversal → denied | NS-07 |
| Oversized upload → 413 or documented validation error | `FileUploadSecurityTest`: 20 MiB + 1 byte returns `FILE_TOO_LARGE` (422); a body above the multipart limit returns 413 |
| Malicious MIME mismatch → denied | `FileUploadSecurityTest`: PDF extension with PNG bytes, ZIP renamed to DOCX, script in a PDF-named file |
| Stale optimistic-lock update → 409 | NS-09 |
| Duplicate membership → 409 | `DUPLICATE` on invitation accept and membership insert (constraint test) |
| Revoked user → denied | NS-08; `REVOKED` and `DEACTIVATED` actors |

### 4.5 File security tests

- Magic-byte allowlist: each accepted type (PDF, PNG, JPEG, DOCX) accepted with its true bytes; each rejected type rejected (SECURITY §5).
- Storage key: a fresh UUID per file; no part of the client filename appears in the storage path.
- Download: `Content-Disposition: attachment` and `X-Content-Type-Options: nosniff` present; a path-traversal file ID returns 404.
- Cleanup: a failed metadata commit removes the stored bytes (ARCHITECTURE §12 compensation); a test forces the failure after the write.
- Limits: 5 files per version; the sixth returns `FILE_LIMIT_EXCEEDED`.

### 4.6 Token, lockout, and logging tests

- Lockout: 5 failures lock the account for 15 minutes; the locked response is the same `401 INVALID_CREDENTIALS` as a wrong password (SECURITY §3); attempts during the lock do not extend it.
- Rate limit: the 21st login in one minute from one IP returns 429 with `Retry-After`.
- Revocation: logout revokes the `jti`; password reset and deactivation set `revoked_after` and reject older tokens (DATA_MODEL §3.1).
- Logging: a log-capture test sends a request with a known password, token, and email; no captured line contains the password, the token, or the `Authorization` header, and the email is masked at INFO (SECURITY §8).

## 5. End-to-end, frontend, and accessibility

### 5.1 Golden paths and forbidden paths

- GP-1 (student), GP-2 (faculty), GP-3 (administrator) are E2E tests named after their USER_STORIES identifiers. Each runs against the packaged application with the dev seed and asserts the steps listed in DEMO_SCRIPT.
- Forbidden paths: each role attempts one route from §3.1 of UX_UI it cannot use and sees the forbidden or not-found page from UX_UI §4.5.
- SM-1 (golden path without manual database edits) is verified by a run that uses only the UI and the API; the test fails if it calls a repository or SQL directly.

### 5.2 Accessibility

- axe-core runs on every primary screen (UX_UI §5, one row per screen) in the default and mobile viewports. Zero violations at WCAG 2.1 AA tags are allowed. Known exceptions require an entry in this file with a date and an owner.
- Keyboard test per primary screen: start at the skip link, reach every interactive element with Tab and Shift+Tab, activate each with Enter or Space, close each dialog with Escape, and confirm focus returns to the trigger. The script fails on a keyboard trap.
- Reflow test at 320 px width and 200% zoom: no horizontal page scroll outside a designated scroll container (UX_UI §11).
- Reduced motion: with `prefers-reduced-motion: reduce`, no animation runs longer than 0.01 s.

### 5.3 Frontend component tests

- Each form in UX_UI §6 has tests for its Zod schema (valid, boundary, and invalid values for each limit) and for server field errors attached to the named field.
- Each screen has tests for loading, empty, error with retry, success, and forbidden (UX_UI §4). A screen that cannot reach one of the five states documents why in its test file.
- Role-specific navigation: each role sees exactly the items in UX_UI §3.1.

### 5.4 Error copy coverage

A unit test reads the codes in API_CONTRACT §3 and the message table in UX_UI §9. Each code must appear in UX_UI §9 with a message or an explicit "not shown to users" entry. A code added to the API without a row fails the build.

## 6. Performance and query plans

### 6.1 Query-count limits (NFR-PERF-01)

- Integration tests for every list endpoint assert the maximum number of SQL statements per request using Hibernate statistics. The limit is the number of statements for the fixture at its small size plus one, and does not grow with the number of rows (no N+1).
- A test with 5 and 50 rows asserts the same statement count for both.

### 6.2 Query-plan snapshots

- Every list query in DATA_MODEL §10 (list performance) has an `EXPLAIN FORMAT=JSON` snapshot stored under `backend/src/test/resources/query-plans/`.
- The test fails when the plan uses a full table scan on a table with more than 1,000 rows in the seed, or when the chosen index differs from the snapshot. A deliberate change updates the snapshot in the same pull request, with a reason in the commit message.

### 6.3 SM-6 measurement (Phase 9)

- Seed dataset: about 2,000 projects and 20,000 tasks, generated by a seeded, deterministic script (no real personal data). The generator and its seed value are committed.
- Tool: k6 scripts, one per list endpoint, with 10 virtual users for 3 minutes after a 30 second ramp.
- Measure p95 latency of each list endpoint at that size on the dev host. Target: ≤ 300 ms (SM-6).
- Results go to IMPLEMENTATION_LOG with date, host description, git commit, dataset seed, and raw summary. A miss is recorded in RISKS and the log; the target is not changed (PRODUCT SM-6 note).

## 7. Verification catalog (ADR-015)

The blueprint's §23 catalog lists 25 resources and about 10,000 near-duplicate verification entries. The catalog is treated as a threat-modeling inventory, not as a test count (blueprint §23 "Catalog interpretation").

### 7.1 Method

1. **Inventory.** Each of the 25 resources is listed with its entries in [DECISIONS.md](DECISIONS.md) ADR-015 and in the coverage record described in §7.3.
2. **Classify.** Each entry is marked as one of:
   - **Applicable:** a behavior the MVP implements. It becomes a case in a parameterized suite (§4, §5, §6, or the layer tests in §2).
   - **Prohibited:** a behavior that must never happen. It becomes a negative case in a suite, usually a security case.
   - **Redundant:** the same behavior as an applicable entry for another resource, covered by the same parameterized case.
   - **Not applicable:** the resource or behavior is not an MVP endpoint or is deferred. The reason is recorded.
3. **Consolidate.** Applicable entries sharing the same actor, action, and expected outcome are merged into one parameterized case with a table of inputs.
4. **Trace.** Each consolidated case names the catalog entries it covers, so a reviewer can check any single entry.

### 7.2 Scope by resource

| Resource (blueprint §23) | Treatment in MVP |
|---|---|
| Student Profile | Applicable (`/users/me`, `/users/{id}`); P0 |
| Faculty Profile | Applicable (same endpoints, faculty actor); P0 |
| Academic Term | Applicable (`/catalog/terms`, `/admin/catalog/terms`); P0 |
| Department | Applicable (`/catalog/departments`); P0 |
| Course | Applicable (`/catalog/courses`); P0 |
| Project Proposal | Applicable (`/projects`, `/projects/{id}`, approve, reject, archive); P0 |
| Project Membership | Applicable (members, invitations; no leave or revoke endpoint in MVP); P0 |
| Project Milestone | Applicable (`/projects/{id}/milestones`); P0 |
| Project Task | Applicable (`/projects/{id}/tasks`, `/tasks/{id}`); P0 |
| Task Comment | Applicable; P1 (FR-TASK-05) |
| Submission | Applicable (`/milestones/{id}/submission`); P0 |
| Submission Attachment | Applicable (file endpoints); P0 |
| Faculty Review | Applicable (`/submissions/{id}/versions/{n}/review`); P0 |
| Skill Catalog | Applicable (`/skills`); P1 |
| Student Skill | Applicable (`/users/me/skills`); P1 |
| Certification | Applicable (`/users/me/certifications`); P1 |
| Activity Event | Not applicable in MVP. No activity endpoint is in API_CONTRACT; the feed is P1 (SCOPE §2) and gets its own cases if the endpoint is added |
| Audit Event | Applicable (`/admin/audit-events`, write-side checks through the audited actions list in SECURITY §8); P0 |
| Dashboard Aggregate | Applicable (`/dashboard/*`); P0 for read-scope checks, Phase 7 |
| Calendar Event | Applicable (`/calendar`); P0 |
| Refresh Session | Deferred with refresh tokens (ADR-004). Cases are written in P1 |
| Notification Event | Not applicable in MVP. P2, optional (ADR-011) |
| Outbox Event | Not applicable in MVP. P2, optional (ADR-011); outbox behavior is tested in Phase 8 only |
| Processed Event | Not applicable in MVP. P2 consumer idempotency, tested in Phase 8 only |
| Project Archive | Applicable (`POST /projects/{id}/archive`, admin only); P0 |

### 7.3 Coverage record

- The classification of each catalog entry is kept as a CSV at `backend/src/test/resources/verification-catalog/coverage.csv`, with the columns `entryId`, `resource`, `classification`, `suite`, and `reason`. It is generated in Phase 9 and checked by a test that fails if an applicable entry has no suite.
- The CSV is the detailed trace; this file holds the rules and the resource scope.

## 8. Concurrency and transaction tests

| Scenario | Expected |
|---|---|
| Two members change the same task with the same `version` | One succeeds; the other returns 409 `STALE_VERSION` (NS-09) |
| Two members accept the same invitation at the same time | One membership row; the other returns 409 `DUPLICATE` or 409 `INVALID_TRANSITION` |
| Two reviews of one version | One review row (`UNIQUE` constraint); the other returns 409 `REVIEW_EXISTS` |
| Submit a version while an upload runs | Submit waits on the row lock; the upload is either in the sealed version or rejected with `VERSION_SEALED`, never half-applied |
| Team size at the limit | Two accepts with one seat left: one succeeds; the other returns 409 `TEAM_FULL` |
| Failure between file write and metadata commit | No metadata row; stored bytes removed |

Tests use `CountDownLatch` barriers with at least 20 repetitions per scenario, because a single run rarely exposes a race.

## 9. Test data and fixtures

- Fixtures are generated in code, not copied from production. No real student or staff data is used anywhere in the repository or in CI.
- The dev seed (`CAMPUSHUB_DEV_SEED_ENABLED=true`) creates the demo accounts for GP-1 to GP-3 with test passwords defined in the seed file. These values are for local and demo use only; the production profile refuses to start with the seed enabled (SECURITY §9).
- Test passwords and tokens are not repeated in documentation or logs.
- Each integration test class starts with an empty schema from Flyway, so migration history is exercised on every run.

## 10. Flaky tests and failure policy

- A test that fails once and passes on rerun is reported as flaky in the CI summary and is opened as an issue with the test name. It is not silently retried in CI beyond one automatic rerun.
- A security suite test that becomes flaky is treated as a failure, not as noise. The cause is fixed or the test is rewritten; it is not disabled (§1 principle 5).
- Time-dependent tests use the injected clock. A test that needs a daylight-saving boundary uses a fixed instant (FR-CAL-02).

## 11. Reproducibility

- Pinned tool versions: Java 21 toolchain, Maven wrapper, Node LTS per `.nvmrc`, MySQL 8 image by digest in test configuration.
- Testcontainers requires a running Docker daemon. The local setup guide (README) states this. CI uses the runner's Docker.
- `scripts/verify` runs the same commands as the PR pipeline and exits non-zero on the first failing gate.
- Test results are kept as CI artifacts for 14 days: JUnit XML, JaCoCo HTML, Playwright traces for failures, axe JSON reports.

## 12. Not in MVP

- Load tests beyond SM-6 (ten virtual users).
- Mutation testing.
- Visual regression snapshots. Layout is checked through the responsive rules in UX_UI §11 and the reflow test in §5.2.
- Contract tests with an independently versioned consumer. Pact is used only if such a boundary appears (blueprint §15).
- Penetration testing by an external party. A manual ASVS Level 1 review is done in Phase 10 (SECURITY §12).
