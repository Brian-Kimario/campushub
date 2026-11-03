# CampusHub — Demonstration Script

**Purpose:** The step-by-step script for the capstone demonstration: what to show, in what order, what to say about it, what should happen, and what to do if something fails.
**Scope:** The Phase 11 demonstration of the MVP. It covers GP-1, GP-2, and GP-3 from USER_STORIES §4, the negative security journeys from USER_STORIES §5, and the syllabus items from [SYLLABUS_MAPPING.md](SYLLABUS_MAPPING.md).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [USER_STORIES.md](USER_STORIES.md), [API_CONTRACT.md](API_CONTRACT.md), [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md), [OPERATIONS.md](OPERATIONS.md) §9 (demonstration-day checklist).

## 1. Before the session

Complete the demonstration-day checklist in OPERATIONS §9. In short: readiness is UP; the demo accounts are present and their passwords come from the secret store, not from this document; a backup from the last 24 hours exists; the golden path has been rehearsed in the two hours before.

Demo accounts (names only; passwords are held in the secret store and the seed file):

| Account | Role | Used in |
|---|---|---|
| Admin demo | ADMIN | GP-3, NS-04 setup, NS-12 |
| Faculty demo (supervisor) | FACULTY | GP-2, GP-1 review steps, NS-05 |
| Faculty demo (other) | FACULTY | NS-05 |
| Student demo (owner) | STUDENT | GP-1 owner steps, NS-01, NS-03, NS-04 |
| Student demo (teammate) | STUDENT | GP-1 teammate steps, NS-07 |
| Student demo (outsider) | STUDENT | NS-02, NS-08 |

Set up two browser windows side by side, one as the student and one as the faculty member, and a third window for the administrator. Use a private window for each so sessions do not mix. Have the audit log page open in the administrator window before starting.

Run the demonstration on the staging host only as a rehearsal. The final run is on the production demonstration host.

## 2. Timing

| Segment | Minutes | Content |
|---|---|---|
| Opening | 3 | Problem, roles, architecture in one diagram |
| Student path (GP-1) | 12 | Proposal, team, tasks, submission, revision |
| Faculty path (GP-2) | 6 | Review, feedback, completion |
| Administrator path (GP-3) | 5 | Accounts, catalog, audit, deactivation |
| Security journeys | 6 | Negative paths, live |
| Engineering evidence | 5 | Tests, CI summary, syllabus items, measurements |
| Questions | 3 | |

Total: 40 minutes. If time runs short, skip the optional items marked **(skip if short)**; never skip a security journey.

## 3. Script

Each step gives the action, what to say (brief), and the expected result. A "check" line is what the presenter confirms before moving on.

### 3.1 Opening

1. Show the architecture diagram from ARCHITECTURE §2. Say: modular monolith, React client, Spring Boot API, MySQL, private file storage.
2. Show the role table from PRODUCT §3. Say: every route checks the role first, then the object, then the state.

### 3.2 Student path (GP-1)

| # | Action (student window) | Expected result | Syllabus item |
|---|---|---|---|
| 1 | Sign in with the student owner account | Dashboard shows assigned projects and tasks for this user only | 5 JWT |
| 2 | Open the proposal form. Enter a title and a description. Reload the page. | Draft restored from `sessionStorage` with the same values | 2 Draft persistence |
| 3 | Clear the term field and try to submit | Submit is blocked; the message names the missing field | 1 Controlled form |
| 4 | Complete the term and add one milestone. Submit. | Proposal status moves to submitted | 1, 3 |
| 5 | (Administrator window) approve the proposal and assign the faculty supervisor | Status changes to approved; supervisor shown | 6 RBAC |
| 6 | Owner activates the project | Status becomes active | 6 |
| 7 | Owner invites the teammate account | Invitation shows as pending for both users | 6 |
| 8 | Teammate accepts the invitation | Membership appears for both; team count shown | 6 |
| 9 | Owner creates a milestone with a due date | Milestone listed; date in local time, stored as UTC | 7 Calendar |
| 10 | Owner creates a task and assigns it to the teammate. Move it to IN_PROGRESS, then DONE. | Task board and list show the same data; memoized counts update | 4 Memoized selectors |
| 11 | Open the calendar in month view, switch to week view | The task and milestone dates appear in both views | 7 |
| 12 | Owner opens the task in a second window, edits it there, then edits it in the first window with the old version | Second save returns the "changed by someone else" message, and the latest task reloads | 8 Concurrency (NS-09) |
| 13 | Owner opens the submissions page. Sort by date, go to page 2. | Stable order; page 2 loads | 9 Pagination and sorting |
| 14 | Teammate uploads a PDF to the milestone and submits version 1 | Version 1 sealed; the file is read-only | 12 Method-level security |
| 15 | Teammate tries to delete a file from the sealed version | Denied with a clear message; file still present | NS-10 |
| 16 | (Faculty window) supervisor starts the review, requests changes, adds feedback | Student sees the decision and feedback on the submission and the dashboard | — |
| 17 | Student creates version 2, uploads a file, submits | Version 2 sealed; version 1 still downloadable | US-STU-08 |
| 18 | (Faculty window) supervisor accepts version 2 | Status accepted; feedback history shows both versions | — |
| 19 | Student views the dashboard | Accepted status and latest feedback visible | US-STU-07 |

Check: the student sees the accepted status and feedback with no page reload other than the normal refresh.

**(skip if short)** Step 12 is optional when time is short; NS-09 is also covered by the test suite.

### 3.3 Faculty path (GP-2)

