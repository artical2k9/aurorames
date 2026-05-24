# Implementation Plan: System Activity & Audit Logging

**Branch**: `007-system-activity-audit-logging` | **Date**: 2026-05-24
**Spec**: [spec.md](spec.md) | **Jira**: [MES-7](https://artical.atlassian.net/browse/MES-7)

---

## Summary

Implement the centralised audit trail for MikeMES via three complementary mechanisms: (1) Hibernate Envers entity-change capture for all `@Audited` JPA entities across domain services, backed by a shared `lib-common-audit` library; (2) Kafka-based cross-service audit event streaming consumed by a new standalone `audit-service` microservice; (3) Keycloak authentication event capture via a custom Event Listener SPI that publishes to the same Kafka topic. The audit store is an immutable PostgreSQL schema with INSERT-only DB privileges, per-record SHA-256 checksums, and a tamper-evidence verification REST endpoint. This feature delivers the `lib-common-audit` and `lib-common-events` shared libraries and the `audit-service` microservice referenced in the MikeMES Technology Stack (Constitution §VIII).

---

## Technical Context

**Language/Version**: Java 21 LTS (Eclipse Temurin, Gradle toolchain auto-provision)

**Primary Dependencies**:
- `hibernate-envers` — entity change capture with `ValidityAuditStrategy` (in version catalog)
- `spring-kafka` — Kafka consumer (`AckMode.MANUAL_IMMEDIATE`) and producer (in version catalog)
- `spring-boot-starter-data-jpa` + `flyway-core` + `flyway-postgres` + `postgresql` — persistence
- `spring-boot-starter-web` — REST query API
- `spring-boot-starter-actuator` — health endpoint with Kafka consumer-lag indicator
- `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` — JWT/RBAC (via `lib-common-security`)
- `springdoc-openapi-webmvc` — OpenAPI 3.1 docs
- `testcontainers-postgresql` + `testcontainers-kafka` — integration tests (no mocking of persistence)
- `keycloak-admin-client` (25.0.6) — Keycloak SPI API for `lib-keycloak-audit-spi`
- New: `libs:lib-common-audit` — `MesRevisionEntity`, `MesRevisionListener`, `ChecksumService`, `AuditRecord`, `AuthAuditRecord` JPA entities
- New: `libs:lib-common-events` — `AuditEventMessage` record, `KafkaTopics` constants
- New: `libs:lib-keycloak-audit-spi` — plain Java Keycloak EventListenerProvider JAR (no Spring Boot)

**Storage**: PostgreSQL 16, schema `audit` (Flyway managed by audit-service). Two runtime DB roles: `audit_service` (SELECT + INSERT only — no UPDATE/DELETE) and `audit_flyway` (DDL + superuser for migrations).

**Testing**: JUnit 5 + Mockito (unit), Testcontainers PostgreSQL + Kafka (ITs). No mocking of persistence (Constitution §II). SPI listener tested with embedded Kafka.

**Target Platform**: Docker container, Linux, via `docker/compose-infra.yml`. Service port `8090`. Gateway route `/api/audit/**`.

**Project Type**: New microservice (`services/audit-service`) + 3 new shared libraries (`libs/lib-common-audit`, `libs/lib-common-events`, `libs/lib-keycloak-audit-spi`). Docker Compose entry added to `compose-infra.yml`.

**Performance Goals**:
- Kafka event persistence: p95 ≤ 5 s at 500 events/min (SC-002)
- Query API: < 2 s for 30-day window over 10 M records (SC-003); covered by composite index `(entity_type, entity_id, occurred_at DESC)`

**Constraints**:
- INSERT-only DB role enforced at PostgreSQL layer (FR-002); no UPDATE/DELETE ever granted to `audit_service`
- Keycloak-only auth — no bespoke JWT handling in audit-service (Constitution §VII)
- All `audit_records` must carry `tenant_id` column for future row-level isolation (DEF-002)
- No cross-schema DB queries — audit-service owns `audit` schema; domain services' `_AUD` tables are in their own schemas
- `sonar-project.properties` updated only after source directories exist (ERR-MES-033)
- All new modules need unit test coverage before pushing (ERR-MES-035)

**Scale/Scope**: 1 new microservice + 3 new shared libraries + 1 Keycloak SPI JAR + 2 Flyway migrations + ~8 REST endpoints + Kafka consumer group + Docker Compose entries.

---

## Constitution Check

*Checked after Phase 1 design. All gates resolved.*

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — `spec.md` present and user-approved 2026-05-24 |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS — tasks.md will follow TDD order per template |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — Compliance Verification phase in tasks.md includes defect closure gate |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ✅ PASS — implementation approved by human 2026-05-24 (speckit-implement invoked) |
| Does the spec include a "Compliance References" section? | IV — Compliance by Design | ✅ PASS — 6 standards assessed with applicability in spec.md |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance by Design | ✅ PASS — 21 CFR Part 11 §11.10(e), AS9100D §7.5, CMMC AU domain (AU.2.041/042, AU.3.045/046), EU Annex 11 §9 explicitly addressed |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — This feature IS the audit trail. Per Constitution §V: log access must itself be audited. Reads to `/audit/**` are logged as `AUTH_EVENT` AuditRecords via a Spring `HandlerInterceptor`. |
| Do new data models map to ISA-95 Part 2 object models (where applicable)? | VI — ISA-95/ISA-88 | N/A — Audit records are compliance metadata, not ISA-95 production domain objects. No ISA-95 mapping required. |
| Is authentication delegated to Keycloak (no bespoke auth)? | VII — Security-First | ✅ PASS — audit-service is a Spring Security resource server; all endpoints require Keycloak-issued JWT |
| Is all data scoped by `organisation_id` with no cross-org leakage? | IX — Multi-Org Isolation | ✅ PASS — `tenant_id` column present on all audit tables; row-level filtering deferred per DEF-002 (depends on MES-5 multi-org completion) |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration Integrity | ✅ PASS — Kafka consumer idempotent via `UNIQUE (event_id)` constraint; `AuditEventMessage` validated on deserialization |
| Are shop floor timestamps from source (not synthetic)? | X — Data Accuracy | N/A — no shop floor data ingested |

**Constitution Gate III**: ✅ PASS — all gates resolved. Implementation complete T001–T085.

---

## Project Structure

### Documentation (this feature)

```text
specs/007-system-activity-audit-logging/
├── plan.md           # This file
├── spec.md           # Feature specification (MES-7)
├── jira.json         # {"epicKey": "MES-7", "epicId": "12090"}
├── research.md       # Phase 0 research findings (R1–R10)
├── data-model.md     # DB schema, JPA entities, Kafka message schema
├── quickstart.md     # Local dev setup guide
├── contracts/
│   └── audit-service-api.yaml   # OpenAPI 3.1 for audit-service
└── tasks.md          # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
libs/lib-common-audit/
├── build.gradle                       # java-library + maven-publish to mavenLocal
└── src/
    ├── main/
    │   ├── java/com/mikemes/audit/
    │   │   ├── domain/
    │   │   │   ├── AuditRecord.java            # @Entity @Immutable — audit_records table
    │   │   │   └── AuthAuditRecord.java         # @Entity @Immutable — auth_audit_records
    │   │   ├── envers/
    │   │   │   ├── MesRevisionEntity.java       # @RevisionEntity with userId + serviceSource
    │   │   │   └── MesRevisionListener.java     # Populates userId from SecurityContext
    │   │   ├── checksum/
    │   │   │   └── ChecksumService.java         # SHA-256 compute + verify
    │   │   └── autoconfigure/
    │   │       └── AuditAutoConfiguration.java  # @EnableEnversRepositories + ValidityAuditStrategy
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/mikemes/audit/
            ├── checksum/ChecksumServiceTest.java
            └── envers/MesRevisionListenerTest.java

libs/lib-common-events/
├── build.gradle                       # java-library + maven-publish to mavenLocal
└── src/
    ├── main/
    │   └── java/com/mikemes/events/
    │       ├── audit/
    │       │   └── AuditEventMessage.java       # record DTO (eventId, eventType, ...)
    │       └── KafkaTopics.java                  # constants: MES_AUDIT_EVENTS = "mes.audit.events"
    └── test/
        └── java/com/mikemes/events/
            └── audit/AuditEventMessageTest.java  # serialization round-trip test

libs/lib-keycloak-audit-spi/
├── build.gradle                       # plain java plugin (no Spring Boot)
└── src/
    ├── main/
    │   ├── java/com/mikemes/keycloak/audit/
    │   │   ├── AuditEventListenerProvider.java   # implements EventListenerProvider
    │   │   ├── AuditEventListenerProviderFactory.java
    │   │   └── KafkaAuditPublisher.java           # Kafka producer (no Spring)
    │   └── resources/
    │       └── META-INF/services/
    │           └── org.keycloak.events.EventListenerProviderFactory  # SPI registration
    └── test/
        └── java/com/mikemes/keycloak/audit/
            └── AuditEventListenerProviderTest.java

services/audit-service/
├── Dockerfile
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/mikemes/auditservice/
    │   │   ├── AuditServiceApplication.java
    │   │   ├── api/
    │   │   │   ├── AuditController.java           # GET /audit/entities/** + /audit/auth-events
    │   │   │   ├── VerificationController.java     # POST /audit/verify
    │   │   │   ├── AuditAccessInterceptor.java     # logs reads to audit log (Constitution §V)
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── dto/
    │   │   │       ├── AuditRecordDto.java
    │   │   │       ├── AuthAuditRecordDto.java
    │   │   │       ├── PagedAuditResponse.java
    │   │   │       └── VerificationResult.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java             # resource server; AUDIT_READ + SYSTEM_ADMIN
    │   │   │   ├── JpaConfig.java
    │   │   │   ├── KafkaConfig.java                # AckMode.MANUAL_IMMEDIATE, consumer factory
    │   │   │   └── WebMvcConfig.java               # register AuditAccessInterceptor
    │   │   ├── consumer/
    │   │   │   ├── AuditKafkaConsumer.java         # @KafkaListener mes.audit.events
    │   │   │   └── DuplicateEventHandler.java      # idempotent discard on event_id UNIQUE violation
    │   │   ├── health/
    │   │   │   └── KafkaConsumerLagHealthIndicator.java
    │   │   ├── repository/
    │   │   │   ├── AuditRecordRepository.java      # JPA repository (SELECT + INSERT)
    │   │   │   └── AuthAuditRecordRepository.java
    │   │   └── service/
    │   │       ├── AuditQueryService.java
    │   │       └── TamperVerificationService.java
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           ├── V001__create_audit_schema.sql
    │           └── V002__audit_roles.sql
    └── test/
        └── java/com/mikemes/auditservice/
            ├── integration/
            │   ├── consumer/AuditKafkaConsumerIT.java     # Testcontainers Kafka + PostgreSQL
            │   └── api/AuditControllerIT.java             # MockMvc + Testcontainers PostgreSQL
            └── unit/
                ├── consumer/AuditKafkaConsumerTest.java
                ├── service/AuditQueryServiceTest.java
                ├── service/TamperVerificationServiceTest.java
                ├── api/AuditControllerTest.java
                ├── api/VerificationControllerTest.java
                ├── api/GlobalExceptionHandlerTest.java
                └── health/KafkaConsumerLagHealthIndicatorTest.java

# Changes to existing files:
settings.gradle
    # Uncomment: include 'services:audit-service'
    # Add: include 'libs:lib-common-audit'
    #      include 'libs:lib-common-events'
    #      include 'libs:lib-keycloak-audit-spi'

docker/compose-infra.yml
    # Add: audit-service container (port 8090, healthcheck)
    # Add: volume mount for lib-keycloak-audit-spi.jar into keycloak container

docker/compose-infra.yml (keycloak service)
    # Add volume: ./libs/lib-keycloak-audit-spi/build/libs/lib-keycloak-audit-spi.jar:/opt/keycloak/providers/lib-keycloak-audit-spi.jar

keycloak/mikemes-realm.json
    # Add "mes-audit-listener" to eventsListeners array

services/gateway-service/src/main/resources/application.yml
    # Add route: /api/audit/** → http://audit-service:8090

.env.example
    # Add: AUDIT_SERVICE_DB_PASSWORD, AUDIT_FLYWAY_DB_PASSWORD,
    #      KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS, KEYCLOAK_AUDIT_KAFKA_TOPIC,
    #      AUDIT_HEALTH_KAFKA_LAG_THRESHOLD

sonar-project.properties
    # Add sources + tests for all 3 new libs + audit-service (after dirs exist)
```

---

## Complexity Tracking

*No Constitution Check violations requiring justification.*

| Note | Detail |
|---|---|
| Keycloak SPI is plain Java (no Spring Boot) | The SPI runs inside the Keycloak JVM where Spring Boot autoconfiguration is not available. The separate `lib-keycloak-audit-spi` Gradle module is the minimum necessary scope. |
| `audit` schema cross-boundary access | Domain services write to `_AUD` tables in their own schemas (managed by Envers + `lib-common-audit`). The `audit-service` reads from those `_AUD` tables via the `MesRevisionEntity` datasource. This is a deliberate narrow exception to the no-cross-schema rule — audit data is read-only from the audit-service perspective and the `mes_revisions` table lives in the `audit` schema. |

---

## PR Strategy

### Bundling Analysis

| User Story | Independent Test Available? | Key Dependency |
|---|---|---|
| US1 — Envers Entity Capture | Yes — after lib-common-audit + audit-service scaffold | lib-common-audit, lib-common-events, audit-service scaffold |
| US2 — Kafka Event Streaming | Yes — after audit-service core infra | Must bundle with US1 (same service, no standalone coverage anchor) |
| US3 — Keycloak Auth Capture | Yes — after lib-keycloak-audit-spi + audit-service auth endpoint | Depends on PR1 merged (auth_audit_records table must exist) |
| US4 — Query API | Yes — after US1+US2 merged | P2; depends on PR1 |
| US5 — Tamper-Evidence | Yes — after US1 merged | P2; can bundle with US4 |

**Bundling rules applied**:
- lib-common-audit, lib-common-events, lib-keycloak-audit-spi are all setup/foundational with no standalone tests — bundled with first user story.
- US1 (Envers) and US2 (Kafka consumer) share the same service scaffold and both write to the same `audit_records` table. US2 has no coverage anchor independent of US1's repository and entity — bundle into PR1.
- US3 (Keycloak SPI) can be independently tested once the `auth_audit_records` table exists from PR1. Separate PR2.
- US4 + US5 are P2 — separate PR3 raised after both P1 PRs merged.

### PR Table

| PR | Phases | Scope | Task Range | CI Anchor | Notes |
|---|---|---|---|---|---|
| PR1 | Foundation + US1 + US2 | lib-common-audit, lib-common-events, audit-service scaffold + Envers capture + Kafka consumer | T001–T035 | `./gradlew :libs:lib-common-audit:check :libs:lib-common-events:check :services:audit-service:check` | SonarCloud: add 3 new source paths to `sonar-project.properties` before push |
| PR2 | lib-keycloak-audit-spi + US3 | Keycloak SPI listener + auth event ingestion + auth query endpoint | T036–T055 | `./gradlew :libs:lib-keycloak-audit-spi:check :services:audit-service:check` | Depends on PR1 merged to Develop; Keycloak container volume mount added |
| PR3 | US4 + US5 | Query API pagination/filtering + Tamper-evidence verification endpoint | T056–T080 | `./gradlew :services:audit-service:check` | P2 — raise after PR1 + PR2 both merged; no new modules, only audit-service additions |

**Sequencing note**: PR2 and PR3 are strictly sequential behind PR1. PR3 is independently raisable from PR2 — if PR2 is delayed (e.g. Keycloak SPI complexity), PR3 (P2 query/verify features) can wait or be reprioritised without blocking.
