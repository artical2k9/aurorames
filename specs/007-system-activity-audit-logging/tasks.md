---
description: "Task list for MES-7 — System Activity & Audit Logging"
---

# Tasks: System Activity & Audit Logging (MES-7)

**Input**: Design documents from `specs/007-system-activity-audit-logging/`
**Jira Epic**: [MES-7](https://artical.atlassian.net/browse/MES-7)
**Branch**: `007-system-activity-audit-logging`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/audit-service-api.yaml ✅ | quickstart.md ✅

**Constitution §II — TDD**: Tests are written BEFORE implementation. Each test phase precedes its implementation phase within every user story. Confirm tests FAIL before adding implementation code.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no blocking dependencies)
- **[Story]**: User story label (US1–US5)

---

## PR Strategy

*(Reproduced from plan.md)*

| PR | Phases | Scope | Task Range | CI Anchor | Notes |
|---|---|---|---|---|---|
| PR1 | Foundation + US1 + US2 | lib-common-events, lib-common-audit, audit-service scaffold, Envers capture, Kafka consumer | T001–T047 | `./gradlew :libs:lib-common-audit:check :libs:lib-common-events:check :services:audit-service:check` | Add 2 new source paths to sonar-project.properties before push |
| PR2 | lib-keycloak-audit-spi + US3 | Keycloak SPI listener, auth event ingestion, auth query endpoint | T048–T062 | `./gradlew :libs:lib-keycloak-audit-spi:check :services:audit-service:check` | Depends on PR1 merged; Keycloak volume mount + realm eventsListeners update |
| PR3 | US4 + US5 + Polish | Query API pagination, tamper-evidence, gateway route, Docker Compose | T063–T088 | `./gradlew :services:audit-service:check` | P2 stories; raise after PR1 + PR2 merged |

**Sequencing note**: PR2 and PR3 are independently sequenced behind PR1. PR3 (P2 features) can be reprioritised without blocking PR2.

---

## Phase 1: Setup [PR1]

**Purpose**: Gradle project structure and toolchain for all new modules. No implementation yet — just scaffolding.

- [X] T001 Update `settings.gradle` — uncomment `include 'services:audit-service'`; add `include 'libs:lib-common-audit'`, `'libs:lib-common-events'`, `'libs:lib-keycloak-audit-spi'`
- [X] T002 Create `libs/lib-common-events/build.gradle` — `java-library` plugin, Spring Boot BOM dependency management, `maven-publish` block targeting mavenLocal; create `src/main/java/com/mikemes/events/` and `src/test/java/` directories
- [X] T003 [P] Create `libs/lib-common-audit/build.gradle` — `java-library` plugin, dependencies: `spring-boot-starter-data-jpa`, `hibernate-envers`, `spring-boot-starter-security`, `spring-boot-autoconfigure`; `maven-publish` block; Checkstyle + SpotBugs wired to `check` task
- [X] T004 [P] Create `libs/lib-keycloak-audit-spi/build.gradle` — plain `java` plugin (no Spring Boot); dependencies: keycloak-server-spi (provided), `org.apache.kafka:kafka-clients`; configure `jar` task; Checkstyle + SpotBugs
- [X] T005 Create `services/audit-service/build.gradle` — `org.springframework.boot` plugin; dependencies: `project(':libs:lib-common-security')`, `project(':libs:lib-common-audit')`, `project(':libs:lib-common-events')`, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `spring-kafka`, `flyway-core`, `flyway-postgres`, `postgresql`, `springdoc-openapi-webmvc`, `spring-boot-starter-test`, `testcontainers-junit`, `testcontainers-postgresql`, `testcontainers-kafka`; Checkstyle + SpotBugs wired to `check`
- [X] T006 Create `services/audit-service/Dockerfile` — multi-stage Eclipse Temurin 21; copy assembled JAR; expose port 8090; ENTRYPOINT `java -jar`
- [X] T007 [P] Add Checkstyle configuration files for new modules (`checkstyle.xml` pointing to shared config if present; otherwise copy from `services/platform-service`)

**Checkpoint**: All four new Gradle subprojects resolve cleanly — `./gradlew projects` shows them; `./gradlew :libs:lib-common-events:dependencies` completes without error.

---

## Phase 2: Foundational [PR1]

**Purpose**: Empty stub classes, Flyway migrations, and service bootstrap that the US1–US2 tests will compile against.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete — test compilation depends on these stubs.

