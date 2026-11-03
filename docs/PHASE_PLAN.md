# CampusHub — Phase Plan

**Purpose:** Lists the delivery phases in order, what each produces, what must be true before the next phase starts (exit gate), and which documents and decisions each phase depends on.
**Scope:** Phases 0 to 11 from blueprint §14. Each phase is a stop-and-check point; the MVP is P0 work within these phases (SCOPE §2).
**Last reviewed:** 2026-11-03 (Phase 1)
**Related:** [SCOPE.md](SCOPE.md), [RISKS.md](RISKS.md), [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md), [CHANGELOG.md](CHANGELOG.md), [TEST_STRATEGY.md](TEST_STRATEGY.md) §3, [DECISIONS.md](DECISIONS.md).

## 1. Status

| Phase | Name | Status (2026-11-03) |
|---|---|---|
| 0 | Discovery, repository foundation, and authoritative documentation | Documentation complete; Phase 0 review issued; **stopped for approval** |
| 1 | Local infrastructure and backend skeleton | **Accepted by the owner (2026-11-03)** with open items: CI validated locally (actionlint, `./mvnw verify`) but **not yet run on GitHub** (no remote, RISKS R-22); gate evidence in IMPLEMENTATION_LOG §9 |
| 2 | Identity, roles, and security | Not started |
| 3 | Academic catalog and profiles | Not started |
| 4 | Projects and team membership | Not started |
| 5 | Tasks, milestones, and calendar | Not started |
| 6 | Submissions and faculty reviews | Not started |
| 7 | Skills, certifications, and dashboards (P1) | Not started |
| 8 | Optional Kafka and resilience extension (P2) | Not started; disabled by default |
| 9 | Testing, security hardening, and performance | Not started |
| 10 | CI/CD, deployment, and operational readiness | Not started |
| 11 | Capstone demonstration and handoff | Not started |

Phase 1 code exists under `backend/` and `docker-compose.yml`, and the CI workflow exists at `.github/workflows/ci.yml`; no feature code exists. No phase after 1 may begin until the user approves the Phase 1 gate.

## 2. Phase definitions

Each phase lists its deliverables, its exit gate (from blueprint §14), and the documents and decisions it must satisfy. The gate is a condition, not a target: a phase is not complete while any gate item is unmet.

### Phase 0 — Discovery, repository foundation, and authoritative documentation (stop gate)

- **Deliverables.** Every document in the `/docs` tree with real content; DECISIONS ADR-001 to ADR-024; RISKS; this file; IMPLEMENTATION_LOG; CHANGELOG; cross-check; Phase 0 review.
- **Gate.** Every required document exists with meaningful content; zero contradictions among the role matrix, schema, and API; open high-risk decisions are flagged. Stop and request approval to begin Phase 1.
- **Depends on.** The blueprint (read-only source of truth).

### Phase 1 — Local infrastructure and backend skeleton

- **Deliverables.** Spring Boot 3.x application on Java 21 (ADR-001); MySQL 8 service in Docker Compose; Flyway baseline V1; configuration profiles with placeholder refusal (DEPLOYMENT §3); health endpoints (OPERATIONS §2); RFC 9457 error handling (ADR-012); one JUnit and one Testcontainers test; OpenAPI generation.
- **Gate.** `docker compose up` brings up the API and health returns UP; migrations apply cleanly; CI tests pass.
- **Depends on.** Docker daemon available (RISKS R-16). Before Phase 1 starts, the Docker check is performed and recorded in IMPLEMENTATION_LOG.

### Phase 2 — Identity, roles, and security

- **Deliverables.** User model and role (ADR-021); seeded development accounts (DEV seed only, never in production); BCrypt cost 12 (ADR-005); login with lockout and rate limit (ADR-020); JWT HS256 validation (ADR-003); security filter chain; current-user endpoint; role rules; resource-policy foundation (ADR-006); revocation list and `revoked_after` (ADR-019); auth tests. Refresh-token design is recorded (ADR-004); its implementation is P1.
- **Gate.** Unauthorized, expired-token, wrong-role, and cross-user tests pass (TEST_STRATEGY §4.4 and §4.6).
- **Depends on.** Phase 1. Owner answer for OD-7 (provisioned password process) is needed before the account creation flow is finalized, not before login.

