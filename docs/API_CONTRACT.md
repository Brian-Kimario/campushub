# CampusHub — API Contract

**Purpose:** Design source for the REST API: conventions, endpoints, permissions, request and response shapes, and error codes. The generated OpenAPI document must match this file (checked in CI, Phase 1+).
**Scope:** `/api/v1` for all MVP and P1 endpoints. P2 notification endpoints are not listed.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md) (who may call), [STATE_MACHINES.md](STATE_MACHINES.md) (transitions), [SECURITY.md](SECURITY.md).

## 1. Conventions (ADR-012)

| Topic | Rule |
|---|---|
| Base path | `/api/v1` |
| Format | JSON, UTF-8. Uploads use `multipart/form-data`. |
| Identifiers | UUID strings. Unknown or malformed IDs return 404 (never 400 that reveals format rules for private resources). |
| Authentication | `Authorization: Bearer <access token>` (ADR-003). No cookies in MVP. |
| Timestamps | ISO-8601 UTC with `Z`, e.g. `2026-11-03T09:30:00Z`. |
| Dates | `YYYY-MM-DD` for `startsOn`, `endsOn`, `issuedOn`. |
| Pagination | `page` (0-based, default 0), `size` (default 20, max 100). Response envelope below. |
| Sorting | `sort=<field>,<asc|desc>`; repeated for secondary keys. Allowlist per endpoint; unknown field → 400 `INVALID_SORT`. A unique `id` tie-breaker is always appended. |
| Versioning fields | Writes to `projects`, `milestones`, and `tasks` require `version`. Mismatch → 409 `STALE_VERSION`. |
| Partial updates | `PATCH` with only the fields being changed. Unknown or non-writable fields → 400 `FIELD_NOT_EDITABLE` or 400 `VALIDATION_FAILED`. |
| Errors | RFC 9457 Problem Details, `Content-Type: application/problem+json`. See §3. |
| Idempotency | Not supported as a header in MVP. Invitation accept/decline and state transitions are idempotent by state check: repeating a completed transition returns 409 `INVALID_TRANSITION` with the current state. |

### 1.1 Page envelope

```json
{
  "content": [ { "id": "0190…", "title": "…" } ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7,
  "sort": ["createdAt,desc"]
}
```

Total counts are computed by a separate count query with the same filters and scope.

### 1.2 Concealment

For private resources the response to a caller without access is 404 `NOT_FOUND`, identical in body and timing to a nonexistent ID (ADR-006). Authenticated users with a permitted role but failing an object condition receive 403 `FORBIDDEN_STATE` or 403 `FORBIDDEN_ROLE`, documented per endpoint below.

## 2. Authentication and account endpoints

| Method | Path | Auth | Purpose | Success | Errors |
|---|---|---|---|---|---|
| POST | `/auth/login` | public | Sign in | 200 `{accessToken, tokenType:"Bearer", expiresIn:900, user}` | 400 `VALIDATION_FAILED`; 401 `INVALID_CREDENTIALS` (also used for locked accounts, see SECURITY §3); 429 `RATE_LIMITED` |
| POST | `/auth/logout` | any valid token | Revoke current token | 204 | 401 |
| GET | `/auth/me` | any valid token | Current user summary | 200 `user` | 401; 403 `PASSWORD_CHANGE_REQUIRED` is not returned here |
| POST | `/auth/change-password` | any valid token | Change own password | 204 | 400 `VALIDATION_FAILED`; 401 `INVALID_CREDENTIALS` (current password wrong) |

`user` summary: `{id, email, institutionalId, displayName, role, status, mustChangePassword, departmentId}`. Never includes `password_hash` or lockout fields.

Password policy: at least 8 characters and at most 72 UTF-8 bytes (the BCrypt input limit, ADR-005; IMPLEMENTATION_LOG §10); must differ from the last password; rejects values on a bundled list of common passwords (SECURITY §3).

