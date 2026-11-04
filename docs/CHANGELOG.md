# CampusHub — Changelog

**Purpose:** A dated record of changes to the documentation set, the decisions, and the scope, so that a reviewer can see what changed, when, and why.
**Scope:** Documentation, decisions, scope, and (from Phase 1) code and releases. Entries are added in the same change that makes the modification.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md), [DECISIONS.md](DECISIONS.md), [SCOPE.md](SCOPE.md), [PHASE_PLAN.md](PHASE_PLAN.md).

Format: each entry has a date, a short title, and the affected documents. Accepted ADRs are not edited; a change is a new entry and a new ADR that supersedes the old one.

## [Emergency completion] — 2026-11-04 — User profiles and administrator user management

**Status:** Implemented and verified locally. Not merged. Pushed to `phase-7a-certifications` only.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/API_CONTRACT.md` (§3 `ROLE_CHANGE_NOT_SUPPORTED`; §4 `/users/me`, `/admin/users`), `docs/DEMO_SCRIPT.md` (§7 demo steps, updated limitations), `README.md` (demo pointer).

**Built:**
- Profiles: `GET` and `PATCH /users/me` for display name, department, program, cohort, designation, and bio. Email, role, status, and institutional ID are not editable and return `FIELD_NOT_EDITABLE`. Frontend profile page at `/profile`.
- Administrator user management under `/admin/users`: list with role, status, and prefix search; create (returns a one-time temporary password and sets must-change-password); update (role changes return 422 `ROLE_CHANGE_NOT_SUPPORTED`); deactivate and reactivate (deactivation revokes existing tokens); reset password (revokes existing tokens and issues a new temporary password). Each action is audited (`admin.user.*`). Frontend page at `/admin/users`, visible only to administrators.
- Self-deactivation and deactivating the last active administrator are refused with 422 `PRECONDITION_NOT_MET`. The last-administrator check is defensive only: the self-check already prevents the case through the API.

**Verified:**
- `./mvnw -B verify` passed: 226 tests, 0 failures, 0 errors, 0 skipped. This includes `ProfileFlowIntegrationTest` (9) and `UserAdminFlowIntegrationTest` (11).
- Frontend: `npm run build` (type check and bundle) and `npm run lint` pass.
- Docker Compose: `api` rebuilt with `docker compose up -d --build api`. The MySQL volume was not reset. `/actuator/health/readiness` returns `UP`.
- Authenticated HTTP against the running stack: admin list returns 200; student and faculty calls to `/admin/users` return 403; unauthenticated returns 401; admin deactivating their own account returns 422; admin role change returns 422 `ROLE_CHANGE_NOT_SUPPORTED`.

**Not verified or not built:**
- Browser walkthrough of the admin and profile pages in this release. The demo steps in §7 are written but not rehearsed in the browser.
- Project approval (DRAFT → approval → ACTIVE). Projects are still created ACTIVE.
- Milestone-linked submissions.
- Administrators cannot change a user's role, and cannot clear a user's department through the admin form (`departmentId` is omitted to leave it unchanged).
- GitHub Actions status and GitGuardian are external gates and are not reported here.

## [Phase 8 checkpoint] — 2026-11-04 — Browser check of admin and faculty flows

**Status:** Partial. Checked in the built-in browser against the local stack with Kafka disabled.

**Verified:**
- Admin: signed in; the skill catalog accepts a new skill (POST `/admin/skills` returned 201 and the list refreshed). The project page shows the Administration section (supervisor assignment, archive).
- Faculty: signed in; the supervised project shows the faculty review action on a submitted revision. The skills page shows the catalog without the admin add form. The project page has no Administration section.
- Faculty dashboard: "Supervised projects 2 / Awaiting review 2 / Reviewed 2 / Needing attention 2", with one awaiting review per project. Consistent with the seeded submissions. Not checked against the API response.

**Not verified:**
- The admin dashboard has no statistics: the page says the dashboard is for students and faculty. This is a known gap.
- Submitting a review decision, supervisor reassignment and archiving were not exercised, to avoid changing shared data.
- A test skill, "Checkpoint Skill", was created in the local database during this check.

## [Phase 8] — 2026-11-04 — Optional Kafka and transactional outbox

**Status:** Implemented locally. Disabled by default (ADR-011). Not pushed in this entry.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (FR-EVT-01 to 04, NFR-REL-01), `docs/PHASE_PLAN.md` (Phase 8 status).

**Built:**
- V16 migration: `outbox_events` (unpublished index on `published_at, created_at, id`) and `processed_events` (composite key `event_id, consumer`). No applied migration was changed.
- Producer side: `OutboxService.record` writes an outbox row in the same transaction as the membership change (invitation received, accepted, declined, member removed) and the submission review. When disabled it writes nothing.
- `OutboxRelay` publishes batches of 50 with `FOR UPDATE SKIP LOCKED`, keyed by aggregate ID for ordering, with an idempotent producer (`acks=all`). It stops at the first failure and records the attempt. Its batch runs in an explicit `TransactionTemplate`, because a self-invoked `@Transactional` method skips the proxy.
- Consumer side: `EventConsumer` and `EventHandler` skip an event whose ID is already in `processed_events`, then apply it with `EventEffect`. Retries use exponential backoff. Unparseable messages and unrecoverable failures go to `campushub.events.DLT`.
- Metrics: `campushub.outbox.published`, `campushub.outbox.publish.failures`, `campushub.events.processed`, `campushub.events.duplicates`, `campushub.events.dead_lettered`.
- Local broker: `docker compose --profile kafka up -d` starts a pinned KRaft Kafka. The `api` service reads `CAMPUSHUB_KAFKA_BOOTSTRAP_SERVERS`.

**Verified:**
- `./mvnw -B verify` passed with 186 tests and 0 failures, including `OutboxServiceTest` (2), `KafkaFlowIntegrationTest` (4: exactly-once relay and apply, rollback of the outbox row with the change, transient consumer retry, DLT for an unparseable message).
- The relay stamp bug found in the first run is fixed. Self-invoked `@Transactional` skipped the proxy, so the `published_at` update never committed and the same events were re-sent. The retry test's counter was also miscounted.

**Not verified or not built:**
- No DLT replay tooling. The Phase 8 gate needs "recovery" demonstrated, so the gate is not met yet.
- No test exercises the `SUBMISSION_REVIEWED` outbox write. Its integration coverage is still optional work.
- No run against a managed Kafka broker.

## [Phase G] — 2026-11-04 — Final local verification

**Status:** Complete for local verification. Not pushed. The fix below is committed locally.

**Affected documents:** `docs/CHANGELOG.md` (this entry). Code: `frontend/src/index.css` (checkbox layout on the notifications page).

**Verified:**
- Backend: `./mvnw -B verify` passed on the Phase F tree with 180 tests and 0 failures. No backend code changed after that run.
- Frontend: `npm run build` (type check and bundle) and `npm run lint` pass.
- Docker Compose: `api` image rebuilt from the Phase F tree with `docker compose up -d --build api`. The MySQL volume was not reset. Flyway applied V15 (`notifications`) on top of V14. The API started, but `/actuator/health` returns 401 (known, see below).
- Authorization, API smoke against the running stack: unauthenticated `GET /notifications` returns 401; a student calling `POST /admin/skills` returns 403; a faculty user's `GET /invitations/mine` returns 403; a student reading another user's notification id returns 404; another student reading a project's tasks returns 404.
- Authorization and upload security, by backend tests: `facultyCannotCreateProjects`, `facultyDirectoryIsAdminOnly`, `facultyAndAdministratorsCannotReadOrWriteStudentSkills`, `facultyCannotUploadButTheSupervisorCanReadTheFiles`, `strangersAndAdminsGetNotFoundForFiles`, `fileIdFromAnotherSubmissionIsNotFound`, `pngAndJpegAreDetectedFromTheirBytes`, `contentThatDoesNotMatchItsNameOrSignatureIsRefused`, `fileOverTheSizeLimitIsRefusedWith413`, `sixthFileOnOneRevisionIsRefused`.
- Browser, student and student2 in the built-in browser: sign-in works; the header shows the unread badge (1) after an invitation; the inbox shows "You were invited to join \"E2E smoke project\"."; mark as read clears the badge and shows the read time.
- Mobile width (375px): no horizontal page scroll on the notifications page.

**Fixed during verification:**
- The "Show unread only" checkbox on the notifications page stretched to full width because the global input style applies. Added `.checkbox-row input { width: auto; }`.

**Not verified:**
- Browser E2E as the faculty and administrator roles. Their access rules are verified by the API smoke checks and the backend tests above, not by clicking through the UI.
- Browser E2E for the other Phase D and E features (team, milestones, evidence upload, certifications) across all roles. Only the notifications flow was driven in the browser.
- Mobile responsiveness beyond the notifications page. Not checked elsewhere.
- Docker Compose health: `/actuator/health` returns 401 when unauthenticated, so the compose healthcheck (if any) cannot use it. Not investigated.
- GitHub Actions: backend and image build pass on PR #1. The GitGuardian check fails. This is Phase H work and was not changed here.

**Known gaps (carried forward):**
- No revoke endpoint for pending invitations. Invitee ACTIVE-student check: unverified in the browser.
- Hikari "Communications link failure" ERROR logs appear in some test runs. The build still passes.
- Outbox and Kafka are deferred (FR-EVT-01 to 04 Missing). See Phase F.

## [Phase F] — 2026-11-04 — Database-backed notifications with read state

**Status:** Complete locally. Not pushed. Committed locally at the time of writing.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (new NOTIF-01 row, planned order items 6 and 7). Code: `backend/src/main/resources/db/migration/V15__notifications.sql`, `backend/src/main/java/com/campushub/notifications/` (new package), `backend/src/main/java/com/campushub/projects/MembershipService.java`, `backend/src/test/java/com/campushub/notifications/NotificationFlowIntegrationTest.java`, `frontend/src/features/notifications/`, `frontend/src/components/AppShell.tsx`, `frontend/src/app/router.tsx`, `frontend/src/api/types.ts`, `frontend/src/api/baseApi.ts`.

**Changed (code):**
- V15 adds the `notifications` table: recipient, actor, type (CHECK limited to four types), project, created and read times, and a version column. Foreign keys to users and projects use RESTRICT. Indexes cover the recipient's list and unread lists.
- `MembershipService` writes a notification in the same transaction as each change: invitation received, invitation accepted, invitation declined, and member removed. A rolled-back change also rolls back its notification.
- Endpoints: `GET /notifications` (optional `unreadOnly`, paged, newest first), `GET /notifications/unread-count`, `POST /notifications/{id}/read`, and `POST /notifications/read-all`. A notification that belongs to another user returns 404.
- Frontend: an inbox page at `/notifications` with an unread-only filter, mark as read, and mark all as read. The header shows an unread count that refreshes every 60 seconds.

**Verified:**
- `./mvnw -B verify`: 180 tests, 0 failures, BUILD SUCCESS (174 before Phase F plus 6 new tests).
- `NotificationFlowIntegrationTest` (6 tests): the invitation and both answers notify the right people; a failed invitation writes no notification; a removal notifies the member and ends their access at once; mark read is private to the recipient and idempotent; mark all read clears only the caller's unread rows; a faculty user with no team sees an empty inbox.
- Frontend: `npm run build` and `npm run lint` pass.

**Not verified:**
- Browser flow for the inbox and the header badge. Build and lint only.
- Polling behavior over time. The badge refreshes once a minute; this has not been observed in a running session.

**Deferred (documented, not built):**
- Outbox table and relay (FR-EVT-01, FR-EVT-02), Kafka producer and consumers (FR-EVT-02, FR-EVT-03), and retry and DLQ (FR-EVT-04). All remain Missing. Notifications are written directly to the database, not through an outbox.

**Known gaps and deviations:**
- Messages are rendered on read from the current project title and actor name. If a project is renamed, old notifications show the new title. Nothing is stored as text.
- No delete endpoint and no retention rule. Notifications accumulate.
- No notification for submission review, milestone changes, or task assignment. Only the four membership events above are covered.
- No push, email, or real-time update. The badge uses polling.

## [Phase E.4] — 2026-11-03 — Invitation lifecycle tests and stale-invitation fix

**Status:** Complete locally. Not pushed. Committed locally at the time of writing.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (FR-TEAM-01, FR-TEAM-02 rows, planned order item 4). Code: `backend/src/main/java/com/campushub/projects/MembershipService.java`, `ProjectInvitation.java`, `ProjectInvitationRepository.java`, `backend/src/test/java/com/campushub/projects/MembershipFlowIntegrationTest.java`.

**Changed (code):**
- `MembershipService.invite` now stores an expired PENDING invitation for the same invitee as EXPIRED before its duplicate check, under the project lock. Before this change, an invitation past its 14-day deadline still blocked a new one (409 `DUPLICATE`), because expiry was only derived on read and the stored row stayed PENDING.
- `ProjectInvitation.expire()` sets EXPIRED and leaves `respondedAt` empty. `ProjectInvitationRepository` gains `findByProjectIdAndInviteeIdAndStatus`.
- No schema change. No migration added.

**Verified:**
- Before the fix, the new test `expiredInvitationDoesNotBlockANewInvitation` failed with 409 where 201 was expected. That confirmed the bug.
- `./mvnw -B verify`: 174 tests, 0 failures, BUILD SUCCESS (168 before E.4 plus 6 new tests).
- New tests in `MembershipFlowIntegrationTest`:
  - An expired invitation cannot be accepted or declined (409 `INVALID_TRANSITION`), and the invitee's list shows EXPIRED.
  - An expired invitation does not block a new one, and the new one can be accepted.
  - An answered invitation cannot be answered again: accept twice, decline after accept, and accept after decline all return 409 `INVALID_TRANSITION`.
  - Inviting an existing member returns 409 `DUPLICATE`.
  - Five invitations with four accepted: the fifth accept returns 409 `TEAM_FULL`, and the invitation stays PENDING.
  - After a member is removed, the old ACCEPTED invitation cannot be accepted again, and a new invitation can be accepted.

**Not verified:**
- Browser flow for invitations. Covered by backend tests only.
- The hourly expiry job. It is not built, so no test covers it.

**Known gaps and deviations:**
- No revoke endpoint for a PENDING invitation (STATE_MACHINES §6). An owner waits for the 14-day expiry or asks an administrator. Recorded in RISKS.
- Expiry is lazy. It is applied on read, and a stale row is stored as EXPIRED on the next invite to the same person. The hourly job in DATA_MODEL §5.3 is not built, so rows that are never re-invited stay PENDING in storage, although reads report them as EXPIRED.
- Pending invitations do not reserve seats. The owner can have more pending invitations than free seats. The cap is enforced when each one is accepted, under the project lock, so the surplus accept fails with 409 `TEAM_FULL`.
- Invitee-must-be-ACTIVE-student check is still UNVERIFIED for the browser path.

## [Phase E.3] — 2026-11-03 — Submission evidence files

**Status:** Complete locally. Not pushed. Committed locally at the time of writing.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/API_CONTRACT.md` (§10 MVP subset paragraph), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (FR-SUB-02, FR-SUB-03, FR-SUB-04, FR-CERT-02 rows, planned order). Code: `backend/src/main/resources/db/migration/V14__submission_files.sql`, `backend/src/main/java/com/campushub/infrastructure/storage/FileStorage.java`, `backend/src/main/java/com/campushub/submissions/` (`SubmissionFile*`, `EvidenceType`), `backend/src/main/java/com/campushub/shared/errors/` (four new codes), `frontend/src/features/submissions/EvidenceFiles.tsx`.

