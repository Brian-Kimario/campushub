# CampusHub — CI/CD

**Purpose:** Defines the pipelines, the blocking gates, the pull request review checklist, image tagging, and the secret and action-pinning rules.
**Scope:** GitHub Actions for build, test, scan, image publishing, and promotion. Deployment targets are in [DEPLOYMENT.md](DEPLOYMENT.md).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [TEST_STRATEGY.md](TEST_STRATEGY.md) (what the test steps prove), [SECURITY.md](SECURITY.md) §9–§10 and §12, [DEPLOYMENT.md](DEPLOYMENT.md), [OPERATIONS.md](OPERATIONS.md).

## 1. Principles

1. **One command, two places.** Each CI job calls a script that a developer can run locally (`scripts/verify`, TEST_STRATEGY §2). CI adds no test logic of its own.
2. **Blocking by default.** A failed gate blocks merge or promotion. A gate is made advisory only by an ADR that names the owner and the expiry date.
3. **No secrets in logs or artifacts.** Secrets are supplied through GitHub environment secrets and are masked. Logs and artifacts are scanned for them (gitleaks, §3).
4. **Pinned inputs.** Third-party actions are pinned to full commit SHAs. Dependencies are pinned by lock files (SECURITY §10).
5. **The build that was tested is the build that is deployed.** Promotion uses the same image digest produced by the main pipeline; nothing is rebuilt between staging and production.

## 2. Pipelines

### 2.1 Pull request pipeline

Runs on every pull request and on every push to a PR branch. Target duration: 15 minutes or less on the GitHub-hosted runner.

| Order | Job | Steps | Blocking |
|---|---|---|---|
| 1 | `secrets` | gitleaks over the diff and full history on first run | Yes |
| 2 | `frontend` | `npm ci`, ESLint with security rules, TypeScript check, Vitest with coverage, Vite build, `npm audit --omit=dev` | Yes |
| 3 | `backend` | `./mvnw verify`: compile, unit tests, web slice tests, Testcontainers persistence and migration tests, concurrency tests, ArchUnit, security suite, JaCoCo gates, SpotBugs with find-sec-bugs, OWASP Dependency-Check, OpenAPI snapshot diff | Yes |
| 4 | `e2e` | Build the packaged app with the dev seed, Playwright smoke subset (GP-1 to GP-3 smoke steps, forbidden paths, axe on primary screens, keyboard script) | Yes |
| 5 | `image` | Docker build of the API image and the static web image; no push | Yes |
| 6 | `summary` | Collects reports, writes the gate table to the job summary | Yes |

The `e2e` and `image` jobs start after `frontend` and `backend` pass, to save time on broken changes.

### 2.2 Main branch pipeline

Runs on every push to `main` (after merge).

| Order | Job | Steps | Blocking |
|---|---|---|---|
| 1 | Repeats §2.1 jobs 1–5 | Same commands, same gates | Yes |
| 2 | `e2e-full` | Full Playwright suite (all USER_STORIES golden paths, all forbidden paths, full axe matrix per primary screen, keyboard scripts) | Yes |
| 3 | `codeql` | CodeQL analysis for Java and JavaScript | Yes |
| 4 | `publish` | Push images to the registry with the immutable tags in §5 | Yes |
| 5 | `deploy-staging` | Deploy the digest to staging (DEPLOYMENT §5), run smoke tests (DEPLOYMENT §8) | Yes |
| 6 | `promote-production` | Waits for approval in the `production` GitHub environment, then deploys the same digest (DEPLOYMENT §5) | Manual approval |

### 2.3 Release pipeline

Runs on a Git tag matching `v<major>.<minor>.<patch>`. It publishes the release image with the version tag and creates the release notes from CHANGELOG. It does not rebuild the image: it re-tags the digest from the main pipeline that passed for the tagged commit. A tag on a commit with no successful main pipeline fails.

## 3. Quality gates

