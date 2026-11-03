# CampusHub — Security

**Purpose:** Threat model, security controls, and the verification that proves them. Authorization rules are in [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md).
**Scope:** Application, data, files, dependencies, configuration, and operations for MVP through release.
**Last reviewed:** 2026-11-03 (Phase 0)

## 1. Security objectives

1. Academic records and files are visible only to the people the matrix allows.
2. A stolen or expired credential gives the attacker no lasting access.
3. Submitted work is never silently altered.
4. Secrets never appear in source control, logs, responses, or audit metadata.
5. Each control has a test that fails when the control is removed.

## 2. Assets and actors

| Asset | Sensitivity | Where it lives |
|---|---|---|
| Student identity (email, institutional ID, name) | Personal data | `users` |
| Password hashes | Credential material | `users.password_hash` |
| Project, task, and submission content | Academic, possibly unpublished work | MySQL |
| Submitted files | Academic work; may contain personal data | private storage volume |
| Certifications and evidence | Personal data, owner-private | MySQL and storage |
| Signing key (`CAMPUSHUB_JWT_SECRET`) | Credential material | environment / secret store only |
| Database credentials | Credential material | environment / secret store only |

Actors: anonymous internet user; authenticated student, faculty, or administrator; a student who is a member of one project but not another; a compromised browser session; a malicious uploader; an operator with host access.

## 3. Authentication

| Control | Design | Verified by |
|---|---|---|
| Password storage | BCrypt cost 12 (ADR-005). Argon2id is an accepted substitute if the library is available; the choice is a single configuration point | Hash format test; no plaintext in DB dumps (test fixture scan) |
| Password policy | 8 to 72 UTF-8 bytes (BCrypt input limit, IMPLEMENTATION_LOG §10); not equal to the previous password; rejects a short starter list of common passwords (not the full 10,000-entry list; IMPLEMENTATION_LOG §10) | Unit tests with known weak values |
| Login | Identical 401 response for unknown email, wrong password, and locked account (lock is not observable) | Test that compares responses and timing bounds |
| Lockout | 5 consecutive failures set `locked_until` to now + 15 min; a successful login resets the counter | Integration test |
| Throttling | 20 login attempts per minute per client IP, returning 429 with `Retry-After` | Integration test |
| Access token | JWT, HS256 (ADR-003). Claims: `sub`, `iss=campushub`, `aud=campushub-api`, `iat`, `exp` (15 min), `jti`, `role`. Validation rejects wrong `alg` (including `none`), wrong `iss` or `aud`, expired tokens, and tokens with a missing `jti` | Token validation test matrix |
| Signing key | At least 64 random bytes from the environment; the application refuses to start with a placeholder value or a key shorter than 32 bytes | Startup test |
| Revocation | Logout inserts `jti` into `revoked_tokens`. Password reset and admin deactivation revoke all tokens for the user by recording a `revoked_after` timestamp on `users` (added to V3) | NS-08 test and logout test |
| Per-request status check | Each request re-reads the user's `status` and `must_change_password` | NS-08 test |
| Refresh tokens | Implemented (ADR-025, supersedes ADR-004). Opaque 32-byte token, SHA-256 hash stored in `refresh_sessions`, HttpOnly, Secure by default, SameSite=Strict cookie scoped to `/api/v1/auth`, rotation with family revocation on reuse, 8-hour absolute lifetime | `AuthFlowIntegrationTest` refresh cases |
| Session in browser | Access token kept in memory (Redux `auth` slice). `sessionStorage` holds drafts only, never the token | Frontend unit test that asserts the token key is absent from storage |

## 4. Authorization

Summary of the three layers in [ARCHITECTURE.md](ARCHITECTURE.md) §5:

- Role checks on controllers.
- Object policies per feature, called before side effects.
- Scoped queries that join through membership or supervision.

Denial mapping (ADR-006): 404 for private resources the caller cannot see; 403 for permitted roles that fail an object state condition. The matrix is the single source of truth; every row has a test.

Frontend guards are UX only and are never the sole control.

## 5. File handling

