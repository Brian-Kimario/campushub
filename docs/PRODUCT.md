# CampusHub — Product Definition

**Purpose:** Define who CampusHub serves, what problems it solves, what success means, and what it deliberately excludes.
**Scope:** MVP (P0) and the P1/P2 extensions named in [SCOPE.md](SCOPE.md).
**Last reviewed:** 2026-11-03 (Phase 0)

## 1. Product statement

CampusHub is a single-institution academic workspace where student project teams, faculty supervisors, and administrators coordinate project proposals, team membership, milestones, tasks, submissions, and feedback. It treats academic data as private by default: a person sees a project only through membership, supervision, or an explicitly defined role.

It is not a generic task manager. Its value is the combination of academic workflow (proposal → approval → milestones → submissions → review) with access rules that match university practice.

## 2. Problems

| # | Problem today | How CampusHub addresses it |
|---|---|---|
| P-1 | Proposals, deadlines, and responsibilities live in email and spreadsheets. | One project record with milestones, tasks, and due dates. |
| P-2 | Team responsibilities are unclear. | Explicit ownership, membership, and task assignees. |
| P-3 | Submissions and feedback are scattered; resubmissions overwrite evidence. | Immutable submission versions with one review per version. |
| P-4 | Supervisors lack an at-a-glance view of their projects. | Supervisor dashboard limited to supervised projects. |
| P-5 | Students have no consistent evidence trail for skills and certifications. | Skill self-assessment and certification records owned by the student (P1). |
| P-6 | Systems that share data loosely expose private work. | Server-side object authorization on every private resource. |

## 3. Users

| Role | Who | Primary jobs | Explicit non-jobs |
|---|---|---|---|
| Student | Enrolled student | Maintain profile and skills; propose and join projects; manage tasks; submit milestones; read feedback | Review others' work; see projects they do not belong to |
| Faculty | Teaching or supervising staff | See supervised projects; review submissions; return feedback | Access unassigned projects; create projects in MVP; self-assign supervision |
| Administrator | Academic office staff | Manage accounts, departments, terms, courses, project approval and supervisor assignment; inspect audit log | Routine read access to private student files (see ADR-007) |

Each user has exactly one primary role in MVP (ADR-021).

## 4. Objectives

1. Deliver a complete student → faculty golden path (see [USER_STORIES.md](USER_STORIES.md)).
2. Enforce object-level authorization so every negative security journey fails at the API layer.
3. Demonstrate the Full Stack-II syllabus with working features (see [SYLLABUS_MAPPING.md](SYLLABUS_MAPPING.md)).
4. Be runnable from a clean clone with Docker Compose and documented commands.
5. Stay maintainable: modular monolith, migrations, tests, and documented decisions.

## 5. Success metrics

Metrics are binary acceptance checks or counts, not vanity analytics. Each has an evidence source.

| ID | Metric | Target | Evidence |
|---|---|---|---|
| SM-1 | Golden path completes without manual DB edits | 100% of steps in DEMO_SCRIPT | Demo run record in IMPLEMENTATION_LOG |
| SM-2 | Negative security journeys denied | 100% of journeys in USER_STORIES §5 | Automated security test suite |
| SM-3 | Clean-clone setup succeeds | Pass on a fresh clone with documented commands only | Phase 11 rehearsal log |
| SM-4 | Critical or high security findings open at release | 0 | Dependency and static analysis reports |
| SM-5 | Automated test pass rate on main | 100% (no skipped security tests) | CI history |
| SM-6 | p95 latency of list endpoints at seed-data scale (≈ 2,000 projects, 20,000 tasks) | ≤ 300 ms on the dev host | Phase 9 measurement log, method in TEST_STRATEGY |

SM-6 is a target to measure, not a promise. If it is not met, record it in RISKS and the measurement log rather than changing the number.

## 6. Exclusions (MVP)

Public social network; job marketplace; plagiarism detection; real-time chat; generative AI grading; video conferencing; payments; ERP or SIS integration; native mobile apps; multi-tenancy; numeric grading; open self-registration; email delivery (no mail service in MVP). These appear in SCOPE as "not in scope", not as deferred promises.

## 7. Product assumptions

| ID | Assumption | Impact if wrong | Reference |
|---|---|---|---|
| A-1 | One institution, one time zone (configurable). | Deadline display errors. | ADR-013 |
| A-2 | Accounts are provisioned by administrators; no self-registration. | Requires an onboarding feature. | ADR-021, SCOPE |
| A-3 | Faculty cannot create projects in MVP; administrators approve proposals and assign supervisors. | The faculty golden path changes. | §8 open decision OD-1 |
| A-4 | Feedback is qualitative (accept / request changes). No numeric grades. | Gradebook integration needed. | Blueprint §20 |
| A-5 | Students use modern desktop or mobile browsers; no offline mode. | Draft persistence needs a different approach. | UX_UI.md |
| A-6 | A single supervisor per project. | Multi-supervisor policy needed. | ADR-016 |
| A-7 | The institution accepts self-hosted deployment on a private host or a single managed VM. | Hosting choice changes DEPLOYMENT.md. | ADR-014 |

## 8. Open product decisions

Each has a conservative default that the project proceeds on until the owner decides. See [DECISIONS.md](DECISIONS.md) for the recorded defaults.

| ID | Decision | Default used | Owner action |
|---|---|---|---|
| OD-1 | Who approves project proposals and assigns supervisors? | Administrator approves and assigns (ADR-016 context) | Confirm |
| OD-2 | Can faculty create projects? | No in MVP | Confirm |
| OD-3 | Maximum team size | 5 members including owner | Confirm |
| OD-4 | Are late submissions allowed? | No; supervisor may enable per milestone | Confirm |
| OD-5 | May faculty see student skills and certifications? | No in MVP (owner and administrators' metadata only) | Confirm; required before P1 |
| OD-6 | Institution time zone | UTC until configured | Provide the real zone |
| OD-7 | Initial password process for provisioned accounts | Admin sets a temporary password; forced change at first login | Confirm |
| OD-8 | Hosting provider and budget | Undecided; Phase 10 decides from the criteria in DEPLOYMENT.md | Provide budget and host preference |
| OD-9 | Project licensing and ownership of the code | Undecided | Provide course policy |
| OD-10 | Whether Kafka is required for the capstone or only a stretch goal | Stretch (Phase 8 optional) | Confirm |