**Changed (code):**
- Flyway V14 creates `submission_files`: binary UUID id, `storage_key` (unique, never sent to the client), foreign keys to `submissions` and `users` with `RESTRICT`, a check on `size_bytes` (at least 1) and on `content_type` (PDF, PNG or JPEG), and an index on submission and upload time. No existing row is changed.
- `FileStorage` writes each file under a random UUID key below a configured root, computes SHA-256 while streaming, and refuses any key that is not a UUID or does not resolve inside the root. Delete is best effort.
- `POST /submissions/{id}/files` (multipart `file`, 201). Students who can write team work (owner or accepted member) can upload. Faculty get 403 `FORBIDDEN_ROLE`, and a stranger or an invisible project gets 404. Upload is refused with 409 `VERSION_SEALED` once review starts, and with 422 `PROJECT_ARCHIVED` on an archived project. The check order is: lock the submission row, visibility, role, team membership, archived, sealed, empty file, size, count, then content. The type is checked against the file's first bytes (PDF `%PDF-`, PNG and JPEG signatures), and the extension must match. Size is limited to 20 MiB and five files per revision by the service.
- `GET /submissions/{id}/files` lists files ordered by upload time, for anyone who can see the project. Admins get 404.
- `GET /submissions/{id}/files/{fileId}/content` streams the file as an attachment with `nosniff` and an audited `download` action. Admins and strangers get 404. A file ID from another submission gets 404.
- Error codes added: `VERSION_SEALED` (409), `FILE_TOO_LARGE` (413), `FILE_TYPE_NOT_ALLOWED` (422), and `FILE_LIMIT_EXCEEDED` (422). An upload above the framework ceiling is also mapped to `FILE_TOO_LARGE`.
- `spring.servlet.multipart` is set to 25 MB per file and 26 MB per request. The policy limit of 20 MiB is enforced in the service.
- Frontend: each submission card shows an "Evidence files" section. Students get upload on the latest revision while it is SUBMITTED and the project is active. Other project members can view and download. Admins see no evidence section. Download uses an authenticated request, then an object URL.

