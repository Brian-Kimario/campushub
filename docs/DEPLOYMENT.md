# CampusHub — Deployment

**Purpose:** Defines the environments, the configuration contract, the hosting options and the criteria used to choose among them (OD-8), the migration and release procedure, the smoke tests, and the rollback procedure.
**Scope:** Local development, CI, staging (demo), and production (the institution's demonstration host). Operational routines after deployment are in [OPERATIONS.md](OPERATIONS.md).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [ARCHITECTURE.md](ARCHITECTURE.md) §10 and §12, [CI_CD.md](CI_CD.md), [SECURITY.md](SECURITY.md) §7 and §9, [PRODUCT.md](PRODUCT.md) (A-7, OD-8), [DECISIONS.md](DECISIONS.md) ADR-014.

## 1. Environments

| Environment | Purpose | Where | Data | Seed |
|---|---|---|---|---|
| Local | Development | Developer machine with Docker Compose for MySQL and the Vite dev server | Local only | Optional (`CAMPUSHUB_DEV_SEED_ENABLED=true` in the dev profile) |
| CI | Verification | GitHub-hosted runner with Testcontainers | Generated per test run | Generated per test |
| Staging | Pre-release check and rehearsal | The same host type as production, separate database and file volume | Synthetic only | Demo accounts (SECURITY §9) |
| Production (demo) | Capstone demonstration and handoff | Host chosen under §2 | Synthetic only unless the institution provides a data policy (OD-9) | Demo accounts only if the institution approves; otherwise administrator-provisioned accounts |

No real student data enters any environment until the institution provides a data policy and the security review in SECURITY §12 is complete.

## 2. Hosting options and selection (OD-8)

ADR-014 keeps the choice open until Phase 10. The options below are the candidates. The Phase 10 decision records the selected option and the evidence against the criteria.

| Option | Description | Strengths | Weaknesses |
|---|---|---|---|
| A. Single virtual machine with Docker Compose | One VM with reverse proxy, API container, MySQL container, file volume, and the static SPA | Lowest cost; full control of volumes and backups; long-running Java process supported; simplest rollback | The operator maintains the OS, patches, and backups |
| B. Managed container platform with managed MySQL | API as a container service; managed MySQL with automated backups; object or volume storage for files | Less host maintenance; managed backups and TLS | Higher cost; file storage needs the local volume adapter or an extra adapter (ARCHITECTURE §7) |
| C. Platform-as-a-service with managed database | Buildpack or container deploy with a managed database add-on | Fast setup | Limits on disk for uploads; limits on long-running background work for Phase 8; less control over the migration user |

Selection criteria, each scored pass or fail in the Phase 10 record:

1. Supports a long-running Java 21 process and health checks for liveness and readiness (blueprint §17).
2. Provides persistent MySQL 8 with automated daily backups and a documented restore path.
3. Provides a persistent volume (or equivalent) for submission files that survives redeploys, sized for at least 5 GB.
4. Supports HTTPS with a managed or automatic certificate, and allows the security headers in SECURITY §7.
5. Supports two database users, so the application user has no DDL rights (SECURITY §9).
6. Keeps the previous image available for rollback (§6).
7. Keeps secrets out of the image and the build log (CI_CD §6).
8. Fits the budget provided under OD-8 for the demonstration period.
9. Meets the institution's hosting restrictions under A-7 (self-hosted or single managed VM).
10. Can be shut down or paused without losing data.

The default recommendation is option A unless the budget covers option B, because option A meets every criterion and keeps the file volume and backups under the team's control. Option C is excluded for file storage and Phase 8 background work unless those requirements are dropped by ADR. Vercel or any static-only host is not used for the API (blueprint §17).

## 3. Configuration contract

All configuration comes from environment variables. The names are the same as in `.env.example`. Startup validation refuses placeholder values (those beginning `replace-with-` or `change-me`) and refuses `CAMPUSHUB_DEV_SEED_ENABLED=true` in the production profile (SECURITY §9).

| Variable | Required | Default | Notes |
|---|---|---|---|
| `CAMPUSHUB_DB_HOST`, `CAMPUSHUB_DB_PORT`, `CAMPUSHUB_DB_NAME` | Yes | — | MySQL 8 connection |
| `CAMPUSHUB_DB_APP_USER`, `CAMPUSHUB_DB_APP_PASSWORD` | Yes | — | Runtime user with DML rights only |
| `CAMPUSHUB_DB_MIGRATION_USER`, `CAMPUSHUB_DB_MIGRATION_PASSWORD` | Yes at deploy time | — | Used only by the migration step (§5); never by the running application |
| `CAMPUSHUB_JWT_SECRET` | Yes | — | HS256 key, at least 32 bytes; generated per OPERATIONS §4 |
| `CAMPUSHUB_JWT_ISSUER` | No | `campushub` | Must match the token `iss` claim (SECURITY §3) |
| `CAMPUSHUB_JWT_AUDIENCE` | No | `campushub-api` | Must match `aud` |
| `CAMPUSHUB_JWT_ACCESS_TTL_MINUTES` | No | `15` | Access token lifetime (ADR-003) |
| `CAMPUSHUB_CORS_ALLOWED_ORIGINS` | Yes | `http://localhost:5173` in dev only | Exact origins; no wildcard |
| `CAMPUSHUB_STORAGE_ROOT` | Yes | `/var/campushub/storage` | Mount point of the file volume; must be writable by the application |
| `CAMPUSHUB_UPLOAD_MAX_FILE_BYTES` | No | `20971520` | 20 MiB (SECURITY §5) |
| `CAMPUSHUB_UPLOAD_MAX_FILES_PER_VERSION` | No | `5` | SECURITY §5 |
| `CAMPUSHUB_INSTITUTION_TIMEZONE` | No | `UTC` | IANA name; OD-6 |
| `CAMPUSHUB_KAFKA_ENABLED` | No | `false` | Phase 8 only (ADR-011) |
| `CAMPUSHUB_KAFKA_BOOTSTRAP_SERVERS` | If Kafka enabled | — | Phase 8 only |
| `CAMPUSHUB_DEV_SEED_ENABLED` | No | `false` | Refused in production |

Configuration changes are part of a release (§5). A change to a secret follows OPERATIONS §4.

## 4. Release artifacts

- **API image:** Spring Boot executable jar on a pinned JRE 21 base image. Runs as a non-root user. Exposes port 8080 inside the container only.
- **Web assets:** the Vite production build (`dist/`). Served as static files by the reverse proxy, with long cache lifetimes for hashed assets and no cache for `index.html`.
- **Reverse proxy:** Caddy or Nginx, chosen in Phase 10 (ARCHITECTURE §3). Terminates TLS, serves the SPA, forwards `/api` to the API, sets request-size limits that match the 20 MiB file limit plus form overhead, and sets the security headers from SECURITY §7.
- **Database:** MySQL 8 with the data directory on a persistent volume.
- **File volume:** mounted at `CAMPUSHUB_STORAGE_ROOT`. Holds only submission and certification files.

## 5. Migration and release procedure

### 5.1 Migration strategy

- Migrations are Flyway versioned scripts (`V1` to `V8`, DATA_MODEL §11). They are forward-only. A migration that has been applied to any shared environment is never edited; a correction is a new migration (ADR-002).
- Migrations run as a separate step before the new API version starts, using `CAMPUSHUB_DB_MIGRATION_USER`. The API process uses `CAMPUSHUB_DB_APP_USER`, which has no DDL rights.
- Destructive changes (dropping a column or table) use expand and contract across two releases: first add and dual-write, then remove after the old version is retired. A destructive change in a single release requires a DECISIONS entry and a backup taken immediately before the migration.
- Before every migration in staging or production, the procedure takes a database backup (OPERATIONS §6) and records its name in the deployment log.

### 5.2 Release steps

1. Confirm the main pipeline for the commit passed and that the digest is recorded (CI_CD §2.2).
2. Take a pre-deployment backup of the database and the file volume (OPERATIONS §6). Record the backup identifier.
3. Put the site into maintenance mode when the migration is not backward compatible with the running version. Otherwise continue without downtime.
4. Run the migration step with the migrator credentials and the new digest. Stop if it fails (§6).
5. Start the new API container with the digest. Wait for readiness (OPERATIONS §2) to report UP.
6. Deploy the web assets for the same commit.
7. Run the smoke tests in §8.
8. Leave the previous API digest and web assets available for the rollback window (7 days or the next release, whichever is first).
9. Record the release in IMPLEMENTATION_LOG and CHANGELOG.

### 5.3 Promotion

- Staging deploys the digest from the main pipeline automatically.
- Production uses the same digest after manual approval in the `production` environment (CI_CD §2.2). The approver checks the staging smoke results first.
- Production deployments are made during an announced window when OPERATIONS §9 applies.

## 6. Rollback

Rollback is decided by the release owner within 30 minutes of a failed smoke test or a readiness failure that persists for more than 5 minutes. Use this procedure:

1. **Stop the new release.** Stop the new API container. Do not delete it yet, so it can be inspected.
2. **Decide on the data.**
   - If the migration did not run, or failed before changing data (Flyway reports the failed version and rolls back its transaction where MySQL allows), redeploy the previous API digest. Done.
   - If the migration ran and the previous version is compatible with the new schema (expand phase), redeploy the previous API digest. Done.
   - If the migration ran and the previous version is not compatible, restore the database from the pre-deployment backup (OPERATIONS §6), then redeploy the previous API digest. Restore also resets the file volume to the backup point if files were uploaded after the backup, after the release owner confirms the file list.
3. **Restore the web assets** of the previous commit.
4. **Verify.** Run the smoke tests in §8 against the previous version.
5. **Record.** Add the rollback to IMPLEMENTATION_LOG with the time, the trigger, the digests, the backup used, and any data written between backup and rollback, which is listed for manual follow-up.

The ARCHITECTURE §12 failure mode "migration fails" points here. A failed migration never leaves the application running against a partially migrated schema: the new API does not start until the migration step succeeds.

Canary releases are not used. A canary needs platform traffic shifting and verified rollback (blueprint §14 Phase 10); if the chosen host does not provide both, the release is a stop-and-start deployment.

## 7. Post-deployment verification

- The deployed golden path GP-1 to GP-3 is run in staging before each production release, and in production once after the release, using the demo accounts only if the institution approved them.
- No secrets are present in the image, the build log, or the response headers. A release check greps the image filesystem for placeholder values and for the patterns gitleaks uses.

## 8. Smoke tests

Run after every deployment and rollback. Each check is a scripted HTTP request. Any failure stops the release.

| Check | Expected |
|---|---|
| `GET /actuator/health/liveness` | 200 `{"status":"UP"}` |
| `GET /actuator/health/readiness` | 200 `{"status":"UP"}` (database and storage reachable) |
| `GET /` | 200 with the SPA `index.html` and the security headers from SECURITY §7 |
| `POST /api/v1/auth/login` with a smoke account | 200 with an access token; a wrong password returns 401 `INVALID_CREDENTIALS` |
| `GET /api/v1/catalog/departments` with the token | 200 with the paginated envelope |
| Upload a 1 KiB PNG to a test project, download it, compare bytes | Same bytes; `Content-Disposition: attachment` |
| `POST /api/v1/auth/logout`, then reuse the token | 401 |

The smoke account is created by the release procedure for staging and is disabled after the release, unless the institution keeps the demo accounts.

## 9. Operational limits of the chosen topology

- Single API instance. Rate limiting is per node (R-09). The demo host runs one API container.
- The file volume is a single disk. Its capacity alert is in OPERATIONS §8.
- Recovery objectives are stated in OPERATIONS §6.

## 10. Phase 10 gate

Phase 10 is complete when:

- The hosting option is chosen against §2 and recorded in DECISIONS.
- A deployment to the selected host passes the smoke tests in §8.
- The deployed GP-1 to GP-3 golden path is verified (blueprint §14 Phase 10).
- A rollback is rehearsed in staging with the procedure in §6.
- A restore from a test backup is completed and recorded (OPERATIONS §6; ARCHITECTURE §11).
- The OWASP ASVS Level 1 review in SECURITY §12 is complete.
- No embedded secrets are found by the release check in §7.