Gate: while `mustChangePassword` is true, every endpoint except `/auth/me`, `/auth/logout`, and `/auth/change-password` returns 403 `PASSWORD_CHANGE_REQUIRED`.

Refresh tokens are deferred (ADR-004); no `/auth/refresh` endpoint exists in MVP.

## 3. Error model

| HTTP | `code` (in `errorCode` extension) | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bad field values; `errors[]` lists field and reason |
| 400 | `FIELD_NOT_EDITABLE` | Writable-field policy violated (e.g. `role` on `PATCH /users/me`) |
| 400 | `INVALID_SORT` | Sort field not allowlisted |
| 400 | `RANGE_TOO_LARGE` | Calendar range > 92 days |
| 400 | `INVALID_STATUS_VALUE` | Unknown value in a filter |
| 401 | `UNAUTHENTICATED` | Missing, malformed, invalid, expired, or revoked token; deactivated user |
| 401 | `INVALID_CREDENTIALS` | Login failure or wrong current password |
| 403 | `FORBIDDEN_ROLE` | Role lacks the permission for the endpoint, whatever the object (AUTHORIZATION_MATRIX §1 step 3) |
| 403 | `FORBIDDEN_STATE` | Role permits the action and the caller can see the object, but an object condition fails (not owner, not assignee, not supervisor). See AUTHORIZATION_MATRIX §1 |
| 403 | `PASSWORD_CHANGE_REQUIRED` | Forced password change pending |
| 404 | `NOT_FOUND` | Missing, or hidden by concealment |
| 409 | `STALE_VERSION` | Optimistic lock failed |
| 409 | `DUPLICATE` | Unique key conflict (e.g. catalog code, existing membership) |
| 409 | `INVALID_TRANSITION` | Guarded state machine refused the move |
| 409 | `TEAM_FULL` | Team size cap reached |
| 409 | `CATALOG_ITEM_IN_USE` | Delete of a referenced catalog row |
| 409 | `SUBMISSION_EXISTS` | Second submission for a milestone |
| 409 | `VERSION_SEALED` | Mutation attempted on a sealed version |
| 409 | `REVIEW_EXISTS` | Second decision for a version |
| 413 | `FILE_TOO_LARGE` | File over 20 MiB |
| 422 | `PROPOSAL_INCOMPLETE` | Missing title, description, term, or milestone; `missing[]` lists fields |
| 422 | `PROJECT_ARCHIVED` | Write to an archived project |
| 422 | `PROJECT_NOT_EDITABLE` | Project details cannot change in its current state |
| 422 | `PROJECT_NOT_OPEN_FOR_MEMBERSHIP` | Invitation or join on a completed or archived project |
| 422 | `SUPERVISOR_NOT_ELIGIBLE` | Supervisor is not an active faculty user |
| 422 | `PRECONDITION_NOT_MET` | Authorized action refused for a stated, fixable reason; `missing[]` names each unmet condition (activate, complete, remove owner, deactivate last admin) |
| 422 | `ASSIGNEE_NOT_MEMBER` | Assignee is not an active member |
| 422 | `MILESTONE_CLOSED` | Upload or submit to a closed milestone |
| 422 | `DEADLINE_PASSED` | After `dueAt` without `allowLate` |
| 422 | `NO_FILES` | Submit with zero files |
| 422 | `FILE_LIMIT_EXCEEDED` | More than 5 files in a version |
| 422 | `FILE_TYPE_NOT_ALLOWED` | Magic bytes not in allowlist, or extension/content mismatch |
| 422 | `FEEDBACK_REQUIRED` | CHANGES_REQUESTED without feedback |
| 429 | `RATE_LIMITED` | Throttled; `Retry-After` header set |
| 500 | `INTERNAL_ERROR` | Unexpected; response contains `requestId` only |
| 503 | `DEPENDENCY_UNAVAILABLE` | Database or file storage unavailable |

