# CampusHub — Master Blueprint Compliance Audit

**Purpose:** Records, for every functional and non-functional requirement in [REQUIREMENTS.md](REQUIREMENTS.md), whether the repository implements it, what evidence exists, and what remains.
**Scope:** The requirement IDs in REQUIREMENTS.md, checked against [CAMPUSHUB_MASTER_BLUEPRINT.md](CAMPUSHUB_MASTER_BLUEPRINT.md) §3, §5, §6, §8, and §9.
**Method:** Time-boxed (about 45 minutes). Code searches over `backend/src/main` and `frontend/src`, a read of the migrations and the controller mappings, and a read of the tests named below. Nothing was marked Implemented because a document mentions it. Where a status depends on a test I did not re-read in this pass, it says UNVERIFIED.
**Status key:** **Implemented** (code path exists and the cited evidence supports it), **Partial** (some of the requirement exists), **Missing** (no code path), **UNVERIFIED** (code may exist, but evidence was not checked).
**Priority:** taken from REQUIREMENTS.md.
**Date:** 2026-11-03. Branch `phase-7a-certifications`.

## Summary

| Area | Implemented | Partial | Missing | UNVERIFIED |
|---|---:|---:|---:|---:|
| Authentication and sessions | 6 | 2 | 0 | 2 |
| Users and profiles | 0 | 1 | 3 | 0 |
| Academic catalog | 0 | 0 | 4 | 0 |
| Projects and membership | 6 | 5 | 4 | 1 |
| Milestones and tasks | 3 | 1 | 4 | 0 |
| Calendar | 0 | 2 | 0 | 0 |
| Submissions and reviews | 5 | 2 | 3 | 0 |
| Skills and certifications | 2 | 1 | 1 | 1 |
| Dashboards | 0 | 3 | 1 | 0 |
| Audit and activity | 1 | 1 | 2 | 0 |
| Asynchronous events | 0 | 0 | 4 | 0 |
| Non-functional | 4 | 5 | 1 | 4 |

The earlier summary said the MVP covers "P0 basics". That is only partly accurate. Project approval and completion, milestones, task assignment, file evidence, the academic catalog, profile editing, administrator dashboard and audit endpoints, notifications, and the outbox are not built, and several P0 items depend on them.

## 1. Authentication and sessions

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-AUTH-01 | Sign in with email or institutional ID | Partial | `identity/AuthController` `POST /auth/login`; `AuthFlowIntegrationTest` | Institutional-ID sign-in: UNVERIFIED in code. Email sign-in works. | P0 |
| FR-AUTH-02 | Protected endpoints require a valid token | Implemented | Security configuration; `TokenValidationTest` | None found. | P0 |
| FR-AUTH-03 | `GET /auth/me` returns current record | Implemented | `AuthController` `GET /auth/me` | Freshness vs cached claims: UNVERIFIED. | P0 |
| FR-AUTH-04 | Logout revokes the token | Implemented | `POST /auth/logout`; `revoked_tokens` referenced in 7 source files | Test of 401 after logout: UNVERIFIED in this pass. | P0 |
| FR-AUTH-05 | Temporary password changed at first login | Implemented | `must_change_password` in 5 files; `PASSWORD_CHANGE_REQUIRED` in 2 files; `POST /auth/change-password` | None found. | P0 |
| FR-AUTH-06 | Deactivated accounts lose access immediately | Partial | `DEACTIVATED` status checked in 3 files | No endpoint deactivates a user (see FR-PROF-04). | P0 |
| FR-AUTH-07 | Login failures throttled | Implemented | `locked_until` in 3 files; 429 handling in 4 files | Lockout test coverage: UNVERIFIED. | P0 |
| FR-AUTH-08 | Refresh rotation and reuse detection | Partial | `POST /auth/refresh`; `V7__refresh_sessions.sql` | Reuse-detection test: UNVERIFIED. P1 deferred per ADR-004. | P1 |
| FR-AUTH-09 | No self-registration | Implemented | No registration mapping in any controller | None. | P0 |