**Verified:**
- `./mvnw -B verify`: 168 tests, 0 failures, BUILD SUCCESS. `SubmissionFileFlowIntegrationTest` (10 tests) covers owner upload, list and download with SHA-256 and headers; PNG and JPEG detection; a disguised text file refused with 422; a 20 MiB + 1 byte file refused with 413; a sixth file refused with 422; sealed after review start (409); faculty upload 403; supervisor download 200; stranger and admin 404; a file ID from another submission 404; and an archived project 422. `EvidenceTypeTest` (6) and `SubmissionFileNameTest` (3) cover the signature rules and filename sanitizing.
- `npm run build` (`tsc -b` and `vite build`) and `npm run lint`: exit 0.
- Local stack: the API container was rebuilt, and V14 applied to the existing dev database.
- Built-in browser, as the student owner on a SUBMITTED revision: the upload control appeared only on the latest revision. A PNG was attached and listed with its size and time. Download returned 200 OK. A text file renamed `.png` was refused with the server's message and a reference ID. As the admin, the evidence section and upload control were absent.

**Not verified:**
- Faculty supervisor view in the browser. Covered by backend tests only.
- The framework 25 MB multipart ceiling. MockMvc bypasses it, so the 413 path is tested through the service, not the container.
- Audit rows. The download action is recorded in code but not asserted in tests.