| Control | Rule |
|---|---|
| Size | ≤ 20 MiB per file (`CAMPUSHUB_UPLOAD_MAX_FILE_BYTES`, default 20 971 520); enforced by the multipart parser and again in the service |
| Count | ≤ 5 files per version (`CAMPUSHUB_UPLOAD_MAX_FILES_PER_VERSION`) |
| Type | Allowlist by magic bytes: PDF (`%PDF-`), PNG (`89 50 4E 47 0D 0A 1A 0A`), JPEG (`FF D8 FF`), DOCX (ZIP container containing `[Content_Types].xml` and `word/document.xml`). The declared content type and extension must agree with the detected type, or the upload is rejected with `FILE_TYPE_NOT_ALLOWED` |
| Macros | Macro-enabled formats (`.docm`, `.xlsm`, `.pptm`) are not accepted |
| Names | Basename only; path separators and control characters removed; truncated to 255 characters; never used as a storage path |
| Storage | Random UUID key; written under the configured root, outside the web root and the repository; directory permissions 0700 for the runtime user |
| Serving | Only through the authorized download endpoint. `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`, `Cache-Control: private, no-store` |
| Integrity | SHA-256 recorded at upload and checked on download; mismatch returns 500 and an audit event |
| Malware scanning | Not in MVP. Recorded as risk R-07 with a documented mitigation: attachments only, no inline rendering, and a planned ClamAV adapter behind the `FileStorage` port |
| Write failure | Storage write before metadata commit; on DB failure the object is deleted. On storage failure no row is committed |

Traversal tests (NS-07) include `../`, absolute paths, encoded separators, NUL bytes, and overlong names.

## 6. Input validation and output encoding

- Bean Validation on every request DTO; service-level checks for cross-field rules.
- JSON parsing with unknown-property rejection on writable DTOs (mass-assignment defense, FR-PROF-02).
- Sort fields and filter enums are allowlisted; unknown values return 400, never fall through to SQL.
- All queries use JPA criteria or parameter binding; no string-built SQL.
- React escapes output by default; `dangerouslySetInnerHTML` is prohibited by lint rule. Feedback and task text are rendered as text.
- Markdown is not supported in MVP fields, which removes a rendering risk.

## 7. Transport, headers, and CORS

| Item | Setting |
|---|---|
| TLS | Terminated at the reverse proxy; HSTS `max-age=31536000; includeSubDomains` in production |
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'` |
| Other headers | `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY`, `Permissions-Policy` denying camera, microphone, geolocation |
| CORS | Exact origins from configuration (`CAMPUSHUB_CORS_ALLOWED_ORIGINS`, default `http://localhost:5173`). No wildcard with credentials. Allowed headers: `Authorization`, `Content-Type`, `X-Request-Id` |
| CSRF | Bearer-token APIs are not exposed to CSRF. The refresh cookie is SameSite=Strict and the cookie endpoints (`/auth/refresh`, `/auth/logout`) require the `X-Requested-With: CampusHub` header |

Tests assert each header on a representative endpoint and CORS behavior for a disallowed origin.

## 8. Audit and logging

Audited events include: login success and failure (without the submitted password), lockout, logout, token revocation, password change and reset, account create, deactivate, reactivate, role-sensitive field changes, project status changes, supervisor assignment, membership changes, submissions sealed, file upload and download, reviews, deadline overrides (`allowLate`), catalog changes, and every 403 and 404 concealment decision on a private resource (`outcome=DENIED`).

Allowed `metadata` keys: `fromStatus`, `toStatus`, `targetVersion`, `fileCount`, `reason` (≤ 200 characters), `supervisorId`, `inviteeId`, `milestoneId`, `sizeBytes`, `detectedType`. Anything else is dropped by the audit writer.

Application logs: structured JSON, one line per event, with `requestId`. Never logged: passwords, tokens, `Authorization` headers, file contents, full email addresses at INFO (hashed or masked), stack traces to clients. A logging test feeds a known secret through the login and upload paths and asserts it appears nowhere in the captured output.

## 9. Secrets and configuration

