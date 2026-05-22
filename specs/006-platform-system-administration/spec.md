# Feature Specification: Platform & System Administration

**Feature Branch**: `006-platform-system-administration`

**Created**: 2026-05-23

**Status**: Draft

**Jira Epic**: [MES-6](https://artical.atlassian.net/browse/MES-6)

**Input**: Foundational platform services: Spring Boot Admin for live service monitoring, Spring Cloud Gateway for external API routing and authentication enforcement, Docker Compose service mesh with DNS-based discovery, Portainer-managed container lifecycle, and cross-cutting operational config (Spring profiles, health endpoints, actuator security). Prerequisite for all domain microservices.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Spring Boot Admin Observability (Priority: P1)

An operator opens the Spring Boot Admin UI and sees the live health status, metrics, and log levels for every running MikeMES microservice in one place.

`admin-service` runs a Spring Boot Admin Server. Every other service (iam-service, gateway-service, platform-service, and all future services) is configured as an SBA client that registers itself on startup. The SBA UI is secured — anonymous access is denied.

**Why this priority**: Without system observability, debugging production failures requires ssh + log grep. SBA gives operators the same visibility in a browser. Prerequisite for operational readiness of all P1 services.

**Independent Test**: Start `admin-service` plus `iam-service`. Open `http://localhost:8888`. Both services appear with `UP` status and actuator endpoints are accessible in the UI.

**Acceptance Scenarios**:

1. **Given** admin-service and iam-service are running, **When** an authenticated operator opens the SBA UI, **Then** iam-service appears in the service list with status `UP`, health details, and metrics visible.
2. **Given** iam-service restarts, **When** it re-registers with admin-service, **Then** its status updates to `UP` within 30 seconds without an admin-service restart.
3. **Given** an unauthenticated request hits `/admin/*`, **When** the SBA server evaluates it, **Then** the request is denied (401/redirect to Keycloak login).
4. **Given** a service has a DOWN actuator health, **When** an operator views SBA, **Then** the service shows `DOWN` with the failing health indicator named.

---

### User Story 2 — Gateway Routing for admin-service and platform-service (Priority: P1)

Authenticated API requests to `/api/admin/**` and `/api/platform/**` are routed through the Spring Cloud Gateway with JWT validation. Internal service ports are not exposed externally.

**Why this priority**: Without gateway routes, callers must know service hostnames/ports, bypassing JWT enforcement. All P1+ services must be reachable only via gateway.

**Independent Test**: Send `GET /api/platform/health` to gateway (port 8080) with a valid JWT → `200`. Without JWT → `401`. Direct call to platform-service port from outside Docker network → connection refused.

**Acceptance Scenarios**:

1. **Given** a request to `GET /api/platform/config` with a valid JWT, **When** the gateway forwards it, **Then** platform-service receives it and returns `200`.
2. **Given** a request to `GET /api/platform/config` without a JWT, **When** the gateway evaluates it, **Then** the gateway returns `401` before forwarding.
3. **Given** a request to `GET /api/admin/actuator/health` with a valid JWT, **When** the gateway forwards it, **Then** admin-service returns `200`.

---

### User Story 3 — Platform Service: Organisation System Configuration (Priority: P1)

Administrators manage per-organisation configuration key/value pairs that control MES system behaviour. All entries are scoped by `org_id` and enforced by Keycloak RBAC.

**Why this priority**: Domain services (P2+) need runtime configuration (e.g., tolerance defaults, enabled features per org). Platform-service provides this before those services exist.

**Independent Test**: Create a `SystemConfiguration` entry via `PUT /api/platform/config/test.key` with an admin JWT. Retrieve it via `GET /api/platform/config/test.key`. Attempt same GET with a different org's JWT → `404` (not `403` — no information leakage).

**Acceptance Scenarios**:

1. **Given** a user with `platform:config:manage` privilege, **When** they PUT `/api/platform/config/{key}`, **Then** the entry is upserted in `platform.system_configuration` scoped to their `org_id` and `200` is returned.
2. **Given** an entry exists for org A, **When** a user from org B GETs that key, **Then** `404` is returned (not the value).
3. **Given** a user without `platform:config:manage`, **When** they attempt PUT, **Then** `403` is returned.
4. **Given** a Flyway migration V001 runs, **When** the service starts, **Then** `platform.system_configuration` table exists with `UNIQUE(org_id, config_key)` constraint.

---

### User Story 4 — Docker Compose Service Mesh (Priority: P1)

All MikeMES services communicate by hostname on the `mikemes-net` bridge network. `admin-service` and `platform-service` containers are defined in `compose-infra.yml` with healthchecks that the gateway and SBA can depend on.

**Why this priority**: Without container definitions, the platform cannot be started locally and CI cannot use a real service mesh.

**Independent Test**: `docker compose -f docker/compose-infra.yml up -d`. After healthchecks pass, `docker compose ps` shows all services `healthy`. `iam-service` can reach `admin-service:8888` by hostname.

**Acceptance Scenarios**:

1. **Given** `docker compose up -d` runs, **When** all healthchecks pass, **Then** `docker compose ps` shows `admin-service` and `platform-service` as healthy.
2. **Given** `admin-service` is healthy, **When** `iam-service` SBA client starts, **Then** it registers with `http://admin-service:8888` by hostname (no hardcoded IP).
3. **Given** new env vars are required by admin-service or platform-service, **When** a developer sets up locally, **Then** `.env.example` documents each var with a generation or description note.

---

### User Story 5 — Portainer Container Management UI (Priority: P2)

Operators manage running containers via a Portainer web UI. Portainer is optional and started via a separate Docker Compose file or profile to avoid imposing the Docker socket mount on production-grade deployments.

**Why this priority**: P2 — useful for dev/staging environments but not a hard prerequisite for domain service development.

**Independent Test**: `docker compose -f docker/compose-tools.yml up -d portainer`. Open `http://localhost:9000`. Container list for the running stack is visible.

**Acceptance Scenarios**:

1. **Given** `compose-tools.yml` contains a `portainer` service, **When** it starts, **Then** Portainer UI is accessible on port 9000 with no startup errors.
2. **Given** Portainer is running, **When** an operator views the container list, **Then** all containers on `mikemes-net` are visible.
3. **Given** the main compose stack starts WITHOUT `compose-tools.yml`, **When** it comes up, **Then** no Portainer-related error occurs (truly optional).

---

### Edge Cases

- What happens if admin-service is down when a client service starts? SBA client retries on an interval — service still starts, registers when admin-service recovers.
- What if a platform config key exceeds 255 chars? `@Valid` + Bean Validation `@Size` returns `400 Bad Request`.
- What if two services race to PUT the same config key? `UNIQUE(org_id, config_key)` plus upsert logic (INSERT ON CONFLICT UPDATE) makes it idempotent — last writer wins.
- What if `org_id` is missing from the JWT? `MikeMESJwtAuthenticationConverter` already throws `MissingClaimException` → `401` before reaching the controller.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `admin-service` MUST run `de.codecentric:spring-boot-admin-starter-server` secured by Keycloak OIDC (no anonymous UI access)
- **FR-002**: All services (iam-service, gateway-service, platform-service) MUST include `spring-boot-admin-starter-client` and register with admin-service on startup
- **FR-003**: `gateway-service` routes MUST include `/api/admin/**` → `admin-service` and `/api/platform/**` → `platform-service`, both requiring a valid JWT
- **FR-004**: `platform-service` MUST expose `GET /api/platform/config`, `GET /api/platform/config/{key}`, `PUT /api/platform/config/{key}`, `DELETE /api/platform/config/{key}`
- **FR-005**: `platform-service` MUST expose `GET /internal/config/{key}` protected by webhook token (same pattern as iam-service InternalController)
- **FR-006**: All `platform-service` entities MUST include `org_id UUID NOT NULL` column
- **FR-007**: `platform-service` MUST use `lib-common-security` for JWT decoding and `@RequiresPrivilege` for endpoint protection
- **FR-008**: `iam-service` Flyway MUST seed `platform:config:manage` and `platform:config:read` privileges (new migration V005)
- **FR-009**: `platform-service` Flyway migration V001 MUST create schema `platform` and table `system_configuration` with `UNIQUE(org_id, config_key)`
- **FR-010**: `compose-infra.yml` MUST add `admin-service` and `platform-service` containers with healthchecks
- **FR-011**: `.env.example` MUST document all new environment variables
- **FR-012**: Portainer MUST be defined in a separate `docker/compose-tools.yml` (not in `compose-infra.yml`)
- **FR-013**: `sonar-project.properties` MUST be updated with new module paths after `src/main/java` directories are created (ERR-MES-033)

### Key Entities

- **SystemConfiguration** (platform-service): Represents a named configuration value scoped per organisation. Fields: `id`, `org_id`, `config_key`, `config_value`, `description`, `active`, `created_at`, `updated_at`, `created_by`.
- No persistent entities in admin-service (SBA Server is stateless — instance registry is in-memory).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `./gradlew check` passes across all affected modules with zero failures in CI
- **SC-002**: SBA UI at `http://localhost:8888` shows all registered services with `UP` status after `docker compose up -d`
- **SC-003**: `PUT /api/platform/config/test` then `GET /api/platform/config/test` round-trips the value, and a cross-org GET returns `404`
- **SC-004**: All P1 user stories (US1–US4) have passing integration tests
- **SC-005**: `docker compose ps` shows `admin-service` and `platform-service` as `healthy` after startup
- **SC-006**: Gateway returns `401` for unauthenticated requests to `/api/admin/**` and `/api/platform/**`

---

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| ISA-95 Part 1 | Yes | Level 3 MES functional hierarchy — admin and platform are Level 3 infrastructure services providing monitoring and configuration management |
| ISA-95 Part 2 | Partial | `SystemConfiguration` maps to Resource Management configuration objects; supports ISA-95 Level 3 operational parameters |
| ISA-88 | No | N/A — no batch process control entities |
| MTConnect | No | N/A — no machine data |
| IPC-2591 (CFX) | No | N/A — no assembly process data |
| CMMC CM.2 | Yes | Platform-service configuration management satisfies CM.2: configuration change control, tracking of configuration settings |
| CMMC CM.3 | Partial | Admin-service monitoring supports CM.3 (configuration change analysis) by providing runtime state visibility |
| NIST SP 800-171 §3.1 | Yes | Access control: SBA UI and all platform endpoints require authenticated, privileged principal |
| NIST SP 800-171 §3.13 | Yes | Network boundary: internal SBA registration endpoint not exposed through gateway; direct service ports not externally reachable |
| NIST SP 800-171 §3.3 | Partial | Audit: configuration changes (PUT/DELETE on SystemConfiguration) should be logged — partially satisfied by Spring Boot actuator audit events; full audit deferred to MES-7 (lib-common-audit) |
| AS9100D §7.1.5 | Partial | Monitoring resources: SBA provides infrastructure monitoring satisfying §7.1.5 (monitoring and measurement resources) |
| AS9100D §8.5.1 | No | N/A — manufacturing process control |
| AS9102 (FAI) | No | N/A |
| AS9131 (NCM) | No | N/A |
| AS9145 (APQP) | No | N/A |
| AS6174 / AS5553 | No | N/A — supply chain |
| AS9134 / AS9117 | No | N/A |
| AS13100 | No | N/A |
| 21 CFR Part 11 / EU Annex 11 | No | N/A — SystemConfiguration is not a regulated electronic record |
| ISO 10303 (STEP) / QIF / OAGIS | No | N/A — no product data exchange |

---

## Assumptions

- `admin-service` runs on port 8888 and `platform-service` on port 8090 (no conflicts with existing services on 8080/8085)
- SBA Server uses Keycloak OIDC login (OAuth2 client credentials or authorization code flow for UI) — not Basic Auth
- Privilege seeding (`platform:config:manage`, `platform:config:read`) lands in iam-service Flyway V005; this migration ships in the same PR
- `lib-common-audit` does not exist yet; audit logging for `SystemConfiguration` mutations is deferred to MES-7
- Portainer ships in `compose-tools.yml` as a dev/staging convenience — not required for CI or production

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Audit logging for SystemConfiguration mutations | `lib-common-audit` not built until MES-7; adding manual audit now creates inconsistency with future shared audit library | Configuration changes not tamper-evidently logged — NIST §3.3 partially unmet until MES-7 | P1 (MES-7) | TBD |
| DEF-002 | SBA notification channels (Slack, email alerts on service DOWN) | Out of scope for foundation; requires external SMTP/webhook config complexity | Operators not proactively alerted on service failures | Post-GA | TBD |
| DEF-003 | Feature flags entity in platform-service | Scope unclear until MES-8 (master data) reveals toggle needs; premature now | No runtime feature toggling available to domain services | P2 | TBD |
| DEF-004 | Portainer TLS / HTTPS on port 9443 | HTTP port 9000 acceptable for local dev; TLS config adds complexity before staging infra is defined | Security gap on staging/production Portainer exposure | P3 | TBD |
| DEF-005 | Spring Cloud Config Server for centralised config | Currently using env vars + platform-service KV store; Spring Cloud Config adds complexity before multiple replicas are needed | Config changes require service restarts rather than hot-reload | Post-GA | TBD |