- [X] T008 Create `libs/lib-common-events/src/main/java/com/mikemes/events/audit/AuditEventMessage.java` — Java record with all fields from data-model.md: `eventId`, `eventType`, `serviceSource`, `entityType`, `entityId`, `userId`, `timestamp`, `action`, `payload`, `schemaVersion`; include Jackson annotations for JSON serialisation
- [X] T009 [P] Create `libs/lib-common-events/src/main/java/com/mikemes/events/KafkaTopics.java` — `public final class KafkaTopics` with constant `MES_AUDIT_EVENTS = "mes.audit.events"`
- [X] T010 Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/domain/AuditRecord.java` — stub `@Entity @Immutable @Table(schema="audit", name="audit_records")` class with all fields from data-model.md; no-arg constructor; getters only (no setters — immutable); use `@Column` mappings
- [X] T011 [P] Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/domain/AuthAuditRecord.java` — stub `@Entity @Immutable @Table(schema="audit", name="auth_audit_records")` with all Keycloak event fields from data-model.md
- [X] T012 Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/envers/MesRevisionEntity.java` — stub `@Entity @RevisionEntity(MesRevisionListener.class) @Table(schema="audit", name="mes_revisions")` extending `DefaultRevisionEntity` with `userId` and `serviceSource` String fields
- [X] T013 Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/envers/MesRevisionListener.java` — stub implementing `RevisionListener`; empty `newRevision(Object)` for now
- [X] T014 Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/checksum/ChecksumService.java` — stub with `String compute(AuditRecord r)` returning empty string; `boolean verify(AuditRecord r)` returning false
- [X] T015 [P] Create `libs/lib-common-audit/src/main/java/com/mikemes/audit/autoconfigure/AuditAutoConfiguration.java` — stub `@Configuration @AutoConfiguration` class; empty for now
- [X] T016 Create `libs/lib-common-audit/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — single line: `com.mikemes.audit.autoconfigure.AuditAutoConfiguration`
- [X] T017 Publish stubs to mavenLocal: `./gradlew :libs:lib-common-events:publishToMavenLocal :libs:lib-common-audit:publishToMavenLocal`
- [X] T018 Create `services/audit-service/src/main/java/com/mikemes/auditservice/AuditServiceApplication.java` — `@SpringBootApplication` main class
- [X] T019 Create `services/audit-service/src/main/resources/application.yml` — configure: `server.port=8090`, `spring.application.name=audit-service`, `spring.jpa.properties.org.hibernate.envers.audit_strategy=org.hibernate.envers.strategy.ValidityAuditStrategy`, datasource placeholders, actuator health endpoint exposed, SBA client registration URL
- [X] T020 Create `services/audit-service/src/main/resources/db/migration/V001__create_audit_schema.sql` — full DDL from data-model.md: `CREATE SCHEMA IF NOT EXISTS audit`; `mes_revisions`, `audit_records`, `auth_audit_records` tables with all columns, PKs, UNIQUE constraints, and all indexes
- [X] T021 Create `services/audit-service/src/main/resources/db/migration/V002__audit_roles.sql` — create `audit_service` and `audit_flyway` PostgreSQL roles; `GRANT USAGE ON SCHEMA audit`; `GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA audit TO audit_service`; `REVOKE UPDATE, DELETE ON audit.audit_records FROM PUBLIC`; `REVOKE UPDATE, DELETE ON audit.auth_audit_records FROM PUBLIC`
- [X] T022 Create `services/audit-service/src/main/java/com/mikemes/auditservice/config/SecurityConfig.java` — resource server; `AUDIT_READ` and `SYSTEM_ADMIN` role beans; permit `/actuator/health`
- [X] T023 [P] Create `services/audit-service/src/main/java/com/mikemes/auditservice/config/JpaConfig.java` — `@EnableJpaRepositories`, `@EntityScan` pointing to lib-common-audit entities + local audit-service entities
- [X] T024 Create `services/audit-service/src/main/java/com/mikemes/auditservice/repository/AuditRecordRepository.java` — `JpaRepository<AuditRecord, UUID>` with `findByEntityTypeAndEntityId` + date-range query methods
- [X] T025 [P] Create `services/audit-service/src/main/java/com/mikemes/auditservice/repository/AuthAuditRecordRepository.java` — `JpaRepository<AuthAuditRecord, UUID>` with `findByUserIdAndOccurredAtBetween` query method

**Checkpoint**: Foundation stubs compile — `./gradlew :services:audit-service:compileJava` succeeds with no errors.

---

## Phase 3: User Story 1 — Domain Entity Change Capture (Priority: P1) 🎯 [PR1]

**Goal**: `lib-common-audit` fully implements Hibernate Envers configuration with `ValidityAuditStrategy`, `MesRevisionEntity` populated with the current user's identity, and `ChecksumService` that computes and verifies SHA-256 record checksums.

**Independent Test**: `./gradlew :libs:lib-common-audit:check` passes with all tests green. A test entity annotated `@Audited` creates revision rows in `mes_revisions_AUD` with correct `userId`; `ChecksumService.verify()` returns `false` when any record field is changed.

### Tests for User Story 1 ⚠️ Write these FIRST — confirm FAIL before implementing

