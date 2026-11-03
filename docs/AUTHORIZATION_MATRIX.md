# CampusHub — Authorization Matrix

**Purpose:** The single source of truth for who may call each endpoint and what denial each caller receives. Every row is a test case in the security suite (TEST_STRATEGY §4).
**Scope:** Every endpoint in [API_CONTRACT.md](API_CONTRACT.md) §2–§13, plus the NS-01 to NS-12 negative journeys in [USER_STORIES.md](USER_STORIES.md).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [STATE_MACHINES.md](STATE_MACHINES.md) (state rules applied after authorization), [SECURITY.md](SECURITY.md) §4, [DECISIONS.md](DECISIONS.md) ADR-006 and ADR-007.

## 1. Decision procedure

Every request passes these checks in order. The first failing check decides the response.

| Step | Check | Failure response |
|---|---|---|
| 1 | Token present, valid, not revoked, not older than `revoked_after`, and the user is ACTIVE | 401 `UNAUTHENTICATED` |
| 2 | `mustChangePassword` is false, or the endpoint is one of `/auth/me`, `/auth/logout`, `/auth/change-password` | 403 `PASSWORD_CHANGE_REQUIRED` |
| 3 | The caller's role is allowed on the endpoint | 403 `FORBIDDEN_ROLE` (for every ID, existing or not) |
| 4 | The caller can see the target object (§2 definitions) | 404 `NOT_FOUND` (identical body and timing to a missing ID) |
| 5 | The caller satisfies the object condition (owner, assignee, supervisor) | 403 `FORBIDDEN_STATE` |
| 6 | State and business rules ([STATE_MACHINES.md](STATE_MACHINES.md)) | 409 or 422 with the specific code |

Anonymous callers receive 401 on every endpoint except the public ones: `POST /auth/login`, `GET /actuator/health/liveness`, `GET /actuator/health/readiness`.

Denial mapping (ADR-006): **403** is returned only when the caller has the role and can see the object, but lacks an object condition. **404** is returned when the caller cannot see the object at all. A role-only route never returns 404 for an ID, so role-only denials do not reveal existence.

## 2. Actor and visibility definitions

| Code | Actor | Definition |
|---|---|---|
| SO | Student, project owner | Student with an `OWNER` membership in the project |
| SM | Student, other member | Student with an active non-owner membership |
| SI | Student, invitee | Student with a PENDING invitation and no membership. Sees nothing about the project except through `/invitations/mine` |
| SX | Student, other | Any other student |
| FS | Faculty, supervisor | Faculty user who is `projects.supervisor_id` for the project |
| FX | Faculty, other | Any other faculty user |
| AD | Administrator | `ADMIN` role. Sees project metadata for all projects; excluded categories in §3 |

Visibility of a project: owner, member, supervisor, and admin can see it. SI and SX cannot (404). FX cannot (404).

**Excluded categories for administrators (ADR-007).** Administrators never receive submission file bytes, task comments (P1), or student skills and certifications (P1). For these categories an administrator receives 404, not 403, so that the response does not disclose that the content exists. Administrators do see submission metadata, review decisions and feedback, task titles and status, and audit events.

## 3. Matrix

Symbols: **✔** allowed (subject to §1 step 6 state rules); **404** concealed; **403R** `FORBIDDEN_ROLE`; **403S** `FORBIDDEN_STATE`; **✔\*** allowed with a field or scope restriction given in the notes column; **400F** the actor may act on the object but not on the named field (`FIELD_NOT_EDITABLE`).

### 3.1 Authentication and account

| Action | Endpoint | Allowed for | Denials | Notes |
|---|---|---|---|---|
| Sign in | `POST /auth/login` | Public | 401 `INVALID_CREDENTIALS` (also for locked accounts), 429 | Public endpoint; §1 steps 1–2 do not apply |
| Sign out | `POST /auth/logout` | Any valid token | 401 | Revokes the current `jti` |
| Current user | `GET /auth/me` | Any valid token | 401 | Allowed while `mustChangePassword` is true |
| Change own password | `POST /auth/change-password` | Any valid token | 401 `INVALID_CREDENTIALS` | Allowed while `mustChangePassword` is true |

### 3.2 Users and profiles

| Action | Endpoint | SO | SM | SI | SX | FS | FX | AD | Notes |
|---|---|---|---|---|---|---|---|---|---|
| Own profile | `GET /users/me` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | Self only |
| Edit own profile | `PATCH /users/me` | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | Editable: display name, program, cohort, designation, bio, `departmentId`. `role`, `status`, `email`, `institutionalId` → 400 `FIELD_NOT_EDITABLE` (NS-03) |
| Public profile card | `GET /users/{id}` | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔ | Caller is the target, or the target is ACTIVE and shares a project with the caller as member or supervisor. Otherwise 404 |
| List all users | `GET /admin/users` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | |
| Create user | `POST /admin/users` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | Returns `temporaryPassword` once |
| Deactivate, reactivate | `POST /admin/users/{id}/deactivate`, `/reactivate` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | Last ADMIN cannot be deactivated (422 `PRECONDITION_NOT_MET`) |
| Reset password | `POST /admin/users/{id}/reset-password` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | |
| Edit any user | `PATCH /admin/users/{id}` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | Role change → 422 `ROLE_CHANGE_NOT_SUPPORTED` |

