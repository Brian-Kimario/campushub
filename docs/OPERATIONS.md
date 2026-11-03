# CampusHub — Operations Runbook

**Purpose:** The routines an operator follows after deployment: health monitoring, logs, secrets and token key rotation, scheduled jobs, backups and restore, incidents, and capacity.
**Scope:** The production (demonstration) host and its staging counterpart. Deployment steps are in [DEPLOYMENT.md](DEPLOYMENT.md).
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [SECURITY.md](SECURITY.md) §8, §9, §14, [DEPLOYMENT.md](DEPLOYMENT.md), [ARCHITECTURE.md](ARCHITECTURE.md) §10 and §12, [RISKS.md](RISKS.md).

## 1. Ownership

| Role | Responsibility |
|---|---|
| Operator | Runs the routines in this file; watches health and capacity; performs backups and restore drills |
| Release owner | Decides rollback (DEPLOYMENT §6); approves production promotion (CI_CD §2.2) |
| Institution contact | Receives data-exposure notices (SECURITY §14); named in OD-9 and OD-8 records |

For the capstone, one person may hold all three roles. The runbook still records each action so a reviewer can follow it.

## 2. Health and readiness

| Endpoint | Public | Returns | Use |
|---|---|---|---|
| `GET /actuator/health/liveness` | Yes | `{"status":"UP"}` when the process runs | Restart decisions by the platform |
| `GET /actuator/health/readiness` | Yes | `{"status":"UP"}` or 503 when the database or the storage root fails its check | Traffic routing; deployment waits for UP |
| `GET /actuator/health` | Public response has no component detail (NFR-OPS-01) | Status only | Uptime monitor target |

- An external uptime check calls the readiness endpoint every minute from outside the host. Two consecutive failures raise an alert to the operator.
- The readiness check for storage writes and deletes a small probe file in `CAMPUSHUB_STORAGE_ROOT`. A failure on the probe marks the instance not ready and does not affect reads of existing files.
- Component detail is available only on the host's own loopback interface for the operator, through the management port that the reverse proxy does not expose.

## 3. Logs and request correlation

- The application writes one JSON object per line to standard output. Each line includes `timestamp` (UTC), `level`, `logger`, `requestId`, and `message`.
- Every response carries the `X-Request-Id` header. Clients may send their own ID, which the API validates (length 8–64, safe characters) or replaces.
- Logs never contain passwords, tokens, `Authorization` headers, file contents, or full email addresses at INFO (SECURITY §8). A masked form (`a***@domain`) is used.
- Logs are kept for 30 days on the host (SECURITY §8 and CI_CD §3 retention note). The host's log rotation enforces this.
- To trace a user complaint: take the `X-Request-Id` from the browser network log or the error page, search the JSON logs for it, then search `audit_events` by the time window for the same user and target (SECURITY §14 step 2).
- Access to logs is limited to the operator. Logs are not copied to external services in MVP.

## 4. Secrets and key rotation

### 4.1 Generating secrets

Generate each secret on the host or in the secret store, never in a shared chat or document:

```bash
openssl rand -base64 48   # CAMPUSHUB_JWT_SECRET (minimum 32 bytes after decoding)
openssl rand -base64 24   # database passwords
```

Store the values in the host's secret store or in an environment file readable only by the operator. `.env` is git-ignored (SECURITY §9).

### 4.2 Token signing key rotation

The MVP has one active signing key. Rotating it invalidates every access token, so every user signs in again. This is the emergency and scheduled procedure.

1. Generate a new `CAMPUSHUB_JWT_SECRET` (§4.1).
2. Announce the rotation to users if it is scheduled; state that everyone must sign in again.
3. Update the secret in the host's secret store.
4. Restart the API container. Migrations are not affected.
5. Verify: a request with a token signed by the old key returns 401; a new sign-in returns a token that the API accepts.
6. Record the rotation date, the reason, and the operator in IMPLEMENTATION_LOG. Never record the secret value.

Multiple signing keys (`kid`-based rotation without a forced sign-out) are not in MVP. If the institution requires them, that is a scope change under SCOPE §4 and an ADR.

### 4.3 Database password rotation

1. Set the new password for `campushub_app` in MySQL with `ALTER USER`.
2. Update `CAMPUSHUB_DB_APP_PASSWORD` in the secret store.
3. Restart the API container and verify readiness.
4. Repeat for `campushub_migrator`, which is used only during a release (DEPLOYMENT §5).

### 4.4 Incident rotation

SECURITY §14 step 1 rotates the signing key (§4.2) and disables the affected account. Database passwords are rotated when a host compromise is suspected.

## 5. Scheduled jobs

| Job | Schedule (UTC) | Work | Idempotent | Failure handling |
|---|---|---|---|---|
| Purge revoked tokens | Hourly at minute 05 | Deletes `revoked_tokens` rows where `expires_at` is before now (DATA_MODEL §3) | Yes | Logged at WARN; retried next hour |
| Expire invitations | Hourly at minute 15 | Sets PENDING invitations past their 14-day expiry to EXPIRED (STATE_MACHINES §6). Expiry is also applied when an invitation is read | Yes | Logged at WARN; the read path still enforces expiry |
| Reset lockout windows | Hourly at minute 25 | Clears lockout fields whose 15-minute window has ended (STATE_MACHINES §7) | Yes | Logged at WARN |
| Outbox relay (Phase 8 only) | Every 5 seconds while enabled | Publishes pending outbox rows (STATE_MACHINES §9) | Yes, with retry and backoff | Disabled by default (`CAMPUSHUB_KAFKA_ENABLED=false`) |
| Backup | Daily at 02:00 | Database and file volume backup (§6) | Each run writes a new named backup | Alert on failure |

