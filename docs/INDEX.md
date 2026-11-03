# CampusHub Documentation Index

**Purpose:** Navigation map and authority order for all CampusHub specifications.
**Scope:** Every canonical document in `/docs`.
**Last reviewed:** 2026-11-03 (Phase 0)

## Authority order

When documents disagree, resolve the conflict in this order and record the fix in [DECISIONS.md](DECISIONS.md):

1. [DECISIONS.md](DECISIONS.md) — accepted architectural decisions (ADRs) override earlier text.
2. [SECURITY.md](SECURITY.md) and [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md) — authorization always wins for access questions.
3. [DATA_MODEL.md](DATA_MODEL.md), [STATE_MACHINES.md](STATE_MACHINES.md), [API_CONTRACT.md](API_CONTRACT.md) — schema, lifecycle, and contract; ERD is derived from DATA_MODEL.
4. [REQUIREMENTS.md](REQUIREMENTS.md), [SCOPE.md](SCOPE.md) — what must be built and in what priority.
5. All other documents, including the master blueprint.

Deviations from the master blueprint are listed in [DECISIONS.md](DECISIONS.md) (ADR-016 through ADR-024). The blueprint remains the source of intent.

## Canonical documents

| Document | Purpose |
|---|---|
| [PRODUCT.md](PRODUCT.md) | Users, problems, goals, success metrics, exclusions |
| [REQUIREMENTS.md](REQUIREMENTS.md) | Numbered functional and non-functional requirements with acceptance criteria |
| [USER_STORIES.md](USER_STORIES.md) | Role-specific stories, golden paths, negative paths |
| [SCOPE.md](SCOPE.md) | P0 / P1 / P2 scope and change control |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, module boundaries, runtime topology |
| [DATA_MODEL.md](DATA_MODEL.md) | Tables, columns, constraints, indexes, retention |
| [ERD.md](ERD.md) | Entity-relationship diagram derived from DATA_MODEL |
| [API_CONTRACT.md](API_CONTRACT.md) | Endpoint inventory, DTOs, errors, pagination, authorization |
| [SECURITY.md](SECURITY.md) | Threat model, authentication, tokens, files, secrets, logging |
| [AUTHORIZATION_MATRIX.md](AUTHORIZATION_MATRIX.md) | Actor × action × ownership rules |
| [STATE_MACHINES.md](STATE_MACHINES.md) | Project, milestone, task, submission, invitation transitions |
| [UX_UI.md](UX_UI.md) | Navigation, screens, states, accessibility, responsive rules |
| [TEST_STRATEGY.md](TEST_STRATEGY.md) | Test layers, coverage gates, security test cases, verification-catalog consolidation |
| [CI_CD.md](CI_CD.md) | Pipeline stages, gates, action pinning, image tagging |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Environments, hosting options, configuration, rollback |
| [OPERATIONS.md](OPERATIONS.md) | Health, backups, restore, logs, secret rotation, incidents |
| [SYLLABUS_MAPPING.md](SYLLABUS_MAPPING.md) | Full Stack-II concept → feature → code location → verification |
| [DEMO_SCRIPT.md](DEMO_SCRIPT.md) | Scripted student, faculty, and admin demonstration with expected results |
| [DECISIONS.md](DECISIONS.md) | Architecture decision records (ADRs) |
| [RISKS.md](RISKS.md) | Risk register with severity, mitigation, owner, review trigger |
| [PHASE_PLAN.md](PHASE_PLAN.md) | Phases 0–11, dependencies, exit gates |
| [IMPLEMENTATION_LOG.md](IMPLEMENTATION_LOG.md) | Dated evidence of commands run and results |
| [CHANGELOG.md](CHANGELOG.md) | Human-readable change history |

## Source material

| File | Status |
|---|---|
| [CAMPUSHUB_MASTER_BLUEPRINT.md](CAMPUSHUB_MASTER_BLUEPRINT.md) | Original brief. Sections 0–22 are the primary contract. Section 23 is a 10,000-row candidate inventory that is consolidated in [TEST_STRATEGY.md](TEST_STRATEGY.md) rather than implemented row by row. |

## Maintenance rules

- Every document carries a purpose, scope, and last-reviewed date.
- Canonical definitions are linked, not copied. If a list must be repeated, the canonical document wins.
- A schema change updates DATA_MODEL, ERD, API_CONTRACT, and the relevant tests in the same change.
- A permission change updates AUTHORIZATION_MATRIX, SECURITY, and negative tests.
- A scope change updates SCOPE, PHASE_PLAN, and DECISIONS.