### Phase 3 — Academic catalog and profiles

- **Deliverables.** Departments, terms, courses (admin CRUD, deactivation per ADR-017); student and faculty profiles; catalog cache (ADR-010); validation; offset pagination with sort allowlists (ADR-012); indexes (DATA_MODEL §10); role-specific frontend shell (ADR-009).
- **Gate.** Catalog and profile flows work end to end.
- **Depends on.** Phase 2.

### Phase 4 — Projects and team membership

- **Deliverables.** Proposal CRUD with draft persistence in `sessionStorage` (ADR-009); project status transitions (STATE_MACHINES §2); administrator approval and supervisor assignment (ADR-016, ADR-022); team invitations and membership (STATE_MACHINES §6; team cap 5 per OD-3); ownership enforcement; audit trail (ADR-023); project list and detail UI.
- **Gate.** Student project creation and faculty assignment demonstrated; cross-project authorization negative tests pass (NS-01, NS-02, NS-05).
- **Depends on.** Phase 3.

### Phase 5 — Tasks, milestones, and calendar

- **Deliverables.** Task CRUD, assignment (assignee must be a member, NS-11), priority, deadlines, state transitions (STATE_MACHINES §3); milestones (due dates, `allowLate` per ADR-024); calendar aggregation over a range of 92 days or fewer (ADR-013); task filters and memoized selectors (SYLLABUS_MAPPING rows 4 and 7).
- **Gate.** Task completion updates project progress and calendar correctly.
- **Depends on.** Phase 4.

### Phase 6 — Submissions and faculty reviews

- **Deliverables.** Protected upload and download (ADR-008); submission metadata and versioning; sealing and immutability (ADR-018); submission state transitions (STATE_MACHINES §5); faculty review decisions and feedback (one decision per version, US-FAC-02); file checks (magic bytes, size, count); audit events (ADR-023).
- **Gate.** Student submission, faculty review, and student feedback visible; unauthorized file access blocked (NS-01, NS-05, NS-07, NS-10, NS-12).
- **Depends on.** Phase 5.

### Phase 7 — Skills, certifications, and dashboards (P1)

- **Deliverables.** Skill catalog; student self-assessment; certifications with evidence uploads (reuses ADR-008); dashboard aggregates; pagination and caching only where appropriate (ADR-010 limits caching to the catalog).
- **Gate.** Metrics derive from real data and respect visibility rules (OD-5 must be answered before this phase; the default is that faculty cannot see skills).
- **Depends on.** Phase 6. Phase 7 is deferred if the Phase 6 gate is not met on time (SCOPE §3).

### Phase 8 — Optional Kafka and resilience extension (P2)

- **Deliverables.** Transactional outbox; Kafka producer and consumer; idempotent consumers keyed by event ID (`processed_events`); retry with backoff; dead-letter topic; monitoring; core workflows unchanged when Kafka is disabled (ADR-011).
- **Gate.** Duplicate message, transient failure, DLQ, and recovery are each demonstrated. **Skip this phase if it threatens core delivery** (blueprint §14; OD-10).
- **Depends on.** Phase 6 (events come from the submission and review transitions).

### Phase 9 — Testing, security hardening, and performance

- **Deliverables.** Full suite per TEST_STRATEGY §2; the authorization matrix suite (TEST_STRATEGY §4); file-security tests; pagination limit tests; query profiling (TEST_STRATEGY §6.1); EXPLAIN snapshots (§6.2); the SM-6 latency measurement (§6.3); coverage reports against the gates in TEST_STRATEGY §3; accessibility checks (TEST_STRATEGY §5.2).
- **Gate.** No critical security findings (SM-4); reproducible passing suite (SM-5); SM-6 measured and recorded, with any miss in RISKS.
- **Depends on.** Phases 2 to 6 (and 7 and 8 if delivered).

### Phase 10 — CI/CD, deployment, and operational readiness