**Known gaps and deviations:**
- Limits are per revision. There is no version entity, so the policy's "per version" becomes "per revision".
- Files attach only while the revision is SUBMITTED. There is no DRAFT, so a submission does not require a file (FR-SUB-04 stays partial).
- No DELETE endpoint for evidence files.
- A missing `file` part returns the framework 400 without an `errorCode`.
- Uploads are serialized by the submission row lock. A crash between the storage write and the database commit could leave an orphan file. A commit failure after the write deletes the stored file.
- Admins get 404 on all file routes (ADR-007), which hides the feature from them.
- No malware scanning.
- Certification evidence (FR-CERT-02) is not built. It shares `FileStorage` but has no endpoints.

## [Phase E.2] — 2026-11-03 — Milestones and calendar deadlines

**Status:** Complete locally. Not pushed. Not committed at the time of writing.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (FR-MS, FR-CAL-01, FR-PROJ-09, FR-SUB-05 rows, planned order). Code: `backend/src/main/resources/db/migration/V13__milestones.sql`, `backend/src/main/java/com/campushub/milestones/`, `backend/src/main/java/com/campushub/calendar/CalendarService.java`, `frontend/src/features/milestones/`.

**Changed (code):**
- Flyway V13 creates `milestones`: per-project `sequence` (unique with `project_id`, at least 1), `title` (3 to 200 characters), `due_at`, `allow_late` (default false), `status` (`OPEN` or `CLOSED`), and a version column for optimistic locking. No existing row is changed.
- `GET /projects/{id}/milestones` lists milestones for anyone who can see the project. `POST` creates one (owner only; sequence assigned under a project row lock). `PATCH /milestones/{id}` takes a version: the owner edits `title` and `dueAt`, a supervisor edits `allowLate`, and sending a field the caller may not change returns 400 `FIELD_NOT_EDITABLE`. `POST /milestones/{id}/close` closes a milestone (owner or supervisor); closing is permanent in this version.
- Hidden projects return 404 for every milestone route. Archived projects return 422 `PROJECT_ARCHIVED` for writes. Closed milestones return 409 `INVALID_TRANSITION` for edits and closes.
- `GET /calendar` returns `MILESTONE_DUE` entries on the UTC calendar day of `dueAt`, filtered by project visibility, alongside `TASK_DUE` and `PROJECT_DUE`.
- Frontend: the project page shows a Milestones panel. The owner can create and edit a milestone, a supervisor can toggle `allowLate`, and the owner or supervisor can close one behind a confirmation dialog. The calendar labels `MILESTONE_DUE` as "Milestone due".