### 3.3 Catalog

| Action | Endpoint | Allowed for | Denials |
|---|---|---|---|
| List departments, terms, courses | `GET /catalog/*` | Any authenticated user | 401 |
| Create, update, delete catalog rows | `POST|PATCH|DELETE /admin/catalog/*` | AD | 403R for all other roles |

### 3.4 Projects, membership, and invitations

| Action | Endpoint | SO | SM | SI | SX | FS | FX | AD | Notes |
|---|---|---|---|---|---|---|---|---|---|
| List projects | `GET /projects` | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔\* | ✔ | Scoped query: student sees memberships; faculty sees supervised; admin sees all. Empty page for SI, SX, FX. Never 404 for lists |
| Create project | `POST /projects` | ✔ | ✔ | ✔ | ✔ | 403R | 403R | 403R | STUDENT role; creates a DRAFT for the caller as owner |
| Read project | `GET /projects/{id}` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | NS-02 (non-member 404). NS-05 (FX 404) |
| Edit project details | `PATCH /projects/{id}` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only; state rules in STATE_MACHINES §2.3 |
| Submit proposal | `POST /projects/{id}/submit-proposal` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only |
| Revise rejected proposal | `POST /projects/{id}/revise` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only |
| Approve proposal | `POST /projects/{id}/approve` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | ADMIN only. NS-04 (student owner → 403R) |
| Reject proposal | `POST /projects/{id}/reject` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | ADMIN only |
| Assign supervisor | `PUT /projects/{id}/supervisor` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | ADMIN only; target must be ACTIVE faculty (422 `SUPERVISOR_NOT_ELIGIBLE`) |
| Activate project | `POST /projects/{id}/activate` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only |
| Complete project | `POST /projects/{id}/complete` | 403R | 403R | 403R | 403R | ✔ | 404 | ✔ | Supervisor or ADMIN. Student callers get 403R even when they are members |
| Archive project | `POST /projects/{id}/archive` | 403R | 403R | 403R | 403R | 403R | 403R | ✔ | ADMIN only |
| List members | `GET /projects/{id}/members` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | Basic profile fields only |
| Remove member | `DELETE /projects/{id}/members/{userId}` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only. Owner cannot be removed (422 `PRECONDITION_NOT_MET`) |
| Invite | `POST /projects/{id}/invitations` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only |
| List project invitations | `GET /projects/{id}/invitations` | ✔ | 403S | 404 | 404 | 403S | 404 | ✔ | Owner or ADMIN |
| List own invitations | `GET /invitations/mine` | 403R | 403R | ✔ | ✔ | 403R | 403R | 403R | STUDENT only; returns only the caller's invitations |
| Accept invitation | `POST /invitations/{id}/accept` | 404 | 404 | ✔ | 404 | 403R | 403R | 403R | Invitee only. Other students receive 404 (non-invitee), not 403. Non-student roles get 403R |
| Decline invitation | `POST /invitations/{id}/decline` | 404 | 404 | ✔ | 404 | 403R | 403R | 403R | Invitee only; same rules as accept |

Note on SO and invitations: the owner is the only actor who can invite, and invitations can only be seen by the owner, the admin, and the invitee. A member can see that a team is growing through the members list, not through invitations.

### 3.5 Milestones

| Action | Endpoint | SO | SM | SI | SX | FS | FX | AD | Notes |
|---|---|---|---|---|---|---|---|---|---|
| List milestones | `GET /projects/{id}/milestones` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | |
| Create milestone | `POST /projects/{id}/milestones` | ✔ | 403S | 404 | 404 | 403S | 404 | 403S | Owner only |
| Edit title and due date | `PATCH /milestones/{id}` (title, `dueAt`) | ✔ | 403S | 404 | 404 | 400F | 404 | 403S | Owner only. Supervisor sending `title` or `dueAt` → 400 `FIELD_NOT_EDITABLE` |
| Edit allow-late | `PATCH /milestones/{id}` (`allowLate`) | 400F | 403S | 404 | 404 | ✔ | 404 | 403S | Supervisor only. Owner sending `allowLate` → 400 `FIELD_NOT_EDITABLE` |
| Close milestone | `POST /milestones/{id}/close` | ✔ | 403S | 404 | 404 | ✔ | 404 | 403S | Owner or supervisor |

