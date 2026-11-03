# CampusHub — UX and UI

**Purpose:** Defines navigation, the screen inventory per role, the state every server-driven screen must handle, form and error behavior, accessibility, and responsive rules. Frontend implementation in Phase 1+ follows this file.
**Scope:** Web SPA for desktop and mobile browsers (PRODUCT A-5). No native app, no offline mode.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [REQUIREMENTS.md](REQUIREMENTS.md) (NFR-A11Y-01), [USER_STORIES.md](USER_STORIES.md), [API_CONTRACT.md](API_CONTRACT.md) (error codes), [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md), [ARCHITECTURE.md](ARCHITECTURE.md) §9 (frontend state).

## 1. Principles

1. **The server decides, the UI explains.** Route guards and hidden controls are convenience. Every restricted action is still enforced by the API (ARCHITECTURE §1). When the API refuses, the UI shows the reason from the error `code`, not a generic failure.
2. **Show what can be done, and why not.** A disabled control states its reason in text next to it. Hidden controls are used only where the action does not apply to the role at all (for example, faculty never see "Create project").
3. **Plain language over system terms.** Labels use the words in PRODUCT (proposal, supervisor, milestone). Status names are shown in sentence case with a short description, not raw enum values.
4. **No surprise writes.** Irreversible actions require a confirmation that names the consequence (§7).
5. **Time is shown in the user's time zone and says so** (ADR-013, §8).
6. **Accessible by default.** WCAG 2.1 AA for every implemented screen (NFR-A11Y-01, §10).

## 2. Users and their primary goals

| Role | Primary goals | Where they start |
|---|---|---|
| Student | Propose a project, invite teammates, plan tasks, submit milestone work, see feedback and deadlines | My work: their projects, tasks due this week, open invitations |
| Faculty | Review submissions of supervised projects, see who needs attention, track milestones | Pending reviews and at-risk milestones |
| Administrator | Approve or reject proposals, assign supervisors, manage accounts and the catalog, check audit history | Proposals awaiting decision, account requests, term summaries |

A user has exactly one role (ADR-021). Navigation is therefore fixed per role.

## 3. Information architecture and navigation

### 3.1 Top-level navigation

Left navigation on desktop; a bottom bar with a "More" sheet on phones (§11).

| Item | Student | Faculty | Administrator |
|---|---|---|---|
| Dashboard | ✔ | ✔ | ✔ |
| Projects | ✔ (my projects) | ✔ (supervised) | ✔ (all, filtered) |
| Tasks | ✔ (across my projects) | — | — |
| Calendar | ✔ | ✔ | ✔ |
| Reviews | — | ✔ (pending) | — |
| Invitations | ✔ (with count badge) | — | — |
| Skills and certifications (P1) | ✔ | — | — |
| Accounts | — | — | ✔ |
| Catalog | — | — | ✔ |
| Audit | — | — | ✔ |
| Account menu (profile, change password, sign out) | ✔ | ✔ | ✔ |

Items that a role cannot use are absent from navigation. Direct URLs to them show the forbidden state (§4.5), and the API returns the matching 403 or 404.

### 3.2 Route map

| Route | Screen | Roles | Phase |
|---|---|---|---|
| `/login` | Sign in | Public | 1 |
| `/change-password` | Forced or voluntary password change | Any signed-in user | 1 |
| `/` | Dashboard (role-specific) | All | 1 (student), 7 (faculty and admin dashboards) |
| `/projects` | Project list | All | 2 |
| `/projects/new` | New project (DRAFT) | Student | 2 |
| `/projects/:id` | Project overview | Member, supervisor, admin | 2 |
| `/projects/:id/edit` | Edit project details | Owner | 2 |
| `/projects/:id/team` | Members and invitations | Member, supervisor, admin (invitations: owner, admin) | 3 |
| `/projects/:id/milestones` | Milestones | Member, supervisor, admin | 5 |
| `/projects/:id/tasks` | Task board and list | Member, supervisor, admin | 5 |
| `/tasks/:id` | Task detail | Member, supervisor, admin | 5 |
| `/calendar` | Calendar | All | 5 |
| `/milestones/:id/submission` | Submission workspace | Member, supervisor, admin | 6 |
| `/submissions/:id/versions/:n` | Version detail and review | Member, supervisor; admin (metadata only) | 6 |
| `/reviews` | Pending reviews | Faculty | 6 |
| `/invitations` | My invitations | Student | 3 |
| `/skills`, `/certifications` | Profile skills and certifications | Student | 7 (P1) |
| `/admin/proposals` | Proposal queue | Admin | 4 |
| `/admin/users` | Accounts | Admin | 2 |
| `/admin/catalog` | Departments, terms, courses | Admin | 2 |
| `/admin/audit` | Audit events | Admin | 2 |
| `/help/metrics` | Metric definitions (FR-DASH-04) | All | 7 |
| `/403`, `/404` | Forbidden and not-found pages | All | 1 |

