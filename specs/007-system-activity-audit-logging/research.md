# Research: System Activity & Audit Logging (MES-7)

## R1 — Keycloak Event Integration Approach

**Decision**: Custom Keycloak Event Listener SPI that publishes to a Kafka topic.

**Rationale**:
- Keycloak 24+ supports Event Listener SPI via `org.keycloak.events.EventListenerProvider`. The provider receives both user events (LOGIN, LOGOUT, etc.) and admin events (role mapping changes, user creation).
- Publishing to `mes.audit.events` from within the SPI implementation decouples Keycloak from audit-service; Kafka provides durability if audit-service is temporarily down.
- Alternative A (HTTP webhook from Keycloak to audit-service directly): synchronous coupling; if audit-service is down, Keycloak event delivery fails. Rejected.
- Alternative B (Third-party Keycloak-Kafka extension): adds an uncontrolled external dependency; the SPI is only ~80 LOC and is fully testable. Rejected.
- Implementation: a new JAR `libs/lib-keycloak-audit-listener` deployed into the Keycloak providers directory via Docker volume mount.

**How to apply**: The SPI JAR must be built and mounted into the Keycloak container at `/opt/keycloak/providers/`. The Docker Compose file and `.env.example` must reference the volume mount path.

---

## R2 — Hibernate Envers Audit Strategy

**Decision**: `ValidityAuditStrategy` (not the default `DefaultAuditStrategy`).

**Rationale**:
- `ValidityAuditStrategy` stores a `REVEND` column (the revision at which the row became invalid), enabling efficient point-in-time queries without full table scans.
- Required for FR-013 (2-second query SLA over 10M records): without `REVEND`, finding all rows valid at a given timestamp requires scanning all revisions up to that point.
- Tradeoff: ~30% more write I/O per mutation (must UPDATE the previous revision row with `REVEND`). Acceptable given the compliance-driven read-heavy workload.
- Global configuration in `lib-common-audit` via `@EnableEnversRepositories` + `@org.hibernate.envers.AuditConfiguration`.

**How to apply**: Set `org.hibernate.envers.audit_strategy = org.hibernate.envers.strategy.ValidityAuditStrategy` in shared Hibernate properties. Each `_AUD` table gains `REV`, `REVEND`, `REVTYPE`, and `REVEND_TSTMP` columns.

---

## R3 — Custom RevisionEntity with userId

**Decision**: Custom `@RevisionEntity` class (`MesRevisionEntity`) extending `DefaultRevisionEntity` with `userId` and `serviceSource` fields, populated via a `RevisionListener`.

**Rationale**:
- Hibernate Envers RevisionListener is called within the same transaction as the audited mutation. The Spring Security `SecurityContextHolder` is available at that point — `Authentication.getName()` gives the subject claim.
- System-initiated jobs (no JWT principal) set `userId = "system:" + serviceName` using a thread-local `ServiceIdentityContext` that batch jobs populate before running.
- The `MesRevisionEntity` and `MesRevisionListener` are published as part of `lib-common-audit` so every domain service gets the same revision tracking without boilerplate.

**How to apply**: Domain services include `lib-common-audit`, which auto-configures `@RevisionEntity(MesRevisionEntity.class)` via Spring Boot autoconfiguration. No per-service configuration required.

---

## R4 — SHA-256 Checksum Canonicalization

**Decision**: Checksum computed over a canonical string: `"${id}|${eventType}|${entityType}|${entityId}|${userId}|${serviceSource}|${action}|${timestamp.toEpochMilli()}|${sha256(previousState)}|${sha256(newState)}"`, encoded as hex.

**Rationale**:
- Canonical form must be deterministic regardless of JSON field ordering in `previousState`/`newState`. Using SHA-256 of the JSON string representation of those fields (serialized with sorted keys via Jackson `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY`) ensures stability.
- The outer SHA-256 is computed at insert time and stored in the `checksum` column. Verification re-computes from stored fields and compares.
- Alternative (hash chaining): links each record's checksum to the previous record's checksum, making the chain sequentially verifiable but unparallelizable. Rejected for v1 — per-record checksums satisfy 21 CFR Part 11 §11.10(e) without the complexity penalty.

**How to apply**: A `ChecksumService` bean in `lib-common-audit` exposes `String compute(AuditRecord r)` and `boolean verify(AuditRecord r)`. The audit-service calls this before insert and during verification runs.

---

## R5 — PostgreSQL INSERT-only DB Role for Audit Tables

**Decision**: Flyway migration creates a dedicated `audit_app` PostgreSQL role with INSERT-only privileges on `audit_trail` tables; no UPDATE or DELETE granted. The Spring Boot data source uses a separate application role `audit_service` (which is a member of `audit_app`).

**Rationale**:
- GRANT INSERT ON ALL TABLES IN SCHEMA audit TO audit_app + REVOKE UPDATE, DELETE enforces immutability at the DB layer (FR-002).
- The `audit_service` role also has SELECT for query endpoints, but never UPDATE/DELETE.
- Flyway runs as a superuser role (separate `audit_flyway` role) to manage schema evolution — it has DDL rights but not used at runtime.
- This is consistent with the existing approach in other services but adds an explicit REVOKE step.