**Verified:**
- `./mvnw -B verify`: 149 tests, 0 failures, BUILD SUCCESS. `MilestoneFlowIntegrationTest` (11 tests) covers sequencing, invalid input, stranger 404, member read-only, owner and supervisor field rules, stale version 409, close irreversibility, admin 403, other faculty 404, archived 422, and the calendar entry.
- `npm run build` (`tsc -b` and `vite build`) and `npm run lint`: exit 0.
- Local stack: V13 applied to the existing dev database (`Successfully applied 1 migration ... now at version v13`). Built-in browser, as the project owner: created "Literature review submitted" due 1 Dec 2026, 09:00; it showed "Late submissions not allowed". As the supervisor (faculty): ticked "Allow late submissions" and the text changed to "Late submissions allowed".

**Not verified:**
- Browser edit of title or due date, and browser close. Both are covered by backend tests only. Close was not clicked in the browser because it is irreversible.
- Calendar display of `MILESTONE_DUE` in the browser.

**Known gaps:**
- Submissions are not linked to milestones, so a closed milestone does not yet reject submissions and `allowLate` is not enforced (FR-SUB-05 remains missing).
- Tasks have no `milestoneId`, and there is no task filter by milestone or assignee.
- Milestone creation does not check that `dueAt` is in the future.
- `ProjectStatus` has only `ACTIVE` and `ARCHIVED`. The project COMPLETED state (FR-PROJ-06) is not built, so it does not depend on milestones yet.
- No progress metric on the project page (FR-PROJ-09 progress remains UNVERIFIED).