- [X] T026 [P] [US1] Write `MesRevisionListenerTest` in `libs/lib-common-audit/src/test/java/com/mikemes/audit/envers/MesRevisionListenerTest.java` — mock `SecurityContextHolder` with a JWT principal; call `newRevision(MesRevisionEntity)`; assert `userId` equals JWT subject; assert fallback `"system:test"` when no principal present
- [X] T027 [P] [US1] Write `ChecksumServiceTest` in `libs/lib-common-audit/src/test/java/com/mikemes/audit/checksum/ChecksumServiceTest.java` — build a known `AuditRecord`; call `compute()`; assert 64-character hex string; call `verify()` → true; mutate `userId` field; call `verify()` → false
- [X] T028 [US1] Write `AuditRecordImmutabilityTest` in `libs/lib-common-audit/src/test/java/com/mikemes/audit/domain/AuditRecordImmutabilityTest.java` — assert no setter methods on `AuditRecord` or `AuthAuditRecord` via reflection; confirm `@Immutable` annotation present

### Implementation for User Story 1

- [X] T029 [US1] Implement `MesRevisionListener.newRevision(Object)` — extract `Authentication` from `SecurityContextHolder`; set `userId = auth.getName()` or `"system:" + ServiceIdentityContext.getCurrentService()`; set `serviceSource` from `spring.application.name` env — `libs/lib-common-audit/src/main/java/com/mikemes/audit/envers/MesRevisionListener.java`
- [X] T030 [P] [US1] Create `ServiceIdentityContext.java` — `ThreadLocal<String>` for system-job service identity; `static void set(String)`, `static String get()`, `static void clear()` — `libs/lib-common-audit/src/main/java/com/mikemes/audit/envers/ServiceIdentityContext.java`
- [X] T031 [US1] Implement `ChecksumService.compute(AuditRecord)` — build canonical string using `ObjectMapper` with `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`; return hex SHA-256 of canonical string — `libs/lib-common-audit/src/main/java/com/mikemes/audit/checksum/ChecksumService.java`
- [X] T032 [US1] Implement `ChecksumService.verify(AuditRecord)` — re-compute canonical checksum; compare to `record.getChecksum()`; log WARN on mismatch — `libs/lib-common-audit/src/main/java/com/mikemes/audit/checksum/ChecksumService.java`
- [X] T033 [US1] Implement `AuditAutoConfiguration` — declare `@Bean ValidityAuditStrategy`; declare `@Bean MesRevisionListener`; set Hibernate property `org.hibernate.envers.audit_strategy` via `HibernatePropertiesCustomizer` — `libs/lib-common-audit/src/main/java/com/mikemes/audit/autoconfigure/AuditAutoConfiguration.java`
- [X] T034 [US1] Re-publish updated `lib-common-audit` to mavenLocal: `./gradlew :libs:lib-common-audit:publishToMavenLocal`
- [X] T035 [US1] Run `./gradlew :libs:lib-common-audit:check` — confirm all 3 test classes pass, SpotBugs zero bugs, Checkstyle zero violations

**Checkpoint**: `./gradlew :libs:lib-common-audit:check :libs:lib-common-events:check` both green. US1 independently verified.

---

## Phase 4: User Story 2 — Cross-Service Audit Event Streaming (Priority: P1) [PR1]

**Goal**: `audit-service` consumes `mes.audit.events` Kafka topic, persists `AuditRecord` entries within 5 seconds, deduplicates on `event_id`, and reports Kafka consumer lag via the `/actuator/health` endpoint.

**Independent Test**: Publish an `AuditEventMessage` JSON to `mes.audit.events` via `kafka-console-producer`; assert `audit_records` row appears within 5 s; publish same `eventId` again; assert still exactly one row. Health endpoint shows `"kafka": {"status":"UP"}` when lag ≤ threshold.

### Tests for User Story 2 ⚠️ Write these FIRST — confirm FAIL before implementing

- [X] T036 [P] [US2] Write `AuditKafkaConsumerTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/consumer/AuditKafkaConsumerTest.java` — mock `AuditRecordRepository`; call `consume(message, ack)` with valid `AuditEventMessage`; assert `repository.save()` called once; assert `ack.acknowledge()` called
- [X] T037 [P] [US2] Write `DuplicateEventHandlerTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/consumer/DuplicateEventHandlerTest.java` — simulate `DataIntegrityViolationException` on save; call handler; assert no exception propagated; assert `ack.acknowledge()` still called
- [X] T038 [P] [US2] Write `KafkaConsumerLagHealthIndicatorTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/health/KafkaConsumerLagHealthIndicatorTest.java` — mock `AdminClient`; configure lag=500 below threshold → `UP`; lag=1500 above threshold → `DOWN`
- [X] T039 [US2] Write `AuditKafkaConsumerIT` in `services/audit-service/src/test/java/com/mikemes/auditservice/integration/consumer/AuditKafkaConsumerIT.java` — `@SpringBootTest` with `@Testcontainers` (PostgreSQL + Kafka); publish 1 valid event; `await().atMost(5, SECONDS)` for record in DB; publish duplicate; assert count remains 1

### Implementation for User Story 2

