# CampusHub — User Stories and Journeys

**Purpose:** Role-specific stories, golden paths, and negative security journeys that drive acceptance tests.
**Scope:** P0 stories in full; P1 stories marked.
**Last reviewed:** 2026-11-03 (Phase 0)

Story IDs are referenced by [REQUIREMENTS.md](REQUIREMENTS.md), [DEMO_SCRIPT.md](DEMO_SCRIPT.md), and the test suites. Format: *As a [role], I want [capability], so that [outcome]*, followed by acceptance criteria.

## 1. Student stories

**US-STU-01 Sign in.** As a student, I want to sign in with my email or institutional ID, so that I can reach my workspace.
- Valid credentials → dashboard for the student role.
- Invalid credentials → one generic message; no account-existence hint.
- If my password is temporary → forced change screen before anything else.

**US-STU-02 Propose a project.** As a student, I want to draft a project proposal and save it, so that I can finish it later.
- Draft survives a page reload in the same browser session (sessionStorage per user, cleared on logout).
- Submit is blocked until title, description, term, and one milestone exist; each missing field is named.

**US-STU-03 Invite teammates.** As a project owner, I want to invite eligible students, so that the team is formed under policy.
- Invitation shows as pending for me and for the invitee.
- Invitee accepts or declines; a declined invitation cannot be accepted later.
- Invitation expires after 14 days.

**US-STU-04 Manage tasks.** As a team member, I want to create, assign, and move tasks, so that responsibilities are visible.
- Tasks show in a board and a list with the same data.
- Moving to BLOCKED asks for a reason.
- A version conflict shows a message and reloads the latest task.

**US-STU-05 Track deadlines.** As a student, I want a calendar of my milestones and assigned tasks, so that I can plan.
- Dates appear in my local time zone; the stored value is UTC.

**US-STU-06 Submit work.** As a team member, I want to upload files for a milestone and submit them, so that my supervisor can review them.
- Upload rejects oversized and wrong-type files with a specific message.
- After submit, files are read-only; the version shows as sealed.

**US-STU-07 Read feedback.** As a student, I want to see the supervisor's decision and feedback, so that I know what to change.
- Decision and feedback appear on the submission and on the dashboard's latest-reviews list.

**US-STU-08 Resubmit.** As a team member, I want to create a new version after requested changes, so that the earlier version stays on record.
- Old versions remain downloadable by members and the supervisor.

**US-STU-09 Maintain skills (P1).** As a student, I want to record my skills and proficiency with evidence, so that my progress is visible to me.
- Only my own skills; no peer or faculty visibility in MVP (OD-5).

**US-STU-10 Keep certifications (P1).** As a student, I want to store my certifications with an optional file, so that I have one record.
- Owner-only; file validation identical to submissions.

## 2. Faculty stories

**US-FAC-01 See supervised projects.** As a faculty member, I want a list of projects I supervise, so that I focus on my assignments.
- Only supervised projects are listed; others are not discoverable by search or ID.

**US-FAC-02 Review a submission.** As a supervisor, I want to open a submitted version, review its files, and record a decision with feedback, so that the student gets clear direction.
- Decision requires feedback when changes are requested.
- Only one decision per version.

**US-FAC-03 Monitor milestones.** As a supervisor, I want to see at-risk milestones and pending reviews, so that I act early.
- At-risk definition is shown in the UI, not implied.

**US-FAC-04 Control deadlines.** As a supervisor, I want to allow late submission for a specific milestone, so that exceptions are explicit.
- The change is audited.

**US-FAC-05 Complete a project.** As a supervisor, I want to mark an active project complete once its milestones are closed, so that the record is final.

## 3. Administrator stories

**US-ADM-01 Manage accounts.** As an administrator, I want to create, deactivate, and reset accounts, so that access matches enrollment.
- Deactivation takes effect on the next request.
- A temporary password is shown once at creation and never stored in plain text.

**US-ADM-02 Manage the academic catalog.** As an administrator, I want to manage departments, terms, and courses, so that projects have valid context.
- Referenced items are deactivated, not deleted.

**US-ADM-03 Approve proposals and assign supervisors.** As an administrator, I want to approve or reject proposals and assign a supervisor, so that every project has accountable oversight.

**US-ADM-04 Review the audit log.** As an administrator, I want to filter the audit log by actor, action, or target, so that I can investigate.
- The log shows sanitized metadata only.

## 4. Golden paths

**GP-1 Student golden path.** Sign in → dashboard → create proposal draft → submit with milestone → administrator approves and assigns supervisor → owner activates → owner invites teammate → teammate accepts → owner creates milestone → assignee creates and completes task → member creates submission → uploads a file → submits → supervisor starts review → supervisor requests changes → member creates version 2 → uploads → submits → supervisor accepts → student sees accepted status and feedback.

**GP-2 Faculty golden path.** Sign in → supervised project list → open project → inspect milestone and submission → download a file → start review → record decision → student sees feedback → supervisor marks project complete when milestones are closed.

**GP-3 Administrator golden path.** Sign in → create term and course → create a faculty account → create a student account (temporary password, forced change) → approve a proposal → assign supervisor → review audit log → deactivate an account → confirm that account can no longer sign in and that its historical submissions remain.

## 5. Negative security journeys

Each must produce the status listed in [API_CONTRACT.md](API_CONTRACT.md) and an audit entry where applicable.

| ID | Journey | Expected result |
|---|---|---|
| NS-01 | Student A reads Student B's private certification. | 404 (certifications are owner-only). |
| NS-02 | Non-member requests a private project by ID. | 404 (concealment). |
| NS-03 | Student tries to set their own role to FACULTY or ADMIN. | 400 `FIELD_NOT_EDITABLE`; no change. |
| NS-04 | Student tries to approve their own proposal. | 403 (role lacks permission). |
| NS-05 | Faculty reviews a project they do not supervise. | 404. |
| NS-06 | Member uploads after the deadline without `allowLate`. | 422 `DEADLINE_PASSED`. |
| NS-07 | Member uploads a file named `../../etc/passwd` or with a mismatched type. | Name sanitized or rejected; type mismatch 422 `FILE_TYPE_NOT_ALLOWED`; nothing outside storage root written. |
| NS-08 | Deactivated user uses a valid token. | 401 on next request. |
| NS-09 | Two members update the same task with the same version. | Second request 409 `STALE_VERSION`. |
| NS-10 | Student attempts to modify a sealed submission version. | 409 or 422; no change; test asserts DB row unchanged. |
| NS-11 | Member tries to move another member's task via a forged assignee. | 422 for non-member assignee; 403 or 404 for non-assignee transition per matrix. |
| NS-12 | Administrator tries to download a student's private submission file. | 404 in MVP (ADR-007); a break-glass path is deferred. |

## 6. Accessibility stories

**US-A11Y-01.** As a keyboard-only user, I want to complete the golden path without a mouse, so that the product is usable with assistive technology. Acceptance: every primary action is reachable with Tab and Enter; focus is visible; dialogs trap and restore focus.