### 3.6 Tasks and task comments

| Action | Endpoint | SO | SM | SI | SX | FS | FX | AD | Notes |
|---|---|---|---|---|---|---|---|---|---|
| List tasks | `GET /projects/{id}/tasks` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | Filters and sort allowlist per API_CONTRACT §8 |
| Create task | `POST /projects/{id}/tasks` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member. Project APPROVED or ACTIVE |
| Read task | `GET /tasks/{id}` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | |
| Edit task fields | `PATCH /tasks/{id}` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member may edit title, description, priority, due date, milestone, and assignee. `status` → 400 `FIELD_NOT_EDITABLE`. Assignee must be an active member (422 `ASSIGNEE_NOT_MEMBER`, NS-11) |
| Change status | `POST /tasks/{id}/transitions` | ✔\* | 403S | 404 | 404 | 403S | 404 | 403S | ✔\* for the assignee or the owner. Other members → 403S (NS-11). Owner-only transitions are in STATE_MACHINES §3.2 |
| List task comments (P1) | `GET /tasks/{id}/comments` | ✔ | ✔ | 404 | 404 | ✔ | 404 | 404 | Admin is an excluded category (§2) |
| Add task comment (P1) | `POST /tasks/{id}/comments` | ✔ | ✔ | 404 | 404 | ✔ | 404 | 404 | Append-only; no edit or delete endpoint |

### 3.7 Calendar

| Action | Endpoint | Allowed for | Notes |
|---|---|---|---|
| Calendar range | `GET /calendar?from&to` | Any authenticated user | Scoped: students see their projects' milestones and their own tasks; faculty see supervised projects; admins see all. Range ≤ 92 days (400 `RANGE_TOO_LARGE`) |

### 3.8 Submissions, files, and reviews

| Action | Endpoint | SO | SM | SI | SX | FS | FX | AD | Notes |
|---|---|---|---|---|---|---|---|---|---|
| Create submission | `POST /milestones/{id}/submission` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member. 409 `SUBMISSION_EXISTS` |
| Read submission header | `GET /milestones/{id}/submission` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | Versions summarized |
| Read version | `GET /submissions/{id}/versions/{versionNo}` | ✔ | ✔ | 404 | 404 | ✔ | 404 | ✔ | Admin sees file names, sizes and types, never storage keys or bytes |
| New version | `POST /submissions/{id}/versions` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member. Latest version must be CHANGES_REQUESTED |
| Upload file | `POST /submission-versions/{versionId}/files` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member; DRAFT only. Uploader is recorded |
| Remove file | `DELETE /submission-versions/{versionId}/files/{fileId}` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member; DRAFT only |
| Seal and submit | `POST /submission-versions/{versionId}/submit` | ✔ | ✔ | 404 | 404 | 403S | 404 | 403S | Any member; DRAFT only |
| Start review | `POST /submission-versions/{versionId}/review/start` | 403R | 403R | 403R | 403R | ✔ | 404 | 403R | FACULTY role, then supervision. Admin does not review (ADR-007) |
| Record decision | `POST /submission-versions/{versionId}/review` | 403R | 403R | 403R | 403R | ✔ | 404 | 403R | FACULTY role, then supervision. NS-05 (FX → 404) |
| Download file | `GET /submission-files/{fileId}/download` | ✔ | ✔ | 404 | 404 | ✔ | 404 | 404 | NS-12 (admin → 404). Every download is audited. `Content-Disposition: attachment` |

### 3.9 Skills and certifications (P1)

| Action | Endpoint | Allowed for | Denials | Notes |
|---|---|---|---|---|
| Skill catalog | `GET /skills` | Any authenticated user | 401 | Paginated; `category` filter |
| Add skill | `POST /admin/skills` | AD | 403R | 409 `DUPLICATE` on normalized name |
| Read own skills | `GET /users/me/skills` | STUDENT (self) | 403R for other roles | Faculty and admin never see student skills (OD-5) |
| Replace own skills | `PUT /users/me/skills` | STUDENT (self) | 403R for other roles | Full set replaced atomically; max 50 |
| List own certifications | `GET /users/me/certifications` | STUDENT (self) | 403R for other roles | |
| Create certification | `POST /users/me/certifications` | STUDENT (self) | 403R for other roles | `credentialUrl` must be `https` |
| Delete certification | `DELETE /users/me/certifications/{id}` | STUDENT owner | 404 for non-owners (NS-01) | Audited |
| Upload evidence | `POST /users/me/certifications/{id}/file` | STUDENT owner | 404 for non-owners | |
| Download evidence | `GET /users/me/certifications/{id}/file` | STUDENT owner | 404 for non-owners (NS-01) | Attachment only |

### 3.10 Dashboards, audit, and operations