- [X] T040 [US2] Create `KafkaConfig.java` — `ConsumerFactory` with `AckMode.MANUAL_IMMEDIATE`, `enable-auto-commit=false`, `auto-offset-reset=earliest`, deserializer `JsonDeserializer<AuditEventMessage>`; `ConcurrentKafkaListenerContainerFactory` — `services/audit-service/src/main/java/com/mikemes/auditservice/config/KafkaConfig.java`
- [X] T041 [US2] Update `application.yml` — add `spring.kafka.consumer.*` properties: `bootstrap-servers`, `group-id=audit-service-group`, `enable-auto-commit=false`; add `audit.health.kafka-lag-threshold=1000`
- [X] T042 [US2] Implement `DuplicateEventHandler` — `public void handleDuplicate(AuditEventMessage msg, Acknowledgment ack)`: log DEBUG "duplicate event_id {}", call `ack.acknowledge()` — `services/audit-service/src/main/java/com/mikemes/auditservice/consumer/DuplicateEventHandler.java`
- [X] T043 [US2] Implement `AuditKafkaConsumer` — `@KafkaListener(topics = KafkaTopics.MES_AUDIT_EVENTS)`; map `AuditEventMessage` → `AuditRecord` (set `checksum` via `ChecksumService.compute()`); call `repository.save()`; catch `DataIntegrityViolationException` → delegate to `DuplicateEventHandler`; call `ack.acknowledge()` on success — `services/audit-service/src/main/java/com/mikemes/auditservice/consumer/AuditKafkaConsumer.java`
- [X] T044 [US2] Implement `KafkaConsumerLagHealthIndicator` — inject `AdminClient`; `health()`: compute total lag for group `audit-service-group`; return `Health.up()` or `Health.down()` based on threshold — `services/audit-service/src/main/java/com/mikemes/auditservice/health/KafkaConsumerLagHealthIndicator.java`
- [X] T045 [US2] Update `sonar-project.properties` — add `services/audit-service/src/main/java` and `services/audit-service/src/test/java` to `sonar.sources` and `sonar.tests`; add `libs/lib-common-audit/src/main/java` and `libs/lib-common-events/src/main/java` similarly
- [X] T046 [US2] Annotate test utility methods with `@SuppressWarnings("unchecked")` where generic casts are needed per ERR-MES-003; ensure no raw type warnings in audit-service tests
- [X] T047 [US2] Run `./gradlew :services:audit-service:check` — all unit + IT tests green; SpotBugs zero bugs; Checkstyle zero violations

**Checkpoint**: US1 + US2 fully implemented and tested. `./gradlew :libs:lib-common-audit:check :libs:lib-common-events:check :services:audit-service:check` all green.

> **Raise PR1 after this checkpoint** (T001–T047) | CI: `./gradlew :libs:lib-common-audit:check :libs:lib-common-events:check :services:audit-service:check` | Target: `Develop`

---

## Phase 5: lib-keycloak-audit-spi + User Story 3 — Authentication & Session Audit (Priority: P1) [PR2]

**Goal**: A custom Keycloak Event Listener SPI publishes LOGIN, LOGOUT, LOGIN_ERROR, and admin role-mapping events to `mes.audit.events`. `audit-service` consumes them and persists `AuthAuditRecord` entries. `GET /audit/auth-events` returns paginated results filtered by userId/eventType/date.

**Independent Test**: Start Keycloak with the SPI JAR mounted. Log in via Keycloak. Call `GET /audit/auth-events?userId={id}` and confirm LOGIN event with IP, timestamp, and clientId.

### Tests for User Story 3 ⚠️ Write these FIRST — confirm FAIL before implementing

- [X] T048 [P] [US3] Write `AuditEventListenerProviderTest` in `libs/lib-keycloak-audit-spi/src/test/java/com/mikemes/keycloak/audit/AuditEventListenerProviderTest.java` — mock `KafkaAuditPublisher`; call `onEvent(loginEvent)`; assert `publish()` called with `eventType=AUTH_EVENT`, `action=AUTH`, `entityType=USER`; assert `userId` equals Keycloak `event.getUserId()`
- [X] T049 [P] [US3] Write `KafkaAuditPublisherTest` in `libs/lib-keycloak-audit-spi/src/test/java/com/mikemes/keycloak/audit/KafkaAuditPublisherTest.java` — mock Kafka `Producer`; call `publish(message)`; assert `ProducerRecord` sent to topic `mes.audit.events`; assert `eventId` non-null UUID
- [X] T050 [US3] Write `AuthAuditKafkaConsumerIT` in `services/audit-service/src/test/java/com/mikemes/auditservice/integration/consumer/AuthAuditKafkaConsumerIT.java` — Testcontainers Kafka + PostgreSQL; publish `AuditEventMessage` with `eventType=AUTH_EVENT`; assert `AuthAuditRecord` row persisted in `audit.auth_audit_records`

### Implementation for User Story 3