| # | Action (faculty window, supervisor) | Expected result | Syllabus item |
|---|---|---|---|
| 1 | Sign in as the supervisor | Supervised project list shows only the GP-1 project and any other supervised projects | 6 RBAC |
| 2 | Open the project. Show the milestone progress summary. | Progress shown; at-risk definition visible in the UI (US-FAC-03) | 4 Memoized selectors |
| 3 | Open the submission version 1 and download a file | Download with `Content-Disposition: attachment`; file opens locally | 13 Tokens and file handling |
| 4 | Start the review, record "changes requested" without feedback | Blocked: feedback is required; the field is named | US-FAC-02 |
| 5 | Add feedback and record the decision | Decision recorded once | US-FAC-02 |
| 6 | Try to record a second decision on the same version | Refused; one decision per version | US-FAC-02 |
| 7 | After version 2 is accepted, mark the project complete | Project becomes completed when milestones are closed | US-FAC-05 |
| 8 | **(skip if short)** Open the review of a project the supervisor does not supervise, by ID | 404 (NS-05) | 6 RBAC |

### 3.4 Administrator path (GP-3)

| # | Action (administrator window) | Expected result | Syllabus item |
|---|---|---|---|
| 1 | Sign in as administrator | Administrator navigation only (no proposal creation, no review actions) | 6 RBAC |
| 2 | Create a term and a course under a department | Items appear; catalog cache is refreshed | 11 Caching |
| 3 | Create a faculty account | Temporary password shown once | US-ADM-01 |
| 4 | Create a student account | Temporary password shown once; account must change password on first sign-in | US-ADM-01 |
| 5 | Approve a proposal and assign a supervisor | Audit entries written for each action | US-ADM-03 |
| 6 | Open the audit log. Filter by actor, then by action. | Entries shown with sanitized metadata only; no password or token appears | US-ADM-04 |
| 7 | Deactivate a student account that has a submission | Account deactivated | US-ADM-01 |
| 8 | Sign in as the deactivated student in the outsider window | Sign-in refused with the generic message | NS-08 |
| 9 | Open the deactivated student's old submission from the teammate window | Submission still present; history intact | GP-3 final condition |

### 3.5 Security journeys (live)

Each journey is one request or one short sequence. Show the response; state the expected status and code from API_CONTRACT §3.

| ID | Action | Expected |
|---|---|---|
| NS-01 | Student A requests Student B's certification | 404 |
| NS-02 | Outsider requests the private project by ID | 404, body identical to a nonexistent ID |
| NS-03 | Student sends a PATCH setting their own role to FACULTY | 400 `FIELD_NOT_EDITABLE`; role unchanged |
| NS-04 | Student tries to approve their own proposal | 403 `FORBIDDEN_ROLE` |
| NS-05 | Faculty requests a project they do not supervise | 404 |
| NS-06 | Member uploads after the deadline with no late exception | 422 `DEADLINE_PASSED` |
| NS-07 | Member uploads a file named `../../etc/passwd`, and a PDF extension on a PNG body | Name sanitized; type mismatch 422 `FILE_TYPE_NOT_ALLOWED`; nothing written outside storage |
| NS-08 | Deactivated user reuses an old token | 401 on the next request |
| NS-09 | Two saves with the same version | Second 409 `STALE_VERSION` |
| NS-10 | Student attempts to change a sealed version | 409 or 422; row unchanged |
| NS-11 | Member submits a task assignee who is not a member | 422 |
| NS-12 | Administrator requests a student's private submission file | 404 (ADR-007) |

Presenter note: NS-12 is the point where the administrator's limit is explained. The break-glass path is deferred (ADR-007), so the demonstration states it rather than hiding it.

### 3.6 Engineering evidence

1. Show the latest CI run on `main`: the gate table in the job summary (CI_CD §3). Point to the security suite count and the coverage per module.
2. Show the OpenAPI snapshot diff from the last change (empty, or the deliberate change with its snapshot update).
3. Show one measurement from IMPLEMENTATION_LOG: the render-count profile for the task board and the SM-6 latency result (TEST_STRATEGY §6.3). Report the number as measured, including a miss if there is one.
4. Show the syllabus table from [SYLLABUS_MAPPING.md](SYLLABUS_MAPPING.md). Name the N/A-documented rows and the reason for each: canary, Pact, AES-GCM.
5. Show the restore record from OPERATIONS §6.5 (drill date, time to ready).

## 4. If something fails

| Failure | Action |
|---|---|
| Sign-in fails for a demo account | Use the spare demo account for that role. Do not show the secret store. |
| Upload fails | Show the error message (it should name the reason). Use the pre-prepared sample PDF. |
| Calendar does not load | Skip to the next item; show the calendar from the recorded backup screenshot, labeled as such. |
| Readiness not UP | Stop the demonstration. Run the rollback decision in DEPLOYMENT §6 only if the release owner decides. Use the rehearsal recording. |
| A security journey returns an unexpected status | Say so. Show the test that covers it (TEST_STRATEGY §4) and the matrix row. Do not change the expected value on the spot. |
| Network loss | Switch to the recorded run from the rehearsal, with a spoken note that it is a recording. |

## 5. Rehearsal and record

- Rehearse once on staging at least two days before the session and once on the production host within two hours before it.
- Record each rehearsal in IMPLEMENTATION_LOG: date, host, the steps that failed, and the fix.
- Keep a recording of the full path as the fallback.
- After the session, record the outcome, the questions asked, and any gap found in IMPLEMENTATION_LOG, and create RISKS entries for any gap that remains.

## 6. What the demonstration does not claim

- No real student data is used; all records are synthetic (DEPLOYMENT §1).
- Single API instance; rate limits are per node (DEPLOYMENT §9, R-09).
- No canary release, no Pact contract tests, no AES-GCM for third-party tokens (SYLLABUS_MAPPING §2).
- Kafka and notifications are shown only if Phase 8 is complete and enabled; otherwise they are described, not shown.
