# CampusHub — Risk Register

**Purpose:** The single list of project risks: what could go wrong, how severe it is, what mitigates it, who accepts what remains, and its current status.
**Scope:** Delivery, security, data, operations, and documentation risks for the capstone MVP and its optional phases. Baseline risks come from blueprint §19; other risks come from decisions made in Phase 0.
**Last reviewed:** 2026-11-03 (Phase 0)
**Related:** [DECISIONS.md](DECISIONS.md), [SECURITY.md](SECURITY.md) §14, [OPERATIONS.md](OPERATIONS.md) §7, [PRODUCT.md](PRODUCT.md) §8, [SCOPE.md](SCOPE.md), [PHASE_PLAN.md](PHASE_PLAN.md).

## 1. How to read this register

- **Severity** is the blueprint's scale for baseline risks (Critical, High, Medium) and this document's scale for new ones. Severity is the impact if the risk occurs, before mitigation.
- **Status** is one of *Open* (no mitigation in place yet), *Mitigated* (a mitigation is in place and has evidence), *Accepted* (the owner has accepted the residual risk in writing in IMPLEMENTATION_LOG), or *Closed*.
- **Phase** is the phase by which the mitigation must be present.
- A risk is added when a document names a limitation that could cause harm. A risk is never removed by deleting it; it moves to *Closed* with the reason.
- IDs R-01 to R-06 and R-08 and R-11 to R-13 are the blueprint §19 baseline in table order. IDs R-07, R-09, R-10, R-16, R-17, and R-19 are cited by other documents and keep their numbers. R-14, R-15, R-18, R-20, and R-21 were identified in Phase 0. R-22 and R-23 were identified in the Phase 1 review.

## 2. Baseline risks (blueprint §19)

| ID | Risk | Severity | Mitigation | Phase | Status |
|---|---|---|---|---|---|
| R-01 | Scope creep prevents delivery | High | P0/P1/P2 gates in SCOPE; MVP scope frozen at the Phase 0 review; any addition needs an ADR and a scope change entry | 0–11 | Open |
| R-02 | Broken object-level authorization | Critical | Centralized object-policy checks (ADR-006); three-layer checks (AUTHORIZATION_MATRIX §1); negative tests for every matrix row (TEST_STRATEGY §4) | 2–9 | Open |
| R-03 | JWT or token leakage | High | HS256 with 15-minute lifetime (ADR-003); tokens never logged (SECURITY §8); revocation list (ADR-019); rotation procedure (OPERATIONS §4.2) | 2 | Open |
| R-04 | Unsafe uploaded files | High | Size and count limits (ADR-008); magic-byte allowlist; private storage with random keys; `Content-Disposition: attachment` and `nosniff`; NS-07 test | 3, 6 | Open |
| R-05 | Inconsistent state transitions | High | Service transactions; guarded transitions (STATE_MACHINES §1); optimistic locking (`@Version`); MySQL triggers on sealed versions (ADR-018); transition-table tests | 3–7 | Open |
| R-06 | Kafka increases fragility | Medium | Kafka is optional, Phase 8, disabled by default (ADR-011); outbox written in the same transaction; the MVP does not depend on it | 8 | Open |
| R-08 | Overcomplicated UI state | Medium | RTK Query for server state; local state and React Hook Form for forms (ADR-009); no server data copied into slices | 1–5 | Open |
| R-11 | Database N+1 queries | Medium | Query-count tests at 5 and 50 rows (TEST_STRATEGY §6.1); fetch plans; offset pagination capped at 100 (ADR-012) | 3–9 | Open |
| R-12 | CI passes but the application fails in the demonstration | High | Seeded golden-path E2E (TEST_STRATEGY §5.1); clean-clone rehearsal (DEMO_SCRIPT §5); deploy smoke tests (DEPLOYMENT §8) | 4–11 | Open |
| R-13 | Inaccurate documentation | High | Traceability (SYLLABUS_MAPPING, AUTHORIZATION_MATRIX); cross-check at Phase 0 and each phase review; documentation standard (blueprint §18) | 0–11 | Open |

## 3. Risks identified in Phase 0