- [X] T051 [US3] Create `libs/lib-keycloak-audit-spi/src/main/java/com/mikemes/keycloak/audit/KafkaAuditPublisher.java` — plain Java; `Properties` from env vars (`KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_AUDIT_KAFKA_TOPIC`); `KafkaProducer<String, String>`; `publish(AuditEventMessage)` serializes to JSON via Jackson; partition key = `entityType + ":" + entityId`
- [X] T052 [US3] Create `libs/lib-keycloak-audit-spi/src/main/java/com/mikemes/keycloak/audit/AuditEventListenerProvider.java` — implements `EventListenerProvider`; `onEvent(Event e)` → map to `AuditEventMessage` (eventType=AUTH_EVENT, action=AUTH, entityType="USER", entityId=e.getUserId(), userId=e.getUserId(), details=e.getDetails()); call `publisher.publish()`; `onEvent(AdminEvent a)` → map similarly for role-mapping events
- [X] T053 [US3] Create `libs/lib-keycloak-audit-spi/src/main/java/com/mikemes/keycloak/audit/AuditEventListenerProviderFactory.java` — implements `EventListenerProviderFactory`; `getId()` returns `"mes-audit-listener"`; `create(KeycloakSession)` returns `AuditEventListenerProvider`; `init(Config.Scope)` initialises `KafkaAuditPublisher`
- [X] T054 [US3] Create `libs/lib-keycloak-audit-spi/src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory` — single line: `com.mikemes.keycloak.audit.AuditEventListenerProviderFactory`
- [X] T055 [US3] Update `docker/compose-infra.yml` — add volume mount to Keycloak service: `./libs/lib-keycloak-audit-spi/build/libs/lib-keycloak-audit-spi.jar:/opt/keycloak/providers/lib-keycloak-audit-spi.jar:ro`; add env vars `KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS` and `KEYCLOAK_AUDIT_KAFKA_TOPIC`
- [X] T056 [P] [US3] Update `keycloak/mikemes-realm.json` — add `"mes-audit-listener"` to the `eventsListeners` array
- [X] T057 [US3] Extend `AuditKafkaConsumer` to route `AUTH_EVENT` messages — when `message.eventType().equals("AUTH_EVENT")` → map to `AuthAuditRecord` and call `AuthAuditRecordRepository.save()` instead — `services/audit-service/src/main/java/com/mikemes/auditservice/consumer/AuditKafkaConsumer.java`
- [X] T058 [US3] Write `AuthEventHandlerTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/consumer/AuthEventHandlerTest.java` — verify AUTH_EVENT type routes to `AuthAuditRecordRepository.save()` and not `AuditRecordRepository.save()`
- [X] T059 [US3] Add `GET /audit/auth-events` to `AuditController` — `@PreAuthorize("hasRole('AUDIT_READ') or hasRole('SYSTEM_ADMIN')")`; accept `userId`, `eventType`, `from`, `to`, `page`, `size` params; delegate to `AuditQueryService.findAuthEvents()`; return `PagedAuthResponse` — `services/audit-service/src/main/java/com/mikemes/auditservice/api/AuditController.java`
- [X] T060 [P] [US3] Write `AuditControllerTest` additions in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/api/AuditControllerTest.java` — assert `GET /audit/auth-events` returns 200 with mocked `AuthAuditRecordDto` list; assert 403 when no AUDIT_READ role
- [X] T061 [US3] Update `sonar-project.properties` — add `libs/lib-keycloak-audit-spi/src/main/java` and `libs/lib-keycloak-audit-spi/src/test/java`
- [X] T062 [US3] Run `./gradlew :libs:lib-keycloak-audit-spi:check :services:audit-service:check` — all tests green, zero lint violations

**Checkpoint**: US3 fully implemented. Keycloak SPI JAR built. Auth events ingested and queryable.

> **Raise PR2 after this checkpoint** (T048–T062) | CI: `./gradlew :libs:lib-keycloak-audit-spi:check :services:audit-service:check` | Target: `Develop`

---

## Phase 6: User Story 4 — Audit Trail Query API (Priority: P2) [PR3]

**Goal**: Paginated REST endpoints for entity history and cross-entity queries. RBAC enforced. Response time < 2s for 30-day window. `AuditAccessInterceptor` logs all read requests (Constitution §V).

**Independent Test**: Seed 1,000 `audit_records` via Testcontainers. Query with 7-day date range; assert response < 2s and correct pagination. Call without AUDIT_READ role; assert HTTP 403.

### Tests for User Story 4 ⚠️ Write these FIRST — confirm FAIL before implementing

- [X] T063 [P] [US4] Write `AuditQueryServiceTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/service/AuditQueryServiceTest.java` — mock `AuditRecordRepository`; call `findEntityHistory("WorkOrder", "id-1", from, to, pageable)`; assert `findByEntityTypeAndEntityIdAndOccurredAtBetween()` called with correct args; assert result mapped to `AuditRecordDto`
- [X] T064 [P] [US4] Write `AuditControllerTest` entity endpoint cases in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/api/AuditControllerTest.java` — `GET /audit/entities/WorkOrder/id-1/history` returns 200 with mocked records; `GET /audit/entities` with filters returns 200; no-role caller returns 403
- [X] T065 [US4] Write `AuditControllerIT` in `services/audit-service/src/test/java/com/mikemes/auditservice/integration/api/AuditControllerIT.java` — Testcontainers PostgreSQL; seed 1000 records spanning 30 days; query 7-day window; assert correct count; assert sorted descending by `occurred_at`; measure response time < 2s; assert 403 for unauthenticated caller