**Environment note:** the dev API container was rebuilt to apply V13. The demo milestone "Literature review submitted" was created in the dev database during the browser check. This is local seed data, not reset.

## [Phase E.1 and E.5] — 2026-11-03 — Task assignment and task pagination

**Status:** Complete locally. Not pushed. Not committed at the time of writing.

**Affected documents:** `docs/CHANGELOG.md` (this entry), `docs/MASTER_BLUEPRINT_COMPLIANCE.md` (FR-TASK-02 and FR-TEAM-03 rows, planned order). Code: `backend/src/main/resources/db/migration/V12__task_assignee.sql`.

**Changed (code):**
- Flyway V12 adds `tasks.assignee_id` (nullable foreign key to `users`, `ON DELETE RESTRICT`) and index `ix_tasks_project_assignee`. No existing row is changed; existing tasks stay unassigned.
- `POST /projects/{id}/tasks` and `PATCH /tasks/{id}` accept `assigneeId`. The assignee must be the project owner or an active team member, otherwise 422 `PRECONDITION_NOT_MET`. The PATCH is full-field, so `null` unassigns. `assigneeId` is returned in task responses.
- Removing a member clears that member's assignment on open tasks in the same transaction (`TaskRepository.unassignOpenTasks`). DONE tasks keep their assignee and their version. Each cleared task has its version incremented.
- Frontend: the task form has an assignee select, populated from the team list. The select stays disabled until the team list loads, and the current assignee is kept while it is not loaded, so a failed load cannot unassign a task. Task cards show the assignee's name or "Unassigned".
- Frontend: the task list fetches one page at a time (20 per page) with Previous and Next controls. The earlier 50-task cap and "hidden tasks" text are removed, so no task is hidden.

**Verified:**
- `./mvnw -B verify`: 138 tests, 0 failures, BUILD SUCCESS. Includes `assigneeMustBeTheOwnerOrAnActiveMemberAndNullUnassigns` and `removingAMemberClearsOnlyTheirOpenAssignments` in `TaskFlowIntegrationTest`.
- `npm run build` (`tsc -b` and `vite build`) and `npm run lint`: exit 0.
- Built-in browser, local stack rebuilt with V12 applied to the existing database (`Successfully applied 1 migration ... now at version v12`): as the task owner, the task was edited to assign Dev Student Two, saved, and the card then read "Assignee Dev Student Two".

**Not verified:**
- The pager in the browser. The demo project has one task, so the controls do not render. Pagination is not covered by a backend test in this change either.
- Assignment removal through the UI (the backend test covers the cascade on member removal).

**Known gaps:**
- No task-list filter by assignee or milestone.
- Deactivated users: no user deactivation endpoint exists (FR-PROF-04), so a deactivated user's assignment is not handled.