Problem type URI: `urn:campushub:problem:<code lower-kebab>`, e.g. `urn:campushub:problem:stale-version`. The response body includes `type`, `title`, `status`, `detail` (safe text), `instance`, `errorCode`, `requestId`, and `errors[]` for validation.

## 4. User endpoints

| Method | Path | Who | Purpose | Notes |
|---|---|---|---|---|
| GET | `/users/me` | self | Own profile | Full profile fields |
| PATCH | `/users/me` | self | Edit display name, program, cohort, designation, bio, departmentId | Rejects `role`, `status`, `email`, `institutionalId` with 400 `FIELD_NOT_EDITABLE` (NS-03) |
| GET | `/users/{id}` | see matrix | Public profile card | Name, role, department. 404 if not permitted |
| GET | `/admin/users` | ADMIN | List with `role`, `status`, `q` (email/name prefix) | Paginated; sort allowlist: `displayName`, `email`, `createdAt` |
| POST | `/admin/users` | ADMIN | Create account | Body: `email`, `institutionalId?`, `displayName`, `role`, `departmentId?`. Returns 201 with `temporaryPassword` shown once |
| POST | `/admin/users/{id}/deactivate` | ADMIN | Deactivate (ADR-017) | 200; writes audit; 422 if the user is the last ADMIN |
| POST | `/admin/users/{id}/reactivate` | ADMIN | Reactivate | 200 |
| POST | `/admin/users/{id}/reset-password` | ADMIN | New temporary password | 201 with `temporaryPassword`; sets `mustChangePassword=true`; revokes existing tokens |
| PATCH | `/admin/users/{id}` | ADMIN | Edit profile fields and `departmentId` | Role change returns 422 `ROLE_CHANGE_NOT_SUPPORTED` in MVP |
| GET | `/users/faculty` | ADMIN | Active faculty for choosing a supervisor | MVP addition, not in the full contract. Returns a plain array of `{id, displayName}`, unpaginated (IMPLEMENTATION_LOG §11). Other roles get 403 `FORBIDDEN_ROLE` |