### Implementation for User Story 4

- [X] T066 [US4] Implement `AuditQueryService` — `findEntityHistory(String entityType, String entityId, OffsetDateTime from, OffsetDateTime to, Pageable p)` and `findAuditRecords(AuditFilter filter, Pageable p)` using JPA dynamic queries or `Specification`; map `AuditRecord` → `AuditRecordDto` — `services/audit-service/src/main/java/com/mikemes/auditservice/service/AuditQueryService.java`
- [X] T067 [US4] Implement `AuditController GET /audit/entities/{entityType}/{entityId}/history` — `@PreAuthorize`; validate `from`/`to` params; delegate to `AuditQueryService`; return `PagedAuditResponse` — `services/audit-service/src/main/java/com/mikemes/auditservice/api/AuditController.java`
- [X] T068 [P] [US4] Implement `AuditController GET /audit/entities` — cross-entity query endpoint with all filters; paginated — `services/audit-service/src/main/java/com/mikemes/auditservice/api/AuditController.java`
- [X] T069 [P] [US4] Create `AuditRecordDto`, `AuthAuditRecordDto`, `PagedAuditResponse`, `PagedAuthResponse` DTOs in `services/audit-service/src/main/java/com/mikemes/auditservice/api/dto/` — plain Java records/classes with Jackson annotations
- [X] T070 [US4] Implement `AuditAccessInterceptor` — `HandlerInterceptor.afterCompletion()` for all `/audit/**` paths; build an `AuditEventMessage` with `eventType=AUTH_EVENT`, `action=AUTH`, `entityType=AUDIT_ACCESS`, `entityId=requestUri`, `userId=principal`; publish to `AuditRecordRepository.save()` — `services/audit-service/src/main/java/com/mikemes/auditservice/api/AuditAccessInterceptor.java`
- [X] T071 [US4] Create `WebMvcConfig.java` — `@Configuration implements WebMvcConfigurer`; `addInterceptors()` registers `AuditAccessInterceptor` for path pattern `/audit/**` — `services/audit-service/src/main/java/com/mikemes/auditservice/config/WebMvcConfig.java`
- [X] T072 [P] [US4] Implement `GlobalExceptionHandler` — `@RestControllerAdvice`; handle `MethodArgumentTypeMismatchException` → 400; `AccessDeniedException` → 403; `ConstraintViolationException` → 400; return `ErrorResponse` DTO — `services/audit-service/src/main/java/com/mikemes/auditservice/api/GlobalExceptionHandler.java`
- [X] T073 [P] [US4] Write `AuditAccessInterceptorTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/api/AuditAccessInterceptorTest.java` — mock `AuditRecordRepository`; simulate GET request to `/audit/entities/...`; assert save called with correct `entityType=AUDIT_ACCESS`
- [X] T074 [P] [US4] Write `GlobalExceptionHandlerTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/api/GlobalExceptionHandlerTest.java` — assert 400 on bad date param; assert 403 on access denied

**Checkpoint**: Query API complete. Entity history and cross-entity queries working with RBAC. Audit access interception wired.

---

## Phase 7: User Story 5 — Tamper-Evidence Verification (Priority: P2) [PR3]

**Goal**: `POST /audit/verify?from=&to=` re-computes SHA-256 checksums for all `audit_records` in the window and reports violations. Requires SYSTEM_ADMIN role. WARN log emitted on any violation.

**Independent Test**: Write 100 records. Run verify → PASS. Manually UPDATE one record's `userId` via direct DB connection. Run verify → FAIL with that record's ID in violations list.

### Tests for User Story 5 ⚠️ Write these FIRST — confirm FAIL before implementing

- [X] T075 [P] [US5] Write `TamperVerificationServiceTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/service/TamperVerificationServiceTest.java` — mock `AuditRecordRepository` returning 5 clean records → result is PASS; return 5 records with one having wrong checksum → result is FAIL with 1 violation containing correct `recordId` and `CHECKSUM_MISMATCH` reason
- [X] T076 [P] [US5] Write `VerificationControllerTest` in `services/audit-service/src/test/java/com/mikemes/auditservice/unit/api/VerificationControllerTest.java` — mock `TamperVerificationService`; assert 200 with PASS result; assert 403 for caller with only AUDIT_READ role (SYSTEM_ADMIN required)

### Implementation for User Story 5