**Environment note:** the demo task "Write literature review" was assigned to Dev Student Two during the browser check. This is local seed data, not reset.

## [Phase A] — 2026-11-03 — Browser end-to-end verification and phone-width fix

**Status:** Complete locally. Committed on `phase-7a-certifications` (`199e1a4` header nav fix). Not pushed.

**Affected documents:** `docs/CHANGELOG.md` (this entry). Code: `frontend/src/index.css`.

**Changed (code):**
- The header navigation now wraps (`.app-header nav` has `flex-wrap: wrap`). At 375px the links pushed the page to 395px wide and caused horizontal page scroll. After the change the page is 375px wide on the calendar, projects, project detail, and dashboard pages.

**Verified in the built-in browser, local demo accounts, localhost only:**
- Student2 accepts a team invitation; the project's team section then lists Dev Student Two as a member ("2 of 5 people on this team").
- Student2 sees no "Send invitation" form on the project page, and the projects list shows only the one project they belong to.
- Student calendar: 20 Nov 2026 shows the "Write literature review" task, and 15 Dec 2026 shows the Demo Capstone Project due date. Both link to the project.
- Admin sees all three projects. The admin panel on the project page shows supervisor assignment and archive controls. Archive was not clicked.
- Faculty sees the two supervised projects. The dashboard shows 2 supervised projects, 2 awaiting review, and 2 needing attention. The submission panel shows the "Record decision" form. A decision was not submitted, so seed data is unchanged.

**Not verified:**
- Non-member access by direct URL. Only the list filter was checked. The list hides the student's "E2E smoke project" rows from student2, but no direct-URL request was made.
- Invitations page and team panel at 375px. Only overflow was measured on the calendar, projects, project detail, and dashboard pages.
- Submitting a faculty decision, and admin archive or supervisor reassignment.
- Backend tests were not rerun in Phase A because this phase changed no backend code. The last full run is the Phase B and C run: 136 tests, 0 failures.

**Environment note:** The `campushub-api-1` image was stale and returned 404 for the calendar route. Rebuilding it (`docker compose up -d --build api`) fixed this.

**Known gaps (carried forward):** stale "E2E smoke project" rows in the local database. Not deleted; deleting needs approval.

## [Phase B and C] — 2026-11-03 — Project membership, invitations, and academic calendar

**Status:** Complete locally. Committed on `phase-7a-certifications` (`9d4c6fd` membership backend, `c65a7c3` calendar backend, `b13091f` frontend). Not pushed. No edit to an applied migration.

**Affected documents:** `docs/API_CONTRACT.md` §6.1 (members and invitations, with deviations) and §9 (calendar, with deviations); `docs/DATA_MODEL.md` (header, §5.2, §5.3, §11 V11 row); `docs/CHANGELOG.md` (this entry).

**Changed (code):**
- Flyway V11 adds `project_members` (MEMBER rows only) and `project_invitations`. `pending_key` is a generated column with a unique index, so only one PENDING invitation exists per project and invitee.
- Membership endpoints: list members, remove member (owner only), invite by email (owner only), list invitations (owner only), `GET /invitations/mine`, accept and decline (invitee only; other callers get 404). Team cap of five counts the owner. Removal revokes access at once.
- `GET /calendar?from=&to=` returns project and task due dates visible to the caller. Range ≤ 92 days.
- Frontend: team panel on the project page (member list, invite form, invitation list), invitations page for students with accept and decline, calendar page with a monthly grid, previous, next, and today navigation, and selected-day deadline details.

**Verified:**
- `./mvnw -B verify`: 136 tests, 0 failures, BUILD SUCCESS. Includes 7 membership tests and 4 calendar tests.
- `npm run build` (`tsc -b` and `vite build`) and `npm run lint`: exit 0.
- Built-in browser, logged-out session: `/calendar` redirects to `/login`. The one console error is the expected 401 from the unauthenticated session check.

**Not verified:** the team, invitation, and calendar screens in a browser with real data (Phase A sign-in is blocked until the demo password is supplied).

**Known gaps:**
- Tasks have no assignee in the MVP (V5 documents this), so removing a member has no task side effects. The earlier contract wording about unassigning tasks was wrong for this build and is corrected.
- Invitation expiry is derived on read; the scheduled expiry job is not built.
- The calendar has no milestone deadlines (milestones are not in the schema).

