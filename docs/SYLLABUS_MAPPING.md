# CampusHub — Syllabus Mapping

**Purpose:** Maps each Full Stack-II concept in the blueprint to the CampusHub feature that demonstrates it, the evidence that proves it, the phase that delivers it, and the decision that governs its scope.
**Scope:** The concept table in blueprint §2. Evidence means a working use case plus a test or a recorded demonstration, not a library listed in a build file (blueprint §2 note).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [DEMO_SCRIPT.md](DEMO_SCRIPT.md) (where each item is shown), [TEST_STRATEGY.md](TEST_STRATEGY.md), [PRODUCT.md](PRODUCT.md), [SCOPE.md](SCOPE.md), [DECISIONS.md](DECISIONS.md).

## 1. Count note

Blueprint §2 lists **20 concept rows**, not the 23 mentioned in the Phase 0 brief. This file maps all 20 rows. No additional concepts were invented to reach 23; if the brief's count came from another source, the owner should name the three extra concepts and they will be added here with evidence.

## 2. Mapping

Status values: **P0** (must deliver), **P1** (deliver if P0 stable), **P2** (stretch), **N/A-documented** (intentionally not used; the reason is recorded in DECISIONS).

| # | Concept (blueprint §2) | CampusHub feature | Working use case | Evidence to show | Phase | Status | Governing decision |
|---|---|---|---|---|---|---|---|
| 1 | React components and hooks | Dashboard, proposal form, project detail | Student dashboard lists assigned projects; project detail composes reusable status, member, and milestone components | Controlled form with React Hook Form and Zod; `useEffect` for data loading with cancellation; reusable `StatusBadge` and `DataTable` components covered by Vitest component tests | 1, 2, 4 | P0 | ADR-009 (frontend stack) |
| 2 | Draft persistence | Project proposal draft | Student starts a proposal, reloads the page, finds the draft restored, edits, and discards it | Draft saved to `sessionStorage` keyed by user ID and cleared on logout (USER_STORIES US-STU-02, UX_UI §6); component test for restore, edit, and discard | 4 | P0 | ADR-009 (draft storage) |
| 3 | Redux Toolkit | Auth session, project filters, notification state | Sign-in stores the session in a slice; project list filters persist across navigation | Slices for `auth` and `projectFilters`; RTK Query for server state; tests for reducers and the async flow | 2, 4 | P0 | ADR-009 |
| 4 | Memoized selectors | Filtered task lists and progress summaries | Task board filters by assignee and status without recomputing on unrelated state changes | `createSelector` selectors for filtered tasks and milestone progress; profiling note in IMPLEMENTATION_LOG showing render counts before and after | 5 | P0 | ADR-009 |
| 5 | JWT authentication | Sign-in and every protected API call | Valid token reaches protected routes; invalid, malformed, and expired tokens return 401 | `TokenValidationTest` covers valid, expired, malformed, `alg=none`, wrong `iss`, wrong `aud`, wrong signature (TEST_STRATEGY §4.4); HS256 with 15-minute access tokens (ADR-003) | 2 | P0 | ADR-003, ADR-004 |
| 6 | RBAC | Student, faculty, and administrator features | Each role sees and can call only its own routes; denials follow ADR-006 | Positive and negative tests for every row of AUTHORIZATION_MATRIX §3; NS-01 to NS-12 named tests | 2, 4–7, 9 | P0 | ADR-006, ADR-007 |
| 7 | Calendar | Project deadlines, milestones, and assigned tasks | Student views a month and a week schedule; dates show in local time; storage is UTC | `GET /calendar` with a range of 92 days or fewer; tests across a daylight-saving boundary using a fixed instant; frontend tests for month and week views | 5 | P0 | ADR-013 (time handling) |
| 8 | UI performance | Task board and project lists | Long lists render without jank; routes load on demand | Lazy routes per feature; list virtualization for tables over 200 rows; render-count and load-time measurements in IMPLEMENTATION_LOG; memoized rows | 4, 5, 9 | P0 | UX_UI §13 |
| 9 | Pagination and sorting | Projects, tasks, and submissions lists | Lists page by offset (size up to 100) with stable sort | Sort allowlists per endpoint (NFR-SEC-04); a test that the same page and sort return the same order twice; rejection of unknown sort fields with 400 | 3–6 | P0 | ADR-012 (pagination) |
| 10 | Query optimization | Project dashboard | The dashboard loads in a fixed number of queries regardless of project count | Hibernate statistics test: the same statement count at 5 and 50 rows (TEST_STRATEGY §6.1); indexes checked by `EXPLAIN` snapshots (TEST_STRATEGY §6.2) | 3, 9 | P0 | DATA_MODEL §10 |
| 11 | Caching | Academic catalog (departments, terms, courses) | Catalog reads are served from cache; an administrator change invalidates the cache | Caffeine via Spring Cache; test that an update evicts the entry; test that private data is not cached; catalog keys carry no user data | 3 | P0 | ADR-010 |
| 12 | Method-level security | Review and approval operations | Supervisor review and administrator approval are guarded at the service layer as well as at the route | Role check annotation on each service method plus the object policy call; a test that calls the service directly without the route and is still denied | 2, 6 | P0 | AUTHORIZATION_MATRIX §1 (three layers) |
| 13 | Encryption and tokens | Secrets, sessions, and transport | Passwords hashed; tokens signed; TLS terminates at the proxy; secrets from environment | BCrypt cost 12 test; signing key length check at startup; security headers test; TLS configuration in DEPLOYMENT §4. Optional AES-GCM for third-party tokens is not used because MVP has no third-party tokens | 2, 10 | P0 (AES-GCM N/A-documented) | ADR-003, ADR-005, SECURITY §3 |
| 14 | Kafka producer and consumer | Optional notification event pipeline | A submission review publishes an event; a consumer records the notification | Outbox row written in the same transaction; relay publishes to Kafka; consumer logs the event; test with Testcontainers Kafka | 8 | P2 (optional) | ADR-011 |
| 15 | Retries, DLQ, and idempotency | Optional notification delivery | A failing consumer retries with backoff, then moves the event to a dead-letter topic; a duplicate event is processed once | `processed_events` table keyed by event ID; retry test with an injected failure; DLQ test; duplicate delivery test | 8 | P2 (optional) | ADR-011, STATE_MACHINES §9 |
| 16 | Unit, integration, and contract tests | Services and APIs | Each layer has its test type | JUnit unit tests; MockMvc web tests; Testcontainers integration tests; OpenAPI snapshot as the contract check. Pact is not used because no separate consumer team exists | 1–9 | P0 (Pact N/A-documented) | ADR-015, TEST_STRATEGY §2 |
| 17 | Docker and CD | Backend and MySQL | Containers build reproducibly; CI gates block a bad change; deployment uses one digest | Dockerfile with pinned base digest; CI pipeline with gates (CI_CD §3); smoke tests after deploy (DEPLOYMENT §8) | 1, 10 | P0 | ADR-014, CI_CD §5 |
| 18 | Canary release | Optional deployment extension | Not claimed | No canary is performed. The host has no traffic splitting; the release is stop-and-start with rollback (DEPLOYMENT §6) | 10 | N/A-documented | DEPLOYMENT §6 (blueprint §2 "do not claim canary") |
| 19 | Full-stack integration | Project lifecycle | A single flow runs from React through the API and the database to the UI | GP-1 end to end in Playwright against the packaged app (TEST_STRATEGY §5.1); SM-1 check that no SQL is used | 4–7, 9 | P0 | USER_STORIES §4 |
| 20 | Capstone integration | The whole CampusHub | A role-aware demonstration with docs, tests, and CI | GP-1, GP-2, GP-3 shown in DEMO_SCRIPT; the documentation set; the CI summary; the review in PHASE_PLAN | 11 | P0 | DEMO_SCRIPT |

## 3. Coverage check

- P0 rows: 1–13, 16 (Pact excluded), 17, 19, 20 — delivered in the MVP phases.
- P2 rows: 14, 15 — optional, Phase 8, disabled by default (`CAMPUSHUB_KAFKA_ENABLED=false`).
- N/A-documented rows: 13 (AES-GCM), 16 (Pact), 18 (canary). Each is stated, with the reason, so no reviewer is left to infer it.

## 4. Evidence rules

- A concept is marked **evidenced** only when the test or recorded demonstration named in its row passes in CI or is recorded in IMPLEMENTATION_LOG with date and commit.
- A library in `pom.xml` or `package.json` without a use case is not evidence.
- Phase 11 review confirms each evidenced row against DEMO_SCRIPT and records the result in the review in PHASE_PLAN.
