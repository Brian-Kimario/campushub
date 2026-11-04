# CampusHub — 8-Minute Demonstration Script

**Purpose:** Run a live demonstration of CampusHub on the local seeded stack in about eight minutes: sign-in, project work, supervisor assignment, faculty review, notifications, admin user management, and the engineering points behind them.
**Branch and commit this script was verified against:** `phase-7a-certifications` at `019327a` (Document profile and user administration).
**Last reviewed:** 2026-11-04 (demo readiness check)
**Related:** [API_CONTRACT.md](API_CONTRACT.md), [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md), [CI_CD.md](CI_CD.md), [CHANGELOG.md](CHANGELOG.md).

## 0. Disclosure: deferred features

These are **not** part of this demo and must be stated if asked:

- **2C — Project lifecycle.** A project is created `ACTIVE` immediately. The `DRAFT` → approval → `ACTIVE` flow is not implemented.
- **2D — Milestone-linked submissions.** Submissions are not linked to milestones.

Also not in this demo: file upload and evidence download (not exercised in the final check), invitations, calendar editing (the calendar view is shown only), and an audit log page in the UI (the audit log is reachable through the API only; see §4).

## 1. Before the demo (checklist)

1. Start the database and API (`.env` is created from `.env.example`, with `CAMPUSHUB_DEV_SEED_ENABLED=true` and `CAMPUSHUB_DEV_SEED_PASSWORD` set to the local seed password):

   ```bash
   docker compose up -d --build
   ```

2. Confirm readiness. The response must show `"status":"UP"`:

   ```bash
   curl -s http://localhost:8080/actuator/health/readiness
   ```

3. Start the frontend in a second terminal:

   ```bash
   cd frontend && npm install && npm run dev
   ```

4. Open the app at **http://localhost:5173/login**.
5. Use a private window for each role, or sign out between roles. The access token lives in memory; a reload restores the session from the HttpOnly refresh cookie.

Demo accounts (all verified to sign in on 2026-11-04). The password is the value of `CAMPUSHUB_DEV_SEED_PASSWORD` from your local `.env`. It is for local development only and must never be deployed publicly.

| Email | Role | Used in |
|---|---|---|
| `student@campushub.local` | STUDENT | Minutes 1–2, 4 |
| `admin@campushub.local` | ADMIN | Minutes 2, 5 |
| `faculty@campushub.local` | FACULTY | Minutes 3 |
| `student2@campushub.local` | STUDENT | Optional negative check (§6) |

## 2. The 8-minute sequence

Check each expected result before moving on. If a result differs, say so and continue; do not change the expected value on the spot.

| Time | Who (URL) | Action | Expected result |
|---|---|---|---|
| 0:00–0:45 | Terminal | Run the readiness `curl` from §1. Show the login page. | `status` is `UP`. The login form appears. |
| 0:45–2:00 | **Student** `/login` | Sign in as `student@campushub.local`. Go to **Projects** → **New project**. Enter a title, such as "Live demo project", and save. Open it and add a task, "Write demo outline", with priority High. Move the task to In progress. | The project appears as `ACTIVE`. The task appears as `TODO`, then `IN_PROGRESS`. |
| 2:00–2:40 | **Student** (same session) | On the project, create a submission: "Revision 1: Demo outline draft", with a link beginning `https://`. | The revision shows `SUBMITTED`. A link that starts with `http://` is refused by validation. |
| 2:40–3:20 | **Admin** `/login` (sign out the student first) | Sign in as `admin@campushub.local`. Open the new project and assign **Dev Faculty** as supervisor. | "Dev Faculty" appears as Supervisor. The admin has the **Users** navigation item. |
| 3:20–4:20 | **Faculty** `/login` (sign out the admin first) | Sign in as `faculty@campushub.local`. On **Dashboard**, the supervised project shows "1 awaiting review". Open the project → **Start review**. Leave Feedback empty and try **Save decision** with "Request changes". Then enter feedback and save. | The empty feedback is refused (`FEEDBACK_REQUIRED`). After saving, the revision shows "Changes requested" with the feedback text. |
| 4:20–5:00 | **Student** (sign out faculty first) | Open **Notifications**. | "Dev Faculty requested changes to your submission in …" is listed as unread, with the time. The nav shows an unread badge. |
| 5:00–6:10 | **Admin** `/admin/users` | In **Create an account**, enter a disposable email such as `demo.live@campushub.local`, a name, and role Student. Create it. Note the one-time temporary password. Then select **Reset password** for that account. Then **Deactivate**, and then **Reactivate**. On the admin's own row, note that Deactivate is disabled ("You cannot deactivate your own account"). | The new account shows "must change password". Each action is confirmed in the directory. The one-time password is shown once. Your own row cannot be deactivated. |
| 6:10–6:50 | **Access control** (sign in as faculty, or as the student if time allows) | Open `http://localhost:5173/admin/users`. | The page shows "Administrators only" and the user directory does not load. The API also refuses this with 403 (§3). |
| 6:50–7:40 | **Terminal** | Run the API commands in §3: log in, call the audit search, and show the admin actions from the last five minutes. Then show the CI checks for `019327a` (§4). | Login returns a token. The audit search lists `admin.user.created`, `admin.user.deactivated`, `admin.user.reactivated`, and `admin.user.password_reset`. |
| 7:40–8:00 | Close | State what was shown, the deferred items (§0), and the known limitations (§5). | — |