Route-level code splitting (ARCHITECTURE §9) loads admin and dashboard routes lazily.

## 4. Screen states

Every screen that loads data from the server implements these five states. ARCHITECTURE §9 requires them; this section defines them. Each state is designed, not left as a blank panel.

### 4.1 Loading

- Skeleton placeholders with the final layout, not a spinner in an empty page. Skeletons are hidden from assistive technology (`aria-hidden="true"`) and the region announces "Loading" once through a polite live region.
- Loading appears after 150 ms, so fast responses do not flash.
- Buttons that trigger a write show a progress state and are disabled while the request is in flight. Repeat clicks cannot submit twice.

### 4.2 Empty

Each list has its own empty message with one next action. Examples:

| Screen | Empty message | Action |
|---|---|---|
| Projects (student) | "You are not on a project yet." | Create a project, or check invitations |
| Projects (faculty) | "No projects are assigned to you as supervisor yet." | None; the administrator assigns |
| Tasks | "No tasks yet. Add the first task for this project." | Add task (members only) |
| Pending reviews | "Nothing is waiting for review." | None |
| Invitations | "No invitations right now." | None |

### 4.3 Error

- The page shows the message for the error `code` from API_CONTRACT §3 (mapping in §9) with a **Try again** button that repeats the request.
- Validation errors appear next to the field (§6), not only at the top of the page.
- A 5xx or network error shows the request ID when the API returns one, so a user can report it. The request ID is never a secret and is shown as plain text.
- Errors do not clear the user's input.

### 4.4 Success

- Confirmation is shown as a non-blocking status message that is announced to assistive technology. The message names what happened ("Proposal submitted to the administrator").
- Destructive successes (for example, a removed member) stay visible until dismissed or until the next navigation, so the user can see the result.

### 4.5 Forbidden and not found

- **Forbidden (403)** on a route the user can reach: "You do not have permission to do this." followed by the reason where it is safe to say it (for example, "Only the project owner can submit the proposal."). The reason comes from the server text, not from the UI's guess.
- **Not found (404)** for a private object the user cannot see: "We could not find that page, or you do not have access to it." This is the same page for a nonexistent ID and for an unauthorized one, matching ADR-006. The UI must not explain which it was.
- Forbidden and not-found pages offer a link back to the user's dashboard.

## 5. Screen inventory and key behaviors

### 5.1 Student

| Screen | Content | Primary actions | Notes |
|---|---|---|---|
| Dashboard | Tasks due in 7 days, overdue tasks, upcoming milestones, completion rate, latest 5 reviews, skills count (P1) | Open a task; open a submission | Each figure has a help icon linking to `/help/metrics` (FR-DASH-04) |
| New project | Title, description, term, optional course | Save as draft; submit proposal (requires a milestone) | Term picker shows only ACTIVE terms |
| Project overview | Status, supervisor, members, progress, milestone summary | Edit (owner, DRAFT or REJECTED); submit proposal; activate; invite | Status banner explains the next step in one sentence |
| Invitations | Pending invitations with project title, owner name, expiry date | Accept; decline | Accept is refused with a reason when the team is full (409 `TEAM_FULL`) |
| Milestones | Ordered list with due date and status (Open, Closed) | Owner: create, edit. Supervisor: allow late. Owner or supervisor: close | Closing asks for confirmation (§7) |
| Task board | Columns for To do, In progress, In review, Blocked, Done | Change status from the card's status control; filter by assignee and milestone | A status control is a menu, not drag-only (§10.3) |
| Task detail | Fields, status history (P1), comments (P1) | Edit fields; change status | Changing to Blocked asks for a reason (FR-TASK-04) |
| Calendar | Month or week view of milestones and tasks | Open the item | Range limited to 92 days; the picker explains the limit |
| Submission workspace | Current version, files (up to 5), limit and type notes, deadline | Upload; remove (draft only); submit; start new version after changes requested | Submit is irreversible and says so (§7) |
| Version detail | Files (download), review decision and feedback | Download (members and supervisor) | Admin view shows metadata only and says why there is no download |
| Skills and certifications (P1) | Own skills with proficiency; certifications with evidence | Replace skills; add or remove certification | Visible only to the student (OD-5) |

