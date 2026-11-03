# CampusHub — MVP Demonstration Script

**Purpose:** Run the MVP demonstration end to end in the React UI on a local machine, from startup to the student seeing faculty feedback.
**Scope:** The MVP only: login and forced password change, project proposal and supervisor assignment, tasks, submissions and revisions, faculty review with feedback, and the student's view of the decision. Everything else in the full plan (calendar, invitations, milestones, audit log UI, deactivation, file uploads) is out of MVP scope and is not demonstrated.
**Last reviewed:** 2026-11-03 (MVP Stage 4)
**Related:** [API_CONTRACT.md](API_CONTRACT.md), [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md), [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md) §11.

## 1. Software and versions

| Component | Version or setting |
|---|---|
| Java | 21 (the Maven wrapper in `backend/` is used, so no local Maven install is needed) |
| Spring Boot | 3.5.16 |
| MySQL | 8.4, from the `mysql:8.4` image pinned in `docker-compose.yml` |
| Node.js | 20 or later, with npm |
| React | 19, Vite 8, Redux Toolkit with RTK Query, React Router, React Hook Form with Zod 4 |
| Docker | Docker Desktop or Docker Engine with Compose v2 |

API routes are served under `/api/v1`. The frontend calls `/api/v1` through the Vite dev proxy, which forwards to `http://localhost:8080` by default.

## 2. Setup (once)

1. Create a local `.env` in the repository root from `.env.example`. Set the database and JWT values, and for demo use set:
   - `CAMPUSHUB_DEV_SEED_ENABLED=true`
   - `CAMPUSHUB_DEV_SEED_PASSWORD` to a local password of your choice.

   `.env` is gitignored. Do not commit it, and do not copy its values into any document.
2. Start the database and API:

   ```bash
   docker compose up -d --build
   ```

3. Confirm readiness. The response should show `UP`:

   ```bash
   curl -s http://localhost:8080/actuator/health/readiness
   ```

4. Start the frontend dev server in a second terminal:

   ```bash
   cd frontend && npm install && npm run dev
   ```

5. Open `http://localhost:5173/login` in the browser.

### Demo accounts

The seeder creates one account per role. Names only; the password is the value you set in `CAMPUSHUB_DEV_SEED_PASSWORD`.

| Email | Role | Used in |
|---|---|---|
| `admin@campushub.local` | ADMIN | Supervisor assignment (step 3.3) |
| `faculty@campushub.local` | FACULTY | Review and feedback (step 3.4) |
| `student@campushub.local` | STUDENT | Project, task, submission (steps 3.1, 3.2, 3.5) |
| `student2@campushub.local` | STUDENT | Negative checks (step 3.6) |

The seeder skips accounts that already exist, so changing `CAMPUSHUB_DEV_SEED_PASSWORD` later does not change a password that is already stored. To reset a demo password, use the admin account's user management, or reset the local database (see §5).

Use one browser profile for one role at a time, or use a private window per role. The access token is held in memory only. A reload restores the session from the HttpOnly refresh cookie (ADR-025). Signing out ends the session on the server and clears the cookie.

## 3. Script

Each step gives the action in the UI and the expected result. Check each result before moving on.

### 3.1 Student: sign in, create a project and a task

1. Sign in as `student@campushub.local`.
   - Expected: the project list loads. It may be empty.
2. Create a project with a title and description. Save.
   - Expected: the project appears in the list with status `ACTIVE`.
3. Open the project. Create a task with a title, and optionally a due date.
   - Expected: the task appears with status `TODO`.
4. Move the task to `IN_PROGRESS`.
   - Expected: the status changes. Transitions not allowed from the current state are not offered.

### 3.2 Student: submit a revision

1. On the project, open the submissions panel. Create a submission (revision 1) with a summary.
   - Expected: revision 1 appears with status `SUBMITTED`.

Do not show a sealed-version or file-upload step in the MVP. File upload is not in the MVP.

### 3.3 Administrator: assign a supervisor

1. Sign out. Sign in as `admin@campushub.local`.
   - Expected: admin navigation only. Student task creation is not offered.
2. Open the student's project. Assign `Dev Faculty` as supervisor.
   - Expected: the supervisor name appears on the project.
3. Sign out.

### 3.4 Faculty: review with feedback

1. Sign in as `faculty@campushub.local`.
   - Expected: the supervised project appears in the list.
2. Open the project and the submission. Start the review.
   - Expected: the review starts for revision 1.
3. Try to request changes with an empty feedback field.
   - Expected: the request is refused. The message names the feedback requirement (`FEEDBACK_REQUIRED`).
4. Enter feedback and request changes.
   - Expected: the decision is `CHANGES_REQUESTED`. A second decision on the same revision is refused.
5. Sign out.

### 3.5 Student: see the feedback and revise

1. Sign in as `student@campushub.local`.
   - Expected: the project shows `Changes requested` and the faculty feedback text.
2. Create revision 2 with a new summary and submit.
   - Expected: revision 2 is created. Revision 1 stays visible in the history.