- [X] T077 [US5] Create `VerificationResult` and `VerificationViolation` DTOs in `services/audit-service/src/main/java/com/mikemes/auditservice/api/dto/VerificationResult.java` — `VerificationResult(String status, long recordsChecked, List<VerificationViolation> violations, OffsetDateTime verifiedAt)`; `VerificationViolation(UUID recordId, String reason, String expectedChecksum, String actualChecksum)`
- [X] T078 [US5] Implement `TamperVerificationService` — page through `audit_records` in time window in batches of 500; for each record re-compute `ChecksumService.compute(r)`; compare to `r.getChecksum()`; collect `VerificationViolation` on mismatch; emit `log.warn("TAMPER_DETECTED recordId={} expected={} actual={}")` per violation; return `VerificationResult` — `services/audit-service/src/main/java/com/mikemes/auditservice/service/TamperVerificationService.java`
- [X] T079 [US5] Implement `VerificationController POST /audit/verify` — `@PreAuthorize("hasRole('SYSTEM_ADMIN')")`; validate `from` < `to`; delegate to `TamperVerificationService.verify(from, to)`; return `VerificationResult` — `services/audit-service/src/main/java/com/mikemes/auditservice/api/VerificationController.java`
- [X] T080 [US5] Run `./gradlew :services:audit-service:check` — all tests green (US1–US5), zero lint violations

**Checkpoint**: All five user stories implemented and tested. US4+US5 independently verified.

---

## Phase 8: Polish & Cross-Cutting Concerns [PR3 continued]

**Purpose**: Wire audit-service into the Docker Compose service mesh, configure the gateway route, and update all infrastructure manifests.

- [X] T081 Add gateway route to `services/gateway-service/src/main/resources/application.yml` — `- id: audit-service` with `uri: http://audit-service:8090`, predicates `Path=/api/audit/**`, filter `StripPrefix=2`
- [X] T082 Add `audit-service` to `docker/compose-infra.yml` — `image`, `container_name: audit-service`, `ports: ["8090:8090"]`, `environment` block with DB + Kafka + Keycloak vars, `depends_on: [postgres, kafka, keycloak]`, `healthcheck: test: curl -f http://localhost:8090/actuator/health`
- [X] T083 [P] Update `.env.example` — add all new audit-service variables: `AUDIT_SERVICE_DB_PASSWORD`, `AUDIT_FLYWAY_DB_PASSWORD`, `AUDIT_HEALTH_KAFKA_LAG_THRESHOLD=1000`, `KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS`, `KEYCLOAK_AUDIT_KAFKA_TOPIC=mes.audit.events`; add generation instructions (ERR-MES-016: `.env.example` must stay in sync with compose file)
- [X] T084 [P] Add `audit-service` Spring Boot Admin client config to `services/audit-service/src/main/resources/application.yml` — `spring.boot.admin.client.url=${SBA_URL}`
- [X] T085 Run `./gradlew check` — full multi-module build; all tests pass; zero lint violations across all modules
- [ ] T086 [P] Validate `quickstart.md` steps locally — run `docker compose up -d`, build SPI JAR, smoke-test Kafka event ingestion, smoke-test tamper-evidence verify endpoint

> **Raise PR3 after this checkpoint** (T063–T086) | CI: `./gradlew :services:audit-service:check` | Target: `Develop`

---

## Phase 9: Compliance Verification & Defect Closure

**Purpose**: Mandatory per Constitution §II and §IV. Run before transitioning MES-7 to Done.

- [ ] T087 Verify all Constitution Check gates in `specs/007-system-activity-audit-logging/plan.md` show ✅ PASS — update Gate III to ✅ PASS after human plan approval; update Gate V note to reflect `AuditAccessInterceptor` implementation
- [ ] T088 [P] Confirm audit trail immutability at DB layer — attempt `UPDATE audit.audit_records SET user_id='tampered'` using `audit_service` credentials; assert `ERROR: permission denied`
- [ ] T089 [P] Confirm Kafka consumer idempotency — publish identical `eventId` twice; assert `count(*) from audit.audit_records where event_id = ?` returns 1
- [ ] T090 [P] Confirm `tenant_id` column present on `audit_records` and `auth_audit_records` — run `SELECT column_name FROM information_schema.columns WHERE table_schema='audit'`; assert `tenant_id` present in both tables
- [ ] T091 Confirm AUDIT_READ privilege exists in `iam-service` privilege registry (platform module seed migration); add Flyway migration `V006__seed_audit_module_privileges.sql` to `services/iam-service/src/main/resources/db/migration/` if missing
- [ ] T092 [P] Compliance spot-check 21 CFR Part 11 §11.10(e) — verify: (a) all audit records have UTC timestamp; (b) no UPDATE/DELETE path exists for app user; (c) `checksum` populated on every record; confirm in integration test output
- [ ] T093 [P] Compliance spot-check CMMC AU.2.041 — verify log generation: run `./gradlew :services:audit-service:check`; confirm AuditKafkaConsumerIT produces records for all event types (CREATE, UPDATE, DELETE, AUTH)
- [ ] T094 Confirm all test failures logged during this feature's development are tracked as Jira defects and resolved (Constitution §II — no open defects before Done)
- [ ] T095 Run retrospective gate per CLAUDE.md — review this session's work for new errors or near-misses; update `docs/governance/MES-ERR-001_Agent_Error_Log.md` with any new entries; promote as applicable

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user story phases
- **Phase 3 (US1)**: Depends on Phase 2 — tests compile against Phase 2 stubs
- **Phase 4 (US2)**: Depends on Phase 2 — Kafka config + repository stubs needed; can start in parallel with US1 after Phase 2
- **Phase 5 (US3)**: Depends on PR1 merged (needs `auth_audit_records` table and `AuditKafkaConsumer` routing)
- **Phase 6 (US4)**: Depends on PR1 merged; can start in parallel with Phase 5
- **Phase 7 (US5)**: Depends on Phase 6 (`ChecksumService` in use); can bundle with Phase 6 in PR3
- **Phase 8 (Polish)**: Depends on US4 + US5 complete
- **Phase 9 (Compliance)**: Depends on all PRs merged to Develop

