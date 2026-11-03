# CampusHub — Scope and Change Control

**Purpose:** Define what is in the MVP, what is optional, what is excluded, and how scope changes are approved.
**Scope:** Product scope for all phases; reconciles blueprint §3 with phase placement in [PHASE_PLAN.md](PHASE_PLAN.md).
**Last reviewed:** 2026-11-03 (Phase 0)

## 1. Priority tiers

| Tier | Meaning | Rule |
|---|---|---|
| P0 | Must deliver for the capstone. | Blocks the capstone if missing. |
| P1 | Deliver only after P0 gates pass. | May slip without affecting the golden path. |
| P2 | Stretch. | Attempt only after P0 and P1 are stable. Never trade security or P0 completeness for P2. |

## 2. Scope table

| Capability | Tier | Phase | Notes |
|---|---|---|---|
| Authentication (JWT access token, logout, forced password change) | P0 | 2 | ADR-003 |
| Role-aware navigation and route guards | P0 | 3 | Guards are UX only (ADR-006) |
| Student and faculty profiles | P0 | 3 | |
| Admin account management | P0 | 3 | No self-registration |
| Academic catalog (departments, terms, courses) | P0 | 3 | Cached reads (ADR-010) |
| Project proposals, status machine, supervisor assignment | P0 | 4 | |
| Team invitations and membership | P0 | 4 | Accepted membership required |
| Milestones and deadline enforcement | P0 | 5 | |
| Tasks: create, assign, transition, filter, version lock | P0 | 5 | |
| Task comments | P1 | 5 | Simple append-only comments |
| Calendar aggregation | P0 | 5 | ADR-013 |
| Submissions, versions, protected files | P0 | 6 | ADR-008, ADR-018 |
| Reviews and feedback | P0 | 6 | One decision per version |
| Audit log (write and admin read) | P0 | 2–6 | |
| Student and faculty and admin dashboards | P0 | 7 | Derived from real data only |
| Project activity feed | P1 | 5 | Reads `project_activity` |
| Skill catalog and student skills | P1 | 7 | Owner-only (OD-5) |
| Certifications with optional evidence file | P1 | 7 | Owner-only (FR-CERT-03) |
| Refresh tokens with rotation and reuse detection | P1 | 2 (deferred) | ADR-004 |
| Notifications via outbox and Kafka | P2 | 8 | Optional; disabled by default |
| Kafka DLQ demonstration | P2 | 8 | Part of Phase 8 gate, stretch |
| Performance benchmarking report | P2 | 9 | Measurement only |
| Canary release | P2 | 10 | Only if the host supports traffic shifting |
| Advanced charting | P2 | — | Not planned; tables suffice |
| Fine-grained notification preferences | P2 | — | Not planned |
| Organization-specific policy configuration | P2 | — | Not planned |
| Break-glass administrator access to private files | Out | — | Deferred pending policy (ADR-007) |
| Faculty visibility of student certifications or skills | Out (MVP) | — | Requires policy decision OD-5 |
| Public self-registration | Out | — | |
| Email delivery and password-reset emails | Out | — | No mail service in MVP |
| Numeric grading and gradebook export | Out | — | |
| Plagiarism detection, generative AI grading | Out | — | Non-goals per PRODUCT §6 |
| Multi-tenancy | Out | — | Single institution |
| Native mobile apps | Out | — | Responsive web only |

## 3. Reconciliation with the master blueprint

The blueprint places some items in both §3 and §14. Resolutions:

- **Dashboards** are P0 in §3 and delivered in Phase 7. Phase 7 is the final integration point for dashboard data; the basic cards in earlier phases may show counts only. The P0 tier stays.
- **Certifications** are P1 in §3 but §1.2 describes them as core. Resolution: P1, scheduled in Phase 7 after the P0 gates. If P0 slips, certifications are the first item cut.
- **Kafka**: §2 names it as optional; §3 puts DLQ in P2; §14 Phase 8 requires a DLQ demonstration. Resolution: all of Phase 8 is P2. Phase 8 is skipped entirely if it threatens P0 delivery (ADR-011).
- **Refresh tokens**: §3 lists them as P1; §14 Phase 2 asks for a decision. Resolution: decided and documented in Phase 2 (ADR-004), implemented in P1.

## 4. Change control

Every scope change follows this procedure:

1. **Request** recorded in [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md) with the requester, reason, and affected requirement IDs.
2. **Classify** the change:
   - *Additive P1/P2 item:* allowed when P0 gates are green; recorded in CHANGELOG.
   - *P0 addition or change to a P0 requirement:* requires an ADR in [DECISIONS.md](DECISIONS.md) and owner approval if it changes the golden path.
   - *Security, data-model, or authorization change:* requires an ADR and updates to AUTHORIZATION_MATRIX, SECURITY, DATA_MODEL, and tests in the same change.
   - *Removal of a P0 item:* requires owner approval and an ADR stating the impact on SYLLABUS_MAPPING.
3. **Update** SCOPE, PHASE_PLAN, and DECISIONS together.
4. **Verify** that the golden path and negative journeys still pass.

## 5. Scope guardrails

- A feature is not added because a library is interesting. Syllabus concepts must map to a working use case (SYLLABUS_MAPPING).
- No endpoint exists only to mirror a table (blueprint §9).
- No dashboard metric without a defined denominator and a data source (FR-DASH-04).
- Breadth is limited to the 25 resources in the verification catalog only where they map to an endpoint in API_CONTRACT. Other catalog resources are marked "not applicable" in TEST_STRATEGY.