**How to apply**: `V002__audit_roles.sql` migration creates roles and grants. The `application.yml` `spring.datasource.username` for audit-service is `audit_service`. `.env.example` documents `AUDIT_SERVICE_DB_PASSWORD`.

---

## R6 — Kafka Consumer Idempotency

**Decision**: Unique constraint on `event_id` column in `audit_records`; consumer catches `DataIntegrityViolationException` on INSERT and silently acknowledges (idempotent discard).

**Rationale**:
- Spring Kafka default `AckMode.RECORD` commits offset after each successful listener call. For at-least-once delivery, a duplicate event arriving after an offset reset must be deduplicated.
- A unique index on `event_id` (UUID, already in `AuditEventMessage`) makes the duplicate check a O(log n) B-tree lookup at insert time.
- The consumer catches `DataIntegrityViolationException`, logs at DEBUG level, and returns normally (offset is committed). This avoids infinite retry on genuine duplicates.
- Alternative (select-before-insert): two round trips per event; rejected for performance.

**How to apply**: `V001__create_audit_schema.sql` includes `UNIQUE (event_id)`. `AuditKafkaConsumer` wraps the save call in a try-catch and delegates to `DuplicateEventHandler`.

---

## R7 — Kafka Consumer Offset Management

**Decision**: `AckMode.MANUAL_IMMEDIATE` with `@KafkaListener` + `Acknowledgment` parameter; offset committed only after successful `repository.save()`.

**Rationale**:
- Auto-commit (`AckMode.BATCH` or `AckMode.RECORD` with `enable.auto.commit=true`) risks committing an offset before the DB write completes, causing silent data loss on service restart (violates SC-006).
- `MANUAL_IMMEDIATE` commits only when the listener explicitly calls `ack.acknowledge()` — ensuring the DB record exists before the offset advances.
- On restart, uncommitted offsets are replayed and deduplicated by the `event_id` unique constraint (R6).

**How to apply**: Set `spring.kafka.listener.ack-mode=MANUAL_IMMEDIATE` and `spring.kafka.consumer.enable-auto-commit=false` in `application.yml`. Consumer group ID: `audit-service-group`.

---

## R8 — Consumer Lag Health Indicator

**Decision**: Custom `KafkaConsumerLagHealthIndicator` implementing Spring Boot `HealthIndicator`, reading consumer group lag via `AdminClient.listConsumerGroupOffsets()` and comparing against topic end offsets.

**Rationale**:
- Spring Boot Actuator does not expose Kafka consumer lag natively as a health indicator. A custom `HealthIndicator` bean reports `DOWN` when lag exceeds the configurable threshold (default: 1,000 messages).
- This satisfies FR-014 without requiring a Prometheus/Grafana setup in v1.

**How to apply**: Register `KafkaConsumerLagHealthIndicator` as a Spring bean. Configurable via `audit.health.kafka-lag-threshold` in `application.yml`.

---

## R9 — lib-common-audit vs audit-client Scope

**Decision**: Two shared libraries:
- `libs/lib-common-audit`: Hibernate Envers configuration (`MesRevisionEntity`, `MesRevisionListener`, `ValidityAuditStrategy` autoconfiguration). Consumed by all domain services that have `@Audited` entities.
- `libs/lib-common-events`: Kafka event schemas (`AuditEventMessage` DTO, `KafkaTopics` constants). Consumed by all domain services publishing to Kafka and by audit-service consuming from Kafka.

**Rationale**:
- Separating Envers config from Kafka schema allows services without Kafka to consume `lib-common-audit` without pulling in Kafka transitive dependencies.
- `lib-common-events` is the broader events library (not audit-specific) — it will also carry work order and other domain event schemas in future Epics.
- `lib-common-audit` provides: `MesRevisionEntity`, `MesRevisionListener`, `ChecksumService`, `AuditRecord` JPA entity, `AuthAuditRecord` JPA entity.
- `lib-common-events` provides: `AuditEventMessage`, `KafkaTopics` constants.

**How to apply**: Both libraries follow the `lib-common-security` pattern — `java-library` plugin, `maven-publish` to mavenLocal, Spring Boot autoconfiguration via `spring.factories` or `AutoConfiguration.imports`.

---

## R10 — Keycloak SPI Deployment Pattern

**Decision**: Build the SPI listener JAR as a separate Gradle module `libs/lib-keycloak-audit-spi` (plain Java JAR, no Spring Boot). Mount into Keycloak container via Docker volume at build time.

**Rationale**:
- Keycloak SPI runs inside the Keycloak JVM; Spring Boot autoconfiguration does not apply. The SPI JAR must be a minimal implementation depending only on Keycloak SPI API + Kafka client.
- The JAR is built via `./gradlew :libs:lib-keycloak-audit-spi:jar` and referenced from Docker Compose as a bind-mounted volume (`./libs/lib-keycloak-audit-spi/build/libs/lib-keycloak-audit-spi.jar:/opt/keycloak/providers/lib-keycloak-audit-spi.jar`).
- Keycloak 24+ auto-discovers providers in `/opt/keycloak/providers` on startup.

**How to apply**: `docker/compose-infra.yml` Keycloak service gains a volume mount for the SPI JAR. CI must build the JAR before starting Keycloak in integration tests. The `keycloak/mikemes-realm.json` gains an `eventsListeners` entry for the new provider.