- **Deliverables.** GitHub Actions pipelines (CI_CD §2); images with pinned digests (CI_CD §5); registry publishing with secrets (CI_CD §6); hosting option chosen against DEPLOYMENT §2 and recorded in DECISIONS (ADR-014); migration step and release procedure (DEPLOYMENT §5); backups and restore drill (OPERATIONS §6.5); health checks, logs, and rollback (DEPLOYMENT §6). Canary only if the host supports traffic shifting and rollback verification (currently not planned; SYLLABUS_MAPPING row 18).
- **Gate.** The deployed golden path is verified (DEPLOYMENT §10); no embedded secrets (DEPLOYMENT §7); rollback rehearsed in staging; restore completed and recorded; ASVS Level 1 review (SECURITY §12) complete.
- **Depends on.** Phase 9.

### Phase 11 — Capstone demonstration and handoff

- **Deliverables.** Updated architecture diagrams, README, OpenAPI snapshot, screenshots, demo accounts (safe development data only), sample dataset, the syllabus evidence in SYLLABUS_MAPPING, the runbook (OPERATIONS) reviewed against the final host, known limitations (DEMO_SCRIPT §6), and presentation notes (DEMO_SCRIPT).
- **Gate.** Clean-clone setup succeeds with documented commands only (SM-3); live role-based demonstration completes (DEMO_SCRIPT §3); the review in §4 of this file is updated with the final result.
- **Depends on.** Phases 9 and 10.

## 3. Dependency summary

```
0 → 1 → 2 → 3 → 4 → 5 → 6 → 9 → 10 → 11
                         ↘ 7 (P1, optional) ↗
                         ↘ 8 (P2, optional) ↗
```

- Phases 7 and 8 do not block Phases 9 to 11 when skipped. Skipping either must be recorded in CHANGELOG and SCOPE.
- Phase 9 begins only after Phase 6 is complete. Testing is not deferred to the end of the project; each phase adds its own tests (TEST_STRATEGY §2), and Phase 9 hardens and measures them.

## 4. Phase 0 review record

Review date: 2026-11-03. Status: complete for Phase 0; **awaiting the user's approval to begin Phase 1**. No commit has been made.

### 4.1 Repository summary

- **Git state.** The repository is on `master` with no commits. All Phase 0 files are untracked: `README.md`, `.gitignore`, `.editorconfig`, `.env.example`, and `docs/`.
- **Documentation set.** `docs/` holds 25 files: the master blueprint (read-only source, `docs/CAMPUSHUB_MASTER_BLUEPRINT.md`), the canonical documents listed in INDEX §2, and the Phase 0 control documents (DECISIONS, RISKS, PHASE_PLAN, IMPLEMENTATION_LOG, CHANGELOG).
- **Code.** None. No application, migration, test, or pipeline exists. `.env.example` holds placeholders only.

### 4.2 Architecture assessment

- **Shape.** One Spring Boot 3.x modular monolith on Java 21 with MySQL 8 (ADR-001, ADR-002). Frontend is React with TypeScript, RTK Query, and React Hook Form (ADR-009). Phase 8 Kafka is optional and off by default (ADR-011).
- **Security design.** Three authorization layers, a fixed evaluation order (STATE_MACHINES §1), 404 concealment for invisible objects (ADR-006), private file storage behind a port (ADR-008), sealed versions enforced by service and database triggers (ADR-018), and an append-only audit log written in the same transaction (ADR-023).
- **Consistency.** The Phase 0 cross-check found and fixed the inconsistencies listed in §4.5. After the fixes, the role matrix, the state machines, the API error codes, and the schema agree on the names, codes, and transitions that were checked.
- **Weakest points.** Rate limits are per node (R-09). There is no malware scanning (R-07) and no MFA (R-10). Sign-in is needed every 15 minutes because refresh tokens wait for P1 (R-19). The Docker daemon has not been verified (R-16). Object-level authorization (R-02, Critical) is the most important risk; its mitigation exists only as design until Phase 2 tests run.
- **Assessment.** The design is coherent and traceable. Nothing has been built or executed, so none of it is proven yet.

### 4.3 Owner decisions OD-1 to OD-10

Each default is applied and recorded in DECISIONS §3 and §5. Owner confirmation is still pending for every item.