## 3. JWT and role-based access, shown through the API

Set the local seed password once in your terminal. Do not paste it into chat or documents.

```bash
export CAMPUSHUB_DEV_SEED_PASSWORD='<your local seed password>'
```

Log in as the administrator. The response carries an `accessToken` (short-lived bearer JWT). The refresh token is set as an HttpOnly cookie, `campushub_refresh`, which the browser sends only to `/api/v1/auth`.

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Requested-With: CampusHub' \
  -d "{\"email\":\"admin@campushub.local\",\"password\":\"$CAMPUSHUB_DEV_SEED_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
```

Show the admin-only audit search, which is an API endpoint with no UI page:

```bash
curl -s "http://localhost:8080/api/v1/admin/audit-events?size=5" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Show the role rule. Log in as the student the same way (`student@campushub.local`), storing the token in `STUDENT_TOKEN`, and call the admin endpoint with it. Expected: HTTP 403 (verified over HTTP on 2026-11-04; the same call as admin returns 200).

```bash
STUDENT_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' -H 'X-Requested-With: CampusHub' \
  -d "{\"email\":\"student@campushub.local\",\"password\":\"$CAMPUSHUB_DEV_SEED_PASSWORD\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
curl -s -o /dev/null -w '%{http_code}\n' "http://localhost:8080/api/v1/admin/audit-events" \
  -H "Authorization: Bearer $STUDENT_TOKEN"
```

What to say: the server enforces the role on every `/api/v1/admin/**` route (`SecurityConfig`). The UI hides navigation, but the API check is what protects the data. A token issued before a user is deactivated or has a password reset is refused, because `AuthenticationFilter` compares the token's issue time against the account's revocation time.

## 4. Engineering points: how to demonstrate each

| Topic | What to show | What to say |
|---|---|---|
| **REST APIs** | The `curl` calls in §3. The API contract at [API_CONTRACT.md](API_CONTRACT.md). | All routes are under `/api/v1`. Errors use RFC 9457 problem responses with an `errorCode` such as `FEEDBACK_REQUIRED`. Lists use one paging shape: `content`, `page`, `totalPages`, `sort`. |
| **JWT and RBAC** | The student-to-admin 403 in §3. The "Administrators only" page in minute 6. | Stateless bearer JWT. Roles are `STUDENT`, `FACULTY`, `ADMIN`. Route-level checks plus per-resource checks (for example, only the supervisor can review). Refresh and logout also require the `X-Requested-With` header (CSRF defence). |
| **MySQL** | The stack is running MySQL 8.4 in Docker. Flyway migrations run on startup (18 in total). | Schema changes are versioned migrations. Applied migrations are never edited. Optimistic locking (`@Version`) protects concurrent edits. Hibernate validates the schema and does not generate it. |
| **Kafka (optional)** | Explain only. Do not claim it is running unless you started it. Optional start: `docker compose --profile kafka up -d`, with `CAMPUSHUB_KAFKA_ENABLED=true`. | Kafka is an optional event broker, **off by default** (ADR-011). Notifications in this demo are database rows that the browser polls once a minute, not pushed events. The transactional outbox that would publish them to Kafka is deferred. Kafka was not exercised in the final verification. |
| **CI/CD** | GitHub Actions run `41757115852` for commit `019327a` (see below). | CI runs the backend `mvnw verify` (including Testcontainers MySQL tests), the frontend typecheck, build and lint, and an API image build that does not push. Deployment is not automated and was not performed. |

