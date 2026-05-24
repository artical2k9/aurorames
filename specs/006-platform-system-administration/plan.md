# Implementation Plan: Platform & System Administration

**Branch**: `006-platform-system-administration` | **Date**: 2026-05-23  
**Spec**: [spec.md](spec.md) | **Jira**: [MES-6](https://artical.atlassian.net/browse/MES-6)

---

## Summary

Implement the foundation platform layer for MikeMES: `admin-service` running Spring Boot Admin Server for live microservice observability, `platform-service` providing per-organisation system configuration (key/value store), Spring Cloud Gateway routes for both new services, Docker Compose service mesh entries with healthchecks, and optional Portainer management UI. This is the second P1 Foundation epic — prerequisite for all domain microservices (P2+) that need operational monitoring and runtime configuration.

---

## Technical Context

**Language/Version**: Java 21 LTS (Eclipse Temurin, auto-provisioned via Gradle toolchain)

**Primary Dependencies**:
- `de.codecentric:spring-boot-admin-starter-server:3.4.3` — SBA Server (admin-service)
- `de.codecentric:spring-boot-admin-starter-client:3.4.3` — SBA client (all services)
- `spring-boot-starter-oauth2-client` + `spring-boot-starter-security` — SBA UI Keycloak OIDC login (admin-service)
- `spring-cloud-gateway` 2025.0.0 — already in gateway-service; add new routes
- `lib-common-security` (existing) — JWT decoding and `@RequiresPrivilege` for platform-service
- `spring-boot-starter-data-jpa` + `flyway-core` + `postgresql` — persistence (platform-service)
- `spring-boot-starter-actuator` — healthchecks and SBA metrics (all services)

**Storage**: PostgreSQL 16, schema `platform` (Flyway managed, platform-service only). admin-service is stateless.

**Testing**: JUnit 5 + Mockito (unit), Testcontainers with real PostgreSQL (platform-service ITs), MockMvc (platform-service), WebTestClient (gateway IT additions). No persistence mocking per Constitution §II.

**Target Platform**: Docker container, Linux (on-premises via Docker Compose). Spring Boot 3.5.0.

**Project Type**: Two new microservices (`admin-service`: SBA server; `platform-service`: REST API + JPA). Config-only additions to `gateway-service` and all existing services (SBA client wiring).

**Performance Goals**: SBA registration/heartbeat ≤10s; platform config read `GET /api/platform/config/{key}` < 50ms p95 at 100 concurrent orgs.

**Constraints**: No bespoke auth anywhere — all authentication via Keycloak. All `platform-service` data scoped by `org_id`. No secrets in source. No cross-schema DB foreign keys. `sonar-project.properties` updated only after directories exist (ERR-MES-033).

**Scale/Scope**: 2 new services + 2 Docker Compose containers + 1 DB table + 2 Flyway migrations + ~12 REST endpoints + SBA client config in 3 existing services.

---

## Constitution Check

*Re-checked after Phase 1 design. All gates resolved.*

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — `spec.md` present and user-approved |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS — tasks.md (Phase 2) will follow TDD order per template |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — Compliance Verification phase in tasks.md will include defect closure gate |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ✅ PASS — approved via ExitPlanMode 2026-05-23 |
| Does the spec include a "Compliance References" section? | IV — Compliance by Design | ✅ PASS — 19 standards assessed in spec.md |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance by Design | ✅ PASS — ISA-95 Parts 1 & 2, CMMC CM.2/CM.3, NIST §3.1, §3.3, §3.13, AS9100D §7.1.5 addressed |
| Do all data mutations produce an audit log entry? | V — Auditability | ❌ FAIL (justified) — `lib-common-audit` not built until MES-7; DEF-001 in spec.md defers this. NIST §3.3 partially unmet until MES-7 integration. |
| Do new data models map to ISA-95 Part 2 object models (where applicable)? | VI — ISA-95/ISA-88 | ✅ PASS — `SystemConfiguration` maps to Resource Management configuration objects at ISA-95 Level 3 |
| Is authentication delegated to Keycloak (no bespoke auth)? | VII — Security-First | ✅ PASS — SBA UI uses Keycloak OIDC login; platform-service uses lib-common-security JWT |
| Is all data scoped by `organisation_id` with no cross-org leakage? | IX — Multi-Org Isolation | ✅ PASS — `org_id` on `SystemConfiguration`; GET by key returns 404 (not the value) for wrong org |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration Integrity | ✅ PASS — `PUT /config/{key}` is idempotent upsert; `@Valid` + Bean Validation on request DTOs |
| Are shop floor timestamps from source (not synthetic)? | X — Data Accuracy | N/A — no shop floor data |

**Constitution Gate V justification**: Audit logging requires `lib-common-audit` (MES-7). Adding manual audit now creates divergence from the future shared library. DEF-001 in spec.md is tracked. Dependency chain: MES-6 → MES-7 (MES-6 must complete first, MES-7 adds audit). Partial mitigation: Spring Boot actuator audit events capture failed auth events.

---

## Project Structure

### Documentation (this feature)

```text
specs/006-platform-system-administration/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Research findings (R1–R9)
├── data-model.md        # DB schema and entity definitions
├── quickstart.md        # Local dev setup guide
├── contracts/
│   ├── platform-service-api.yaml   # OpenAPI 3.0 for platform-service
│   └── admin-service-api.yaml      # OpenAPI 3.0 for admin-service (minimal)
├── jira.json            # {"epicKey": "MES-6"}
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code

```text
services/admin-service/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/mikemes/admin/
    │   │   ├── AdminServiceApplication.java
    │   │   └── config/
    │   │       └── SecurityConfig.java          # Keycloak OIDC + permit /actuator/health
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/mikemes/admin/
            └── AdminServiceApplicationTest.java # Context load smoke test

services/platform-service/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/mikemes/platform/
    │   │   ├── PlatformServiceApplication.java
    │   │   ├── api/
    │   │   │   ├── SystemConfigController.java   # GET/PUT/DELETE /api/platform/config
    │   │   │   ├── InternalConfigController.java # GET /internal/config/{key}
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── dto/
    │   │   │       ├── SystemConfigDto.java
    │   │   │       └── UpsertConfigRequest.java
    │   │   ├── config/
    │   │   │   ├── JpaConfig.java
    │   │   │   └── SecurityConfig.java
    │   │   ├── domain/
    │   │   │   └── SystemConfiguration.java     # JPA entity
    │   │   ├── repository/
    │   │   │   └── SystemConfigurationRepository.java
    │   │   └── service/
    │   │       └── SystemConfigService.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V001__create_platform_schema.sql
    └── test/
        └── java/com/mikemes/platform/
            ├── integration/
            │   └── api/
            │       └── SystemConfigControllerIT.java  # Testcontainers (Postgres)
            └── unit/
                └── service/
                    └── SystemConfigServiceTest.java

# Changes to existing services:
services/gateway-service/src/main/resources/application.yml
    # Add routes: /api/admin/** and /api/platform/**

services/iam-service/src/main/resources/db/migration/
    └── V005__seed_platform_module_privileges.sql

libs/lib-common-security/src/main/resources/application.yml (or each service yml)
    # spring.boot.admin.client.url added to iam-service, gateway-service

docker/compose-infra.yml
    # Add admin-service and platform-service containers

docker/compose-tools.yml                          # New file — Portainer
.env.example                                      # New vars documented
sonar-project.properties                          # Updated after src dirs created
settings.gradle                                   # Add/uncomment admin-service, platform-service
```

---

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Gate V — no audit log | `lib-common-audit` unbuilt; MES-6 blocks MES-7 | Cannot add shared audit before the shared library exists; manual audit creates debt |
