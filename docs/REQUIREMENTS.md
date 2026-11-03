# CampusHub — Requirements

**Purpose:** Authoritative, numbered requirements with acceptance criteria and priority.
**Scope:** Functional (FR) and non-functional (NFR) requirements for MVP (P0) through P2.
**Last reviewed:** 2026-11-03 (Phase 0)

**Priority key:** P0 must deliver; P1 deliver if P0 is stable; P2 stretch only. Phase numbers refer to [PHASE_PLAN.md](PHASE_PLAN.md). Requirement IDs are stable; retired IDs are never reused.

## 1. Authentication and sessions

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-AUTH-01 | A user signs in with email or institutional ID plus password. | Valid credentials return 200 with an access token and user summary. Invalid credentials return 401 with the same message for unknown user and wrong password. | P0 | 2 |
| FR-AUTH-02 | Protected endpoints require a valid access token. | No token → 401; malformed, bad signature, wrong issuer or audience, or expired → 401; valid token → authorized per matrix. | P0 | 2 |
| FR-AUTH-03 | `GET /auth/me` returns the current user and role. | Returns the token subject's current record, not cached claims. | P0 | 2 |
| FR-AUTH-04 | Logout revokes the current access token. | After `POST /auth/logout`, the same token returns 401 until its natural expiry. Revocation uses `revoked_tokens` (ADR-019). | P0 | 2 |
| FR-AUTH-05 | Provisioned accounts must change a temporary password at first login. | While `must_change_password` is true, all endpoints except `POST /auth/change-password`, `GET /auth/me`, and `POST /auth/logout` return 403 `PASSWORD_CHANGE_REQUIRED`. | P0 | 2 |
| FR-AUTH-06 | Deactivated accounts lose access immediately. | Each request checks `users.status`; a DEACTIVATED user receives 401 on the next request even with an unexpired token. | P0 | 2 |
| FR-AUTH-07 | Login failures are throttled. | 5 consecutive failures lock the account for 15 minutes (`locked_until`); per-IP limit of 20 login attempts per minute returns 429. | P0 | 2 |
| FR-AUTH-08 | Refresh tokens with rotation and reuse detection. | HttpOnly cookie, rotation on each use, reuse of a rotated token revokes the family. | P1 | 2 (deferred per ADR-004) |
| FR-AUTH-09 | No self-registration. | No public registration endpoint exists. Administrators create accounts. | P0 | 2 |

## 2. Users and profiles

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-PROF-01 | A user edits their own profile. | Students edit display name, program, cohort, bio. Faculty edit display name, designation, department. Validation rejects over-length fields (name 100, bio 1000). | P0 | 3 |
| FR-PROF-02 | Role, status, and email changes are not self-service. | `PATCH /users/me` containing `role`, `status`, or `institutionalId` returns 400 `FIELD_NOT_EDITABLE`. Mass assignment is covered by a test. | P0 | 3 |
| FR-PROF-03 | Other users' profiles are visible only per AUTHORIZATION_MATRIX. | Non-permitted read returns 404 (concealment policy, ADR-006). | P0 | 3 |
| FR-PROF-04 | Administrators create and deactivate accounts and reset temporary passwords. | Deactivation sets status DEACTIVATED and writes an audit event. Account records are never hard-deleted (soft deactivation). | P0 | 3 |

## 3. Academic catalog

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-CAT-01 | Administrators manage departments, academic terms, and courses. | CRUD for admins; create/update validated; duplicate `code` returns 409. | P0 | 3 |
| FR-CAT-02 | Authenticated users read catalog lists with pagination. | `page`, `size` (max 100), `sort` allowlisted; deterministic tie-breaker. | P0 | 3 |
| FR-CAT-03 | Catalog items referenced by other records are deactivated, not deleted. | `DELETE` on a referenced item returns 409 `CATALOG_ITEM_IN_USE`; deactivation sets `active=false`. | P0 | 3 |
| FR-CAT-04 | Catalog reads are cached with explicit invalidation. | Spring Cache entries for departments, terms, and courses evict on any admin write; a test proves the stale value disappears after write. Private data is never cached (ADR-010). | P0 | 3 |