Each gate lists what it proves and what fails it.

| Gate | Tool | What fails the build |
|---|---|---|
| Secret scan | gitleaks | Any finding not in the allow list (the allow list is a committed file with a reason per entry; placeholders in `.env.example` are allowed by pattern) |
| Lint and types | ESLint, TypeScript | Any error. Warnings are reported; zero warnings are not required |
| Frontend tests | Vitest, React Testing Library | Any failing test, or coverage below the TEST_STRATEGY §3 threshold |
| Backend tests | JUnit 5, MockMvc, Testcontainers | Any failing test, or any skipped or disabled test tagged `security` (SM-5) |
| Coverage | JaCoCo | Line coverage below 80% for application and domain packages; branch coverage below 90% for policy packages |
| Architecture | ArchUnit | Any module dependency rule violation or cycle (ARCHITECTURE §4.1) |
| Migrations | Flyway on Testcontainers | A migration that fails on an empty database; a migration that fails on a database at the previous version; Hibernate `validate` failure |
| API contract | OpenAPI snapshot diff | An unexplained difference between the generated OpenAPI and the committed snapshot (API_CONTRACT §15). A deliberate change updates the snapshot in the same pull request |
| Static analysis | SpotBugs with find-sec-bugs; ESLint security rules | Any high-severity finding without an accepted exception |
| Dependency vulnerabilities | OWASP Dependency-Check; `npm audit --omit=dev` | Critical or high finding without an accepted exception (SM-4) |
| Code scanning | CodeQL | Any high or critical alert on `main` |
| Accessibility | axe-core through Playwright | Any WCAG 2.1 AA violation on a primary screen without a recorded exception (TEST_STRATEGY §5.2) |
| Keyboard | Playwright scripts | Any keyboard trap, unreachable control, or focus lost after a dialog closes |
| Responsive | Playwright at 320 px | Horizontal page scroll outside a designated scroll container |
| Container | Docker build; Trivy scan of the built images | Critical vulnerability with a fix available, without an exception |
| Smoke after deploy | Scripted HTTP checks (DEPLOYMENT §8) | Any failed check on staging; production promotion stops |

Exceptions are recorded in `docs/RISKS.md` with the item, the reason, the owner, and an expiry date no more than 30 days away. An exception without an expiry is rejected in review.

## 4. Pull request review checklist

Reviewers use this checklist for every pull request. The authorization checks in item 1 are required by AUTHORIZATION_MATRIX §6.

1. **Authorization.** Every new or changed endpoint has a row in [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md) §3, the same row in [API_CONTRACT.md](API_CONTRACT.md), and a test in the security suite. A change to a row changes all three and has a DECISIONS entry in the same pull request.
2. **Denial mapping.** Denials follow ADR-006: 404 for objects the caller cannot see, 403 only for permitted roles failing an object condition. Role-only routes return 403 for every ID.
3. **Error codes.** Each new error code is in API_CONTRACT §3 and in the message table in UX_UI §9.
4. **State changes.** Each new or changed transition is in STATE_MACHINES with its guard, permission, and audit event. Same-state requests are refused as STATE_MACHINES §1 says.
5. **Persistence.** Schema changes come only from a new Flyway migration (NFR-DATA-01). DATA_MODEL is updated in the same pull request, including the migration table in its §11. Destructive changes follow DEPLOYMENT §5.
6. **Concurrency.** Mutable entities that a user can edit concurrently carry `@Version` and their updates return `STALE_VERSION` on conflict.
7. **Input.** Every new write and list parameter is validated, with sort allowlists for list endpoints (NFR-SEC-04).
8. **Files.** Any upload or download goes through the `FileStorage` port with the validation in SECURITY §5.
9. **Logging and audit.** No secret, token, password, file content, or full email at INFO (SECURITY §8). Audited actions write an audit event with allow-listed metadata keys.
10. **Caching.** Only catalog reads are cached. Private data is not added to a cache (ARCHITECTURE §8).
11. **Accessibility and copy.** New screens meet UX_UI §4 (five states) and §10 (accessibility). New user-facing text follows UX_UI §14.
12. **Configuration.** New settings are added to `.env.example` with a placeholder value, to DEPLOYMENT §3, and to the startup validation if they are secrets or have no safe default.
13. **Tests.** The change has tests at the lowest layer that proves the behavior (TEST_STRATEGY §1). No security test is disabled.
14. **Documentation.** Affected documents are updated, with IMPLEMENTATION_LOG updated for phase-level changes and CHANGELOG updated for user-visible changes.
15. **Dependencies.** New dependencies have a reason in the pull request description, a license compatible with the course policy (OD-9), and a committed lock file change.