CI results for `019327a` (run 41757115852, checked 2026-11-04):

| Check | Result |
|---|---|
| backend (`mvnw verify`) | pass (9m 8s) |
| frontend (typecheck, build, lint) | pass (10s) |
| api image (build only, no push) | pass (1m 54s) |
| GitGuardian Security Checks | **fail** (see §5) |

## 5. Known limitations

- **GitGuardian check fails.** It flags one Generic Password finding at `backend/src/test/java/com/campushub/identity/PasswordPolicyTest.java:31`, which is the deliberately short test input `"Test@12"` (introduced in commit `b3cad32`). It is not a live credential. The finding is not dismissed in this repository; it needs a decision in the GitGuardian dashboard. Say this plainly if asked.
- **2C and 2D are deferred** (§0).
- Evidence files are exercised on disposable records only. Accepted revisions no longer offer "Add a file", so uploads apply to open revisions only. A non-member gets 404 for a file, not 403, so the file's existence is not revealed.
- Audit search has no UI page; it is API only.
- The admin dashboard (`/dashboard`) shows counts only: active accounts by role and projects by status. There are no charts or time series. Admin role changes are refused (`ROLE_CHANGE_NOT_SUPPORTED`), and a department cannot be cleared through the admin form.
- The task list returns at most 50 tasks per project, and there are no pagination controls in the UI.
- Notifications are polled, not pushed.
- Mobile layout was checked only on `/projects` at 375 px (no horizontal scroll). Other pages were not checked at that width.

## 6. Optional extended checks (only if time allows)

These are not part of the 8-minute run.

1. **Cross-user access.** Sign in as `student2@campushub.local` and open the first student's project URL. Expected: not found or access denied.
2. **Password change.** Open `/change-password`, change the password, sign out, and sign in with the old password (refused) and the new one (accepted). Restore the original password afterwards so the demo accounts stay usable.
3. **Skills catalog.** As admin, open `/skills` and add a skill. Adding the same name with different case shows "A skill with this name already exists."
4. **Certifications.** As student, open `/certifications`. An expiry date before the issue date, or a link starting with `http://`, is refused before saving.
5. **Dashboards.** Student `/dashboard` shows counts and task progress. Faculty `/dashboard` shows only supervised projects.

## 7. Verification status (as of 2026-11-04)