| ID | Default applied | Owner confirmation needed by |
|---|---|---|
| OD-1 Proposal approval and supervisor assignment | Administrator approves and assigns (ADR-016) | Phase 2 |
| OD-2 Faculty creating projects | No in MVP (ADR-016) | Phase 2 |
| OD-3 Team size | Five members including the owner (ADR-022) | Phase 2 |
| OD-4 Late submissions | Off; the supervisor enables per milestone (ADR-024) | Phase 2 |
| OD-5 Faculty visibility of student skills | Not visible in MVP | Phase 7 (P1) |
| OD-6 Institution time zone | UTC until configured (ADR-013) | Phase 5 |
| OD-7 Provisioned account passwords | Administrator sets a temporary password; forced change at first sign-in (ADR-021) | Phase 2 |
| OD-8 Hosting provider and budget | Deferred to Phase 10 against DEPLOYMENT §2 (ADR-014) | Phase 10 |
| OD-9 Licensing and code ownership | Undecided | Before any real data (RISKS R-20) and Phase 10 |
| OD-10 Kafka required or stretch | Stretch, Phase 8 (ADR-011) | Phase 8 |

### 4.4 Roadmap

| Phase | Name | Exit gate (summary) |
|---|---|---|
| 0 | Discovery and authoritative documentation | This review; approval to start Phase 1 |
| 1 | Local infrastructure and backend skeleton | `docker compose up` brings up the API; health is UP; migrations apply; CI tests pass |
| 2 | Identity, roles, and security | Unauthorized, expired-token, wrong-role, and cross-user tests pass |
| 3 | Academic catalog and profiles | Catalog and profile flows work end to end |
| 4 | Projects and team membership | Project creation and supervisor assignment demonstrated; NS-01, NS-02, NS-05 pass |
| 5 | Tasks, milestones, and calendar | Task completion updates progress and calendar |
| 6 | Submissions and faculty reviews | Submission, review, and feedback visible; NS-01, NS-05, NS-07, NS-10, NS-12 pass |
| 7 | Skills and dashboards (P1) | Metrics come from real data and respect visibility rules |
| 8 | Kafka and resilience (P2) | Duplicate, transient failure, DLQ, and recovery each demonstrated; skipped if it threatens delivery |
| 9 | Testing, hardening, and performance | No critical findings (SM-4); suite reproducible (SM-5); SM-6 measured |
| 10 | CI/CD, deployment, and operations | Deployed golden path verified; no embedded secrets; rollback and restore rehearsed |
| 11 | Capstone demonstration and handoff | Clean-clone setup (SM-3) succeeds; live role-based demonstration completes |

### 4.5 Verification results

The full table is in IMPLEMENTATION_LOG §2. In summary:

- **Passed.** Document presence; no placeholder headings; ADR-001 to ADR-024 each defined once with no out-of-range numbers; cited ADRs and risk IDs exist; relative Markdown links resolve; SYLLABUS_MAPPING governing decisions match the ADR index.
- **Fixed during the cross-check.** Section citations in nine ADRs (ADR-007, 009, 010, 011, 017, 018, 019, 021, 023), one golden-path step citation (ADR-017, GP-3 step 8), PRODUCT SM-2 (now USER_STORIES §5), and the SYLLABUS_MAPPING ADR citations. The IMPLEMENTATION_LOG "Pass" entries now state what was checked.
- **Not verified.** The Docker daemon (R-16). No build, test, or CI run exists because there is no code.

### 4.6 Readiness verdict

- **Phase 1 may start once the user approves this review.**
- **Before Phase 1 begins,** the Docker daemon check must be run and recorded in IMPLEMENTATION_LOG §2 (R-16). If Docker is unavailable, Testcontainers cannot run, and Phase 1 cannot close its gate.
- **Before Phase 2,** the owner should confirm OD-1 to OD-4 and OD-7. Their defaults are documented, so they do not block Phase 1.
- **Owner acceptance pending.** R-07 (malware scanning) and R-10 (no MFA) are *Accepted pending owner acceptance* and need written acceptance before any real student file or account is used.

## 5. Change control for this plan

- A change to a phase's gate or order is a scope change: it updates this file, SCOPE, and DECISIONS in the same change (blueprint §18).
- A skipped phase or a deferred deliverable is recorded in CHANGELOG with the reason.