### 5.2 Faculty

| Screen | Content | Primary actions | Notes |
|---|---|---|---|
| Dashboard | Pending reviews, supervised projects by status, at-risk milestones, submissions by status | Open a review | Help icons as for students |
| Reviews | Versions in SUBMITTED or UNDER_REVIEW across supervised projects, oldest first | Start review; record decision | Feedback is required for changes requested (422 `FEEDBACK_REQUIRED`) |
| Project (supervised) | Milestones, tasks summary, submissions, members | Allow late on a milestone; mark project complete when all milestones are closed | Faculty cannot edit project details or invite |

### 5.3 Administrator

| Screen | Content | Primary actions | Notes |
|---|---|---|---|
| Dashboard | Active users by role, projects by status, per-term summaries | Open queue items | Counts exclude deactivated users (FR-DASH-03) |
| Proposal queue | PROPOSED projects with owner, term, milestones, supervisor | Approve; reject with reason; assign supervisor | Approve is disabled with the reason "Assign a supervisor first" |
| Accounts | Search by email or name; role and status filters | Create account (shows temporary password once); deactivate; reactivate; reset password | Deactivate and reset password confirm (§7); last admin is protected with a message |
| Catalog | Departments, terms, courses with active flags | Create; edit; delete if unreferenced | Delete of a referenced row explains it is in use (409 `CATALOG_ITEM_IN_USE`) |
| Audit | Filterable list of audit events | Filter by actor, action, target, dates | Metadata displayed as allow-listed keys only |

### 5.4 Shared

| Screen | Notes |
|---|---|
| Sign in | Generic failure message for all sign-in failures, including locked accounts (§9). Rate-limit message shows the retry time from `Retry-After` |
| Change password | Required while `mustChangePassword`; shows the password rules (12–128 characters, not a common password, not the last password) before submitting |
| Help: metrics | One section per metric with name, definition, denominator, and data source, taken from API_CONTRACT §12 |

## 6. Forms and validation

- Forms use React Hook Form with Zod schemas. The Zod schema mirrors the API limits (for example, project title 3–200 characters, description ≤ 5000; FR-PROJ-01). The schema is the first check; the server is still authoritative.
- Field errors come from the API's `errors[]` array and are attached to the named field. Errors that name no field appear in a summary at the top of the form, with links to the fields.
- Character counts appear on fields with a limit at 80% of the maximum and always on the description and feedback fields.
- Required fields are marked in text ("required") as well as with an asterisk. The asterisk alone is not used.
- **Unsaved changes.** Leaving a dirty form asks for confirmation. Drafts of proposals are kept in `sessionStorage`, keyed by user ID, and cleared on sign-out (ARCHITECTURE §9). Drafts are a convenience only: nothing is saved to the server until the user saves.
- **Optimistic locking.** Forms for projects, milestones, and tasks send `version`. On `STALE_VERSION` the form shows: "This item was changed by someone else. Reload to see the latest version. Your changes are kept below." The user can copy them before reloading. The form never overwrites silently.
- Passwords are never displayed after entry. Temporary passwords are shown once in a dialog with a copy control and a warning that they cannot be shown again.

## 7. Destructive and irreversible actions

Each action below requires a confirmation dialog. The dialog names the item, states the consequence in one sentence, and puts the safer choice first. The confirm button names the action ("Archive project"), not "OK".

| Action | Consequence stated in the dialog |
|---|---|
| Reject proposal | "The owner can revise and resubmit. Enter the reason the owner will see." (reason required) |
| Archive project | "The project becomes read-only for everyone. This cannot be undone in MVP." |
| Close milestone | "No new uploads or submissions can be made to this milestone. This cannot be undone in MVP." |
| Submit submission version | "Files in this version are sealed and can no longer change. You can start a new version only after changes are requested." |
| Remove member | "Their open tasks become unassigned. They lose access to the project." |
| Deactivate account | "They cannot sign in and existing sessions end immediately." |
| Reset password | "A new temporary password is shown once. Their current sessions end." |
| Delete catalog row | "This cannot be undone." Shown only when the row is unreferenced; otherwise the delete button is replaced by the in-use message. |
| Reopen a done task | Not confirmed; shown as a status change with an undo toast for 10 seconds |

