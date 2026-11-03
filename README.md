# CampusHub

CampusHub is a role-aware academic collaboration platform for student projects. It connects project teams, tasks, milestones, skills, certifications, submissions, and faculty feedback in one system, with server-enforced authorization for every private resource.

> **Status: Phase 2 — identity, roles, and security (implemented locally; backend tests pass, not yet reviewed or pushed).** Sign-in, sign-out, password change, lockout, rate limit, HS256 bearer tokens, role checks, and the audit log are in place. No student-facing features yet. The gate to Phase 3 requires owner approval (see [docs/PHASE_PLAN.md](docs/PHASE_PLAN.md)).

## Planned stack

| Layer | Choice |
|---|---|
| Frontend | React 18+, TypeScript, Vite, React Router, Redux Toolkit + RTK Query |
| Backend | Java 21 LTS, Spring Boot 3.x, Spring Security (JWT), Spring Data JPA |
| Database | MySQL 8 with Flyway migrations |
| Delivery | Docker Compose (local), GitHub Actions (CI/CD) |
| Optional | Apache Kafka via transactional outbox (Phase 8 only) |

Architecture: a modular monolith. One deployable Spring Boot service, internally split into feature modules. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DECISIONS.md](docs/DECISIONS.md).

## Documentation map

Start at **[docs/INDEX.md](docs/INDEX.md)**. Canonical sources:

- Product and scope: [PRODUCT](docs/PRODUCT.md), [REQUIREMENTS](docs/REQUIREMENTS.md), [USER_STORIES](docs/USER_STORIES.md), [SCOPE](docs/SCOPE.md)
- Design: [ARCHITECTURE](docs/ARCHITECTURE.md), [DATA_MODEL](docs/DATA_MODEL.md), [ERD](docs/ERD.md), [API_CONTRACT](docs/API_CONTRACT.md), [STATE_MACHINES](docs/STATE_MACHINES.md), [UX_UI](docs/UX_UI.md)
- Security: [SECURITY](docs/SECURITY.md), [AUTHORIZATION_MATRIX](docs/AUTHORIZATION_MATRIX.md)
- Quality and delivery: [TEST_STRATEGY](docs/TEST_STRATEGY.md), [CI_CD](docs/CI_CD.md), [DEPLOYMENT](docs/DEPLOYMENT.md), [OPERATIONS](docs/OPERATIONS.md), [DEMO_SCRIPT](docs/DEMO_SCRIPT.md)
- Governance: [SYLLABUS_MAPPING](docs/SYLLABUS_MAPPING.md), [DECISIONS](docs/DECISIONS.md), [RISKS](docs/RISKS.md), [PHASE_PLAN](docs/PHASE_PLAN.md), [IMPLEMENTATION_LOG](docs/IMPLEMENTATION_LOG.md), [CHANGELOG](docs/CHANGELOG.md)
- Master blueprint: [docs/CAMPUSHUB_MASTER_BLUEPRINT.md](docs/CAMPUSHUB_MASTER_BLUEPRINT.md) (the original brief; Sections 0–22 are the primary contract, Section 23 is a candidate coverage inventory)

## Prerequisites (for Phase 1 onward)

- Git
- JDK 21 or newer (Phase 1 pins the toolchain release level)
- Maven 3.9+ (or the Maven wrapper once added)
- Node.js: current Active LTS, pinned in `frontend/package.json` `engines` during Phase 1
- Docker Engine running (required for Docker Compose and Testcontainers)

Versions were checked on the Phase 0 machine: git 2.55, Java 26, Maven 3.9.16, Node 26.8, Docker 29.8. Phase 1 pins the supported versions explicitly.

## Repository layout

```text
campushub/
├── README.md
├── .gitignore  .editorconfig  .env.example
├── docs/                 canonical specifications (Phase 0)
├── backend/              Spring Boot modular monolith (Phase 1+)
├── frontend/             React + TypeScript SPA (Phase 1+)
├── infra/                Docker and scripts (Phase 1+)
└── .github/workflows/    CI/CD (Phase 1+)
```

Application directories are created by the phase that populates them, not as empty placeholders.

## Phase 0 commands

Phase 0 is documentation only. Verification commands are listed in [docs/IMPLEMENTATION_LOG.md](docs/IMPLEMENTATION_LOG.md).

## Configuration

Copy `.env.example` to `.env` for local work. Never commit `.env` or any real secret. See [docs/SECURITY.md](docs/SECURITY.md) and [docs/OPERATIONS.md](docs/OPERATIONS.md).

## Local verification and CI

Run the backend tests (Testcontainers starts MySQL 8.4, so Docker must be running):

```bash
cd backend && JAVA_HOME=/path/to/jdk-21 ./mvnw -B verify
```

Start the local stack and check readiness:

```bash
docker compose up -d --build
curl -s http://localhost:8080/actuator/health/readiness
```

`.github/workflows/ci.yml` runs the same `./mvnw -B verify` and a Docker image build. It has been validated locally with actionlint but has not yet run on GitHub, because the repository has no remote. See [docs/RISKS.md](docs/RISKS.md) (R-22, R-23).

## License and ownership

Academic capstone project. Ownership and licensing are not yet decided; see [docs/RISKS.md](docs/RISKS.md).
