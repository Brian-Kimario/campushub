# CampusHub — Entity Relationship Diagram

**Purpose:** Visual map of entities and relationships. Column detail is in [DATA_MODEL.md](DATA_MODEL.md); this file must match it.
**Last reviewed:** 2026-11-03 (Phase 0)

## 1. Core project, team, and submission graph

```mermaid
erDiagram
  DEPARTMENTS ||--o{ USERS : "belongs to"
  DEPARTMENTS ||--o{ COURSES : offers
  ACADEMIC_TERMS ||--o{ PROJECTS : hosts
  COURSES |o--o{ PROJECTS : "may be linked to"
  USERS ||--o{ PROJECTS : "owns (owner_id)"
  USERS |o--o{ PROJECTS : "supervises (supervisor_id)"
  PROJECTS ||--o{ PROJECT_MEMBERS : has
  USERS ||--o{ PROJECT_MEMBERS : "joins as"
  PROJECTS ||--o{ PROJECT_INVITATIONS : issues
  USERS ||--o{ PROJECT_INVITATIONS : "receives (invitee_id)"
  PROJECTS ||--o{ MILESTONES : schedules
  PROJECTS ||--o{ TASKS : contains
  MILESTONES |o--o{ TASKS : "groups"
  USERS |o--o{ TASKS : "is assigned"
  TASKS ||--o{ TASK_COMMENTS : "has (P1)"
  MILESTONES ||--o| SUBMISSIONS : "receives one"
  SUBMISSIONS ||--|{ SUBMISSION_VERSIONS : "has versions"
  SUBMISSION_VERSIONS ||--o{ SUBMISSION_FILES : "contains"
  SUBMISSION_VERSIONS ||--o| REVIEWS : "receives one"
  PROJECTS ||--o{ PROJECT_ACTIVITY : "logs (P1)"
```

## 2. Identity, skills, and audit

```mermaid
erDiagram
  USERS ||--o{ REVOKED_TOKENS : "owns tokens"
  USERS ||--o{ AUDIT_EVENTS : "acts (actor_id, nullable)"
  USERS ||--o{ USER_SKILLS : "declares (P1)"
  SKILLS ||--o{ USER_SKILLS : "is declared in (P1)"
  USERS ||--o{ CERTIFICATIONS : "owns (P1)"
  CERTIFICATIONS ||--o{ CERTIFICATION_FILES : "evidence (P1)"
```

## 3. Phase 8 (optional)

```mermaid
erDiagram
  OUTBOX_EVENTS {
    binary id PK
    varchar aggregate_type
    binary aggregate_id
    varchar event_type
    json payload
    datetime created_at
    datetime published_at
  }
  PROCESSED_EVENTS {
    varchar consumer PK
    binary event_id PK
    datetime processed_at
  }
```

Outbox rows are written in the same transaction as the domain change; `processed_events` is keyed per consumer so one event can be consumed by more than one consumer.

## 4. Cardinality rules that matter

| Relationship | Rule | Enforced by |
|---|---|---|
| Project ↔ owner | Exactly one OWNER membership per project | Generated `owner_key` unique index |
| Project ↔ supervisor | Zero or one supervisor; required from APPROVED | CHECK constraint on status and `supervisor_id` |
| Milestone ↔ submission | At most one submission per milestone | `UNIQUE (milestone_id)` on `submissions` |
| Submission version ↔ review | At most one review per version | `UNIQUE (version_id)` on `reviews` |
| Task ↔ milestone | Same project only | Composite FK `(project_id, milestone_id)` |
| Invitation | At most one PENDING per project and invitee | Generated `pending_key` unique index |
| Team size | ≤ 5 members including owner (OD-3) | Service check inside a locked project row |