| Action | Endpoint | Allowed for | Denials | Notes |
|---|---|---|---|---|
| Student dashboard | `GET /dashboard/student` | STUDENT (self data only) | 403R for other roles | Own tasks and own memberships |
| Faculty dashboard | `GET /dashboard/faculty` | FACULTY (supervised projects only) | 403R for other roles | |
| Admin dashboard | `GET /dashboard/admin` | AD | 403R for other roles | Aggregates only |
| Audit events | `GET /admin/audit-events` | AD | 403R for other roles | Metadata contains allow-listed keys only (SECURITY §8) |
| Liveness, readiness | `GET /actuator/health/*` | Public | None | Readiness exposes no component detail |

## 4. Negative security journeys (NS-01 to NS-12)

Each journey maps to rows above and to an automated test named after its ID.

| ID | Journey | Expected | Matrix row | Test |
|---|---|---|---|---|
| NS-01 | Student A reads Student B's private certification | 404 | §3.9 "Download evidence" (non-owner) | `NS01_otherStudentCertificationIsConcealed` |
| NS-02 | Non-member requests a private project by ID | 404 | §3.4 "Read project" (SX, SI) | `NS02_nonMemberProjectIsConcealed` |
| NS-03 | Student sets own role to FACULTY or ADMIN | 400 `FIELD_NOT_EDITABLE`; no change | §3.2 "Edit own profile" | `NS03_roleCannotBeSelfAssigned` |
| NS-04 | Student approves own proposal | 403R | §3.4 "Approve proposal" (SO) | `NS04_studentCannotApprove` |
| NS-05 | Faculty reviews a project they do not supervise | 404 | §3.8 "Record decision" (FX) | `NS05_unrelatedFacultyIsConcealed` |
| NS-06 | Member uploads after the deadline without `allowLate` | 422 `DEADLINE_PASSED` | §3.8 "Upload file" (SM, state rule step 6) | `NS06_lateUploadRejected` |
| NS-07 | Member uploads `../../etc/passwd` or a mismatched file type | Name sanitized or rejected; 422 `FILE_TYPE_NOT_ALLOWED`; nothing written outside storage root | §3.8 "Upload file" | `NS07_hostileUploadRejected` |
| NS-08 | Deactivated user uses a valid token | 401 on next request | §1 step 1 | `NS08_deactivatedTokenRejected` |
| NS-09 | Two members update the same task with one version | Second request 409 `STALE_VERSION` | §3.6 "Edit task fields" | `NS09_staleVersionOnTask` |
| NS-10 | Student modifies a sealed submission version | 409 `VERSION_SEALED`; DB row unchanged | §3.8 "Upload file" and "Remove file" | `NS10_sealedVersionImmutable` |
| NS-11 | Member moves another member's task, or assigns a non-member | 403S for the transition; 422 `ASSIGNEE_NOT_MEMBER` for the assignee | §3.6 "Change status", "Edit task fields" | `NS11_nonAssigneeCannotTransition`, `NS11_nonMemberAssigneeRejected` |
| NS-12 | Administrator downloads a student's submission file | 404 | §3.8 "Download file" (AD) | `NS12_adminCannotDownloadSubmissionFiles` |

## 5. Rules that are not visible in a single cell

- **Concealment is by object, not by route.** A student who knows a task ID from another project receives 404 for it, the same as for a nonexistent ID. Tests compare the response body and status for an existing and a nonexistent ID to confirm they are identical.
- **Role-only routes do not reveal existence.** Step 3 of §1 runs before any lookup, so a student calling `POST /projects/{id}/approve` receives 403R for every ID.
- **Supervisor reassignment moves access.** A former supervisor loses access to the project and its reviews at the moment of reassignment (404 thereafter).
- **Removed members lose access immediately.** After `DELETE /projects/{id}/members/{userId}`, the removed student's project and task endpoints return 404 on the next request. Their open tasks are unassigned in the same transaction.
- **Deactivation revokes access immediately.** `revoked_after` makes every existing token invalid on its next use (NS-08).
- **No caller can grant themselves a role or a membership.** Membership is created only by an accepted invitation, which the owner initiates; there is no self-join endpoint and no admin endpoint that adds members directly.

## 6. Test obligations

- The matrix in §3 is the input to a parameterized security test. For each row and each actor the test asserts the status and the `code` in the table. Rows marked ✔\* have a separate test for the restriction in the notes.
- A test asserts that every endpoint in API_CONTRACT has at least one row here, and that every row maps to a mapped Spring MVC route (API_CONTRACT §15).
- The response for 404 is compared with the response for a nonexistent ID: same status, same `code`, same `title`, and a latency within the tolerance set in TEST_STRATEGY §4.
- Any change to a row requires an update to this file, to API_CONTRACT, and to a DECISIONS entry in the same change. A pull request that changes one without the others fails the review checklist (CI_CD §4).