## 5. Catalog endpoints

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/catalog/departments` | any authenticated | Paginated list; `active` filter |
| GET | `/catalog/terms` | any authenticated | Paginated; `status` filter; sort `startsOn` |
| GET | `/catalog/courses` | any authenticated | Paginated; `departmentId`, `active` filters |
| POST | `/admin/catalog/departments` | ADMIN | Create; 409 `DUPLICATE` on code |
| PATCH | `/admin/catalog/departments/{id}` | ADMIN | Update name or active |
| DELETE | `/admin/catalog/departments/{id}` | ADMIN | Delete only if unreferenced; else 409 `CATALOG_ITEM_IN_USE` (FR-CAT-03) |
| POST/PATCH/DELETE | `/admin/catalog/terms[/{id}]` | ADMIN | Same rules; `endsOn > startsOn` (422 `VALIDATION_FAILED`) |
| POST/PATCH/DELETE | `/admin/catalog/courses[/{id}]` | ADMIN | Same rules |

Catalog GET responses are cached (ADR-010). Every admin write evicts the affected cache name.

## 6. Project endpoints

**MVP subset (Stage 1, IMPLEMENTATION_LOG §11).** Only the rows below are implemented. Everything else in this section is deferred and must not be documented as available. Deviations from the full rows:

- Project status is `ACTIVE` or `ARCHIVED` only. There is no DRAFT, PROPOSED, APPROVED, REJECTED, or COMPLETED state, so the lifecycle endpoints (`submit-proposal`, `revise`, `approve`, `reject`, `activate`, `complete`) are not implemented.
- `POST /projects` takes `title`, `description?`, `subject?`, `startDate?`, `dueDate?`. `termId` and `courseId` are not implemented. `dueDate` before `startDate` returns 422 `VALIDATION_FAILED` with field `dueDate`. Create is 400 `VALIDATION_FAILED` for invalid input, and is STUDENT only.
- `PATCH /projects/{id}` is owner-only and works on `ACTIVE` projects. Full-field semantics: an omitted optional field is cleared. Stale `version` returns 409 `STALE_VERSION`; an archived project returns 422 `PROJECT_ARCHIVED`, which is checked before the version.
- `PUT /projects/{id}/supervisor` takes `{supervisorId, version}`. It is ADMIN only, and the supervisor must be an active FACULTY account.
- `POST /projects/{id}/archive` is ADMIN only, takes `{version}`, and moves `ACTIVE` to `ARCHIVED`. Archived projects are read-only.
- The `GET /projects` filter is `status` only. `termId`, `role`, and `supervisorId` filters are not implemented. Sort is `createdAt,desc` with `id,desc` as the tie-breaker. Invalid `status` or `size` values return 400 `VALIDATION_FAILED`.
- A malformed `{id}` path value returns 404 `NOT_FOUND`, the same as a missing one.

| Method | Path | Who | Purpose | Success | Notes |
|---|---|---|---|---|---|
| GET | `/projects` | scoped | List visible projects | 200 page | Filters: `status`, `termId`, `supervisorId` (admin only), `role` (`OWNER`,`MEMBER`,`SUPERVISOR`). Student sees membership; faculty sees supervised; admin sees all |
| POST | `/projects` | STUDENT | Create DRAFT | 201 | Body: `title`, `description?`, `termId`, `courseId?`. Term must be ACTIVE (422 `VALIDATION_FAILED` with field `termId`) |
| GET | `/projects/{id}` | member, supervisor, admin | Detail | 200 | Includes members, supervisor summary, `progress`, milestone summary. 404 otherwise |
| PATCH | `/projects/{id}` | owner | Edit details | 200 | Allowed in DRAFT and REJECTED; `version` required. Otherwise 422 `PROJECT_NOT_EDITABLE`; archived 422 `PROJECT_ARCHIVED` |
| POST | `/projects/{id}/submit-proposal` | owner | DRAFT → PROPOSED | 200 | 422 `PROPOSAL_INCOMPLETE` with `missing[]` |
| POST | `/projects/{id}/revise` | owner | REJECTED → DRAFT | 200 | Clears `rejectionReason` only when resubmitted |
| POST | `/projects/{id}/approve` | ADMIN | PROPOSED → APPROVED | 200 | 422 `SUPERVISOR_NOT_ELIGIBLE` if no supervisor assigned |
| POST | `/projects/{id}/reject` | ADMIN | PROPOSED → REJECTED | 200 | Body `reason` 1–1000 chars |
| PUT | `/projects/{id}/supervisor` | ADMIN | Assign or reassign supervisor | 200 | Body `supervisorId`; must be ACTIVE FACULTY (422 `SUPERVISOR_NOT_ELIGIBLE`); audited (ADR-022) |
| POST | `/projects/{id}/activate` | owner | APPROVED → ACTIVE | 200 | Requires ≥ 1 ACTIVE member; otherwise 422 `PRECONDITION_NOT_MET` with `missing[]` |
| POST | `/projects/{id}/complete` | supervisor, ADMIN | ACTIVE → COMPLETED | 200 | Requires ≥ 1 milestone and all CLOSED; else 422 `PRECONDITION_NOT_MET` with `missing[]` |
| POST | `/projects/{id}/archive` | ADMIN | COMPLETED or REJECTED → ARCHIVED | 200 | Read-only afterwards |

### 6.1 Members and invitations

| Method | Path | Who | Purpose | Notes |
|---|---|---|---|---|
| GET | `/projects/{id}/members` | member, supervisor, admin | List | Basic profile fields only |
| DELETE | `/projects/{id}/members/{userId}` | owner | Remove member | 204; cannot remove owner (422 `PRECONDITION_NOT_MET`); unassigns their open tasks in the same transaction |
| POST | `/projects/{id}/invitations` | owner | Invite | 201; body `inviteeId`; invitee must be ACTIVE STUDENT; 409 `TEAM_FULL`; 409 `DUPLICATE` if already a member or PENDING |
| GET | `/projects/{id}/invitations` | owner, ADMIN | List | Paginated |
| GET | `/invitations/mine` | STUDENT | Own invitations | Paginated; `status` filter |
| POST | `/invitations/{id}/accept` | invitee | Accept | 200; creates membership in one transaction; 409 `TEAM_FULL`; 422 `PROJECT_NOT_OPEN_FOR_MEMBERSHIP`; 404 for non-invitee |
| POST | `/invitations/{id}/decline` | invitee | Decline | 200; 404 for non-invitee |

Invitations expire 14 days after creation. Expiry is applied on read and by an hourly job (DATA_MODEL §5.3).

## 7. Milestone endpoints

| Method | Path | Who | Purpose | Notes |
|---|---|---|---|---|
| GET | `/projects/{id}/milestones` | member, supervisor, admin | List by sequence | |
| POST | `/projects/{id}/milestones` | owner | Create | 201; allowed when project is DRAFT, REJECTED, APPROVED, or ACTIVE; `sequence` assigned by the service |
| PATCH | `/milestones/{id}` | owner (title, dueAt); supervisor (allowLate only) | Edit | Unknown field for caller → 400 `FIELD_NOT_EDITABLE`; `version` required |
| POST | `/milestones/{id}/close` | owner, supervisor | OPEN → CLOSED | 200; irreversible in MVP |

## 8. Task endpoints

| Method | Path | Who | Purpose | Notes |
|---|---|---|---|---|
| GET | `/projects/{id}/tasks` | member, supervisor, admin | List | Filters: `status`, `assigneeId`, `milestoneId`, `dueBefore`. Sort allowlist: `dueAt`, `createdAt`, `priority`, `status` |
| POST | `/projects/{id}/tasks` | member | Create | 201; project must be APPROVED or ACTIVE (422 `PROJECT_NOT_EDITABLE` otherwise) |
| GET | `/tasks/{id}` | member, supervisor, admin | Detail | |
| PATCH | `/tasks/{id}` | member | Edit title, description, priority, dueAt, milestoneId, assigneeId | `version` required; `assigneeId` must be an active member (422 `ASSIGNEE_NOT_MEMBER`) |
| POST | `/tasks/{id}/transitions` | assignee, owner | Change status | Body `{to, version, blockedReason?}`; see STATE_MACHINES §3. A member who is neither assignee nor owner → 403 `FORBIDDEN_STATE`; a non-member → 404 `NOT_FOUND` |
| GET | `/tasks/{id}/comments` | member, supervisor (P1; admin receives 404) | List task comments | P1; paginated, oldest first |
| POST | `/tasks/{id}/comments` | member, supervisor | Append a comment | P1; 201; body 1–2000 characters; append-only, no edit or delete endpoints |

Task `status` is never writable through `PATCH /tasks/{id}`; sending it there returns 400 `FIELD_NOT_EDITABLE` (FR-TASK-03).

**MVP subset (IMPLEMENTATION_LOG §11, Stage 2).** Implemented: list (filter `status` only; sort `createdAt,asc`, `id,asc`), create (owner only; default priority `MEDIUM`; archived project → 422 `PROJECT_ARCHIVED`, not `PROJECT_NOT_EDITABLE`), detail, PATCH (owner only; `title`, `description`, `priority`, `dueDate`; `version` required), and transitions (owner only; `blockedReason` required for `BLOCKED` and cleared on any other transition; an illegal or same-state move → 409 `INVALID_TRANSITION`). Deviations: the request field is `dueDate`, not `dueAt`; `assigneeId`, `milestoneId`, `dueBefore`, and the comment endpoints are not built. The assignee role is absent, so the owner performs every transition.

## 9. Calendar

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/calendar?from=YYYY-MM-DD&to=YYYY-MM-DD` | any authenticated | Milestones (`type: MILESTONE_DUE`) and tasks (`type: TASK_DUE`) visible to the caller |

