# Feature Specification: Service Decomposition — Extract Item Master, BOM & ECO from work-order-service

**Feature Branch**: `111-service-decomposition`

**Created**: 2026-06-04

**Status**: Draft

**Jira**: [MES-111](https://artical.atlassian.net/browse/MES-111)

## Clarifications

### Session 2026-06-04

- Q: Does `GET /api/v1/ecos/{id}` need BOM revision/status inline in the response, or is a UUID list sufficient? → A: UUID list only (`outputBomIds: string[]`); Angular client calls `/api/v1/boms/{id}` separately if BOM detail is needed.
- Q: What serialisation format should the `bom.released` Kafka event use? → A: JSON — `spring-kafka` `JsonSerializer`/`JsonDeserializer`, consistent with all existing events; no new infrastructure required.
- Q: What is the required strategy for existing `work_order` schema data when new services go live? → A: Clean start — new services start empty; no cross-schema data migration scripts; development test data re-entered manually after cut-over.
- Q: Should the `work-order-service` gateway route be removed entirely at PR 3 or narrowed to `Path=/api/v1/work-orders/**`? → A: Narrow to `/api/v1/work-orders/**` — correct permanent predicate, zero current traffic, no disruption to future Work Orders domain.
- Q: Should `inventory-service` be formally constrained as a Kafka producer-only service to permanently prevent the thread-starvation pattern? → A: Yes — explicit FR prohibiting Kafka consumers in `inventory-service`; enforced at code review.

---

## Problem Statement

`work-order-service` acts as a catch-all monolith, hosting controllers for domains it does not own:

- `ItemMasterController` → belongs in `inventory-service`
- `BomController` → belongs in `inventory-service`
- `EcoController` → belongs in `engineering-service`
- `UserGridPreferenceController` → belongs in `platform-service`

The API gateway routes the entire `/api/v1/**` prefix to `work-order-service`. This violates Constitution §XI (Service Boundary Integrity): no service may expose APIs outside its declared ISA-95 domain, and catch-all gateway predicates are prohibited.

**Live incident (2026-06-02):** Kafka consumer churn in `work-order-service` starved HTTP worker threads, taking down Item Master and BOM screens that have no logical dependency on work-order processing. Angular NG0100 errors and two-click load failures traced partly to this coupling.

---

## User Scenarios & Testing

### User Story 1 — Create inventory-service (Priority: P1)

An operator navigates to Item Master and BOM screens. These pages load on the first click with no timeout or error, even when Kafka consumer activity is high in `work-order-service`.

**Why this priority**: This directly resolves the live availability incident. Item Master and BOM are core daily-use screens.

**Independent Test**: Stop `work-order-service`. Item Master list and BOM browser still load data from `inventory-service`. Kafka consumer alerts on `work-order-service` do not appear in `inventory-service` logs. `grep -r "@KafkaListener" services/inventory-service/src/main/java` returns zero results.

**Acceptance Scenarios**:

1. **Given** `work-order-service` is stopped, **When** a user navigates to `/item-master`, **Then** the item list loads successfully from `inventory-service`
2. **Given** a normal deployment, **When** a user calls `GET /api/v1/item-master`, **Then** the gateway routes to `inventory-service` (not `work-order-service`)
3. **Given** `inventory-service` starts fresh, **When** Flyway runs, **Then** `inventory` schema tables are created and all V001–V014 equivalents apply cleanly
4. **Given** an item with BOM, **When** `BomService.releaseBom()` is called and that BOM is linked to an ECO, **Then** a `bom.released` Kafka event is published and `engineering-service` consumes it to call `addOutputBom()`

---

### User Story 2 — Create engineering-service (Priority: P1)

An engineer navigates to ECO screens. These pages load independently of `inventory-service` availability. ECO approval does not require direct JVM coupling to BOM code.

**Why this priority**: ECO domain is constitutionally incorrect in `work-order-service`; cross-service call from BOM → ECO must become an event.

**Independent Test**: Stop `inventory-service`. ECO list, detail, and approve flows all work against `engineering-service`. Kafka event `bom.released` is consumed and `outputBomId` set on the ECO.

**Acceptance Scenarios**:

1. **Given** `inventory-service` is stopped, **When** a user calls `GET /api/v1/ecos`, **Then** the ECO list loads successfully from `engineering-service`
2. **Given** a BOM is released in `inventory-service`, **When** that BOM is linked to an ECO, **Then** `engineering-service` receives `bom.released` Kafka event and adds the output BOM ID to the ECO record
3. **Given** a normal deployment, **When** a user calls `POST /api/v1/ecos/{id}/approve`, **Then** the gateway routes to `engineering-service`

---

### User Story 3 — Migrate UserGridPreferences to platform-service (Priority: P2)

A user customises their column picker on the Item Master list. Their preference is saved and restored after re-login, regardless of what `work-order-service` or `inventory-service` is doing.

**Why this priority**: Platform-level concerns (user preferences, grid layout) belong in `platform-service` per Constitution §VI. Lower priority because the live incident does not involve this domain.

**Independent Test**: Stop `inventory-service`. Navigate to Item Master — column preferences still load from `platform-service`.

**Acceptance Scenarios**:

1. **Given** a user has saved column preferences, **When** `inventory-service` is redeployed, **Then** preferences survive
2. **Given** `GET /api/v1/users/preferences/grid/{module}`, **Then** the gateway routes to `platform-service` (not `work-order-service`)

---

### User Story 4 — Decommission migrated domains from work-order-service (Priority: P2)

`work-order-service` contains only the scaffolding for future Work Orders & Scheduling. No Item Master, BOM, ECO, or Preference code remains in it. The catch-all `/api/v1/**` gateway predicate is removed.

**Why this priority**: Depends on US1–US3 being stable and tested first.

**Independent Test**: `grep -r "itemmaster\|bom\|eco\|preferences" services/work-order-service/src/main/java` returns zero results. Gateway `work-order-service` route is `Path=/api/v1/work-orders/**` only — no catch-all.

**Acceptance Scenarios**:

1. **Given** US1–US3 are merged and stable, **When** migrated packages are deleted from `work-order-service`, **Then** `./gradlew :services:work-order-service:check` still passes
2. **Given** the gateway config, **When** inspected, **Then** no `Path=/api/v1/**` predicate exists
3. **Given** `/api/v1/item-master/**` request, **Then** gateway routes to `inventory-service` only

---

### Edge Cases

- BOM `releaseBom()` currently makes a direct call to `EcoService.addOutputBom()`. After decomposition this becomes a Kafka event — what happens if the event is lost? → `engineering-service` Kafka consumer must be idempotent; dead-letter topic required.
- `UserGridPreference` data is not migrated — `platform-service` starts with an empty `user_grid_preferences` table; users re-save column preferences after cut-over.
- UDF field definitions are not migrated — each new service starts with an empty `udf_field_definition` table; UDF fields are re-created via the admin UI after cut-over.
- If `inventory-service` and `work-order-service` are deployed simultaneously during cut-over, two services could accept writes. Gateway routing must be updated atomically.
- `work-order-service` Flyway history is in `work_order.flyway_schema_history`. New services start their own versioned migration history from V001 — no dependency on `work-order-service` migration history.

---

## Requirements

### Functional Requirements

- **FR-001**: System MUST create `inventory-service` as a new Spring Boot 3.3 service owning `ItemMaster`, `BillOfMaterials`, `BomLine`, `UdfFieldDefinition` (ITEM_MASTER, BOM_LINE, BOM_HEADER module keys), and `BomExportService`
- **FR-002**: `inventory-service` MUST expose all existing Item Master and BOM REST endpoints unchanged: `GET/POST/PATCH /api/v1/item-master/**` and `GET/POST/PATCH/DELETE /api/v1/boms/**`
- **FR-003**: `inventory-service` MUST use its own `inventory` PostgreSQL schema with Flyway migrations starting at V001
- **FR-004**: `inventory-service` MUST publish Kafka events: `item-master.created`, `item-master.obsoleted`, `bom.released` (existing `BomEventPublisher` + `ItemMasterEventPublisher` moved); all events serialised as JSON using `spring-kafka` `JsonSerializer` — no schema registry required
- **FR-005**: System MUST create `engineering-service` as a new Spring Boot 3.3 service owning `EngineeringChangeOrder`, `EcoAffectedItem`, and `UdfFieldDefinition` (ECO module key)
- **FR-006**: `engineering-service` MUST expose all existing ECO endpoints unchanged: `GET/POST /api/v1/ecos/**`
- **FR-007**: `engineering-service` MUST subscribe to `bom.released` Kafka events and invoke `addOutputBom()` logic (replacing the current direct `EcoService` call from `BomService`); `GET /api/v1/ecos/{id}` returns `outputBomIds: List<UUID>` only — no cross-service enrichment; Angular client fetches BOM detail from `inventory-service` separately if needed
- **FR-008**: `engineering-service` MUST use its own `engineering` PostgreSQL schema with Flyway migrations starting at V001
- **FR-009**: `UserGridPreference` entity and REST endpoint (`/api/v1/users/preferences/**`) MUST be migrated to `platform-service` under the `platform` schema
- **FR-010**: Gateway MUST replace the catch-all `Path=/api/v1/**` → `work-order-service` predicate with domain-specific predicates:
  - `Path=/api/v1/item-master/**` → `inventory-service`
  - `Path=/api/v1/boms/**` → `inventory-service`
  - `Path=/api/v1/udf/**` → `inventory-service` (UDF admin endpoints scoped to inventory domain)
  - `Path=/api/v1/ecos/**` → `engineering-service`
  - `Path=/api/v1/users/preferences/**` → `platform-service`
  - `Path=/api/v1/work-orders/**` → `work-order-service` (narrowed from catch-all; no current traffic; correct permanent predicate for future Work Orders domain)
- **FR-011**: All existing integration tests (`ItemMasterControllerIT`, `BomControllerIT`, `EcoControllerIT`) MUST pass against the new service boundaries
- **FR-012**: `work-order-service` MUST be cleaned of all migrated domain packages after US1–US3 are stable; only infrastructure scaffolding for future work orders remains
- **FR-013**: `inventory-service` MUST be a Kafka **producer-only** service — it MUST NOT register any `@KafkaListener` consumers; this constraint is permanent and must be enforced at code review to prevent re-introducing the HTTP thread-pool starvation pattern that caused the 2026-06-02 incident

### Key Entities

- **ItemMaster** (moves to `inventory-service`, `inventory.item_master` table)
- **BillOfMaterials** (moves to `inventory-service`, `inventory.bill_of_materials`)
- **BomLine** (moves to `inventory-service`, `inventory.bom_line`)
- **UdfFieldDefinition** (split: ITEM_MASTER/BOM keys → `inventory-service`; ECO key → `engineering-service`)
- **EngineeringChangeOrder** (moves to `engineering-service`, `engineering.engineering_change_order`)
- **EcoAffectedItem** (moves to `engineering-service`, `engineering.eco_affected_item`) — stored as `affectedItemIds: List<UUID>`; UUID-only, no cross-service enrichment
- **EcoOutputBom** (moves to `engineering-service`, `engineering.eco_output_bom`) — stored as `outputBomIds: List<UUID>`; UUID-only; `engineering-service` does NOT call `inventory-service` to enrich with BOM revision/status
- **UserGridPreference** (moves to `platform-service`, `platform.user_grid_preferences`)

---

## Success Criteria

- **SC-001**: `ItemMasterControllerIT`, `BomControllerIT`, `EcoControllerIT` all pass with the migrated services; zero test failures
- **SC-002**: Stopping `work-order-service` does not affect Item Master or BOM API availability
- **SC-003**: Gateway config contains zero `Path=/api/v1/**` catch-all predicates in production
- **SC-004**: `./gradlew check` passes on all affected services (inventory-service, engineering-service, work-order-service, gateway-service, platform-service)
- **SC-005**: SonarCloud quality gate passes on all new services (coverage ≥ 80% new code)
- **SC-006**: `bom.released` Kafka event is consumed by `engineering-service` and `outputBomId` is set on the linked ECO — verified by integration test
- **SC-007**: After cut-over, new services start clean; development test data is re-entered manually; no cross-schema data migration scripts are required or produced
- **SC-008**: `grep -r "@KafkaListener" services/inventory-service/src/main/java` returns zero results — producer-only constraint verified

---

## Compliance References

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §7.5 Documented information — audit trails must survive schema migration; §8.1 Operational planning — service boundaries must not break traceability |
| AS9102 (FAI) | No | No FAI logic in scope |
| AS9131 (NCM) | No | No NCM logic in scope |
| NIST SP 800-171 / CMMC | Yes | Access controls enforced per service; JWT validation in each new service; no cross-service data leakage |
| 21 CFR Part 11 / Annex 11 | No — aerospace-only deployment | Envers audit tables must be recreated in each new schema; immutability preserved |
| ISA-95 | Yes | One service per ISA-95 functional domain (§VI); inventory domain = Inventory & Materials; engineering domain = Manufacturing Engineering |
| Constitution §XI | Yes (this spec corrects the violation) | Service Boundary Integrity: no catch-all gateway routes; each service owns only its declared domain |

---

## Assumptions

- Single PostgreSQL instance is retained; each new service gets its own named schema
- Docker Compose DNS service discovery (`inventory-service`, `engineering-service`) — no Eureka required
- No zero-downtime live migration required — this is a development environment; a maintenance window is acceptable
- Existing Flyway migrations in `work_order` schema are NOT modified; new services start fresh V001 histories in new schemas
- New services start with empty data; no cross-schema data migration scripts; development test data is re-entered manually after cut-over
- `platform-service` already has a `platform` schema; `user_grid_preferences` table is added via a new migration

---

## Deferred Decisions

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Zero-downtime cut-over with dual-write / traffic shadowing | Complex for a dev environment; maintenance window acceptable | Risk of write-loss during cut-over in a future live deployment | Post-GA / prod hardening | TBD |
| DEF-002 | Dead-letter queue (DLQ) for `bom.released` event consumer | MVP resilience sufficient; Kafka retry is default | Lost events if `engineering-service` is down during BOM release | P3 | TBD |
| DEF-003 | OpenAPI client generation between services (OpenFeign) | REST currently only used via gateway for external clients; internal sync calls not yet needed | Minor latency for future internal service-to-service calls | P3 | TBD |
| DEF-004 | Work Orders & Scheduling implementation in `work-order-service` | Out of scope for this decomposition ticket | work-order-service remains a stub after decommission | Separate epic (MES-WO) | TBD |