- All secrets come from environment variables or the host's secret store. `.env` is git-ignored; `.env.example` contains placeholders only.
- Startup validation rejects placeholder values such as `replace-with-…`.
- CI secret scanning (gitleaks) runs on every push; a finding fails the build.
- Production database users: `campushub_app` (DML only) and `campushub_migrator` (DDL, used only during deployment). The application never connects as the migrator.
- Default demo accounts are created only when `CAMPUSHUB_DEV_SEED_ENABLED=true`, which is refused in the production profile.

## 10. Dependencies and supply chain

- Dependabot for Maven, npm, Docker base images, and GitHub Actions.
- CI runs OWASP Dependency-Check (or equivalent) and `npm audit --omit=dev`; critical or high findings without an accepted exception fail the build (SM-4).
- Lock files committed: `package-lock.json` and Maven dependency versions pinned in the POM.
- GitHub Actions pinned to commit SHAs for third-party actions.
- Static analysis: SpotBugs with the find-sec-bugs plugin in the backend; ESLint security rules in the frontend; CodeQL on pushes to `main`.

## 11. Threat model (STRIDE summary)

| Threat | Example | Primary controls | Tests |
|---|---|---|---|
| Spoofing | Forged JWT | Signature, `alg`, `iss`, `aud`, `exp`, `jti` checks | Token matrix |
| Spoofing | Brute force login | Lockout, throttling, generic response | Login tests |
| Tampering | Student edits sealed submission | Sealed-state service checks; DB triggers | NS-10 |
| Tampering | Mass assignment of `role` | Unknown-field rejection, service-level allowlist | NS-03 |
| Tampering | Stale overwrite of a task | `version` lock | NS-09 |
| Repudiation | Admin deactivates account, denies it | Audit with actor, request ID | Audit assertions |
| Information disclosure | IDOR on project or file | Object policy, concealment 404 | NS-01, NS-02, NS-05, NS-12 |
| Information disclosure | Verbose error reveals stack | Problem Details with safe `detail` only | Error contract test |
| Information disclosure | Account enumeration | Generic login response; generic password-change error | Login tests |
| Denial of service | Huge upload | Size limits at proxy and app; throttling | Upload size tests |
| Denial of service | Expensive list query | Page size cap; sort allowlist; indexed filters | Query-plan tests |
| Elevation of privilege | Student approves own proposal | Role check plus state check | NS-04 |
| Elevation of privilege | Faculty reviews unsupervised project | Supervisor-of-project check | NS-05 |
| Path traversal | Upload named `../../x` | Random keys; name sanitization | NS-07 |
| XSS | Feedback contains script | React text rendering; CSP | Frontend test with payload |
| CSRF | Cross-site state change | No cookie auth in MVP | Documented; P1 control tested when added |
| SSRF | Certification URL fetched by server | Server never fetches `credentialUrl`; stored as text only | Code review rule and test asserting no outbound call |

## 12. Security verification

| Level | What runs | When |
|---|---|---|
| Unit | Password policy, token validation, sanitizers, state machines | Every commit |
| Integration | Authorization suite (every row in the matrix), upload suite, lockout, revocation, headers | Every PR |
| End-to-end | Negative journeys NS-01 to NS-12 through the UI and API | Every PR touching auth, projects, or submissions; nightly |
| Static and dependency | SpotBugs, CodeQL, OWASP Dependency-Check, npm audit, gitleaks | Every PR |
| Manual | Review of OWASP ASVS Level 1 checklist at Phase 10 | Phase 10 |

## 13. Known limitations (accepted for MVP)

Recorded in [RISKS.md](RISKS.md): no malware scanning (R-07), single-node rate limiting (R-09), no break-glass access (ADR-007), no MFA (R-10). Each has a planned P1 or P2 mitigation or an explicit owner acceptance requirement.

## 14. Incident response (minimal)

1. Contain: rotate `CAMPUSHUB_JWT_SECRET` (invalidates all tokens) and disable the affected account.
2. Assess: query `audit_events` by actor, target, and time window.
3. Recover: restore from the last verified backup if integrity is in doubt (OPERATIONS §6).
4. Record: an entry in IMPLEMENTATION_LOG and, for a data exposure, a note to the institution's data protection contact (owner-provided; OD-9 covers policy owner).