## 2. Users and profiles

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-PROF-01 | User edits own profile | Missing | No `PATCH /users/me`; profile fields exist in V2 only | Profile GET/PATCH with field limits. | P0 |
| FR-PROF-02 | Role, status, email not self-service | Missing | No profile write endpoint, so no mass-assignment test | Build the endpoint with a 400 `FIELD_NOT_EDITABLE` test. | P0 |
| FR-PROF-03 | Other profiles visible only per matrix | Partial | `identity/UserDirectoryController` `GET /users/faculty` only | Profile read endpoint and 404 concealment. | P0 |
| FR-PROF-04 | Admin creates and deactivates accounts | Missing | No admin user endpoints (`/admin/skills` exists for skills only) | Admin user list, deactivate, reset password, with audit. | P0 |

## 3. Academic catalog

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-CAT-01 | Admin manages departments, terms, courses | Missing | Tables from V2 exist; no controllers | CRUD controllers, validation, duplicate code 409. | P0 |
| FR-CAT-02 | Catalog lists paginated and sorted | Missing | No catalog list endpoints | Paginated, allowlisted sort. | P0 |
| FR-CAT-03 | Referenced catalog items deactivated, not deleted | Missing | No catalog delete path | `CATALOG_ITEM_IN_USE` and deactivation. | P0 |
| FR-CAT-04 | Catalog caching with invalidation | Missing | No `@EnableCaching` or `@Cacheable` in source | Caching and eviction test. | P0 |

## 4. Projects and membership

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-PROJ-01 | Create project in DRAFT with term and course | Partial | `POST /projects` (`ProjectController`); title and description limits in V4 | No DRAFT state and no term. The model is ACTIVE/ARCHIVED only (`ProjectStatus`, V4 comment). | P0 |
| FR-PROJ-02 | Owner submits proposal (DRAFT → PROPOSED) | Missing | No PROPOSED state; no `PROPOSAL_INCOMPLETE` | Proposal state and completeness check. | P0 |
| FR-PROJ-03 | Admin approves or rejects proposal | Missing | No approve/reject endpoints | Approval endpoints, rejection reason. | P0 |
| FR-PROJ-04 | Admin assigns supervisor | Partial | `PUT /projects/{id}/supervisor`; `assignSupervisor` in `ProjectService` | ACTIVE-faculty check and audit assertion: UNVERIFIED. | P0 |
| FR-PROJ-05 | Owner activates approved project | Missing | Projects are ACTIVE on creation | Depends on FR-PROJ-02/03. | P0 |
| FR-PROJ-06 | Project completes when milestones closed | Missing | No COMPLETED state; no milestones | Depends on milestones (FR-MS-*). | P0 |
| FR-PROJ-07 | Admin archives completed or rejected projects | Partial | `POST /projects/{id}/archive`; `PROJECT_ARCHIVED` read-only rule | State precondition (completed or rejected) not enforced because those states do not exist. | P0 |
| FR-PROJ-08 | Lists scoped by membership and supervision | Partial | `ProjectService.canSee`; list by status | Term filter missing. Pagination exists. | P0 |
| FR-PROJ-09 | Detail shows members, milestones, supervisor, progress | Partial | `ProjectDetailPage`: members, supervisor, status, milestones, tasks | Progress metric: UNVERIFIED. | P0 |
| FR-PROJ-10 | Status change and audit are atomic | Implemented | `AuditService` writes in the service transaction | Rollback-on-audit-failure test: UNVERIFIED. | P0 |
| FR-TEAM-01 | Owner invites an ACTIVE student | Implemented | `POST /projects/{id}/invitations`; `pending_key` unique index (V11); 5-person cap; 14-day expiry | Invitee-must-be-ACTIVE-student check: UNVERIFIED. | P0 |
| FR-TEAM-02 | Invitee accepts or declines | Implemented | `POST /invitations/{id}/accept` and `/decline`; team-full returns 409 under project lock | None found. | P0 |
| FR-TEAM-03 | Owner removes a member, unassigns their open tasks | Implemented | `DELETE /projects/{id}/members/{userId}`; `MembershipService.removeMember` calls `TaskRepository.unassignOpenTasks`; owner removal blocked | Backend test `removingAMemberClearsOnlyTheirOpenAssignments`. Removal in the browser: UNVERIFIED. | P0 |
| FR-TEAM-04 | No invitation to COMPLETED or ARCHIVED project | Partial | `PROJECT_NOT_OPEN_FOR_MEMBERSHIP` for ARCHIVED | COMPLETED state does not exist. | P0 |