## 4. Projects and membership

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-PROJ-01 | A student creates a project in DRAFT with title, description, term, optional course. | Term must be active (`status=ACTIVE`). Title 3–200 characters; description ≤ 5000. | P0 | 4 |
| FR-PROJ-02 | The owner submits a proposal for approval. | DRAFT → PROPOSED only when title, description, term, and ≥ 1 milestone exist. Otherwise 422 `PROPOSAL_INCOMPLETE` listing missing fields. | P0 | 4 |
| FR-PROJ-03 | An administrator approves or rejects a proposal. | PROPOSED → APPROVED requires an assigned supervisor. Rejection requires a reason ≤ 1000 characters. | P0 | 4 |
| FR-PROJ-04 | An administrator assigns a supervisor. | Supervisor must be an ACTIVE faculty user. Reassignment writes an audit event. Self-appointment is impossible because only administrators call the endpoint. | P0 | 4 |
| FR-PROJ-05 | The owner activates an approved project. | APPROVED → ACTIVE by owner; requires ≥ 1 ACTIVE member. | P0 | 4 |
| FR-PROJ-06 | A project completes when its milestone criteria are met. | ACTIVE → COMPLETED by the supervisor or an administrator; requires every milestone CLOSED and at least one milestone. | P0 | 5 |
| FR-PROJ-07 | Administrators archive completed or rejected projects. | ARCHIVED projects are read-only; any write returns 422 `PROJECT_ARCHIVED`. | P0 | 4 |
| FR-PROJ-08 | Project lists are scoped by membership and supervision. | A student sees projects where they are a member; a faculty user sees supervised projects; an administrator sees all. Filters by status and term; paginated. | P0 | 4 |
| FR-PROJ-09 | Project detail shows members, milestones, supervisor, status, and progress. | Progress = done eligible tasks / eligible tasks (defined in §12 of the blueprint and in API_CONTRACT). | P0 | 4 |
| FR-PROJ-10 | Every project state change is atomic and audited. | Status change and audit row commit in the same transaction; a failed audit insert rolls back the change (tested). | P0 | 4 |
| FR-TEAM-01 | The owner invites an ACTIVE student. | Invitee not already a member; team size ≤ 5 (OD-3); creates PENDING invitation expiring after 14 days. | P0 | 4 |
| FR-TEAM-02 | The invitee accepts or declines. | Only the invitee can respond. Acceptance creates an active membership in one transaction and rejects if the team is now full (409). | P0 | 4 |
| FR-TEAM-03 | The owner removes a member. | The owner cannot be removed. Removing a member unassigns their open tasks in the same transaction. | P0 | 4 |
| FR-TEAM-04 | A student cannot be invited to a project that is COMPLETED or ARCHIVED. | Returns 422 `PROJECT_NOT_OPEN_FOR_MEMBERSHIP`. | P0 | 4 |

## 5. Milestones and tasks

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-MS-01 | The owner creates and edits milestones. | Title ≤ 200; `dueAt` required; sequence unique per project. | P0 | 5 |
| FR-MS-02 | The supervisor may set `allowLate` on a milestone. | Only this field is writable by the supervisor. | P0 | 5 |
| FR-MS-03 | A milestone closes when the owner or supervisor marks it CLOSED. | Closed milestones reject new submissions (422 `MILESTONE_CLOSED`). | P0 | 5 |
| FR-TASK-01 | Any active member creates a task in their project. | Task title 3–200; project must be ACTIVE or APPROVED. | P0 | 5 |
| FR-TASK-02 | Tasks are assigned to an active member or left unassigned. | Assignee must be an active member of the same project (422 otherwise). | P0 | 5 |
| FR-TASK-03 | Task status changes follow the guarded transitions in STATE_MACHINES. | Invalid transition → 422 `INVALID_TRANSITION`. Clients cannot write `status` directly outside the transition endpoint. | P0 | 5 |
| FR-TASK-04 | A BLOCKED task requires a reason. | `blockedReason` 3–500 characters, required when status is BLOCKED; cleared on unblock. | P0 | 5 |
| FR-TASK-05 | Task comments are visible to project members and the supervisor. | Body 1–2000 characters; no edit or delete in MVP (append-only, ADR-018 spirit). | P1 | 5 |
| FR-TASK-06 | Task lists are filterable and sortable. | Filters: status, assignee, milestone, due-before. Sort allowlist: `dueAt`, `createdAt`, `priority`, `status`. | P0 | 5 |
| FR-TASK-07 | Concurrent edits are detected. | `version` required on PATCH; stale version → 409 `STALE_VERSION`. | P0 | 5 |