- Jobs run inside the API process under a scheduler lock in the database, so a second instance (if one is ever added) does not run them twice.
- The job log lines carry the job name and the number of rows affected.
- A job that has failed for two consecutive runs raises an alert.

## 6. Backups, restore, and recovery objectives

### 6.1 What is backed up

- The MySQL database, as a logical dump with routines and triggers included, so sealed-version triggers (ARCHITECTURE §6) are restored.
- The file volume at `CAMPUSHUB_STORAGE_ROOT`, as an archive.
- The deployed image digests and the configuration template (`.env.example`). Secret values are backed up separately, in the secret store's own backup, and are never placed in the same archive as the database.

### 6.2 Schedule and retention

- Daily backup at 02:00 UTC, retained for 14 days.
- Weekly backup each Sunday, retained for 4 weeks.
- Before each release that runs a migration (DEPLOYMENT §5.2), a named backup is taken and kept until the release passes its rollback window.

### 6.3 Protection

- Each archive is encrypted at rest before it leaves the host, with a key kept outside the host (operator-held).
- Backups are copied to a second location controlled by the institution or the operator, not on the same disk as the data.
- Each archive has a SHA-256 checksum recorded in the backup log. A checksum mismatch on restore stops the restore.

### 6.4 Recovery objectives

| Objective | Target |
|---|---|
| Recovery point (RPO) | 24 hours (last daily backup) |
| Recovery time (RTO) | 2 hours from the decision to restore to readiness passing |

These are targets for the demonstration. A restore drill (§6.5) confirms the RTO; if it is exceeded, the target is changed through an ADR, not silently.

### 6.5 Restore procedure and drill

1. Stop the API container. Keep the web assets in place.
2. Create a scratch database on the same server and restore the dump into it. Verify the checksum first.
3. Restore the file archive into a new directory. Verify the checksum.
4. Check the restored data: row counts for `users`, `projects`, `submissions`, `audit_events` match the backup log; `SHOW TRIGGER` lists the sealed-version triggers; a sample of files passes the magic-byte check.
5. Switch the application to the restored database and file directory. Start the API with the image digest that matches the backup. Wait for readiness.
6. Run the smoke tests in DEPLOYMENT §8.
7. Record the restore: time started, time ready, backup used, row counts, and any gap in data since the backup.

Drill cadence: once before the Phase 10 gate, then once before the Phase 11 demonstration. The drill is performed on staging with a copy of the production backup, never by restoring over production.

## 7. Incident handling

| Severity | Definition | Response time | Examples |
|---|---|---|---|
| S1 | Data exposure, account compromise, or the site is down during a scheduled demonstration | Immediately; within 1 hour | Unauthorized file download; leaked signing key; readiness failing for more than 15 minutes |
| S2 | A core workflow is broken for some users | Within one working day | Submission upload fails for every student; invitation accept returns 500 |
| S3 | A minor defect or an isolated error | Next scheduled release | A label is wrong; one list sorts incorrectly |

Steps for S1 follow SECURITY §14:

1. **Contain.** Rotate the signing key (§4.2) if tokens may be misused; disable the affected account. If files may be exposed, take the file endpoint out of service at the reverse proxy.
2. **Assess.** Query `audit_events` by actor, target, and time window. Collect the relevant `X-Request-Id` values from the logs.
3. **Recover.** Restore from the last verified backup only if integrity is in doubt (§6.5). Otherwise fix forward.
4. **Record.** An entry in IMPLEMENTATION_LOG with the timeline, the impact, and the actions. For a data exposure, notify the institution contact the same day.

Post-incident, a short review records the cause, the detection, and the change that prevents recurrence. A recurrence risk goes to RISKS.

## 8. Capacity and monitoring

| Signal | Threshold | Action |
|---|---|---|
| File volume usage | 80% used | Investigate uploads; expand the volume or remove a closed term's files only after the institution approves (no automatic deletion) |
| Database disk usage | 80% used | Expand the volume; check the audit table growth |
| API memory | Above 85% of the container limit for 10 minutes | Investigate; increase the limit within the host's capacity |
| Database connections | Above 80% of the configured pool for 5 minutes | Check for slow queries (TEST_STRATEGY §6) |
| List latency at demo scale | p95 above 300 ms (SM-6) | Record in the measurement log and RISKS |
| Failed logins | More than 50 per hour from one address | Review the audit log; block at the reverse proxy if abusive |

Monitoring in MVP is the uptime check (§2), the job alerts (§5), and host metrics. No external observability service is required.

## 9. Maintenance windows and demonstration day

- Production changes are made in a window announced at least 24 hours ahead. A window is not used during a scheduled demonstration.
- Demonstration day checklist:
  1. Readiness UP and the uptime check green for 24 hours before the session.
  2. Demo accounts are present and their passwords reset from the secret store (not from documentation).
  3. The golden path (DEMO_SCRIPT) is run once in the 2 hours before the session.
  4. A recent backup exists (within 24 hours) and the restore runbook is at hand.
  5. No release is in progress. The release owner is reachable.
  6. The file volume has at least 20% free.

## 10. Access control for operators

- Operator access to the host is by named accounts with key-based login. Shared accounts are not used.
- The database admin account is used only for maintenance. The application and migrator accounts are used by the services only.
- Operator actions on production are recorded in IMPLEMENTATION_LOG with the date and the reason.
- Access is removed when the operator role changes, and the removal is recorded.

## 11. Verification of this runbook

- Phase 10: each routine in §4, §5, and §6 is executed once on staging and the result is recorded in IMPLEMENTATION_LOG. The restore drill in §6.5 is completed.
- Phase 11: the runbook is reviewed against the final deployment, and any step that does not match the host is corrected in this file.