Rules: `from` and `to` required; `to >= from`; range ≤ 92 days (400 `RANGE_TOO_LARGE`). Items carry UTC instants; the client converts to the user's time zone (ADR-013). Visibility: a student sees items in projects where they are members and tasks assigned to them; faculty sees supervised projects; admins see all.

## 10. Submission and review endpoints

| Method | Path | Who | Purpose | Success | Notes |
|---|---|---|---|---|---|
| POST | `/milestones/{id}/submission` | member | Create submission with DRAFT v1 | 201 | 409 `SUBMISSION_EXISTS`; 422 `MILESTONE_CLOSED` |
| GET | `/milestones/{id}/submission` | member, supervisor, admin | Submission header and version summaries | 200 | 404 if none |
| GET | `/submissions/{id}/versions/{versionNo}` | member, supervisor, admin | Version with files and review | 200 | Files list excludes storage keys |
| POST | `/submissions/{id}/versions` | member | Create next DRAFT | 201 | Only when the latest version is CHANGES_REQUESTED; else 409 `INVALID_TRANSITION` |
| POST | `/submission-versions/{versionId}/files` | member (uploader), multipart field `file` | Upload | 201 | 413 `FILE_TOO_LARGE`; 422 `FILE_TYPE_NOT_ALLOWED`, `FILE_LIMIT_EXCEEDED`, `DEADLINE_PASSED`, `MILESTONE_CLOSED`; 409 `VERSION_SEALED` |
| DELETE | `/submission-versions/{versionId}/files/{fileId}` | member | Remove from DRAFT | 204 | 409 `VERSION_SEALED` once sealed |
| POST | `/submission-versions/{versionId}/submit` | member | Seal DRAFT → SUBMITTED | 200 | 422 `NO_FILES`, `DEADLINE_PASSED`; 409 `VERSION_SEALED` if already sealed |
| POST | `/submission-versions/{versionId}/review/start` | supervisor | SUBMITTED → UNDER_REVIEW | 200 | Other users → 404 (not supervisor of project) |
| POST | `/submission-versions/{versionId}/review` | supervisor | Record decision | 201 | Body `decision`, `feedback?`; 422 `FEEDBACK_REQUIRED`; 409 `REVIEW_EXISTS`; requires UNDER_REVIEW (409 `INVALID_TRANSITION` otherwise) |
| GET | `/submission-files/{fileId}/download` | member, supervisor, admin (see matrix) | Stream bytes | 200 | `Content-Disposition: attachment`; `X-Content-Type-Options: nosniff`; each download audited. Unauthorized → 404 (NS-12) |