## 5. Milestones and tasks

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-MS-01 | Owner creates and edits milestones | Implemented | V13 `milestones`; `MilestoneService`; `MilestoneFlowIntegrationTest`; browser create checked as owner | Browser edit of title or due date: UNVERIFIED (backend test only). | P0 |
| FR-MS-02 | Supervisor sets `allowLate` | Implemented | `PATCH /milestones/{id}` with `allowLate` for the supervisor; `FIELD_NOT_EDITABLE` for the owner | Browser checked as supervisor. Enforcement on submissions: missing (FR-SUB-05). | P0 |
| FR-MS-03 | Milestone closes; closed rejects submissions | Partial | `POST /milestones/{id}/close`; closed rejects edits and closes (409) | Submissions do not reference milestones, so closed does not reject submissions yet. Browser close: UNVERIFIED. | P0 |
| FR-TASK-01 | Active member creates a task | Implemented | `POST /projects/{id}/tasks`; `TaskService` create | Project ACTIVE/APPROVED check: APPROVED does not exist. | P0 |
| FR-TASK-02 | Task assigned to an active member or left unassigned | Implemented | V12 `assignee_id`; `TaskService.requireAssignable`; PATCH null unassigns; assignee select in `TaskForm` | Backend test `assigneeMustBeTheOwnerOrAnActiveMemberAndNullUnassigns`; browser assign checked. Deactivated assignees: UNVERIFIED (no deactivation endpoint, FR-PROF-04). | P0 |
| FR-TASK-03 | Guarded status transitions | Implemented | `POST /tasks/{id}/transitions`; `TaskTransitions` | Direct `status` write rejection: UNVERIFIED. | P0 |
| FR-TASK-04 | BLOCKED requires a reason | Implemented | `blockedReason` referenced in 5 files | Reason cleared on unblock: UNVERIFIED. | P0 |
| FR-TASK-05 | Task comments visible to members | Missing | No comment table or endpoints | Comment table and endpoints (P1). | P1 |
| FR-TASK-06 | Filters and sort allowlist | Partial | `GET /projects/{id}/tasks` filters by status only; sort fixed to `createdAt` | Assignee, milestone, due-before filters; sort allowlist. | P0 |
| FR-TASK-07 | Stale version → 409 | Implemented | `version` column in V5; `STALE_VERSION` used | Test coverage: UNVERIFIED in this pass. | P0 |

## 6. Calendar

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-CAL-01 | Calendar returns milestones and tasks for a bounded range | Implemented | `GET /calendar?from=&to=`; 92-day cap; `PROJECT_DUE`, `TASK_DUE`, `MILESTONE_DUE`; `MilestoneFlowIntegrationTest` calendar case | Browser display of `MILESTONE_DUE`: UNVERIFIED. | P0 |
| FR-CAL-02 | UTC instants shown in local time, DST tested | Partial | Dates are `LocalDate` (documented deviation in API_CONTRACT §9) | UTC instant model and DST test. | P0 |