### User Story Dependencies

- **US1 (P1)**: After Phase 2 — no dependency on other US
- **US2 (P1)**: After Phase 2 — no dependency on US1 (shares service scaffold, no functional dependency)
- **US3 (P1)**: After PR1 merged — needs `auth_audit_records` table + routing in `AuditKafkaConsumer`
- **US4 (P2)**: After PR1 merged — needs audit records to exist for query tests
- **US5 (P2)**: After US4 partially complete — needs `ChecksumService` wired into save path

### Parallel Opportunities

- T002, T003, T004 — all three lib build files in parallel (Phase 1)
- T010, T011 — AuditRecord + AuthAuditRecord entity stubs in parallel (Phase 2)
- T026, T027, T028 — all three US1 test files in parallel (Phase 3)
- T036, T037, T038 — all three US2 test files in parallel (Phase 4)
- T048, T049 — both US3 SPI test files in parallel (Phase 5)
- T063, T064 — US4 service + controller unit tests in parallel (Phase 6)
- T075, T076 — both US5 test files in parallel (Phase 7)

---

## Parallel Example: User Story 2 (Kafka Consumer)

```bash
# Write all US2 test stubs in parallel (Phase 4 tests):
Task T036: AuditKafkaConsumerTest
Task T037: DuplicateEventHandlerTest
Task T038: KafkaConsumerLagHealthIndicatorTest

# After tests confirmed FAILING — implement in parallel:
Task T040: KafkaConfig.java
Task T042: DuplicateEventHandler.java  (no dependency on T040)
```

---

## Implementation Strategy

### MVP (PR1 — US1 + US2)

1. Complete Phase 1 (Setup) → scaffold compiles
2. Complete Phase 2 (Foundational) → stubs compile, migrations ready
3. Complete Phase 3 (US1 — Envers) → entity capture working
4. Complete Phase 4 (US2 — Kafka) → event streaming working
5. **STOP and VALIDATE**: `./gradlew :libs:lib-common-audit:check :services:audit-service:check` green → raise PR1

### Incremental Delivery

- PR1 merged → audit-service running; entity changes and Kafka events captured
- PR2 merged → Keycloak auth events captured; compliance picture complete for CMMC AU domain
- PR3 merged → compliance query API + tamper-evidence verification → MES-7 Done

### Parallel Team Strategy

After Phase 2 completes and PR1 is raised:
- **Dev A**: Phase 5 (lib-keycloak-audit-spi + US3 / PR2)
- **Dev B**: Phase 6 + 7 (US4 + US5 / PR3)
- Both raise PRs independently; no merge dependency between PR2 and PR3

---

## Summary

| Metric | Value |
|---|---|
| Total tasks | 95 |
| Phase 1 (Setup) | T001–T007 (7 tasks) |
| Phase 2 (Foundational) | T008–T025 (18 tasks) |
| Phase 3 (US1 — Envers) | T026–T035 (10 tasks) |
| Phase 4 (US2 — Kafka) | T036–T047 (12 tasks) |
| Phase 5 (US3 — Keycloak SPI) | T048–T062 (15 tasks) |
| Phase 6 (US4 — Query API) | T063–T074 (12 tasks) |
| Phase 7 (US5 — Tamper Evidence) | T075–T080 (6 tasks) |
| Phase 8 (Polish) | T081–T086 (6 tasks) |
| Phase 9 (Compliance) | T087–T095 (9 tasks) |
| Parallelisable tasks [P] | 35 |
| PRs | 3 (PR1: T001–T047 / PR2: T048–T062 / PR3: T063–T086) |
| MVP scope | PR1 (Phase 1–4): US1 + US2 — entity capture + Kafka streaming |