**MVP subset (IMPLEMENTATION_LOG §11, Stage 3).** Built under project-scoped paths instead of the milestone and version paths above: `POST /projects/{id}/submissions` (owner only; 201; creates revision 1, or the next revision only after CHANGES_REQUESTED, else 409 `INVALID_TRANSITION`; archived project → 422 `PROJECT_ARCHIVED`), `GET /projects/{id}/submissions` (unpaginated, ordered by `revisionNo` ascending), `GET /submissions/{id}`, `POST /submissions/{id}/review/start` (faculty supervisor only; SUBMITTED → UNDER_REVIEW; 200), and `POST /submissions/{id}/review` (faculty supervisor only; body `{decision, feedback?}`; 201). The review rules are: an admin gets 403 `FORBIDDEN_ROLE`; a non-supervisor faculty member or an invisible project gets 404; a decision on a decided submission gets 409 `REVIEW_EXISTS`; a decision before the review starts gets 409 `INVALID_TRANSITION`; CHANGES_REQUESTED without non-blank feedback gets 422 `FEEDBACK_REQUIRED`. Deviations: there is no DRAFT, no version-create endpoint, no file upload or download, and no milestone scope. A submission is a single revision row with a `https://` link instead of files. Submission reviews are blocked on archived projects (422 `PROJECT_ARCHIVED`). Revision and review checks run after the archived check and before any write.

