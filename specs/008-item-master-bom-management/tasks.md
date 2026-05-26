# Tasks: Item Master & BOM Management (MES-8)

**Branch**: `008-item-master-bom-management` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Stack**: Java 21 · Spring Boot 3.3 · PostgreSQL 16 (schema: `work_order`) · Kafka · Testcontainers
**New modules**: `services/work-order-service/` · `libs/mes-udf-lib/`
**Package root**: `com.mes.workorder` (service) · `com.mes.udf` (lib)

**TDD**: Per Constitution §II — write tests FIRST, confirm FAILING before any implementation.
Log all test failures as tracked defects before closing the story.

**Commit convention**: Every task gets its own commit tagged with the task ID. Format:
```
[type](MES-8): description [TXXX]

Ref: MES-8
Task: TXXX
```
Example: `[feat](MES-8): add ItemMaster JPA entity [T027]`
The `Task: TXXX` footer links each commit back to this file without requiring Jira sub-issues.

---

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Phase 1 + 2 + 3 + 4 | T001–T050, T104–T112 | `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Scaffold + US1 + US3 bundled: UDF validation is part of item master create/patch; separating would require a mid-feature schema migration. Grid preferences API included here as it shares the item-master service boundary |
| PR 2 | Phase 5 | T051–T066 | `./gradlew :services:work-order-service:check` | Depends on PR 1 merged (item master FK required for BOM parent) |
| PR 3 | Phase 6 + 7 | T067–T087 | `./gradlew :services:work-order-service:check` | Depends on PR 2 merged; US4 and US5 share BOM domain — bundled to avoid split state |
| PR 4 | Phase 8 | T088–T094 | `./gradlew :services:work-order-service:check` | Depends on PR 2 merged; P3 priority — raise after PR 3 |
| PR 5 | Phase 10 | T113–T130 | `ng build --configuration=production` in `frontend/angular/` | Depends on PR 1 merged (item-master + grid preferences API live); Angular-only — no Spring Boot changes; shared grid + shared theme infrastructure enables all future screens |

**Sequencing note**: US3 (UDF, P2) is inside PR 1 with the P1 stories. US4+US5 (both P2) are PR 3. US6 (P3) is PR 4. PR 5 (Angular UI) can begin after PR 1 merges; it is independent of PRs 2–4.

---

## Phase 1: Setup — New Module Scaffolding [PR 1]

**Purpose**: Create the two new Gradle subprojects and wire them into build, CI, Docker, and SonarCloud.

- [X] T001 Add `:libs:mes-udf-lib` and `:services:work-order-service` to `settings.gradle` (include both subprojects)
- [X] T002 Create `libs/mes-udf-lib/build.gradle` — plain Java library, `group = 'com.mes'`, dependencies: spring-boot-starter-data-jpa, spring-boot-starter-web, spring-boot-starter-validation; publish block targeting mavenLocal
- [X] T003 Create `services/work-order-service/build.gradle` — Spring Boot plugin, dependencies: lib-common-security, lib-common-audit, mes-udf-lib, spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-actuator, flyway-core, flyway-database-postgresql, spring-kafka, postgresql (runtime), springdoc-openapi-webmvc, spring-boot-admin-starter-client; testImplementation: spring-boot-starter-test, testcontainers-junit-jupiter, testcontainers-postgresql, testcontainers-kafka
- [X] T004 [P] Create `services/work-order-service/src/main/java/com/mes/workorder/WorkOrderServiceApplication.java` — `@SpringBootApplication`, `@EnableJpaAuditing`, `ApplicationReadyEvent` listener stub for privilege registration
- [X] T005 [P] Create `services/work-order-service/src/main/resources/application.yml` — server.port 8095, datasource (work_order schema), Flyway locations, Keycloak issuer URI, IAM service URL, Kafka bootstrap, mes.bom.max-depth: 50, Spring Boot Admin client URL, autoconfigure exclusions
- [X] T006 [P] Create `services/work-order-service/Dockerfile` — multi-stage, Eclipse Temurin 21, matching pattern of `services/platform-service/Dockerfile`
- [X] T007 Add `work-order-service` service block to `docker/compose-infra.yml` — build context, port 8095:8095, network mes-net, env vars (datasource URL, Keycloak issuer URI, IAM service URL, Kafka, MES_SECURITY_WEBHOOK_TOKEN), depends_on postgres+kafka+keycloak, healthcheck `wget http://localhost:8095/actuator/health`
- [X] T008 [P] Add `work-order-service` service block to `docker/compose-prod.yml` — image `ghcr.io/artical2k9/mes-work-order-service:${TAG}`, same env pattern as compose-infra
- [X] T009 [P] Add `work-order-service:local` override to `docker/compose-local-override.yml`
- [X] T010 Add `work-order-service` to `.github/workflows/publish.yml` image build matrix (alongside existing service names)
- [X] T011 Add `services/work-order-service/src/main/java` and `libs/mes-udf-lib/src/main/java` to `sonar.sources` in `sonar-project.properties`; add corresponding test paths to `sonar.tests`
- [X] T012 [P] Create `libs/mes-udf-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — entry: `com.mes.udf.config.UdfAutoConfiguration`; create stub `UdfAutoConfiguration.java` in `libs/mes-udf-lib/src/main/java/com/mes/udf/config/`
- [X] T013 [P] Run `./gradlew :libs:mes-udf-lib:build :services:work-order-service:build -x test` — confirm both modules compile with zero errors before proceeding

**Checkpoint**: Both modules compile. Docker Compose can build the work-order-service image.

---

## Phase 2: Foundational — Flyway Migrations & Service Config [PR 1]

**Purpose**: Database schema, privilege seeds, Kafka topics, Envers config, and Testcontainers base class. Must be complete before any user story implementation.

- [ ] T014 Create `services/work-order-service/src/main/resources/db/migration/V001__create_work_order_schema.sql` — `CREATE SCHEMA IF NOT EXISTS work_order;`
- [ ] T015 Create `services/work-order-service/src/main/resources/db/migration/V002__create_item_master.sql` — full DDL for `work_order.item_master`: all columns (id UUID PK, org_id UUID NOT NULL, part_number, revision, description, unit_of_measure, cage_code, classification, make_buy_code, traceability_method, shelf_life_controlled BOOLEAN NOT NULL DEFAULT false, shelf_life_days INTEGER, step_part_ref, counterfeit_risk_level, approved_suppliers JSONB, verification_required BOOLEAN, custom_fields JSONB, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', audit columns); UNIQUE(org_id, part_number, revision); CHECK(shelf_life_controlled = false OR shelf_life_days IS NOT NULL); all indexes from data-model.md
- [ ] T016 Create `services/work-order-service/src/main/resources/db/migration/V003__create_bom_tables.sql` — DDL for `work_order.bill_of_materials` (id, org_id, parent_item_id UUID FK→item_master.id, bom_revision, status DEFAULT 'DRAFT', description, eco_id UUID nullable, audit columns; UNIQUE(org_id, parent_item_id, bom_revision)) and `work_order.bom_line` (id, bom_id FK→bill_of_materials.id, component_item_id FK→item_master.id, quantity NUMERIC(18,6), unit_of_measure, find_number, reference_designators, effectivity_method VARCHAR(10), effective_from_date DATE, effective_to_date DATE, effective_from_unit, effective_to_unit, audit columns); all indexes from data-model.md
- [ ] T017 Create `services/work-order-service/src/main/resources/db/migration/V004__create_eco_tables.sql` — DDL for `work_order.engineering_change_order` (id, org_id, eco_number UNIQUE, title, description TEXT, status DEFAULT 'DRAFT', initiated_by, approved_by, approved_at, implemented_at, audit columns) and `work_order.eco_affected_item` (eco_id FK, item_id FK, PK(eco_id, item_id)); index on (org_id, status)
- [ ] T018 Create `services/work-order-service/src/main/resources/db/migration/V005__create_udf_field_definition.sql` — DDL for `work_order.udf_field_definition`: id, org_id, module_key VARCHAR(50), field_key VARCHAR(100), label, field_type, required BOOLEAN, default_value, list_options JSONB, validation_rules JSONB, display_order INTEGER, active BOOLEAN DEFAULT true, audit columns; UNIQUE(org_id, module_key, field_key)
- [ ] T019 Create `services/work-order-service/src/main/resources/db/migration/V006__add_envers_tables.sql` — DDL for `work_order.revinfo` (rev INTEGER PK, revtstmp BIGINT, actor VARCHAR(255)), `work_order.item_master_aud`, `work_order.bill_of_materials_aud`, `work_order.bom_line_aud`, `work_order.engineering_change_order_aud` (each mirrors entity columns + REV INT FK→revinfo, REVTYPE SMALLINT)
- [ ] T020 Create `services/work-order-service/src/main/resources/db/migration/V007__seed_item_master_privileges.sql` — INSERT into `iam.privilege` for all 5 item-master privilege keys (item-master:records:view, item-master:records:manage, item-master:bom:manage, item-master:eco:manage, item-master:udf:manage); INSERT into `iam.role_privilege` granting all 5 to SYSTEM_ADMIN and item-master:records:view + item-master:records:manage + item-master:bom:manage + item-master:eco:manage to ENGINEER; use SELECT to look up role IDs by name
- [ ] T021 [P] Create `services/work-order-service/src/main/java/com/mes/workorder/config/SecurityConfig.java` — extends lib-common-security pattern; permits `/actuator/health` without auth; requires JWT for all other endpoints; uses `@EnableMESSecurity` annotation from lib-common-security
- [ ] T022 [P] Implement privilege registration on startup: update `WorkOrderServiceApplication.java` to implement `ApplicationListener<ApplicationReadyEvent>` and call `PrivilegeRegistryClient.register(moduleName, privileges)` with the 5 item-master privilege keys and descriptions
- [ ] T023 Create `services/work-order-service/src/test/java/com/mes/workorder/integration/BaseIntegrationTest.java` — abstract class with `@Testcontainers(disabledWithoutDocker = true)`, PostgreSQL 16 container, Kafka container, `@DynamicPropertySource` registering datasource URL, Kafka bootstrap servers, `mes.security.iam-service-url` mock, Keycloak issuer URI override with RSA JWT support (pattern from existing IT tests); `systemProperty 'api.version', '1.41'` per ERR-MES-036
- [ ] T104 Create `services/work-order-service/src/main/resources/db/migration/V008__create_user_grid_preferences.sql` — DDL for `work_order.user_grid_preferences`: id UUID PK DEFAULT gen_random_uuid(), org_id UUID NOT NULL, user_id VARCHAR(255) NOT NULL (stores JWT sub claim), module_key VARCHAR(50) NOT NULL, column_config JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(); UNIQUE(org_id, user_id, module_key); index on (org_id, user_id)

**Checkpoint**: Foundation ready — `./gradlew :services:work-order-service:build` succeeds; Flyway migration applies cleanly in a fresh Testcontainers PostgreSQL instance (verified by running FlywayMigrationIT once written in Phase 3).

---

## Phase 3: User Story 1 — Item Master CRUD (Priority: P1) 🎯 MVP [PR 1]

**Goal**: Create, retrieve, patch, obsolete item master records; Kafka events on create/update; Envers audit trail; org-scoped; UDF validation stub (validates only if definitions exist, passes if none defined yet).

**Independent Test**: `ENGINEER` creates a fabricated aluminium bracket (partNumber=BRKT-001, revision=A), retrieves it by ID, PATCHes the description, then queries `work_order.item_master_aud` and confirms 2 audit rows. GET /item-master returns the record in the paginated list. Both `item-master.created` and `item-master.updated` Kafka messages appear on `work-order.item-master.events` within 5 seconds.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T024 [P] [US1] Write `FlywayMigrationIT`: assert all V001–V007 tables exist in `work_order` schema; assert 5 privilege rows exist in `iam.privilege` for module item-master; assert SYSTEM_ADMIN has all 5 grants — `services/work-order-service/src/test/java/com/mes/workorder/integration/FlywayMigrationIT.java`
- [ ] T025 [P] [US1] Write `ItemMasterControllerIT`: POST creates record → 201 + Location header; POST duplicate partNumber+revision → 409; PATCH description → 200 + modified fields; GET unauthenticated → 401; GET with ENGINEER token → 200; shelfLifeControlled=true without shelfLifeDays → 422 — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/ItemMasterControllerIT.java`
- [ ] T026 [P] [US1] Write `ItemMasterServiceTest` (unit): uniqueness constraint throws conflict exception; shelf-life constraint throws validation exception; audit fields (createdBy, modifiedBy) populated from security context — `services/work-order-service/src/test/java/com/mes/workorder/unit/itemmaster/ItemMasterServiceTest.java`
- [ ] T027 [P] [US1] Write `ItemMasterKafkaIT`: create item master → assert `work-order.item-master.events` receives message with eventType=ITEM_MASTER_CREATED and entityId; PATCH → assert ITEM_MASTER_UPDATED message — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/ItemMasterKafkaIT.java`
- [ ] T028 [US1] Confirm all 4 tests above FAIL (RED) with compile or assertion errors before writing any production code
- [ ] T105 [P] [US1] Write `UserGridPreferenceControllerIT`: GET with no saved config → 200 + default column list for module; PUT saves config → 200; second GET → returns saved config; different `moduleKey` → independent config; different JWT sub (different user) → independent config — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/UserGridPreferenceControllerIT.java`
- [ ] T106 [US1] Confirm T105 FAILS (RED) before writing any preference implementation