The reviewer records the checklist result in the pull request as a comment. A pull request that changes an authorization row without items 1 and 2 is not approved.

## 5. Images, tags, and digests

- The API image is one image built from the Spring Boot jar on Eclipse Temurin 21 JRE (a pinned digest). The web image serves the built SPA behind the reverse proxy or is the static files only, as DEPLOYMENT §4 decides.
- Each main-branch build produces the tag `sha-<short commit SHA>` and the digest. Deployments reference the digest.
- The tag `latest` is published for convenience only. It is never the only deployment identifier and is not used by any deployment script (blueprint §16).
- Release tags add `v<version>` on the same digest.
- Images carry labels for the source commit, build time, and version.
- The registry keeps the last 20 `sha-` tags. Release tags are kept indefinitely.

## 6. Secrets in CI

- Secrets are stored as GitHub environment secrets (`staging`, `production`) or repository secrets for build-only values. No secret is stored in a workflow file.
- Production secrets are available only to the `promote-production` job, which uses the protected `production` environment with required reviewers (§2.2).
- Each workflow sets `permissions: contents: read` at the top. A job requests `packages: write` only in `publish`.
- The deploy job uses a deploy key or a short-lived token for the host. The key is restricted to the deploy command.
- Logs are masked for every secret. A step that prints environment variables is rejected in review.
- The gitleaks allow list is reviewed in each pull request that changes it.

## 7. Action and dependency pinning

- Third-party actions are referenced by full commit SHA with a comment naming the release (SECURITY §10).
- Maven dependencies are pinned in the POM. npm dependencies are locked by `package-lock.json` and installed with `npm ci`.
- Dependency update pull requests are reviewed like any other change. A dependency update that changes a security-relevant library (JWT, password hashing, file parsing) runs the security suite twice: once before and once after the update.

## 8. Failure handling

| Situation | Action |
|---|---|
| A gate fails on a pull request | The author fixes it or requests a recorded exception. Merge is blocked. |
| A gate fails on `main` | The main pipeline stops before publish. The team fixes forward on `main` within one working day or reverts the commit. |
| Staging smoke tests fail | Production promotion does not start. The staging deployment is rolled back per DEPLOYMENT §6. |
| A flaky test | Handled per TEST_STRATEGY §10. |
| Registry or runner outage | Pipelines are re-run when service returns. No manual image push is allowed; the digest must come from a pipeline run. |

## 9. Timing and cost

- PR pipeline target: 15 minutes or less. If it exceeds 25 minutes for three consecutive pull requests, the slowest job is reviewed and the cause is recorded in IMPLEMENTATION_LOG.
- Caches: Maven and npm caches keyed by lock file hashes. Testcontainers images pulled once per runner.
- Playwright runs with two shards on PRs and four on `main`.

## 10. Verification of this document

- Phase 1 creates the workflows and proves each gate by introducing a deliberate failure in a branch for each gate class (secret, failing test, skipped security test, architecture violation, migration failure, axe violation, OpenAPI diff). Results are recorded in IMPLEMENTATION_LOG.
- The review checklist in §4 is copied into the pull request template in Phase 1.