## 6. Calendar

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-CAL-01 | Calendar returns milestones and tasks visible to the caller for a bounded range. | `from` and `to` required; range ≤ 92 days; otherwise 400 `RANGE_TOO_LARGE`. | P0 | 5 |
| FR-CAL-02 | Due dates are stored as UTC instants and displayed in the user's local time zone. | Tests cover a due date crossing a daylight-saving boundary. Semantics in ADR-013. | P0 | 5 |

## 7. Submissions and reviews

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-SUB-01 | An active member creates a submission for an open milestone. | One submission per milestone; creating it opens DRAFT version 1. Duplicate → 409. | P0 | 6 |
| FR-SUB-02 | Members upload files to the open DRAFT version. | ≤ 5 files per version; ≤ 20 MB each; allowed types by magic bytes (SECURITY §5). Violations: 413 for size, 422 `FILE_TYPE_NOT_ALLOWED`. | P0 | 6 |
| FR-SUB-03 | Files are downloadable only by authorized actors. | Download checks the file's submission and caller authorization; unauthorized → 404. Each download is audited. | P0 | 6 |
| FR-SUB-04 | Submitting seals the DRAFT version. | Requires ≥ 1 file. After sealing, files cannot be added or removed; submission status becomes SUBMITTED. | P0 | 6 |
| FR-SUB-05 | Deadlines are enforced. | Submit after `milestone.dueAt` without `allowLate` → 422 `DEADLINE_PASSED`. | P0 | 6 |
| FR-SUB-06 | Resubmission creates a new version. | After CHANGES_REQUESTED, members create version N+1 as DRAFT. Previous versions remain readable and immutable. | P0 | 6 |
| FR-SUB-07 | Submitted content is never silently replaced. | No endpoint modifies a sealed version or its files (verified by test and by DB constraints). | P0 | 6 |
| FR-REV-01 | The assigned supervisor starts reviewing a submitted version. | SUBMITTED → UNDER_REVIEW; any other caller → 404 or 403 per matrix. | P0 | 6 |
| FR-REV-02 | The supervisor records one decision per version. | ACCEPTED or CHANGES_REQUESTED; CHANGES_REQUESTED requires feedback 1–5000 characters. A second review on the same version → 409. | P0 | 6 |
| FR-REV-03 | Students see the decision and feedback. | Members of the project read reviews of their submissions. | P0 | 6 |
| FR-REV-04 | A faculty user cannot review or view a submission in a project they do not supervise. | 404 `NOT_FOUND` for every caller who does not supervise or belong to the project, including faculty (ADR-006, NS-05). 403 is not used for this case. See AUTHORIZATION_MATRIX. | P0 | 6 |

## 8. Skills and certifications

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-SKILL-01 | Authenticated users read the skill catalog. | Paginated; normalized unique names; administrators manage the catalog. | P1 | 7 |
| FR-SKILL-02 | Students set their own skills with proficiency and evidence note. | Proficiency ∈ {BEGINNER, INTERMEDIATE, ADVANCED, EXPERT}; evidence note ≤ 500; `PUT` replaces the owner's set atomically. | P1 | 7 |
| FR-CERT-01 | Students create, list, and delete their certifications. | Required: title, issuer, issuedOn. `credentialUrl` must be `https`. Delete is hard delete of the owner's own record with audit (certificate files are removed from storage). | P1 | 7 |
| FR-CERT-02 | Certification evidence files follow the file rules in FR-SUB-02. | Same validation and download authorization; owner only in MVP (OD-5). | P1 | 7 |
| FR-CERT-03 | Certifications are owner-private in MVP. | Non-owner reads → 404 (no faculty or peer access). | P1 | 7 |

## 9. Dashboards and analytics

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-DASH-01 | Student dashboard shows assigned tasks due in the next 7 days, overdue tasks, milestones, completion rate, latest 5 reviews, skills count. | Every figure comes from a server aggregate; test seeds known data and checks exact values. | P0 | 7 |
| FR-DASH-02 | Faculty dashboard shows pending reviews, supervised projects, at-risk milestones, submissions by status. | At-risk = milestone open, due within 7 days, and completion below 50% (parameter documented in API_CONTRACT). | P0 | 7 |
| FR-DASH-03 | Administrator dashboard shows active users by role, projects by status, and per-term summaries. | Deactivated users excluded from "active". | P0 | 7 |
| FR-DASH-04 | Dashboards show no metric without a defined denominator or a data source. | Each metric in API_CONTRACT lists its definition. | P0 | 7 |