### Implementation

- [ ] T029 [P] [US1] Create `ItemMaster.java` JPA entity: all columns from V002 migration, `@Audited`, `@EntityListeners(AuditingEntityListener.class)`, `@Table(name="item_master", schema="work_order")`, `@Column` mappings, shelf-life check constraint enforced in `@PrePersist`/`@PreUpdate` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/domain/ItemMaster.java`
- [ ] T030 [P] [US1] Create enums: `Classification`, `MakeBuyCode`, `TraceabilityMethod`, `ItemStatus` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/domain/`
- [ ] T031 [US1] Create `ItemMasterRepository.java`: `findByOrgIdAndPartNumberAndRevision`, `findByOrgIdAndId`, `findAllByOrgIdAndStatusAndClassification` (Page), `existsByOrgIdAndPartNumberAndRevision` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/repository/ItemMasterRepository.java`
- [ ] T032 [US1] Create DTOs: `ItemMasterDto`, `CreateItemMasterRequest` (Bean Validation annotations), `PatchItemMasterRequest` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/dto/`
- [ ] T033 [US1] Create `ItemMasterMapper.java` (MapStruct or manual): entity ↔ DTO conversion, customFields JSONB ↔ `Map<String, Object>` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/dto/ItemMasterMapper.java`
- [ ] T034 [US1] Create `ItemMasterService.java`: `create()` (unique check, shelf-life validation, UdfValidator.validate() — no-op if no definitions exist, save, publish CREATED event), `get()` (org-scoped), `patch()` (org-scoped, validate, save, publish UPDATED event), `obsolete()`, `list()` (paginated, org-scoped) — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/service/ItemMasterService.java`
- [ ] T035 [US1] Create `ItemMasterEventPublisher.java`: sends JSON event to `work-order.item-master.events` Kafka topic; event payload includes `eventId` (UUID), `eventType`, `entityId`, `orgId`, `actorId`, `occurredAt`, `payload` (DTO snapshot) — `services/work-order-service/src/main/java/com/mes/workorder/kafka/ItemMasterEventPublisher.java`
- [ ] T036 [US1] Create `ItemMasterController.java`: `GET /item-master` (paginated list, classification + status + counterfeitRiskLevel filters), `POST /item-master` (201 + Location), `GET /item-master/{itemId}`, `PATCH /item-master/{itemId}`, `POST /item-master/{itemId}/obsolete`; privilege checks via `@PreAuthorize` or `RequiresPrivilege` annotation from lib-common-security — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/ItemMasterController.java`
- [ ] T107 [P] [US1] Create `UserGridPreference.java` entity: all columns from V008 migration, `columnConfig` mapped as `@Column(columnDefinition="jsonb")` with Jackson JSON type converter, `@Table(name="user_grid_preferences", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/domain/UserGridPreference.java`
- [ ] T108 [US1] Create `UserGridPreferenceRepository.java`: `findByOrgIdAndUserIdAndModuleKey(UUID orgId, String userId, String moduleKey): Optional<UserGridPreference>` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/repository/UserGridPreferenceRepository.java`
- [ ] T109 [P] [US1] Create DTOs: `ColumnPreferenceEntry` record (`columnKey: String, visible: boolean, order: int`), `UserGridPreferenceDto` (moduleKey + list of entries), `UpsertUserGridPreferenceRequest` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/api/dto/`
- [ ] T110 [US1] Create `UserGridPreferenceService.java`: `get(orgId, userId, moduleKey)` returns saved column list or the module's built-in default (defaults registered via `@Bean Map<String,List<ColumnPreferenceEntry>> defaultColumnRegistry` so any service can contribute its defaults); `upsert(orgId, userId, moduleKey, entries)` saves-or-replaces via findByOrgIdAndUserIdAndModuleKey + save — `services/work-order-service/src/main/java/com/mes/workorder/preferences/service/UserGridPreferenceService.java`
- [ ] T111 [US1] Create `UserGridPreferenceController.java`: `GET /api/v1/users/preferences/grid/{moduleKey}` (userId extracted from JWT sub claim; no additional privilege — users read their own prefs); `PUT /api/v1/users/preferences/grid/{moduleKey}` (userId from JWT sub; any authenticated user) — `services/work-order-service/src/main/java/com/mes/workorder/preferences/api/UserGridPreferenceController.java`
- [ ] T112 [US1] Run `./gradlew :services:work-order-service:test --tests "*UserGridPreference*"` — confirm T105 goes GREEN
- [ ] T037 [US1] Run `./gradlew :services:work-order-service:test --tests "*ItemMaster*" --tests "*FlywayMigrationIT*"` — confirm T024–T027 go GREEN

**Checkpoint**: Item master CRUD end-to-end functional. Kafka events emitting. Envers audit rows present.

---

## Phase 4: User Story 3 — UDF Framework (Priority: P2) [PR 1 continued]

**Goal**: `mes-udf-lib` provides `UdfFieldDefinition` CRUD scoped by org + module; `UdfValidator` enforces required fields, LIST options, NUMBER ranges, TEXT maxLength on item master create/patch. Framework is module-agnostic and reusable by future services.

**Independent Test**: `SYSTEM_ADMIN` defines required TEXT field `drawing_ref` on ITEM_MASTER. ENGINEER creates item master without it → 422. ENGINEER retries with `customFields: {"drawing_ref": "DRW-001"}` → 201. GET returns `customFields.drawing_ref = "DRW-001"`. SYSTEM_ADMIN deletes `drawing_ref` without `force=true` when 1 record has value → 409.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T038 [P] [US3] Write `UdfValidatorTest` (unit): TEXT required field missing → error; LIST field value not in options → error; NUMBER below min → error; NUMBER above max → error; BOOLEAN type coercion; DATE format validation; no definitions → passes — `libs/mes-udf-lib/src/test/java/com/mes/udf/service/UdfValidatorTest.java`
- [ ] T039 [P] [US3] Write `UdfFieldDefinitionControllerIT`: POST defines field → 201; GET lists fields by module; ENGINEER POST → 403; duplicate fieldKey → 409; LIST field with invalid option on item master create → 422; DELETE with values on records without force → 409; DELETE with force=true → 204 + values nulled — `services/work-order-service/src/test/java/com/mes/workorder/integration/udf/UdfFieldDefinitionControllerIT.java`
- [ ] T040 [P] [US3] Write `ItemMasterWithUdfIT`: required UDF missing on create → 422 with field name in error; present → 201 and GET returns customFields; NUMBER range violation → 422 — `services/work-order-service/src/test/java/com/mes/workorder/integration/udf/ItemMasterWithUdfIT.java`
- [ ] T041 [US3] Confirm all 3 tests above FAIL (RED) before writing production code

### Implementation

- [ ] T042 [P] [US3] Create `UdfFieldDefinition.java` entity: all columns from V005 migration, `@Table(name="udf_field_definition", schema="work_order")` (schema is injected via `@ConfigurationProperties` so lib is schema-agnostic), `@Audited` — `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/UdfFieldDefinition.java`
- [ ] T043 [P] [US3] Create `UdfFieldType.java` enum (TEXT, NUMBER, DATE, BOOLEAN, LIST) and `ModuleKey.java` enum (ITEM_MASTER, WORK_ORDER, ROUTING, RECEIVING, INVENTORY) — `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/`
- [ ] T044 [US3] Create `UdfFieldDefinitionRepository.java`: `findByOrgIdAndModuleKeyAndActiveTrue`, `findByOrgIdAndModuleKeyAndFieldKey`, `countByOrgIdAndModuleKeyAndFieldKeyAndCustomFieldValueNotNull` — `libs/mes-udf-lib/src/main/java/com/mes/udf/repository/UdfFieldDefinitionRepository.java`
- [ ] T045 [US3] Create `UdfValidator.java`: `validate(orgId, moduleKey, Map<String,Object> customFields)` — loads active definitions, checks required fields present, validates type + constraints; returns list of `UdfViolation` records (fieldKey, message) — `libs/mes-udf-lib/src/main/java/com/mes/udf/service/UdfValidator.java`
- [ ] T046 [US3] Create `UdfFieldDefinitionService.java`: `define()` (unique check, save), `list(orgId, moduleKey)`, `deactivate(orgId, fieldId, force)` (if !force and values exist → throw conflict with count; if force → null values across all records in consuming service's table via a configurable callback, record audit entry) — `libs/mes-udf-lib/src/main/java/com/mes/udf/service/UdfFieldDefinitionService.java`
- [ ] T047 [US3] Create `UdfFieldDefinitionController.java`: `GET /udf/fields?module=ITEM_MASTER`, `POST /udf/fields` (requires `item-master:udf:manage` privilege), `DELETE /udf/fields/{fieldId}?force={bool}` — `libs/mes-udf-lib/src/main/java/com/mes/udf/api/UdfFieldDefinitionController.java`
- [ ] T048 [US3] Update `UdfAutoConfiguration.java` to register `UdfValidator`, `UdfFieldDefinitionService`, `UdfFieldDefinitionController`, `UdfFieldDefinitionRepository` as beans — `libs/mes-udf-lib/src/main/java/com/mes/udf/config/UdfAutoConfiguration.java`
- [ ] T049 [US3] Wire `UdfValidator` into `ItemMasterService.create()` and `ItemMasterService.patch()`: inject bean, call before save, map `UdfViolation` list to HTTP 422 `ErrorResponse` listing each missing/invalid field — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/service/ItemMasterService.java`
- [ ] T050 [US3] Run `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` — confirm all US1+US3 tests GREEN

**Checkpoint**: Full item master + UDF framework functional and GREEN.

> **Raise PR 1 after this checkpoint** (T001–T050, T104–T112) | CI: `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Target: `Develop`

---

## Phase 5: User Story 2 — Multi-Level BOM Authoring (Priority: P1) [PR 2]

**Goal**: Create BOM revisions, author BOM lines, release BOM (freezes structure), explode flat and indented via PostgreSQL recursive CTE; circular reference detection at line-add time; `counterfeitRiskAlert` and `componentObsoleted` flags on explosion nodes.

**Independent Test**: Engineer creates a two-level BOM (Assembly→Sub-Assembly→Component, 3 lines total). Requests GET `/boms/{bomId}/explosion?format=indented`. Response contains 3 nodes at correct depths with rolled-up quantities. Attempting to add a line creating a cycle returns 422. Releasing the BOM and then adding another line returns 409.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T051 [P] [US2] Write `BomControllerIT`: create BOM header → 201; add lines → 201; GET /boms/{id}/lines returns all lines; release → 200; add line to released BOM → 409; create line with non-existent componentItemId → 422 — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomControllerIT.java`
- [ ] T052 [P] [US2] Write `BomExplosionIT`: 3-level BOM flat explosion returns all nodes; indented explosion returns nested tree; explosion with depth > `mes.bom.max-depth` returns 422; circular line add → 422; `counterfeitRiskAlert` true for HIGH-risk component — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomExplosionIT.java`
- [ ] T053 [P] [US2] Write `BomServiceTest` (unit): draft-only guard throws conflict for RELEASED BOM; circular detection mock verifies CTE query called before insert; duplicate (bomId, findNumber, componentItemId) → no-op or error depending on spec — `services/work-order-service/src/test/java/com/mes/workorder/unit/bom/BomServiceTest.java`
- [ ] T054 [P] [US2] Write `BomKafkaIT`: release BOM → `work-order.bom.events` receives BOM_RELEASED message — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomKafkaIT.java`
- [ ] T055 [US2] Confirm all 4 tests FAIL (RED)

### Implementation

- [ ] T056 [P] [US2] Create `BillOfMaterials.java` entity: all columns from V003 migration, `@Audited`, FK to `ItemMaster` via UUID column (no JPA FK to avoid cross-aggregate coupling — use UUID field and look up separately), `@Table(name="bill_of_materials", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/BillOfMaterials.java`
- [ ] T057 [P] [US2] Create `BomLine.java` entity: all columns from V003 (effectivity columns nullable), `@Audited`, `@Table(name="bom_line", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/BomLine.java`
- [ ] T058 [P] [US2] Create `BomStatus.java` and `EffectivityMethod.java` enums — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/`
- [ ] T059 [US2] Create `BomRepository.java`: `findByOrgIdAndId`, `findByOrgIdAndParentItemIdAndBomRevision`, `existsByOrgIdAndParentItemIdAndBomRevision`; custom native query `hasAncestorCycle(bomId UUID, candidateComponentId UUID) : boolean` (pre-insert circular check CTE) — `services/work-order-service/src/main/java/com/mes/workorder/bom/repository/BomRepository.java`
- [ ] T060 [US2] Create `BomLineRepository.java`: `findAllByBomId`, `findByBomIdAndFindNumber` (for effectivity checks) — `services/work-order-service/src/main/java/com/mes/workorder/bom/repository/BomLineRepository.java`
- [ ] T061 [US2] Create DTOs: `BomDto`, `CreateBomRequest`, `BomLineDto` (includes `counterfeitRiskAlert`, `componentObsoleted`), `CreateBomLineRequest`, `BomExplosionNode` (recursive children list for indented) — `services/work-order-service/src/main/java/com/mes/workorder/bom/api/dto/`
- [ ] T062 [US2] Create `BomExplosionService.java`: `explode(bomId, format, asOfDate, asOfUnit)` — executes native recursive CTE (WITH RECURSIVE bom_tree … CYCLE component_item_id SET is_cycle USING cycle_path) via `@Query(nativeQuery=true)` on `BomLineRepository`; respects `mes.bom.max-depth`; builds flat list or indented tree; decorates each node with `counterfeitRiskAlert` (component risk level HIGH/CRITICAL) and `componentObsoleted` (item status OBSOLETE); detects effectivity gaps and throws 422 — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [ ] T063 [US2] Create `BomService.java`: `createBom()` (validate parentItemId exists in org, check revision uniqueness), `addLine()` (guard DRAFT status, validate componentItemId exists, run circular-ancestor CTE check, save), `releaseBom()` (DRAFT→RELEASED state machine, emit BOM_RELEASED event), `getBom()`, `listLines()` — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [ ] T064 [US2] Create `BomEventPublisher.java`: publishes to `work-order.bom.events` for BOM_RELEASED and BOM_OBSOLETED event types — `services/work-order-service/src/main/java/com/mes/workorder/kafka/BomEventPublisher.java`
- [ ] T065 [US2] Create `BomController.java`: `POST /boms`, `GET /boms/{bomId}`, `POST /boms/{bomId}/release`, `GET /boms/{bomId}/lines`, `POST /boms/{bomId}/lines`, `GET /boms/{bomId}/explosion` — all require `item-master:bom:manage` privilege — `services/work-order-service/src/main/java/com/mes/workorder/bom/api/BomController.java`
- [ ] T066 [US2] Run `./gradlew :services:work-order-service:check` — confirm all US1+US3+US2 tests GREEN

**Checkpoint**: BOM authoring + explosion end-to-end functional. Circular detection works. Kafka events emitting.

> **Raise PR 2 after this checkpoint** (T051–T066) | CI: `./gradlew :services:work-order-service:check` | Target: `Develop`

---

## Phase 6: User Story 4 — BOM Effectivity Management (Priority: P2) [PR 3]

**Goal**: DATE and UNIT effectivity on BOM lines; `effectiveFrom*` required when method set; `effectiveTo*` optional (null = open-ended); overlap validation with specific error identifying conflicting line by find number and UUID; effectivity gap detection in explosion.

**Independent Test**: BOM with two DATE-effective lines for find number 003: line A effective 2025-01-01→2025-12-31, line B effective 2026-01-01→null (open-ended). Explosion for asOfDate=2025-06-01 returns only line A. Explosion for asOfDate=2026-06-01 returns only line B. Attempting a third line for find 003 with dates overlapping line A returns 422 with "date range overlap for BOM line find number 003 — conflicts with existing line ID {uuid}".

### Tests (write FIRST — must FAIL before implementation)

- [ ] T067 [P] [US4] Write `BomEffectivityIT`: AS1 date range inclusion/exclusion (2025-06-01 includes, 2026-01-01 excludes 2025 line); AS2 unit range inclusion/exclusion; AS3 overlap → 422 message contains find number + conflicting line UUID; AS4 explosion for date with no covering line → 422 effectivity gap; AS5 open-ended line (effectiveToDate null) included for all future dates — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomEffectivityIT.java`
- [ ] T068 [P] [US4] Write `EffectivityValidatorTest` (unit): overlap detection for same findNumber across DATE lines; open-ended (null effectiveTo) treated as far-future; UNIT method with null effectiveToUnit = open-ended; no effectivity method set = always included — `services/work-order-service/src/test/java/com/mes/workorder/unit/bom/EffectivityValidatorTest.java`
- [ ] T069 [US4] Confirm both tests FAIL (RED)

### Implementation

- [ ] T070 [US4] Create `EffectivityValidator.java`: `validateNewLine(bomId, newLine)` — queries existing lines for same `(bomId, findNumber)` with DATE effectivity; checks overlap with new line (treating null effectiveTo as `LocalDate.MAX`); on conflict throws `EffectivityOverlapException(findNumber, conflictingLineId)` which maps to HTTP 422; for UNIT method validates `effectiveFromUnit` not null when method set — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/EffectivityValidator.java`
- [ ] T071 [US4] Update `BomService.addLine()` to call `EffectivityValidator.validateNewLine()` before persisting; validate `effectiveFromDate`/`effectiveFromUnit` is non-null when `effectivityMethod` is set; throw 422 if violation — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [ ] T072 [US4] Update `BomExplosionService.explode()` to apply effectivity filter: for DATE lines, include if `effectiveFromDate ≤ asOfDate` AND (`effectiveToDate IS NULL` OR `effectiveToDate ≥ asOfDate`); for UNIT lines, include if `effectiveFromUnit ≤ asOfUnit` AND (`effectiveToUnit IS NULL` OR `effectiveToUnit ≥ asOfUnit`); detect find numbers with lines but none covering the requested date/unit → throw 422 gap error — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [ ] T073 [US4] Update recursive CTE in `BomExplosionService` native query to pass `:asOfDate` parameter; handle null asOfDate (no effectivity filter applied) — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [ ] T074 [US4] Run `./gradlew :services:work-order-service:test --tests "*BomEffectivity*"` — confirm US4 tests GREEN

**Checkpoint**: Effectivity filtering working; overlap detection error messages include find number and conflicting line UUID.

---

## Phase 7: User Story 5 — Engineering Change Orders (Priority: P2) [PR 3 continued]

**Goal**: ECO CRUD; Draft→Approved state machine; Approved ECOs are immutable; link to affected item masters and output BOM revisions; concurrent ECO warning on create; `eco.approved` Kafka event.

**Independent Test**: Create ECO referencing item masters A and B. Create a second ECO also referencing item master A — response has `concurrentEcoWarning: true`. Approve first ECO. Create new BOM revision referencing ecoId. GET first ECO → `outputBomIds` contains the new BOM ID. Attempt to edit approved ECO description → 409.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T075 [P] [US5] Write `EcoControllerIT`: AS1 create draft → 201; AS2 approve → 200 + approvedBy set; AS3 new BOM with ecoId → ECO outputBomIds updated; AS4 concurrent ECO for same item → 201 with concurrentEcoWarning=true; AS5 edit APPROVED ECO → 409 — `services/work-order-service/src/test/java/com/mes/workorder/integration/eco/EcoControllerIT.java`
- [ ] T076 [P] [US5] Write `EcoKafkaIT`: approve ECO → `work-order.eco.events` receives ECO_APPROVED message — `services/work-order-service/src/test/java/com/mes/workorder/integration/eco/EcoKafkaIT.java`
- [ ] T077 [P] [US5] Write `EcoServiceTest` (unit): concurrent check queries open ECOs for same item IDs; APPROVED status rejects mutation; state machine transition only from DRAFT — `services/work-order-service/src/test/java/com/mes/workorder/unit/eco/EcoServiceTest.java`
- [ ] T078 [US5] Confirm all 3 tests FAIL (RED)

### Implementation

- [ ] T079 [P] [US5] Create `EngineeringChangeOrder.java` entity: all V004 columns, `@Audited`, `@ElementCollection` for `affectedItemIds` (UUID list via eco_affected_item table), `@ElementCollection` for `outputBomIds` (UUID list), `@Table(name="engineering_change_order", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/eco/domain/EngineeringChangeOrder.java`
- [ ] T080 [P] [US5] Create `EcoStatus.java` enum (DRAFT, APPROVED, IMPLEMENTED) — `services/work-order-service/src/main/java/com/mes/workorder/eco/domain/EcoStatus.java`
- [ ] T081 [US5] Create `EcoRepository.java`: `findByOrgIdAndId`, `findOpenEcosForItemId(orgId, itemId)` (status IN (DRAFT, APPROVED)) — `services/work-order-service/src/main/java/com/mes/workorder/eco/repository/EcoRepository.java`
- [ ] T082 [US5] Create DTOs: `EcoDto` (includes `concurrentEcoWarning`, `affectedItemIds`, `outputBomIds`), `CreateEcoRequest` — `services/work-order-service/src/main/java/com/mes/workorder/eco/api/dto/`
- [ ] T083 [US5] Create `EcoService.java`: `create()` (check concurrent ECOs per affected item, set concurrentEcoWarning, generate ecoNumber sequence, save); `approve()` (DRAFT→APPROVED guard, set approvedBy from JWT sub, set approvedAt, emit ECO_APPROVED event); `addOutputBom(ecoId, bomId)` (called from BomService.releaseBom when ecoId present); reject any mutation if status ≠ DRAFT — `services/work-order-service/src/main/java/com/mes/workorder/eco/service/EcoService.java`
- [ ] T084 [US5] Create `EcoEventPublisher.java`: publishes to `work-order.eco.events` for ECO_APPROVED and ECO_IMPLEMENTED — `services/work-order-service/src/main/java/com/mes/workorder/kafka/EcoEventPublisher.java`
- [ ] T085 [US5] Create `EcoController.java`: `POST /ecos`, `GET /ecos/{ecoId}`, `POST /ecos/{ecoId}/approve` — requires `item-master:eco:manage` privilege — `services/work-order-service/src/main/java/com/mes/workorder/eco/api/EcoController.java`
- [ ] T086 [US5] Update `BomService.releaseBom()` to call `EcoService.addOutputBom(ecoId, bomId)` when BOM's `ecoId` is non-null — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [ ] T087 [US5] Run `./gradlew :services:work-order-service:check` — confirm all US4+US5 tests GREEN

**Checkpoint**: ECO lifecycle functional. BOM release links to ECO. Kafka events emitting.

> **Raise PR 3 after this checkpoint** (T067–T087) | CI: `./gradlew :services:work-order-service:check` | Target: `Develop`

---

## Phase 8: User Story 6 — AS5553 Counterfeit-Part Risk Fields (Priority: P3) [PR 4]

**Goal**: Surface `counterfeitRiskAlert` flag on BOM explosion nodes for HIGH/CRITICAL components; add `compliance.as5553-risk-added` Kafka event when a high-risk component is added to a BOM; search by `counterfeitRiskLevel` on item master list.

**Independent Test**: Create item master with `counterfeitRiskLevel=HIGH`. PATCH adds AS5553 fields. GET returns all AS5553 fields. Create BOM with that item as a component — BOM line has `counterfeitRiskAlert=true`. Query GET /item-master?counterfeitRiskLevel=HIGH returns the item.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T088 [P] [US6] Write `AS5553IT`: PATCH item master with AS5553 fields → 200 and GET returns fields; BOM explosion node for HIGH-risk component has counterfeitRiskAlert=true; search by counterfeitRiskLevel=HIGH returns matching item — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/AS5553IT.java`
- [ ] T089 [US6] Confirm test FAILS (RED) — verify counterfeitRiskAlert logic not yet implemented in explosion

### Implementation

- [ ] T090 [US6] Confirm AS5553 columns (`counterfeit_risk_level`, `approved_suppliers` JSONB, `verification_required`) are present in V002 migration and in `ItemMaster.java` entity — no schema change needed (columns included from PR 1)
- [ ] T091 [US6] Update `BomExplosionService.java`: when building explosion nodes, look up each component's `counterfeitRiskLevel`; set `counterfeitRiskAlert=true` if level is HIGH or CRITICAL; batch load item master risk levels in a single query to avoid N+1 — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [ ] T092 [US6] Update `ItemMasterEventPublisher.java`: emit `compliance.as5553-risk-added` event on `work-order.item-master.events` when a BOM line is saved with a component whose `counterfeitRiskLevel` is HIGH or CRITICAL; call from `BomService.addLine()` after save — `services/work-order-service/src/main/java/com/mes/workorder/kafka/ItemMasterEventPublisher.java`
- [ ] T093 [US6] Add `counterfeitRiskLevel` filter parameter to `ItemMasterController.listItemMasters()` and `ItemMasterRepository.findAllByOrgId…` query — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/ItemMasterController.java`
- [ ] T094 [US6] Run `./gradlew :services:work-order-service:check` — confirm all US6 tests GREEN

**Checkpoint**: AS5553 fields surfaced. Explosion alert flags working. Risk-level search working.

> **Raise PR 4 after this checkpoint** (T088–T094) | CI: `./gradlew :services:work-order-service:check` | Target: `Develop`

---

## Phase 9: Polish & Compliance Verification [all PRs]

**Purpose**: Cross-cutting quality gates and constitution compliance verification.

- [ ] T095 [P] Verify all Constitution Check gates in `specs/008-item-master-bom-management/plan.md` are ✅ PASS; obtain owner sign-off before raising any PR
- [ ] T096 [P] Confirm `OrganisationContextHolder` used in every service-layer method that queries the DB: grep all service classes in `services/work-order-service/src/main/java/com/mes/workorder/` for missing org_id scope; fix any gap
- [ ] T097 Write `AuditTrailIT`: after item master create+patch, query `work_order.item_master_aud` and assert 2 rows; after BOM release, assert `bill_of_materials_aud` row; after ECO approve, assert `engineering_change_order_aud` row — `services/work-order-service/src/test/java/com/mes/workorder/integration/AuditTrailIT.java`
- [ ] T098 [P] Confirm all Kafka event publishers include `eventId` UUID field (idempotency dedup key): search `ItemMasterEventPublisher`, `BomEventPublisher`, `EcoEventPublisher` for `eventId` in payload map
- [ ] T099 [P] Validate `privilege_registration` smoke test: start work-order-service against local stack (quickstart.md), query `GET /roles/privilege-map` via iam-service, confirm 5 item-master privilege keys present for SYSTEM_ADMIN and ENGINEER
- [ ] T100 [P] Run Checkstyle + SpotBugs across both new modules: `./gradlew :services:work-order-service:spotbugsMain :libs:mes-udf-lib:spotbugsMain` — resolve all violations before raising PR
- [ ] T101 Run `.\scripts\feature-cost.ps1` and paste output into each PR description as `## Usage Cost` section
- [ ] T102 [P] Compliance spot-check — verify demonstrable coverage of: AS9100D §7.5 (audit rows in AuditTrailIT), AS9102 (partNumber+revision uniqueness test), AS5553 (AS5553IT), ISA-95 Material Class mapping (comment in ItemMaster entity JavaDoc), BOM depth limit (BomExplosionIT covers depth guard)
- [ ] T103 Retrospective gate: review session work for new errors or near-misses; log any to `docs/governance/MES-ERR-001_Agent_Error_Log.md` before transitioning MES-8 to Done

---

## Phase 10: Angular Frontend — Shared Grid Infrastructure & Item Master UI [PR 5]

**Purpose**: Install PrimeNG, build the reusable shared grid column-picker system, and implement the Item Master list screen. The shared architecture means any future screen (BOM, Work Orders, Receiving, Inventory) gains persistent column customisation by providing a `moduleKey` and `DEFAULT_COLUMNS` constant — no additional infrastructure work required.

**Shared module contract**: All column-picker infrastructure lives under `frontend/angular/src/app/shared/grid/`. Each feature imports the barrel, provides its `moduleKey` and defaults, and uses `ColumnPickerComponent` directly. The backend API (`/api/v1/users/preferences/grid/{moduleKey}`) is already module-agnostic (added in PR 1).

- [ ] T113 Install PrimeNG + Angular CDK in `frontend/angular/`: `npm install primeng @angular/cdk`; configure `provideAnimationsAsync()` and `providePrimeNG({theme: {preset: Aura}})` in `frontend/angular/src/app/app.config.ts`
- [ ] T114 [P] Create shared grid barrel at `frontend/angular/src/app/shared/grid/index.ts`; define `ColumnDef` interface (`{ key: string; label: string; visible: boolean; order: number; locked?: boolean; udf?: boolean }`) in `frontend/angular/src/app/shared/grid/models/column-def.model.ts`; export from barrel
- [ ] T115 [P] Create generic `UserGridPreferenceApiService` in `frontend/angular/src/app/shared/grid/services/user-grid-preference-api.service.ts` — `getPreferences(moduleKey: string): Observable<ColumnDef[]>`; `putPreferences(moduleKey: string, columns: ColumnDef[]): Observable<void>`; calls `GET/PUT /api/v1/users/preferences/grid/{moduleKey}` on work-order-service; export from shared/grid barrel
- [ ] T116 Create `GridPreferenceService` in `frontend/angular/src/app/shared/grid/services/grid-preference.service.ts` — not singleton (provided in component so each screen has its own instance); constructor takes `moduleKey: string` and `defaultColumns: ColumnDef[]`; exposes `activeColumns$: BehaviorSubject<ColumnDef[]>`; `load()` calls `UserGridPreferenceApiService.getPreferences()` and falls back to `defaultColumns` on 404; `apply(columns: ColumnDef[])` calls PUT then updates BehaviorSubject; `reset()` calls PUT with `defaultColumns` then updates BehaviorSubject; export from shared/grid barrel
- [ ] T117 Create `ColumnPickerComponent` (standalone) in `frontend/angular/src/app/shared/grid/components/column-picker/column-picker.component.ts` — `@Input() columns: ColumnDef[]`; CDK `cdkDropList` + `cdkDrag` for reordering within each section; auto-splits into Standard Columns and User-Defined Fields sections (by `column.udf`); locked columns (`column.locked`) show "Required" badge, checkbox disabled, no drag handle; UDF columns show ice-blue "UDF" badge; "Reset to default" link in header emits `@Output() reset = new EventEmitter<void>()`; Apply footer button emits `@Output() applied = new EventEmitter<ColumnDef[]>()`; Cancel closes without emitting; export from shared/grid barrel
- [ ] T118 [P] Scaffold item master feature with routing: `ng generate component features/item-master/pages/item-master-list --standalone` in `frontend/angular/`; add lazy route `/item-master → ItemMasterListComponent` to `app.routes.ts`; define `DEFAULT_ITEM_MASTER_COLUMNS: ColumnDef[]` in `frontend/angular/src/app/features/item-master/constants/default-columns.ts` — Part Number (locked), Revision (locked), Description (locked), Classification, Make/Buy, Unit of Measure, Status all `visible: true`; CAGE Code + Shelf Life Days `visible: false`
- [ ] T119 [P] Create `ItemMasterApiService` in `frontend/angular/src/app/features/item-master/services/item-master-api.service.ts` — typed methods: `list(params: ItemMasterListParams): Observable<Page<ItemMasterDto>>`; `getById(id: string): Observable<ItemMasterDto>`; `create(req: CreateItemMasterRequest): Observable<ItemMasterDto>`; `patch(id: string, req: PatchItemMasterRequest): Observable<ItemMasterDto>`
- [ ] T120 Create `ItemMasterListComponent` in `frontend/angular/src/app/features/item-master/pages/item-master-list/item-master-list.component.ts` — provides `GridPreferenceService` in component with `moduleKey: 'ITEM_MASTER'` and `DEFAULT_ITEM_MASTER_COLUMNS`; calls `gridPreference.load()` on init; PrimeNG `p-table` with `[columns]` bound to `activeColumns$ | async`; server-side pagination via `(onLazyLoad)`; filter bar with search, Classification dropdown, Status dropdown; settings icon button (column-picker trigger) next to "Clear filters" toggles PrimeNG `p-overlayPanel` containing `ColumnPickerComponent`; handles `(applied)` by calling `gridPreference.apply()` and `(reset)` by calling `gridPreference.reset()` — `frontend/angular/src/app/features/item-master/pages/item-master-list/`
- [ ] T121 [P] Apply Aurora MES dark mode colour tokens as CSS custom properties in `frontend/angular/src/styles.scss` — import PrimeNG Aura dark preset; override surface, primary, and text variables with Aurora MES dark hex values from Penpot Token Reference Board (bg.base `#0A1628`, bg.subtle `#0D1F3C`, brand.primary `#2563EB`, text.primary `#F1F5F9`, text.secondary `#94A3B8`, border.subtle `#1E3A5F`); scope all overrides inside `.aurora-dark` class selector to avoid polluting light mode
- [ ] T124 [P] Configure PrimeNG dark mode selector in `frontend/angular/src/app/app.config.ts` — set `darkModeSelector: '.aurora-dark'` inside `providePrimeNG()` so PrimeNG switches component theme via CSS class (not OS media query); this gives Aurora MES full user-controlled override independent of OS setting
- [ ] T125 [P] Create `ThemeService` in `frontend/angular/src/app/shared/theme/services/theme.service.ts` — `isDark$: BehaviorSubject<boolean>` initialised from localStorage key `aurora-mes-theme`; falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches` if no saved preference; `toggle()` flips state, persists to localStorage, adds/removes `.aurora-dark` on `document.documentElement`; `init()` called once at bootstrap to hydrate state before first render; export from `frontend/angular/src/app/shared/theme/index.ts` barrel
- [ ] T126 Create `ThemeToggleComponent` (standalone) in `frontend/angular/src/app/shared/theme/components/theme-toggle/theme-toggle.component.ts` — icon button bound to `ThemeService.isDark$`; renders PrimeNG `pi-sun` icon in dark mode, `pi-moon` icon in light mode; `aria-label` reflects current action ("Switch to light mode" / "Switch to dark mode"); 36×36px touch target; no text label; export from `shared/theme/index.ts` barrel
- [ ] T127 Add Aurora MES light mode CSS token overrides to `frontend/angular/src/styles.scss` — within `:root:not(.aurora-dark)` selector override PrimeNG Aura surface vars to light preset values; add Aurora MES light tokens: bg.base `#F8FAFC`, bg.subtle `#EFF6FF`, text.primary `#0F172A`, text.secondary `#64748B`, border.subtle `#CBD5E1`, brand.primary `#2563EB` (unchanged — blue works on both themes); ensure table stripe, overlay panel, and drawer backgrounds respond correctly
- [ ] T128 Place `ThemeToggleComponent` in the app top navigation bar immediately left of the user avatar icon — `frontend/angular/src/app/app.component.html` (or the shell layout template); confirm 8px gap between toggle and avatar matches Penpot shell frame spec
- [ ] T129 Call `ThemeService.init()` in `AppComponent.ngOnInit()` before any route resolves — `frontend/angular/src/app/app.component.ts`; prevents flash-of-wrong-theme on hard reload by applying the saved class synchronously before Angular renders any component
- [ ] T130 [P] For each Penpot frame built this sprint, create a corresponding light mode variant frame on the "Aurora MES / Shell" page — "Shell (light)", "Item Master List (light)", "Column Picker (light)" — applying Aurora MES light mode tokens; document the final light + dark hex values in `specs/008-item-master-bom-management/research.md` under a `## Theme Tokens` section for future reference
- [ ] T122 Run `ng build --configuration=production` in `frontend/angular/` — zero compilation errors
- [ ] T123 Start dev server (`ng serve`), open browser at `http://localhost:4200/item-master`; verify: theme toggle renders next to user avatar; click toggles sun↔moon icon and switches all component colours; preference survives page refresh; column picker and table both respond to theme; OS dark preference applied on first visit when no saved preference exists

**Checkpoint**: Shared grid infrastructure working. Any future screen can add column customisation by importing `GridPreferenceService` + `ColumnPickerComponent` from `shared/grid` and providing its own `moduleKey` + defaults. Item Master list fully functional end-to-end.

> **Raise PR 5 after this checkpoint** (T113–T130) | CI: `ng build --configuration=production` in `frontend/angular/` | Target: `Develop`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — blocks all user story phases
- **Phase 3 (US1) + Phase 4 (US3)**: Depends on Phase 2; US3 bundled with US1 in PR 1
- **Phase 5 (US2)**: Depends on PR 1 merged (item master FK required)
- **Phase 6 (US4) + Phase 7 (US5)**: Depends on PR 2 merged (BOM entities required)
- **Phase 8 (US6)**: Depends on PR 2 merged; independent of PR 3 (AS5553 fields in schema from PR 1)
- **Phase 9 (Polish)**: All PRs complete
- **Phase 10 (Angular Frontend)**: Depends on PR 1 merged (item-master API + grid preferences API live); independent of PRs 2–4 (reads item master list only; BOM/ECO screens added in future sprints)

### User Story Dependencies

| Story | Depends On | Can Parallelise With |
|---|---|---|
| US1 (Item Master) | Foundation | US3 (same PR) |
| US3 (UDF) | US1 item_master table | US1 lib code |
| US2 (BOM) | US1 merged (item_master FK) | — |
| US4 (Effectivity) | US2 merged | US5 (same PR) |
| US5 (ECO) | US2 merged | US4 (same PR) |
| US6 (AS5553) | US2 merged (BOM line alert) | US4, US5 |
| Angular UI | PR 1 merged (API live) | PRs 2–4 (frontend is read-only against item-master in this sprint) |

### Within Each Phase

```
Tests (RED) → Entity/Domain → Repository → Service → Controller → Tests (GREEN)
```

---

## Parallel Opportunities

```
# Phase 1 — all parallel within setup:
T004 WorkOrderServiceApplication.java
T005 application.yml
T006 Dockerfile
T012 mes-udf-lib package skeleton

# Phase 3 US1 — tests parallel before impl:
T024 FlywayMigrationIT
T025 ItemMasterControllerIT
T026 ItemMasterServiceTest
T027 ItemMasterKafkaIT

# Phase 3 US1 — entities parallel:
T029 ItemMaster.java
T030 Enums (Classification, MakeBuyCode, TraceabilityMethod)

# Phase 5 US2 — entities parallel:
T056 BillOfMaterials.java
T057 BomLine.java
T058 BomStatus / EffectivityMethod enums

# Phase 10 Angular — shared infrastructure parallel before item master feature:
T114 ColumnDef model + shared/grid barrel
T115 UserGridPreferenceApiService
T118 ItemMasterApiService + DEFAULT_ITEM_MASTER_COLUMNS scaffold
T119 ItemMasterListComponent scaffold
T121 Aurora MES dark mode CSS token overrides
T124 PrimeNG dark mode selector config
T125 ThemeService
T126 ThemeToggleComponent
T127 Light mode CSS token overrides
T130 Penpot light mode frame variants
```

---

## Implementation Strategy

### MVP (PR 1 only — US1 + US3)

1. Complete Phase 1: module scaffolding
2. Complete Phase 2: Flyway migrations + Testcontainers base
3. Complete Phase 3: Item Master CRUD + events + audit
4. Complete Phase 4: UDF framework
5. Raise PR 1 → CI green → merge
6. **STOP and VALIDATE**: item master API working end-to-end with UDF validation

### Incremental Delivery

- PR 1: Item master foundation (MVP for BOM-dependent services to start consuming)
- PR 2: BOM authoring + explosion (enables work order materialisation design)
- PR 3: Effectivity + ECO (enables AS9100D §8.1 change control)
- PR 4: AS5553 enrichment (enables supply-chain compliance queries)
- PR 5: Angular Item Master UI with shared grid + shared theme infrastructure (column picker and dark/light toggle reusable by all future screens — BOM, Work Orders, Receiving, Inventory)