| Area | Status | Evidence |
|---|---|---|
| Sign-in for all four demo accounts | Verified in browser | Each account signed in and reached its landing page |
| Student project create, task create and transition, submission create | Verified in browser | Project `Demo Golden Path Project`, task `Write demo outline`, revision 1 `SUBMITTED` |
| Admin supervisor assignment | Verified in browser | Supervisor shows "Dev Faculty" |
| Faculty dashboard (supervised projects, awaiting-review count) | Verified in browser | Shows "1 awaiting review" on the golden path project |
| Faculty review: empty feedback refused; request changes with feedback saved | Verified in browser | Revision shows "Changes requested" with the feedback text |
| Student notifications for the decision | Verified in browser | Unread notification "Dev Faculty requested changes to your submission…" |
| Admin: create account, must-change-password flag | Verified in browser | Account created; row shows "must change password" |
| Admin: reset password (one-time temporary password) | Verified in browser | Temporary password shown once; audit event `admin.user.password_reset` present |
| Admin: deactivate and reactivate | Verified in browser | Status moved Active → Deactivated → Active; audit events present |
| Admin: own-row deactivate disabled | Verified in browser | Button reads "You cannot deactivate your own account" |
| Non-admin blocked from `/admin/users` | Verified in browser (faculty) and over HTTP (student) | Page shows "Administrators only"; `GET /api/v1/admin/audit-events` returns 403 for the student and 200 for the admin |
| Audit search `GET /api/v1/admin/audit-events` with action filter | Verified over HTTP | Returns the admin actions; `action=admin.user.password_reset` filter returns one row |
| Profile page loads | Verified in browser | Admin profile page renders |
| Calendar page loads | Verified in browser | Month view renders, with the empty-day message |
| Mobile layout at 375 px (`/projects`) | Verified in browser | No horizontal overflow (scrollWidth equals clientWidth) |
| CI for `019327a` (backend, frontend, image build) | Verified on GitHub | Run 41757115852: all pass except GitGuardian |
| Admin dashboard `/dashboard` reachable from the nav; shows counts | Verified in browser | Admin nav shows Dashboard (fixed: it was hidden from admins). Active accounts by role and projects by status shown |
| Faculty accepts revision 2; student is notified | Verified in browser | Revision 2 shows "Accepted"; student's unread notification "Dev Faculty accepted your submission in…" |
| Evidence upload, download and integrity | Verified in browser | PNG uploaded to a disposable project; downloaded bytes match the SHA-256 of the original |
| Evidence authorization | Verified over HTTP | Non-member student gets 404 for the file content; no token gets 401 |
| Invitation, accept and access | Verified in browser | Owner invites `student2@campushub.local`; invitee accepts on `/invitations`; the project appears in their list and the owner gets the acceptance notification |
| CI for `b71dfd7` (backend, frontend, image build) | Verified on GitHub | Run 41758924884: all pass; GitGuardian still fails (see §5) |
| Mobile layout on pages other than `/projects` | Not verified | — |
| Kafka path | Not verified | Off by default; not exercised |

## 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Login returns 404 for `/api/v1/auth/login` | The container on port 8080 is an old build. Rebuild with `docker compose up -d --build api`. Do not remove volumes. |
| Login returns 401 for every account | The seed password does not match `CAMPUSHUB_DEV_SEED_PASSWORD`. The seeder skips accounts that already exist, so a changed value does not change a stored password. Reset it through admin user management. |
| Readiness is `DOWN` | Check `docker compose logs api`. |
| Frontend calls fail with a network error | The API is not on the port the Vite proxy targets (`http://localhost:8080`). Set `CAMPUSHUB_API_TARGET` before `npm run dev`. |
| Reload shows the login screen | The refresh cookie is missing or expired (8-hour limit). Sign in again. |
| Startup refuses the seed | `CAMPUSHUB_DEV_SEED_ENABLED=true` requires `CAMPUSHUB_DEV_SEED_PASSWORD`. |

Do not run `docker compose down -v` before the demo. It deletes the local database volume.

## 9. Appendix: profile and user administration (detail)

Use this appendix only if asked for more detail on the profile and user administration flow. The 8-minute run in §2 covers it.

- **Student edits a profile.** Sign in as the student, open **Profile**, change the display name and other fields, and save. The page shows "Profile saved." The email, institutional ID, and role are read-only; the API refuses changes to them with `FIELD_NOT_EDITABLE`.
- **Filter and search.** In **Directory**, set Role to Student and search for `demo`. The password is never shown in the list.
- **Deactivated sign-in.** Sign in as a deactivated account in a private window: the sign-in is refused. Reactivate it as admin, and it signs in again.
- **Role change refused.** `PATCH /api/v1/admin/users/{id}` with `{"role":"ADMIN"}` returns 422 `ROLE_CHANGE_NOT_SUPPORTED`, and the role is unchanged.