## 7. Submissions and reviews

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-SUB-01 | Member creates submission for an open milestone | Partial | `POST /projects/{id}/submissions` creates a revision | No milestone, so "one per milestone" cannot apply. | P0 |
| FR-SUB-02 | Members upload files (≤5 per version, ≤20 MB, magic-byte type check) | Partial | V14 `submission_files`; `POST /submissions/{id}/files` (students who can write team work, SUBMITTED revisions only); `FileStorage` writes under a UUID key; PDF, PNG and JPEG by magic byte; 20 MiB and 5 files per revision enforced in the service; tests in `SubmissionFileFlowIntegrationTest` | Limits are per revision, not per "version" (no version entity). Faculty cannot upload. No DRAFT, so files attach after submit. Framework 25 MB multipart ceiling not exercised by MockMvc. No malware scan. | P0 |
| FR-SUB-03 | Downloads only by authorized actors, audited | Partial | `GET /submissions/{id}/files/{fileId}/content`: project visibility and per-file checks; admins get 404; `ATTACHMENT` disposition with `nosniff`; audited in `download` | Audit rows are not asserted in tests. Download is not paged or rate-limited. | P0 |
| FR-SUB-04 | Submit seals the draft; needs ≥1 file | Partial | Revision is sealed on creation (V6); files stop at SUBMITTED for a sealed revision (409 `VERSION_SEALED` on upload after review starts) | No DRAFT. A submission does not require a file, so the ≥1 rule is not enforced. | P0 |
| FR-SUB-05 | Deadline enforced; `allowLate` | Missing | Submission has no milestone deadline | Depends on FR-MS-01. | P0 |
| FR-SUB-06 | Resubmission creates a new version | Implemented | Revisions with `UNIQUE (project_id, revision_no)`; `CHANGES_REQUESTED` flow in `SubmissionFlowIntegrationTest` | None found. | P0 |
| FR-SUB-07 | Submitted content never replaced | Implemented | No edit endpoint for submissions; V6 check constraints | None found. | P0 |
| FR-REV-01 | Assigned supervisor starts review | Implemented | `POST /submissions/{id}/review/start` | None found. | P0 |
| FR-REV-02 | One decision per version; feedback required for changes | Implemented | `POST /submissions/{id}/review`; `ck_submissions_feedback` in V6 | Second-review 409 test: UNVERIFIED in this pass. | P0 |
| FR-REV-03 | Students see decision and feedback | Implemented | Project submissions readable by members | None found. | P0 |
| FR-REV-04 | Non-supervising faculty get 404 | Implemented | `canSee` gating in `SubmissionService` | Browser check was done for the supervisor view only. | P0 |

## 8. Skills and certifications

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-SKILL-01 | Skill catalog readable and paginated | Implemented | `GET /skills`; `POST /admin/skills` | Pagination shape: UNVERIFIED. | P1 |
| FR-SKILL-02 | Set own skills with proficiency and note | Implemented | `PUT /users/me/skills`; `SkillFlowIntegrationTest` | Note length ≤500: UNVERIFIED. | P1 |
| FR-CERT-01 | Create, list, delete own certifications | Partial | `/users/me/certifications` CRUD; `credentialUrl` stored | Required fields and HTTPS check: UNVERIFIED. Evidence file removal depends on FR-CERT-02. | P1 |
| FR-CERT-02 | Certification evidence files | Missing | Shared `FileStorage` exists from FR-SUB-02, but no certification file endpoints | Certification upload and download with owner-only authorization and the same type and size checks. | P1 |
| FR-CERT-03 | Certifications owner-private | Implemented | Owner-scoped repository queries | Non-owner 404 test: UNVERIFIED. | P1 |

## 9. Dashboards

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-DASH-01 | Student dashboard: due tasks, overdue, milestones, completion, reviews, skills | Partial | `GET /users/me/dashboard/student`; `DashboardIntegrationTest` | Milestones metrics missing. Exact-value assertions: UNVERIFIED. | P0 |
| FR-DASH-02 | Faculty dashboard: pending reviews, at-risk milestones | Partial | `GET /users/me/dashboard/faculty` (Dashboard page shows counts) | At-risk milestone metric missing. | P0 |
| FR-DASH-03 | Admin dashboard | Missing | No admin dashboard endpoint | Endpoint and tests. | P0 |
| FR-DASH-04 | Every metric has a definition | Partial | Definitions in API_CONTRACT for implemented metrics | Missing for milestone metrics; check per metric. | P0 |

## 10. Audit and activity

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-AUD-01 | State-changing operations audited | Partial | `AuditService.record` in several services | Coverage per SECURITY §8 not asserted by tests in this pass. | P0 |
| FR-AUD-02 | Admin reads audit log with filters | Missing | No `/audit-events` endpoint | Endpoint with pagination (≤100) and filters. | P0 |
| FR-AUD-03 | Audit metadata has no secrets | Implemented | `AuditService` allows only the `reason` metadata key, capped at 200 characters | None found. | P0 |
| FR-ACT-01 | Project activity feed | Missing | No `project_activity` table or endpoint | Table and paginated endpoint (P1). | P1 |

## 11. Asynchronous events (optional)