3. Sign out. Sign in as faculty. Start the review of revision 2 and record `ACCEPTED`, with feedback if the form requires it.
   - Expected: revision 2 is accepted; the decision is recorded once. (Not yet verified in the browser; see §4.)
4. Sign in as the student again.
   - Expected: the project shows the accepted decision and both revisions. (Not yet verified in the browser; see §4.)

### 3.6 Negative checks (optional, when time allows)

These checks confirm the authorization rules in the UI. Each one should be refused or hidden.

1. Sign in as `student2@campushub.local`. Open the first student's project by its URL.
   - Expected: not found or access denied. The project data is not shown.
2. Sign in as the student. Try to open the admin user area.
   - Expected: no admin navigation. A direct URL redirects or shows not found.
3. Sign in as the student. Try to create a task on a project that is not theirs.
   - Expected: not found.

If a check returns an unexpected result, say so. Do not change the expected value during the demonstration. Refer to the test that covers it in the backend suite.

### 3.7 Password change (optional)

The seeded accounts do not require a password change. Use this step with an account that has `mustChangePassword` set, or with the student account after the change is made.

1. Open `/change-password` while signed in. Enter the current password and a new one of 8 to 72 bytes.
   - Expected: the change succeeds.
2. Sign out. Sign in with the old password, then with the new one.
   - Expected: the old password is refused with the generic message; the new one works.
3. Restore the original password with the same steps, so the demo accounts stay usable.

## 4. Verification status

What was checked, and how. Anything not listed as verified was not demonstrated end to end in the browser.

| Area | Status | Evidence |
|---|---|---|
| `/api/v1` routing and auth | Verified | `./mvnw -B verify`, 97 tests, 0 failures |
| Login (correct and wrong password), empty-field validation, `/projects` redirect without session | Verified in browser | Built-in browser pane |
| Student project create, task create, transition to `IN_PROGRESS`, revision 1 and 2 submit | Verified in browser | Built-in browser pane |
| Admin supervisor assignment | Verified in browser | Built-in browser pane |
| Faculty review start, changes requested with feedback, student sees feedback | Verified in browser | Built-in browser pane |
| Password change through the UI (old rejected, new accepted) | Verified in browser | Built-in browser pane; original password restored afterwards |
| Session restored on reload; sign-out ends it | Verified in browser | Reload stays on `/projects` (refresh returns 200). Sign-out returns 204 on the first click, lands on `/login`, and the next visit to `/projects` redirects to login (refresh returns 401) |
| Faculty accepts revision 2 and the student sees the accepted decision | Not verified in browser | Backend decision rules covered by `SubmissionFlowIntegrationTest` |
| Student attempts admin actions and cross-user access in the UI | Not verified in browser | Covered by backend tests only |
| Task transitions beyond `IN_PROGRESS` (`BLOCKED`, `DONE`, archive) | Not verified in browser | Covered by backend tests (`TaskFlowIntegrationTest`) |
| Block and reset flows | Not verified in browser | — |
| Project archive from the UI | Not verified in browser | Backend endpoint exists |
| Pagination past the first page (50-task limit) | Not verified | No pagination controls in the UI |

## 5. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Login returns 404 for `/auth/login` | The container on port 8080 is an old build without the `/api/v1` prefix. Rebuild with `docker compose up -d --build api`. Do not remove volumes. |
| Login returns 401 for every account | The seed password does not match. Check `CAMPUSHUB_DEV_SEED_PASSWORD` in `.env`, or reset the demo password through admin user management. |
| Readiness is `DOWN` | Check `docker compose logs api`. A missing storage path causes a `DOWN` status locally; the Compose volume provides `/var/campushub/storage`. |
| Frontend calls fail with a network error | The API is not running on the port the proxy targets. Set `CAMPUSHUB_API_TARGET` before `npm run dev` if the API is on another port. |
| Page reload shows the login screen | The refresh cookie is missing or expired (8-hour limit). Sign in again. If the browser refuses the Secure cookie over plain HTTP, set `campushub.refresh.cookie-secure=false` for that local run only. |
| Startup refuses the seed | `CAMPUSHUB_DEV_SEED_ENABLED=true` requires `CAMPUSHUB_DEV_SEED_PASSWORD` to be set. |

Reset the local database only if you accept losing its data. The command below removes the volumes and is not part of the normal demo:

```bash
docker compose down -v
```

## 6. Known limitations

- The MVP has no file upload, calendar, invitations, milestones, audit log UI, or account deactivation UI. These are out of MVP scope.
- The task list returns at most 50 tasks per project, and the UI has no pagination controls.
- The break-glass path for administrators to read a private submission is deferred (ADR-007), so the admin account cannot open a student's submission files.
- The intermittent sign-out failure seen earlier (needing a second click) did not recur in the final browser check, but it was not stress-tested. The refresh-cookie fix addresses its likely cause (an expired access token blocking `/auth/logout`), which is covered by a backend test.
- The compose API port (8080) must not be used by another process during the demo.