| ID | Risk | Severity | Mitigation | Phase | Status |
|---|---|---|---|---|---|
| R-07 | No malware scanning of uploaded files in MVP | High | Attachments only, never rendered inline; magic-byte allowlist; `nosniff`; a ClamAV adapter planned behind the `FileStorage` port (SECURITY §5, ADR-008). Owner acceptance is required before any real student file is accepted | 3 (mitigation); 10 (adapter if time allows) | Accepted (owner acceptance pending) |
| R-09 | Rate limits are per node, so a second API instance would weaken login throttling | Medium | One API instance in the demonstration host (DEPLOYMENT §9); a shared limiter is a future ADR if a second instance is added | 2, 10 | Accepted for MVP |
| R-10 | No multi-factor authentication | Medium | Lockout and rate limits (ADR-020); strong password policy; administrator-provisioned accounts (ADR-021). MFA is not in scope (SECURITY §3). Owner acceptance is required | 2 | Accepted (owner acceptance pending) |
| R-14 | Lockout can be used to keep a legitimate user out for 15 minutes | Low | Uniform locked response; attempts during a lock do not extend it (ADR-020); the lock clears when its 15-minute window ends (OPERATIONS §5) | 2 | Accepted (ADR-020) |
| R-15 | Hosting is not chosen until Phase 10, so deployment risk is unmeasured until then | Medium | Host-neutral configuration (DEPLOYMENT §3); selection criteria fixed in DEPLOYMENT §2; default recommendation option A | 10 | Open |
| R-16 | Docker daemon is unavailable on the development machine, so Testcontainers cannot run | High | Verify Docker before Phase 1 starts; CI runner has Docker; Phase 1 cannot close without a passing Testcontainers run | 1 | Mitigated (Phase 1): Docker server 29.8.2 and Compose v5.5.0 verified on the development machine; Testcontainers MySQL test passed. CI runner Docker not yet verified (no workflow exists) |
| R-17 | Institution time zone is unconfirmed (OD-6), so deadline display may be wrong for users | Medium | UTC storage (ADR-013); institution zone is configurable (`CAMPUSHUB_INSTITUTION_TIMEZONE`); DST boundary tests with fixed instants | 5 | Open until OD-6 is answered |
| R-18 | Single file volume fills, causing upload failures during the demonstration | Medium | 80% capacity alert (OPERATIONS §8); file volume at least 20% free on demonstration day (OPERATIONS §9); per-file and per-version limits (ADR-008) | 3, 10 | Open |
| R-19 | Without refresh tokens, users must sign in again every 15 minutes | Low | Access lifetime is configurable; refresh tokens are designed in Phase 2 and implemented in P1 (ADR-004) | 2 (design); P1 (build) | Accepted for MVP |
| R-20 | Real student data enters a non-production environment before a data policy exists | High | Synthetic data only (DEPLOYMENT §1); data policy and SECURITY §12 review required before any real data (OD-9) | 0–11 | Open |
| R-21 | Owner decisions OD-1 to OD-10 are not answered in time, so a default is applied without confirmation | Medium | Each default is stated in PRODUCT §8 and recorded in DECISIONS §5; the Phase 0 review lists which are still open | 0–2 | Open |
| R-22 | The CI workflow has never run on GitHub (no remote), so the runner's Docker support for Testcontainers and the workflow's real behaviour are unverified | High | Workflow validated locally with actionlint and `./mvnw -B verify`; first push to a GitHub remote, with owner approval, must show a green run before Phase 1 counts as CI-verified (IMPLEMENTATION_LOG §9 follow-up) | 1, 10 | Open |
| R-23 | No secret scanning runs in CI, so a secret committed later would not be caught automatically | Medium | Local gitleaks scan recorded in IMPLEMENTATION_LOG §9 follow-up; add a secret-scanning job before the first push or in Phase 10 | 1, 10 | Open |

## 4. Review

- Each phase review (PHASE_PLAN) re-reads this register. A risk whose mitigation is not in place by its phase is moved to *Open* with a note, and the phase gate records it.
- A new risk is added in the same change that identifies it.
- Owner acceptance of an *Accepted* risk is recorded in IMPLEMENTATION_LOG with the date and the name of the accepting person. No acceptance is recorded on the team's behalf.