Admin download: ADMIN has no path to student submission files in MVP (ADR-007). `GET /submission-files/{fileId}/download` returns 404 for administrators on submission files. Administrators can still see audit metadata about submissions.

## 11. Skills and certifications (P1)

| Method | Path | Who | Purpose | Notes |
|---|---|---|---|---|
| GET | `/skills` | any authenticated | Catalog, paginated, `category` filter | |
| POST | `/admin/skills` | ADMIN | Add skill | 409 `DUPLICATE` on normalized name |
| GET | `/users/me/skills` | STUDENT | Own skills | |
| PUT | `/users/me/skills` | STUDENT | Replace full set atomically | Body: `[{skillId, proficiency, evidenceNote?}]`; max 50 entries |
| GET | `/users/me/certifications` | owner | List own | Paginated |
| POST | `/users/me/certifications` | owner | Create | 201; `credentialUrl` must be `https` (422 `VALIDATION_FAILED`) |
| DELETE | `/users/me/certifications/{id}` | owner | Delete with file | 204; audited; 404 for non-owners |
| POST | `/users/me/certifications/{id}/file` | owner | Upload evidence | 201; same validation as submission files |
| GET | `/users/me/certifications/{id}/file` | owner | Download evidence | 200 attachment; 404 otherwise |

## 12. Dashboards (read-only aggregates)

| Method | Path | Who | Metrics |
|---|---|---|---|
| GET | `/dashboard/student` | STUDENT | `tasksDueNext7Days` (assigned, status ≠ DONE, `dueAt` in [now, now+7d]); `overdueTasks` (assigned, status ≠ DONE, `dueAt` < now); `upcomingMilestones` (next 5 OPEN milestones of own projects); `completionRate` = DONE tasks ÷ all tasks assigned to caller (null if none); `latestReviews` (latest 5 decisions on own submissions); `skillsCount` (P1) |
| GET | `/dashboard/faculty` | FACULTY | `pendingReviews` (versions SUBMITTED or UNDER_REVIEW in supervised projects); `supervisedProjects` by status; `atRiskMilestones` (OPEN, `dueAt` within 7 days, and project progress < 50%); `submissionsByStatus` |
| GET | `/dashboard/admin` | ADMIN | `activeUsersByRole` (status ACTIVE only); `projectsByStatus`; `termSummaries` (count per term and status) |

Every metric is defined here; the UI displays the definition on hover or in a help panel (FR-DASH-04).

**Project progress** (used above and on project detail): `DONE tasks ÷ all tasks in the project`; null when the project has no tasks. Deterministic and computed in one aggregate query.

## 13. Audit

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/admin/audit-events` | ADMIN | Paginated; filters `actorId`, `action`, `targetType`, `targetId`, `from`, `to`; sort `occurredAt` (default desc); max size 100 |

Response items: `{id, occurredAt, actorId, action, targetType, targetId, outcome, requestId, metadata}`. `metadata` only contains allow-listed keys (SECURITY §8).

## 14. Health and operations

| Method | Path | Who | Response |
|---|---|---|---|
| GET | `/actuator/health/liveness` | public | `{"status":"UP"}` |
| GET | `/actuator/health/readiness` | public | `{"status":"UP"}` or 503 when DB or storage check fails. No component details are exposed publicly (NFR-OPS-01) |

Management endpoints other than health are not exposed on the public port.

## 15. Contract checks

- Every endpoint above has a Spring MVC mapping and a test in the security suite (TEST_STRATEGY §4).
- The generated OpenAPI file is compared with a committed snapshot in CI; unexplained diffs fail the build.
- The error code list in §3 must equal the enum in `shared/errors` (a unit test compares them).
