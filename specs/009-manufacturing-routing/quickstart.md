# Quickstart: Manufacturing Routing (`routing-service`)

## Prerequisites

- Infra stack up: `docker compose -f docker/compose-infra.yml ps` (postgres, kafka, keycloak, gateway healthy).
- `routing` DB user/schema provisioned (see Deployment below) and `routing-service` container running on 8100.
- Dev data seeded (`scripts/seed-dev-data.ps1`) so an approved Item/BOM exists to route.

## Build & test (backend)

```bash
DOCKER_HOST='npipe:////./pipe/docker_engine' ./gradlew :services:routing-service:check
```
Runs Checkstyle + SpotBugs + unit + Testcontainers integration tests (real PostgreSQL; EmbeddedKafka for event paths).

## Build & test (frontend)

```bash
cd frontend/angular && npm run lint && npm run build && npm test   # ERR-MES-089 gate
```

## Smoke test (via gateway, port 8082 — ERR-MES-067)

1. Get a token (password grant, `mes-frontend`, `admin@test.org`/`Admin123!`).
2. Reference data: `POST /api/v1/routing/work-centres` (create a work centre); `GET /api/v1/routing/route-types` (confirm seeded Standard).
3. Create a route: `POST /api/v1/routes` with an approved part/BOM/inspection-plan revision and `routeTypeId` = Standard → expect 201, status DRAFT, revision 1.
4. Try a second Standard route for the same part/revision → expect 409 (FR-004b). An alternate type → 201.
5. Add operations 10/20 (`POST .../operations`); add 30/40 sharing one sequence → both reported Parallel (derived).
6. Define a mutually-exclusive subset of {30,40} → `PUT .../mutually-exclusive-sets` → 30/40 mutually exclusive, peers parallel.
7. Add resources/standards to an operation (US3 endpoints).
8. `POST .../submit` then `POST .../approve` (e-sign). For a route with a significant-process operation, approval is blocked until the additional SME approver signs (FR-024). On approve → `routing.route.approved` emitted.
9. Frontend: Routing → New Route → author in the **grid** view; switch to the **graphical** view and confirm identical structure; edit in either view and confirm round-trip (SC-008).

## Deployment Steps

### 1. Database (ERR-MES-085)
Add `ROUTING_DB_USER`/`ROUTING_DB_PASSWORD` to the **postgres** service env block in `docker/compose-infra.yml` (and `.env` + `.env.example`); the init script creates the `routing` schema/role. Verify on a fresh volume.

### 2. Service
Add the `routing-service` container (port 8100, depends_on postgres/kafka/keycloak healthy — NOT admin-service, ERR-MES-086). Flyway applies `V001__routing_baseline.sql` on startup — expect `Successfully applied N migration(s)`. Privileges auto-register and auto-grant to SYSTEM_ADMIN on `ApplicationReadyEvent` (ERR-MES-075); restart `iam-service` is not required (routing-service registers its own manifest).

### 3. Gateway
Add predicates `Path=/api/v1/routes/**` and `Path=/api/v1/routing/**` → `ROUTING_SERVICE_URL`. Rebuild the gateway image (`--build`) after the route change (ERR-MES-081); verify a real token returns 200 (ERR-MES-067).

### 4. Frontend
Rebuild the Angular app (new `features/routing` area + Settings submodule). Add `sonar.sources`/`sonar.tests` entries for `services/routing-service` (CLAUDE.md pre-PR checklist).