## 10. Audit and activity

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-AUD-01 | Authorization-sensitive and state-changing operations write audit events. | The list in SECURITY §8 is covered by tests that assert an audit row exists. | P0 | 2–6 |
| FR-AUD-02 | Administrators read the audit log with pagination and filters. | `GET /audit-events` admin only; page size ≤ 100; actor, action, target filters. | P0 | 6 |
| FR-AUD-03 | Audit rows contain no secrets or raw personal content. | Tests assert that passwords, tokens, and file contents never appear in metadata. | P0 | 2 |
| FR-ACT-01 | Project activity feed visible to members and the supervisor. | Reads `project_activity`; paginated. | P1 | 5 |

## 11. Asynchronous events (optional)

| ID | Requirement | Acceptance criteria | Pri | Phase |
|---|---|---|---|---|
| FR-EVT-01 | Domain events are written to `outbox_events` in the same transaction as the change. | Atomicity test: rollback leaves no outbox row. | P2 | 8 |
| FR-EVT-02 | A relay publishes outbox events to Kafka when enabled. | Disabled Kafka leaves core workflows unchanged; tests run with Kafka disabled. | P2 | 8 |
| FR-EVT-03 | Consumers are idempotent. | Duplicate delivery of the same event ID produces one effect (`processed_events`). | P2 | 8 |
| FR-EVT-04 | Failed consumption retries with exponential backoff and then goes to a DLQ. | Phase 8 demo shows duplicate, transient failure, DLQ, and replay. | P2 | 8 |

## 12. Non-functional requirements

| ID | Category | Requirement | Acceptance criteria | Phase |
|---|---|---|---|---|
| NFR-SEC-01 | Security | Server-side authorization on every private endpoint. | Authorization test suite covers every endpoint in API_CONTRACT. | 2+ |
| NFR-SEC-02 | Security | Passwords hashed with BCrypt (strength 12) or Argon2id. | No plaintext or reversible storage; hash format test. | 2 |
| NFR-SEC-03 | Security | Secrets never in source control or logs. | Secret scanning in CI; `.env` ignored; log redaction test. | 1+ |
| NFR-SEC-04 | Security | Input validation on every write and every list parameter. | Validation tests per endpoint; sort allowlist tests. | 1+ |
| NFR-SEC-05 | Security | Security headers and constrained CORS. | Response headers asserted in a test; CORS allows only configured origins. | 2 |
| NFR-DATA-01 | Data | Schema changes only through Flyway migrations. | Hibernate `ddl-auto=validate`; migration apply test in CI. | 1 |
| NFR-DATA-02 | Data | Timestamps stored as UTC. | Column type and a clock-zone test. | 1 |
| NFR-DATA-03 | Data | Referential integrity through FKs and unique constraints. | Constraint tests for duplicate membership, duplicate review. | 4+ |
| NFR-PERF-01 | Performance | Bounded list sizes and no N+1 on list endpoints. | Hibernate statistics assertion in integration test (max query count per request). | 4+ |
| NFR-PERF-02 | Performance | Measured list latency per SM-6. | Measurement log in IMPLEMENTATION_LOG. | 9 |
| NFR-A11Y-01 | Accessibility | WCAG 2.1 AA for implemented screens. | axe-core checks in E2E; keyboard navigation test per primary screen. | 3+ |
| NFR-OPS-01 | Operations | Health endpoint exposes only liveness and readiness. | `GET /actuator/health` returns status without component details publicly. | 1 |
| NFR-OPS-02 | Operations | Structured logs with request correlation ID. | Each log line has `requestId`; no PII at INFO. | 1 |
| NFR-REL-01 | Reliability | Core workflows operate with Kafka disabled. | Test profile disables Kafka; golden path passes. | 8 |
| NFR-TEST-01 | Testing | Reproducible tests locally and in CI. | Same command passes on developer machine and CI runner. | 1+ |