## [Phase 9.1 and 10] — 2026-11-03 — Certification editing and dashboards

**Status:** Complete locally. Not committed or pushed. No database reset, no edit to an applied migration.

**Affected documents:** `docs/API_CONTRACT.md` §11 (certification rows, PUT endpoint, 400 decision for non-https links, MVP subset notes) and §12 (implemented dashboard endpoints), `docs/DATA_MODEL.md` (certifications columns, V10 row, outbox row corrected to "not migrated"), `docs/DEMO_SCRIPT.md` (§3.9 expiry, credential ID, edit; §3.10 dashboards; §4 verification), `docs/CHANGELOG.md` (this entry).

**Changed (code):**
- Flyway V10 adds `expires_on`, `credential_id`, and `version` to `certifications`. Expiry cannot precede the issue date; credential ID is 1–100 characters. V9 is unchanged.
- `PUT /users/me/certifications/{id}` (STUDENT, optimistic lock). A stale `version` returns 409 `STALE_VERSION`; another student's row returns 404; other roles get 403. Audited as `certification.updated`.
- Non-https `credentialUrl` returns 400 `VALIDATION_FAILED`, not the 422 the earlier contract listed. The contract is updated to match.
- `GET /users/me/dashboard/student` and `GET /users/me/dashboard/faculty`. Metrics come from aggregate queries over MySQL. Progress is DONE over all tasks, or `null` when a student has no tasks, so an empty project never reads as complete. Faculty sees only projects they supervise. Any other role gets 403.
- Frontend: certification add and inline edit forms; a Dashboard page at `/dashboard` for students and faculty, with a nav link for those roles. `/` still redirects to `/projects`.

**Verified:**
- `./mvnw -B verify`: 125 tests, 0 failures, BUILD SUCCESS. Includes 12 certification tests and 5 dashboard tests.
- `npm run build` and `npx oxlint`: exit 0.
- Built-in browser, local stack: certification create with expiry and credential ID; edit persists after a reload; student dashboard shows the certification count from the database; faculty dashboard shows only supervised projects.

**Not verified:** the student dashboard with real tasks in the browser (covered by backend tests); the admin dashboard message in the browser; the certification stale-version and date refusals in the browser (covered by backend tests).

**Known limit:** evidence file upload and download are still not built.

## [Phase 7a certifications] — 2026-11-03 — Owner-private certification records

**Status:** Complete locally. Not committed or pushed. Evidence files are not included.

**Affected documents:** `docs/DEMO_SCRIPT.md` (§3.9 certifications walkthrough), `docs/DATA_MODEL.md` (migration table: V8 and V9 rows), `docs/CHANGELOG.md` (this entry). `docs/API_CONTRACT.md` §11 and `docs/AUTHORIZATION_MATRIX.md` §3.9 already described this scope and were left unchanged, except for the evidence rows noted below.

**Changed (code):**
- Flyway V9 creates `certifications` (owner, title, issuer, issued date, optional credential URL, created time). The URL must start with `https://` (database CHECK). Index `ix_certs_owner`.
- `GET /users/me/certifications` (paginated, newest issue date first), `POST /users/me/certifications` (201), `DELETE /users/me/certifications/{id}` (204, audited). STUDENT only; other roles get 403 `FORBIDDEN_ROLE`. A row owned by another student reads as 404 `NOT_FOUND`.
- The server stores the credential URL as text and never fetches it (SECURITY.md, SSRF).
- Frontend `/certifications` page for students: add form with client-side checks, a paginated list, and a confirmation before delete. A "Certifications" nav link shows for students only.

**Deferred:** `certification_files` and the evidence upload and download endpoints (`/users/me/certifications/{id}/file`). The MVP has no file storage path yet, so they wait for the storage layer.

**Verified:**
- `./mvnw -B verify`: 116 tests, 0 failures (BUILD SUCCESS). This includes 8 tests in `CertificationFlowIntegrationTest`.
- `npm run build`, `npx tsc -b`, and `npx oxlint` (exit 0).
- Built-in browser: student adds a certification with a `http://` link (refused by the page), then with an `https://` link (saved and listed), then deletes it through the confirmation dialog (list returns to empty).

**Not verified:** the faculty and administrator refusals in the browser (backend test covers them); the audit rows written for create and delete (not asserted by a test yet).

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