| ID | Requirement | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| FR-EVT-01 | Events written to `outbox_events` in the same transaction | Missing | No outbox table or code | Outbox migration and writes in the same transaction. | P2 |
| FR-EVT-02 | Relay publishes to Kafka when enabled | Missing | No Kafka dependency in `pom.xml`; `application.yml` has a placeholder reference only | Relay and producer. | P2 |
| FR-EVT-03 | Idempotent consumers | Missing | No `processed_events` | Consumer with unique event ID. | P2 |
| FR-EVT-04 | Retry, backoff, DLQ | Missing | None | Retry and DLQ. | P2 |

## 12. Non-functional requirements

| ID | Category | Status | Evidence | Remaining work | Priority |
|---|---|---|---|---|---|
| NFR-SEC-01 | Server-side authorization on every endpoint | Partial | Authorization tests exist for implemented modules | Full endpoint-by-endpoint matrix test not present. | P0 |
| NFR-SEC-02 | BCrypt or Argon2id hashing | UNVERIFIED | `PasswordPolicyTest` exists; hash algorithm not read in this pass | Confirm strength and add a hash-format test. | P0 |
| NFR-SEC-03 | Secrets never in source control | Partial | GitGuardian flagged PR #1; `.env` ignore: UNVERIFIED | Resolve PR #1 scan (see Phase H). | P0 |
| NFR-SEC-04 | Input validation on every write and list param | Partial | Validation on the implemented write endpoints | Not on every list parameter. | P0 |
| NFR-SEC-05 | Security headers and constrained CORS | Implemented | Header configuration referenced in 4 files | Header assertion test: UNVERIFIED. | P0 |
| NFR-DATA-01 | Schema changes only through Flyway | Implemented | `db/migration` V1–V11 | `ddl-auto=validate`: UNVERIFIED. | P0 |
| NFR-DATA-02 | Timestamps stored as UTC | UNVERIFIED | `DATETIME(6)` columns; clock-zone test not present | Clock-zone test. | P0 |
| NFR-DATA-03 | FKs and unique constraints | Implemented | FKs and unique keys in V2–V11 | None found. | P0 |
| NFR-PERF-01 | No N+1 on list endpoints | UNVERIFIED | No Hibernate statistics assertion found | Add query-count assertion on list endpoints. | P0 |
| NFR-PERF-02 | Measured list latency | Missing | No measurement log | Measurement in Phase G. | P0 |
| NFR-A11Y-01 | WCAG 2.1 AA for implemented screens | Partial | ARIA labels on calendar grid and navigation; no axe-core run | Automated axe check. | P0 |
| NFR-OPS-01 | Health exposes only liveness and readiness | Partial | `/actuator/health/readiness` responds; `StorageHealthIndicator` exists | Confirm no component details publicly: UNVERIFIED. | P0 |
| NFR-OPS-02 | Structured logs with request ID | UNVERIFIED | Not checked in this pass | Confirm log format. | P0 |
| NFR-REL-01 | Core workflows work with Kafka disabled | Implemented | No Kafka runtime dependency | Kafka-disabled test profile: UNVERIFIED. | P0 |
| NFR-TEST-01 | Reproducible tests locally and in CI | Implemented | `./mvnw -B verify` runs with Testcontainers MySQL (136 tests last run) | CI run: GitHub check is blocked by PR #1. | P0 |

## Planned order of work (from this audit)

1. Task assignment (FR-TASK-02, FR-TEAM-03). Done; see CHANGELOG "Phase E.1 and E.5".
2. Milestones (FR-MS-01 to 03, FR-CAL-01 milestone deadlines, FR-PROJ-09 display). Done locally; see CHANGELOG "Phase E.2". FR-MS-03 submission rejection depends on item 3.
3. Submission evidence files (FR-SUB-02 to 05, FR-CERT-02 storage). Done locally for FR-SUB-02 and FR-SUB-03; see CHANGELOG "Phase E.3". FR-CERT-02 and the FR-SUB-04 file rule remain open.
4. Invitation lifecycle tests (FR-TEAM-01 to 04).
5. Task pagination in the UI.
6. Database-backed notifications with read state (not in REQUIREMENTS.md, added for the release brief).
7. Outbox and Kafka: deferred unless time remains; see the checkpoint report.

Not in this pass, and documented as remaining gaps: project proposal and approval states, the academic catalog, profile editing, admin user management, admin dashboard, audit log endpoint, activity feed, and the Kafka pipeline.