Buttons for these actions are never the default focused button in the dialog. Pressing Enter does not confirm a destructive action.

## 8. Dates, times, and time zones (ADR-013)

- Times are stored and transferred in UTC and rendered in the user's browser time zone. The zone abbreviation appears on due dates ("Fri 14 Nov, 17:00 GMT+3") so that a faculty member in another zone reads the same instant correctly.
- The institution default time zone applies only when the browser does not supply one (`CAMPUSHUB_INSTITUTION_TIMEZONE`, default UTC).
- Date-only fields (`startsOn`, `endsOn`, `issuedOn`) do not carry a time zone and are shown as dates.
- Calendar: the range picker allows at most 92 days and explains the limit. Items cross daylight-saving boundaries without shifting their stated instant (FR-CAL-02).
- "Due in" phrases ("due in 2 days") are shown only with the absolute date beside them, so no one has to guess the zone.

## 9. Error messages

The UI maps API error codes to messages. The table lists the messages for codes that users meet most. The server's `detail` is used only when it is safe text and the code has no mapping here.

| Code | Message |
|---|---|
| `VALIDATION_FAILED` | Field-level message from `errors[]`; summary: "Check the highlighted fields." |
| `UNAUTHENTICATED` | "Your session has ended. Sign in again to continue." Return to the page after sign-in. |
| `INVALID_CREDENTIALS` (sign in) | "Email or password is incorrect." Same text for locked accounts and wrong passwords. |
| `INVALID_CREDENTIALS` (change password) | "Your current password is incorrect." |
| `PASSWORD_CHANGE_REQUIRED` | Redirect to change password: "Choose a new password before you continue." |
| `RATE_LIMITED` | "Too many attempts. Try again in {n} minutes." from `Retry-After`. |
| `FORBIDDEN_ROLE` | "Your account does not have access to this." |
| `FORBIDDEN_STATE` | Server reason if provided; fallback "This action is not available to you for this item." |
| `NOT_FOUND` | Not-found page (§4.5). |
| `STALE_VERSION` | "This item was changed by someone else. Reload to see the latest version." |
| `DUPLICATE` | "This already exists." Field name from the response when available. |
| `INVALID_TRANSITION` | "That status change is not allowed right now. The current status is {status}." |
| `TEAM_FULL` | "This team already has 5 members." |
| `PROJECT_NOT_EDITABLE` | "This project cannot be edited in its current status." |
| `PROJECT_ARCHIVED` | "This project is archived and read-only." |
| `PROJECT_NOT_OPEN_FOR_MEMBERSHIP` | "This project is not accepting new members." |
| `PROPOSAL_INCOMPLETE` | "Before you submit, add: {missing}." |
| `PRECONDITION_NOT_MET` | "Before you continue, {missing}." |
| `SUPERVISOR_NOT_ELIGIBLE` | "Choose an active faculty member as supervisor." |
| `ASSIGNEE_NOT_MEMBER` | "Only current team members can be assigned this task." |
| `MILESTONE_CLOSED` | "This milestone is closed. Uploads and submissions are no longer accepted." |
| `DEADLINE_PASSED` | "The deadline for this milestone has passed." |
| `NO_FILES` | "Add at least one file before you submit." |
| `FILE_LIMIT_EXCEEDED` | "A version can hold up to 5 files. Remove one to add another." |
| `FILE_TYPE_NOT_ALLOWED` | "This file type is not accepted. Upload PDF, PNG, JPEG, or DOCX." |
| `FILE_TOO_LARGE` | "Files can be up to 20 MB." |
| `VERSION_SEALED` | "This version is sealed and cannot change." |
| `SUBMISSION_EXISTS` | "A submission already exists for this milestone." |
| `REVIEW_EXISTS` | "This version has already been reviewed." |
| `FEEDBACK_REQUIRED` | "Add feedback that explains the changes requested." |
| `CATALOG_ITEM_IN_USE` | "This item is used by existing records and cannot be deleted. Deactivate it instead." |
| `RANGE_TOO_LARGE` | "Choose a range of 92 days or fewer." |
| `INVALID_SORT` | Not shown to users; the table falls back to its default sort. |
| `DEPENDENCY_UNAVAILABLE` / `INTERNAL_ERROR` | "Something went wrong on our side. Try again. If it keeps happening, quote request ID {requestId}." |

Writing rules: say what happened and what to do next; never blame the user; do not expose internal names, table names, or stack traces.

## 10. Accessibility (NFR-A11Y-01)

### 10.1 Standard

WCAG 2.1 AA for every implemented screen. Checked by axe-core in E2E tests on each primary screen, and by a keyboard-only test per primary screen (TEST_STRATEGY §5).

### 10.2 Rules

- Semantic HTML first: headings in order, landmarks (`header`, `nav`, `main`, `footer`), lists for lists, buttons for actions, links for navigation.
- Color contrast: 4.5:1 for body text, 3:1 for large text and UI components. Status is never conveyed by color alone; each status has a text label and an icon with an accessible name.
- Focus: visible focus ring on every interactive element; focus moves to the page heading on route change and to the first error on a failed submit; dialogs trap focus and return it on close.
- Keyboard: every action reachable by keyboard; no keyboard traps; skip link to main content.
- Forms: each input has a visible label; errors are linked with `aria-describedby`; required state uses `aria-required`.
- Live regions: success and background status use `role="status"`; errors that block the user use `role="alert"` once.
- Target size: 44 × 44 CSS pixels minimum for touch targets on mobile.
- Motion: no auto-playing animation; respect `prefers-reduced-motion`.
- Language: `lang` set on `html`; dates are written out in full in accessible names.

### 10.3 Task board

The board is usable without drag and drop. Each card has a status menu, and column changes are announced. Drag-and-drop, when present, is an enhancement with the same effect as the menu.

## 11. Responsive layout

| Breakpoint | Width | Layout |
|---|---|---|
| Phone | 320–767 px | Single column; bottom navigation with "More"; tables become stacked cards |
| Tablet | 768–1023 px | Collapsible side navigation; two-column forms where they fit |
| Desktop | 1024 px and up | Persistent side navigation; tables with sortable headers |

- No horizontal page scroll at 320 px. Wide tables scroll inside their own container with a visible scrollbar and a sticky first column.
- The task board on phones becomes a status-grouped list. Columns do not scroll sideways.
- Text is not fixed-size; layouts reflow up to 200% zoom.

## 12. Session and authentication UX

- Access tokens last 15 minutes (ADR-003). The app does not silently refresh (refresh tokens are P1, ADR-004). On the first `UNAUTHENTICATED` response the app clears the session and shows the sign-in page with the message "Your session has ended." It returns the user to the page they were on after signing in. Unsaved input is kept in the draft store where it applies (§6).
- Sign-out revokes the token on the server and clears `sessionStorage` drafts for that user.
- The access token lives in memory only (ARCHITECTURE §9); reloading the page requires signing in again. This is an accepted cost of the token design (SECURITY §3).
- A forced password change (`mustChangePassword`) sends every route to `/change-password` until it is complete.

## 13. Performance expectations seen by the user

- Lists are paginated with a default page size of 20 (maximum 100). Infinite scrolling is not used, so the position and the count are always clear.
- Search fields wait 300 ms after the last keystroke before querying and show a result count.
- The first screen after sign-in should be usable within the p95 target in SM-6; the dashboard shows its skeleton, not a blank page, while its aggregates load.

## 14. Copy and tone

- Sentence case for headings and buttons. Button text is a verb phrase: "Submit proposal", "Accept invitation", "Upload file".
- Use "you" for the signed-in user. Use the names of people and roles, not "the user".
- Dates: "14 Nov 2026, 17:00" in the user's zone; relative phrases only with the absolute date.
- Avoid jargon. "Supervisor" is used everywhere; "advisor" and "mentor" are not used.

## 15. Verification

- Each screen in §5 has an E2E test for its primary action and for each of the five states in §4 that the screen can reach.
- Axe and keyboard checks run on every primary screen in CI (CI_CD §3).
- Error copy in §9 is compared with the codes in API_CONTRACT §3 by a test: every code has a message or an explicit "not shown to users" entry.
- Usability walkthroughs against GP-1, GP-2, and GP-3 in USER_STORIES are part of the Phase 9 gate (PHASE_PLAN).
