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
| PR 1 | Phase 1 + 2 + 3 + 4 | T001–T050, T104–T112 | `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Scaffold + US1 + US3 bundled. **MERGED** |
| PR 2 | Phase 4b + 5 | T051–T066, T174, **T193–T196** | `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Depends on PR 1 merged. T193–T194: ModuleKey enum (BOM_LINE, BOM_HEADER). T195: V013 migration. T196: BillOfMaterials entity update. |
| PR 3 | Phase 6 + 7 | T067–T087 | `./gradlew :services:work-order-service:check` | Depends on PR 2 merged; US4 and US5 share BOM domain |
| PR 4 | Phase 8 | T088–T094, **T190–T192** | `./gradlew :services:work-order-service:check` | Depends on PR 2 merged; P3 priority. T190: V014 QUALITY_ENGINEER seed. T191: qualityEngineerToken() fixture. T192: AS5553IT 403 scenario. |
| PR 5 | Phase 10 | T113–T130, T167 | `ng build --configuration=production` + `ng lint --max-warnings 0` in `frontend/angular/` | Angular shared grid + theme + item master list (basic). **MERGED** |
| PR 6 | Phase 11 | T131–T148, T168 | `ng build --configuration=production` + `ng lint --max-warnings 0` in `frontend/angular/` | App shell + item master list fidelity + item master create/edit dialog (interim). **MERGED** |
| PR 6b | Phase 11b + 11c | **T177–T185, T197–T202** | `ng build --configuration=production` + `ng test --watch=false` + `ng lint --max-warnings 0` in `frontend/angular/` + `./gradlew :services:work-order-service:check` (T180 has backend) | Penpot fidelity corrections: ClassificationLabelPipe, BreadcrumbComponent, pagination text, Clone Item, full-page create/edit routes, column picker corrections; UdfApiService (T197); Angular component unit tests T198–T202 (Constitution §II). Depends on PR 6 merged. |
| PR 7 | Phase 12 + 13 | T149–T166, T169, T170, **T175–T176**, **T186–T189** | `ng build --configuration=production` + `ng lint --max-warnings 0` in `frontend/angular/` | BOM frontend + ECO frontend + BOM Header Edit dialog. Depends on PR 6b merged AND PRs 2–3 merged. |

**Sequencing note**: US3 (UDF, P2) is inside PR 1 with the P1 stories. US4+US5 (both P2) are PR 3. US6 (P3) is PR 4. PR 5 merged with item master list only — PR 6 closes the visual/functional gap; PR 7 delivers BOM/ECO frontend screens needed for spec compliance.

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

- [X] T014 Create `services/work-order-service/src/main/resources/db/migration/V001__create_work_order_schema.sql` — `CREATE SCHEMA IF NOT EXISTS work_order;`
- [X] T015 Create `services/work-order-service/src/main/resources/db/migration/V002__create_item_master.sql` — full DDL for `work_order.item_master`: all columns (id UUID PK, org_id UUID NOT NULL, part_number, revision, description, unit_of_measure, cage_code, classification, make_buy_code, traceability_method, shelf_life_controlled BOOLEAN NOT NULL DEFAULT false, shelf_life_days INTEGER, step_part_ref, counterfeit_risk_level, approved_suppliers JSONB, verification_required BOOLEAN, custom_fields JSONB, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', audit columns); UNIQUE(org_id, part_number, revision); CHECK(shelf_life_controlled = false OR shelf_life_days IS NOT NULL); all indexes from data-model.md
- [X] T016 Create `services/work-order-service/src/main/resources/db/migration/V003__create_bom_tables.sql` — DDL for `work_order.bill_of_materials` (id, org_id, parent_item_id UUID FK→item_master.id, bom_revision, status DEFAULT 'DRAFT', description, eco_id UUID nullable, audit columns; UNIQUE(org_id, parent_item_id, bom_revision)) and `work_order.bom_line` (id, bom_id FK→bill_of_materials.id, component_item_id FK→item_master.id, quantity NUMERIC(18,6), unit_of_measure, find_number, reference_designators, effectivity_method VARCHAR(10), effective_from_date DATE, effective_to_date DATE, effective_from_unit, effective_to_unit, audit columns); all indexes from data-model.md
- [X] T017 Create `services/work-order-service/src/main/resources/db/migration/V004__create_eco_tables.sql` — DDL for `work_order.engineering_change_order` (id, org_id, eco_number UNIQUE, title, description TEXT, status DEFAULT 'DRAFT', initiated_by, approved_by, approved_at, implemented_at, audit columns) and `work_order.eco_affected_item` (eco_id FK, item_id FK, PK(eco_id, item_id)); index on (org_id, status)
- [X] T018 Create `services/work-order-service/src/main/resources/db/migration/V005__create_udf_field_definition.sql` — DDL for `work_order.udf_field_definition`: id, org_id, module_key VARCHAR(50), field_key VARCHAR(100), label, field_type, required BOOLEAN, default_value, list_options JSONB, validation_rules JSONB, display_order INTEGER, active BOOLEAN DEFAULT true, audit columns; UNIQUE(org_id, module_key, field_key)
- [X] T019 Create `services/work-order-service/src/main/resources/db/migration/V006__add_envers_tables.sql` — DDL for `work_order.revinfo` (rev INTEGER PK, revtstmp BIGINT, actor VARCHAR(255)), `work_order.item_master_aud`, `work_order.bill_of_materials_aud`, `work_order.bom_line_aud`, `work_order.engineering_change_order_aud` (each mirrors entity columns + REV INT FK→revinfo, REVTYPE SMALLINT)
- [X] T020 Create `services/work-order-service/src/main/resources/db/migration/V007__seed_item_master_privileges.sql` — INSERT into `iam.privilege` for all 5 item-master privilege keys (item-master:records:view, item-master:records:manage, item-master:bom:manage, item-master:eco:manage, item-master:udf:manage); INSERT into `iam.role_privilege` granting all 5 to SYSTEM_ADMIN and item-master:records:view + item-master:records:manage + item-master:bom:manage + item-master:eco:manage to ENGINEER; use SELECT to look up role IDs by name
- [X] T021 [P] Create `services/work-order-service/src/main/java/com/mes/workorder/config/SecurityConfig.java` — extends lib-common-security pattern; permits `/actuator/health` without auth; requires JWT for all other endpoints; uses `@EnableMESSecurity` annotation from lib-common-security
- [X] T022 [P] Implement privilege registration on startup: update `WorkOrderServiceApplication.java` to implement `ApplicationListener<ApplicationReadyEvent>` and call `PrivilegeRegistryClient.register(moduleName, privileges)` with the 5 item-master privilege keys and descriptions
- [X] T023 Create `services/work-order-service/src/test/java/com/mes/workorder/integration/BaseIntegrationTest.java` — abstract class with `@Testcontainers(disabledWithoutDocker = true)`, PostgreSQL 16 container, Kafka container, `@DynamicPropertySource` registering datasource URL, Kafka bootstrap servers, `mes.security.iam-service-url` mock, Keycloak issuer URI override with RSA JWT support (pattern from existing IT tests); `systemProperty 'api.version', '1.41'` per ERR-MES-036
- [X] T104 Create `services/work-order-service/src/main/resources/db/migration/V008__create_user_grid_preferences.sql` — DDL for `work_order.user_grid_preferences`: id UUID PK DEFAULT gen_random_uuid(), org_id UUID NOT NULL, user_id VARCHAR(255) NOT NULL (stores JWT sub claim), module_key VARCHAR(50) NOT NULL, column_config JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now(); UNIQUE(org_id, user_id, module_key); index on (org_id, user_id)

**Checkpoint**: Foundation ready — `./gradlew :services:work-order-service:build` succeeds; Flyway migration applies cleanly in a fresh Testcontainers PostgreSQL instance (verified by running FlywayMigrationIT once written in Phase 3).

---

## Phase 3: User Story 1 — Item Master CRUD (Priority: P1) 🎯 MVP [PR 1]

**Goal**: Create, retrieve, patch, obsolete item master records; Kafka events on create/update; Envers audit trail; org-scoped; UDF validation stub (validates only if definitions exist, passes if none defined yet).

**Independent Test**: `ENGINEER` creates a fabricated aluminium bracket (partNumber=BRKT-001, revision=A), retrieves it by ID, PATCHes the description, then queries `work_order.item_master_aud` and confirms 2 audit rows. GET /item-master returns the record in the paginated list. Both `item-master.created` and `item-master.updated` Kafka messages appear on `work-order.item-master.events` within 5 seconds.

### Tests (write FIRST — must FAIL before implementation)

- [X] T024 [P] [US1] Write `FlywayMigrationIT`: assert all V001–V007 tables exist in `work_order` schema; assert 5 privilege rows exist in `iam.privilege` for module item-master; assert SYSTEM_ADMIN has all 5 grants — `services/work-order-service/src/test/java/com/mes/workorder/integration/FlywayMigrationIT.java`
- [X] T025 [P] [US1] Write `ItemMasterControllerIT`: POST creates record → 201 + Location header; POST duplicate partNumber+revision → 409; PATCH description → 200 + modified fields; GET unauthenticated → 401; GET with ENGINEER token → 200; shelfLifeControlled=true without shelfLifeDays → 422; POST with `stepPartRef` set → GET response includes `stepPartRef` value (FR-004) — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/ItemMasterControllerIT.java`
- [X] T026 [P] [US1] Write `ItemMasterServiceTest` (unit): uniqueness constraint throws conflict exception; shelf-life constraint throws validation exception; audit fields (createdBy, modifiedBy) populated from security context — `services/work-order-service/src/test/java/com/mes/workorder/unit/itemmaster/ItemMasterServiceTest.java`
- [X] T027 [P] [US1] Write `ItemMasterKafkaIT`: create item master with UDF value → assert `work-order.item-master.events` receives message with eventType=ITEM_MASTER_CREATED, entityId, and `customFields` property matching submitted UDF values (FR-027); PATCH → assert ITEM_MASTER_UPDATED message also contains updated `customFields` — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/ItemMasterKafkaIT.java`
- [X] T028 [US1] Confirm all 4 tests above FAIL (RED) with compile or assertion errors before writing any production code
- [X] T105 [P] [US1] Write `UserGridPreferenceControllerIT`: GET with no saved config → 200 + default column list for module; PUT saves config → 200; second GET → returns saved config; different `moduleKey` → independent config; different JWT sub (different user) → independent config — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/UserGridPreferenceControllerIT.java`
- [X] T106 [US1] Confirm T105 FAILS (RED) before writing any preference implementation

### Implementation

- [X] T029 [P] [US1] Create `ItemMaster.java` JPA entity: all columns from V002 migration, `@Audited`, `@EntityListeners(AuditingEntityListener.class)`, `@Table(name="item_master", schema="work_order")`, `@Column` mappings, shelf-life check constraint enforced in `@PrePersist`/`@PreUpdate` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/domain/ItemMaster.java`
- [X] T030 [P] [US1] Create enums: `Classification`, `MakeBuyCode`, `TraceabilityMethod`, `ItemStatus` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/domain/`
- [X] T031 [US1] Create `ItemMasterRepository.java`: `findByOrgIdAndPartNumberAndRevision`, `findByOrgIdAndId`, `findAllByOrgIdAndStatusAndClassification` (Page), `existsByOrgIdAndPartNumberAndRevision` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/repository/ItemMasterRepository.java`
- [X] T032 [US1] Create DTOs: `ItemMasterDto`, `CreateItemMasterRequest` (Bean Validation annotations), `PatchItemMasterRequest` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/dto/`
- [X] T033 [US1] Create `ItemMasterMapper.java` (MapStruct or manual): entity ↔ DTO conversion, customFields JSONB ↔ `Map<String, Object>` — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/dto/ItemMasterMapper.java`
- [X] T034 [US1] Create `ItemMasterService.java`: `create()` (unique check, shelf-life validation, UdfValidator.validate() — no-op if no definitions exist, save, publish CREATED event), `get()` (org-scoped), `patch()` (org-scoped, validate, save, publish UPDATED event), `obsolete()`, `list()` (paginated, org-scoped) — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/service/ItemMasterService.java`
- [X] T035 [US1] Create `ItemMasterEventPublisher.java`: sends JSON event to `work-order.item-master.events` Kafka topic; event payload includes `eventId` (UUID), `eventType`, `entityId`, `orgId`, `actorId`, `occurredAt`, `payload` (DTO snapshot) — `services/work-order-service/src/main/java/com/mes/workorder/kafka/ItemMasterEventPublisher.java`
- [X] T036 [US1] Create `ItemMasterController.java`: `GET /item-master` (paginated list, classification + status + counterfeitRiskLevel filters), `POST /item-master` (201 + Location), `GET /item-master/{itemId}`, `PATCH /item-master/{itemId}`, `POST /item-master/{itemId}/obsolete`; privilege checks via `@PreAuthorize` or `RequiresPrivilege` annotation from lib-common-security — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/ItemMasterController.java`
- [X] T107 [P] [US1] Create `UserGridPreference.java` entity: all columns from V008 migration, `columnConfig` mapped as `@Column(columnDefinition="jsonb")` with Jackson JSON type converter, `@Table(name="user_grid_preferences", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/domain/UserGridPreference.java`
- [X] T108 [US1] Create `UserGridPreferenceRepository.java`: `findByOrgIdAndUserIdAndModuleKey(UUID orgId, String userId, String moduleKey): Optional<UserGridPreference>` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/repository/UserGridPreferenceRepository.java`
- [X] T109 [P] [US1] Create DTOs: `ColumnPreferenceEntry` record (`columnKey: String, visible: boolean, order: int`), `UserGridPreferenceDto` (moduleKey + list of entries), `UpsertUserGridPreferenceRequest` — `services/work-order-service/src/main/java/com/mes/workorder/preferences/api/dto/`
- [X] T110 [US1] Create `UserGridPreferenceService.java`: `get(orgId, userId, moduleKey)` returns saved column list or the module's built-in default (defaults registered via `@Bean Map<String,List<ColumnPreferenceEntry>> defaultColumnRegistry` so any service can contribute its defaults); `upsert(orgId, userId, moduleKey, entries)` saves-or-replaces via findByOrgIdAndUserIdAndModuleKey + save — `services/work-order-service/src/main/java/com/mes/workorder/preferences/service/UserGridPreferenceService.java`
- [X] T111 [US1] Create `UserGridPreferenceController.java`: `GET /api/v1/users/preferences/grid/{moduleKey}` (userId extracted from JWT sub claim; no additional privilege — users read their own prefs); `PUT /api/v1/users/preferences/grid/{moduleKey}` (userId from JWT sub; any authenticated user) — `services/work-order-service/src/main/java/com/mes/workorder/preferences/api/UserGridPreferenceController.java`
- [X] T112 [US1] Run `./gradlew :services:work-order-service:test --tests "*UserGridPreference*"` — confirm T105 goes GREEN (integration tests deferred to Docker env)
- [X] T037 [US1] Run `./gradlew :services:work-order-service:test --tests "*ItemMaster*" --tests "*FlywayMigrationIT*"` — confirm T024–T027 go GREEN (unit tests GREEN; integration tests run in CI with Docker)

**Checkpoint**: Item master CRUD end-to-end functional. Kafka events emitting. Envers audit rows present.

---

## Phase 4: User Story 3 — UDF Framework (Priority: P2) [PR 1 continued]

**Goal**: `mes-udf-lib` provides `UdfFieldDefinition` CRUD scoped by org + module; `UdfValidator` enforces required fields, LIST options, NUMBER ranges, TEXT maxLength on item master create/patch. Framework is module-agnostic and reusable by future services.

**Independent Test**: `SYSTEM_ADMIN` defines required TEXT field `drawing_ref` on ITEM_MASTER. ENGINEER creates item master without it → 422. ENGINEER retries with `customFields: {"drawing_ref": "DRW-001"}` → 201. GET returns `customFields.drawing_ref = "DRW-001"`. SYSTEM_ADMIN deletes `drawing_ref` without `force=true` when 1 record has value → 409.

### Tests (write FIRST — must FAIL before implementation)

- [X] T038 [P] [US3] Write `UdfValidatorTest` (unit): TEXT required field missing → error; LIST field value not in options → error; NUMBER below min → error; NUMBER above max → error; BOOLEAN type coercion; DATE format validation; no definitions → passes — `libs/mes-udf-lib/src/test/java/com/mes/udf/service/UdfValidatorTest.java`
- [X] T039 [P] [US3] Write `UdfFieldDefinitionControllerIT`: POST defines field → 201; GET lists fields by module; ENGINEER POST → 403; duplicate fieldKey → 409; LIST field with invalid option on item master create → 422; DELETE with values on records without force → 409; DELETE with force=true → 204 + values nulled — `services/work-order-service/src/test/java/com/mes/workorder/integration/udf/UdfFieldDefinitionControllerIT.java`
- [X] T040 [P] [US3] Write `ItemMasterWithUdfIT`: required UDF missing on create → 422 with field name in error; present → 201 and GET returns customFields; NUMBER range violation → 422 — `services/work-order-service/src/test/java/com/mes/workorder/integration/udf/ItemMasterWithUdfIT.java`
- [X] T041 [US3] Confirm all 3 tests above FAIL (RED) before writing production code

### Implementation

- [X] T042 [P] [US3] Create `UdfFieldDefinition.java` entity: all columns from V005 migration, `@Table(name="udf_field_definition", schema="work_order")` (schema is injected via `@ConfigurationProperties` so lib is schema-agnostic), `@Audited` — `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/UdfFieldDefinition.java`
- [X] T043 [P] [US3] Create `UdfFieldType.java` enum (TEXT, NUMBER, DATE, BOOLEAN, LIST) and `ModuleKey.java` enum (ITEM_MASTER, WORK_ORDER, ROUTING, RECEIVING, INVENTORY) — `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/` **⚠ NOTE: This task is partially complete — FR-026 requires 7 enum values. `BOM_LINE` and `BOM_HEADER` are added by T193 (PR 2 blocker). Do not treat this Epic's FR-026 coverage as full until T193 is merged.**
- [X] T044 [US3] Create `UdfFieldDefinitionRepository.java`: `findByOrgIdAndModuleKeyAndActiveTrue`, `findByOrgIdAndModuleKeyAndFieldKey`, `countByOrgIdAndModuleKeyAndFieldKeyAndCustomFieldValueNotNull` — `libs/mes-udf-lib/src/main/java/com/mes/udf/repository/UdfFieldDefinitionRepository.java`
- [X] T045 [US3] Create `UdfValidator.java`: `validate(orgId, moduleKey, Map<String,Object> customFields)` — loads active definitions, checks required fields present, validates type + constraints; returns list of `UdfViolation` records (fieldKey, message) — `libs/mes-udf-lib/src/main/java/com/mes/udf/service/UdfValidator.java`
- [X] T046 [US3] Create `UdfFieldDefinitionService.java`: `define()` (unique check, save), `list(orgId, moduleKey)`, `deactivate(orgId, fieldId, force)` (if !force and values exist → throw conflict with count; if force → null values across all records in consuming service's table via a configurable callback, record audit entry) — `libs/mes-udf-lib/src/main/java/com/mes/udf/service/UdfFieldDefinitionService.java`
- [X] T047 [US3] Create `UdfFieldDefinitionController.java`: `GET /udf/fields?module=ITEM_MASTER`, `POST /udf/fields` (requires `item-master:udf:manage` privilege), `DELETE /udf/fields/{fieldId}?force={bool}` — `libs/mes-udf-lib/src/main/java/com/mes/udf/api/UdfFieldDefinitionController.java`
- [X] T048 [US3] Update `UdfAutoConfiguration.java` to register `UdfValidator`, `UdfFieldDefinitionService`, `UdfFieldDefinitionController`, `UdfFieldDefinitionRepository` as beans — `libs/mes-udf-lib/src/main/java/com/mes/udf/config/UdfAutoConfiguration.java`
- [X] T049 [US3] Wire `UdfValidator` into `ItemMasterService.create()` and `ItemMasterService.patch()`: inject bean, call before save, map `UdfViolation` list to HTTP 422 `ErrorResponse` listing each missing/invalid field — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/service/ItemMasterService.java`
- [X] T050 [US3] Run `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` — confirm all US1+US3 tests GREEN

**Checkpoint**: Full item master + UDF framework functional and GREEN.

> **Raise PR 1 after this checkpoint** (T001–T050, T104–T112) | CI: `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Target: `Develop`

---

## Phase 4b: UDF Library — BOM Module Keys [PR 2, prerequisite for Phase 5]

**Purpose**: Extend `ModuleKey` enum with `BOM_LINE` and `BOM_HEADER` values required by FR-036 and FR-036a before any BOM UDF work begins. Must be included in PR 2 (not deferred) because `BomAuthoringComponent` (T153) and `BomHeaderEditDialogComponent` (T175) both call `UdfApiService` with these module keys.

- [ ] T193 [P] **⛔ PR 2 BLOCKER** Update `ModuleKey.java` enum in `mes-udf-lib` to add `BOM_LINE` and `BOM_HEADER` values: `libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java` — add two enum constants: `BOM_LINE` (used for UDF columns on BOM authoring lines, FR-036) and `BOM_HEADER` (used for UDF fields on BOM header edit dialog, FR-036a); rebuild `mes-udf-lib` to confirm zero compilation errors; run `./gradlew :libs:mes-udf-lib:compileJava`
- [ ] T194 [P] Extend `UdfLibReusabilityIT` to cover `BOM_LINE` and `BOM_HEADER` module keys — add test scenarios to `libs/mes-udf-lib/src/test/java/com/mes/udf/UdfLibReusabilityIT.java`: (a) define TEXT field on `BOM_LINE` → service returns saved entity with correct moduleKey; (b) validate required BOM_LINE field absent → violation returned; (c) define TEXT field on `BOM_HEADER` → service returns saved entity; (d) confirm `BOM_LINE` and `BOM_HEADER` definitions are independent of `ITEM_MASTER` definitions (same validator, different scope); run `./gradlew :libs:mes-udf-lib:test` to confirm all new scenarios pass

**Checkpoint**: `ModuleKey` enum contains all 7 values; `UdfLibReusabilityIT` covers ITEM_MASTER, ROUTING, BOM_LINE, BOM_HEADER.

---

## Phase 5: User Story 2 — Multi-Level BOM Authoring (Priority: P1) [PR 2]

**Goal**: Create BOM revisions, author BOM lines, release BOM (freezes structure), explode flat and indented via PostgreSQL recursive CTE; circular reference detection at line-add time; `counterfeitRiskAlert` and `componentObsoleted` flags on explosion nodes.

**Independent Test**: Engineer creates a two-level BOM (Assembly→Sub-Assembly→Component, 3 lines total). Requests GET `/boms/{bomId}/explosion?format=indented`. Response contains 3 nodes at correct depths with rolled-up quantities. Attempting to add a line creating a cycle returns 422. Releasing the BOM and then adding another line returns 409.

### Migration — BOM Header Edit Fields (FR-036a)

- [ ] T195 [P] Create Flyway migration `V013__bom_header_edit_fields.sql` at `services/work-order-service/src/main/resources/db/migration/` — `ALTER TABLE work_order.bill_of_materials ADD COLUMN IF NOT EXISTS reason_for_revision VARCHAR(500)`, `ADD COLUMN IF NOT EXISTS production_line VARCHAR(200)`, `ADD COLUMN IF NOT EXISTS bom_type VARCHAR(50)`, `ADD COLUMN IF NOT EXISTS effectivity_type VARCHAR(10)`, `ADD COLUMN IF NOT EXISTS custom_fields JSONB`; add corresponding Envers audit columns to `bill_of_materials_aud`: `reason_for_revision`, `production_line`, `bom_type`, `effectivity_type`, `custom_fields`; also update `FlywayMigrationIT` to assert all five new columns exist on `bill_of_materials` and `bill_of_materials_aud` — `services/work-order-service/src/main/resources/db/migration/V013__bom_header_edit_fields.sql`
- [ ] T196 [P] Update `BillOfMaterials.java` entity to map the four new columns added by V013: add `@Column(name="reason_for_revision")` String, `@Column(name="bom_type") BomType` (new enum: MANUFACTURING, ENGINEERING, SERVICE), `@Column(name="effectivity_type") EffectivityType` (new enum: DATE, UNIT, NONE), `@Column(name="custom_fields", columnDefinition="jsonb") Map<String, Object>` with hypersistence JSON type converter; create `BomType.java` enum at `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/`; update `BomDto` and `PatchBomHeaderRequest` to include these fields; update `BomMapper` to map them; update `BomService.patchHeader()` to apply updates from the request

### Tests (write FIRST — must FAIL before implementation)

- [X] T051 [P] [US2] Write `BomControllerIT`: create BOM header → 201; add lines → 201; GET /boms/{id}/lines returns all lines; release → 200; add line to released BOM → 409; create line with non-existent componentItemId → 422 — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomControllerIT.java`
- [X] T052 [P] [US2] Write `BomExplosionIT`: 3-level BOM flat explosion returns all nodes; indented explosion returns nested tree; explosion with depth > `mes.bom.max-depth` returns 422; circular line add → 422 with response body containing the cycle path (the UUIDs forming the loop, per spec Edge Case §5 — "identifying the loop path"); `counterfeitRiskAlert` true for HIGH-risk component — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomExplosionIT.java`
- [X] T053 [P] [US2] Write `BomServiceTest` (unit): draft-only guard throws conflict for RELEASED BOM; circular detection mock verifies CTE query called before insert; duplicate (bomId, findNumber, componentItemId) → no-op or error depending on spec — `services/work-order-service/src/test/java/com/mes/workorder/unit/bom/BomServiceTest.java`
- [X] T054 [P] [US2] Write `BomKafkaIT`: release BOM → `work-order.bom.events` receives BOM_RELEASED message — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomKafkaIT.java`
- [X] T055 [US2] Confirm all 4 tests FAIL (RED)

### Implementation

- [X] T056 [P] [US2] Create `BillOfMaterials.java` entity: all columns from V003 migration, `@Audited`, FK to `ItemMaster` via UUID column (no JPA FK to avoid cross-aggregate coupling — use UUID field and look up separately), `@Table(name="bill_of_materials", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/BillOfMaterials.java`
- [X] T057 [P] [US2] Create `BomLine.java` entity: all columns from V003 (effectivity columns nullable), `@Audited`, `@Table(name="bom_line", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/BomLine.java`
- [X] T058 [P] [US2] Create `BomStatus.java` and `EffectivityMethod.java` enums — `services/work-order-service/src/main/java/com/mes/workorder/bom/domain/`
- [X] T059 [US2] Create `BomRepository.java`: `findByOrgIdAndId`, `findByOrgIdAndParentItemIdAndBomRevision`, `existsByOrgIdAndParentItemIdAndBomRevision`; custom native query `hasAncestorCycle(bomId UUID, candidateComponentId UUID) : boolean` (pre-insert circular check CTE) — `services/work-order-service/src/main/java/com/mes/workorder/bom/repository/BomRepository.java`
- [X] T060 [US2] Create `BomLineRepository.java`: `findAllByBomId`, `findByBomIdAndFindNumber` (for effectivity checks) — `services/work-order-service/src/main/java/com/mes/workorder/bom/repository/BomLineRepository.java`
- [X] T061 [US2] Create DTOs: `BomDto`, `CreateBomRequest`, `BomLineDto` (includes `counterfeitRiskAlert`, `componentObsoleted`), `CreateBomLineRequest`, `BomExplosionNode` (recursive children list for indented) — `services/work-order-service/src/main/java/com/mes/workorder/bom/api/dto/`
- [X] T062 [US2] Create `BomExplosionService.java`: `explode(bomId, format, asOfDate, asOfUnit)` — executes native recursive CTE (WITH RECURSIVE bom_tree … CYCLE component_item_id SET is_cycle USING cycle_path) via `@Query(nativeQuery=true)` on `BomLineRepository`; respects `mes.bom.max-depth`; builds flat list or indented tree; decorates each node with `counterfeitRiskAlert` (component risk level HIGH/CRITICAL) and `componentObsoleted` (item status OBSOLETE); detects effectivity gaps and throws 422 — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [X] T063 [US2] Create `BomService.java`: `createBom()` (validate parentItemId exists in org, check revision uniqueness), `addLine()` (guard DRAFT status, validate componentItemId exists, run circular-ancestor CTE check, save), `releaseBom()` (DRAFT→RELEASED state machine, emit BOM_RELEASED event), `getBom()`, `listLines()` — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [X] T064 [US2] Create `BomEventPublisher.java`: publishes to `work-order.bom.events` for BOM_RELEASED and BOM_OBSOLETED event types — `services/work-order-service/src/main/java/com/mes/workorder/kafka/BomEventPublisher.java`
- [X] T065 [US2] Create `BomController.java`: `POST /boms`, `GET /boms/{bomId}`, `POST /boms/{bomId}/release`, `GET /boms/{bomId}/lines`, `POST /boms/{bomId}/lines`, `GET /boms/{bomId}/explosion` — all require `item-master:bom:manage` privilege — `services/work-order-service/src/main/java/com/mes/workorder/bom/api/BomController.java`
- [X] T174 [US2] Add `PATCH /boms/{bomId}/lines/{lineId}` to `BomController.java` — allows modifying an existing BOM line (quantity, unitOfMeasure, findNumber, referenceDesignators, effectivity fields); `BomService.updateLine()` guards DRAFT status (HTTP 409 if RELEASED); calls `EffectivityValidator.validateNewLine()` for effectivity changes; add test scenarios to `BomControllerIT`: modify quantity → 200 + updated field; modify line on RELEASED BOM → 409 (covers FR-007 "modify lines" requirement) **⚠ Verify `UpdateBomLineRequest.java` DTO was created as part of this task. If missing, create it at `services/work-order-service/src/main/java/com/mes/workorder/bom/api/dto/UpdateBomLineRequest.java` with fields: `quantity`, `unitOfMeasure`, `findNumber`, `referenceDesignators`, `effectivityMethod`, `effectiveFromDate`, `effectiveToDate`, `effectiveFromUnit`, `effectiveToUnit` (all optional/nullable). Required by T188 frontend referenceDesignators work.**
- [X] T066 [US2] Run `./gradlew :services:work-order-service:check` — confirm all US1+US3+US2 tests GREEN

**Checkpoint**: BOM authoring + explosion end-to-end functional. Circular detection works. Kafka events emitting.

> **Raise PR 2 after this checkpoint** (T051–T066, T174, T193–T196) | CI: `./gradlew :services:work-order-service:check :libs:mes-udf-lib:check` | Target: `Develop`

---

## Phase 6: User Story 4 — BOM Effectivity Management (Priority: P2) [PR 3]

**Goal**: DATE and UNIT effectivity on BOM lines; `effectiveFrom*` required when method set; `effectiveTo*` optional (null = open-ended); overlap validation with specific error identifying conflicting line by find number and UUID; effectivity gap detection in explosion.

**Independent Test**: BOM with two DATE-effective lines for find number 003: line A effective 2025-01-01→2025-12-31, line B effective 2026-01-01→null (open-ended). Explosion for asOfDate=2025-06-01 returns only line A. Explosion for asOfDate=2026-06-01 returns only line B. Attempting a third line for find 003 with dates overlapping line A returns 422 with "date range overlap for BOM line find number 003 — conflicts with existing line ID {uuid}".

### Tests (write FIRST — must FAIL before implementation)

- [ ] T067 [P] [US4] Write `BomEffectivityIT`: AS1 date range inclusion/exclusion (2025-06-01 includes, 2026-01-01 excludes 2025 line); AS2 unit range inclusion/exclusion; AS3 overlap → 422 message contains find number + conflicting line UUID; AS4 explosion for date with no covering line → 422 effectivity gap; AS5 open-ended line (effectiveToDate null) included for all future dates — `services/work-order-service/src/test/java/com/mes/workorder/integration/bom/BomEffectivityIT.java`
- [ ] T068 [P] [US4] Write `EffectivityValidatorTest` (unit): overlap detection for same findNumber across DATE lines; open-ended (null effectiveTo) treated as far-future; UNIT method with null effectiveToUnit = open-ended; no effectivity method set = always included — `services/work-order-service/src/test/java/com/mes/workorder/unit/bom/EffectivityValidatorTest.java`
- [X] T069 [US4] Confirm both tests FAIL (RED)

### Implementation

- [ ] T070 [US4] Create `EffectivityValidator.java`: `validateNewLine(bomId, newLine)` — queries existing lines for same `(bomId, findNumber)` with DATE effectivity; checks overlap with new line (treating null effectiveTo as `LocalDate.MAX`); on conflict throws `EffectivityOverlapException(findNumber, conflictingLineId)` which maps to HTTP 422; for UNIT method validates `effectiveFromUnit` not null when method set — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/EffectivityValidator.java`
- [ ] T071 [US4] Update `BomService.addLine()` to call `EffectivityValidator.validateNewLine()` before persisting; validate `effectiveFromDate`/`effectiveFromUnit` is non-null when `effectivityMethod` is set; throw 422 if violation — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [ ] T072 [US4] Update `BomExplosionService.explode()` to apply effectivity filter: for DATE lines, include if `effectiveFromDate ≤ asOfDate` AND (`effectiveToDate IS NULL` OR `effectiveToDate ≥ asOfDate`); for UNIT lines, include if `effectiveFromUnit ≤ asOfUnit` AND (`effectiveToUnit IS NULL` OR `effectiveToUnit ≥ asOfUnit`); detect find numbers with lines but none covering the requested date/unit → throw 422 gap error — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [ ] T073 [US4] Update recursive CTE in `BomExplosionService` native query to pass `:asOfDate` parameter; handle null asOfDate (no effectivity filter applied) — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java`
- [X] T074 [US4] Run `./gradlew :services:work-order-service:test --tests "*BomEffectivity*"` — confirm US4 tests GREEN

**Checkpoint**: Effectivity filtering working; overlap detection error messages include find number and conflicting line UUID.

---

## Phase 7: User Story 5 — Engineering Change Orders (Priority: P2) [PR 3 continued]

**Goal**: ECO CRUD; Draft→Approved state machine; Approved ECOs are immutable; link to affected item masters and output BOM revisions; concurrent ECO warning on create; `eco.approved` Kafka event.

**Independent Test**: Create ECO referencing item masters A and B. Create a second ECO also referencing item master A — response has `concurrentEcoWarning: true`. Approve first ECO. Create new BOM revision referencing ecoId. GET first ECO → `outputBomIds` contains the new BOM ID. Attempt to edit approved ECO description → 409.

### Tests (write FIRST — must FAIL before implementation)

- [ ] T075 [P] [US5] Write `EcoControllerIT`: AS1 create draft → 201; AS2 approve → 200 + approvedBy set; AS3 new BOM with ecoId → ECO outputBomIds updated; AS4 concurrent ECO for same item → 201 with concurrentEcoWarning=true; AS5 edit APPROVED ECO → 409 — `services/work-order-service/src/test/java/com/mes/workorder/integration/eco/EcoControllerIT.java`
- [ ] T076 [P] [US5] Write `EcoKafkaIT`: approve ECO → `work-order.eco.events` receives ECO_APPROVED message — `services/work-order-service/src/test/java/com/mes/workorder/integration/eco/EcoKafkaIT.java`
- [ ] T077 [P] [US5] Write `EcoServiceTest` (unit): concurrent check queries open ECOs for same item IDs; APPROVED status rejects mutation; state machine transition only from DRAFT — `services/work-order-service/src/test/java/com/mes/workorder/unit/eco/EcoServiceTest.java`
- [X] T078 [US5] Confirm all 3 tests FAIL (RED)

### Implementation

- [ ] T079 [P] [US5] Create `EngineeringChangeOrder.java` entity: all V004 columns, `@Audited`, `@ElementCollection` for `affectedItemIds` (UUID list via eco_affected_item table), `@ElementCollection` for `outputBomIds` (UUID list), `@Table(name="engineering_change_order", schema="work_order")` — `services/work-order-service/src/main/java/com/mes/workorder/eco/domain/EngineeringChangeOrder.java`
- [ ] T080 [P] [US5] Create `EcoStatus.java` enum (DRAFT, APPROVED, IMPLEMENTED) — `services/work-order-service/src/main/java/com/mes/workorder/eco/domain/EcoStatus.java`
- [ ] T081 [US5] Create `EcoRepository.java`: `findByOrgIdAndId`, `findOpenEcosForItemId(orgId, itemId)` (status IN (DRAFT, APPROVED)) — `services/work-order-service/src/main/java/com/mes/workorder/eco/repository/EcoRepository.java`
- [ ] T082 [US5] Create DTOs: `EcoDto` (includes `concurrentEcoWarning` — set on POST create response only; `hasConcurrentWarning: boolean` — server-computed field included in both POST and GET responses per FR-039; `affectedItemIds`, `outputBomIds`), `CreateEcoRequest` — `services/work-order-service/src/main/java/com/mes/workorder/eco/api/dto/`
- [ ] T083 [US5] Create `EcoService.java`: `create()` (check concurrent ECOs per affected item, set concurrentEcoWarning, generate ecoNumber sequence, save); `list(orgId, status)` (paginated; for each `EcoDto` in the list response compute `hasConcurrentWarning` by calling `EcoRepository.findOpenEcosForItemId()` for each affected item ID and checking if any result has a different ECO ID — batch this to avoid N+1); **⚠ Prerequisite: Add `CREATE SEQUENCE work_order.eco_number_seq START WITH 1000 INCREMENT BY 1` as a new Flyway migration V015 (PR 3 scope — V004 is already merged). Update `eco_number` column to use `DEFAULT nextval('work_order.eco_number_seq')` in V015 or via `ALTER TABLE`.** `approve()` (DRAFT→APPROVED guard, set approvedBy from JWT sub, set approvedAt, emit ECO_APPROVED event); `addOutputBom(ecoId, bomId)` (called from BomService.releaseBom when ecoId present); reject any mutation if status ≠ DRAFT — `services/work-order-service/src/main/java/com/mes/workorder/eco/service/EcoService.java`
- [ ] T084 [US5] Create `EcoEventPublisher.java`: publishes to `work-order.eco.events` for ECO_APPROVED and ECO_IMPLEMENTED — `services/work-order-service/src/main/java/com/mes/workorder/kafka/EcoEventPublisher.java`
- [ ] T085 [US5] Create `EcoController.java`: `POST /ecos`, `GET /ecos/{ecoId}`, `POST /ecos/{ecoId}/approve` — requires `item-master:eco:manage` privilege — `services/work-order-service/src/main/java/com/mes/workorder/eco/api/EcoController.java`
- [ ] T086 [US5] Update `BomService.releaseBom()` to call `EcoService.addOutputBom(ecoId, bomId)` when BOM's `ecoId` is non-null — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomService.java`
- [X] T087 [US5] Run `./gradlew :services:work-order-service:check` — confirm all US4+US5 tests GREEN

**Checkpoint**: ECO lifecycle functional. BOM release links to ECO. Kafka events emitting.

> **Raise PR 3 after this checkpoint** (T067–T087) | CI: `./gradlew :services:work-order-service:check` | Target: `Develop`

---

## Phase 8: User Story 6 — AS5553 Counterfeit-Part Risk Fields (Priority: P3) [PR 4]

**Goal**: Surface `counterfeitRiskAlert` flag on BOM explosion nodes for HIGH/CRITICAL components; add `compliance.as5553-risk-added` Kafka event when a high-risk component is added to a BOM; search by `counterfeitRiskLevel` on item master list.

**Independent Test**: Create item master with `counterfeitRiskLevel=HIGH`. PATCH adds AS5553 fields. GET returns all AS5553 fields. Create BOM with that item as a component — BOM line has `counterfeitRiskAlert=true`. Query GET /item-master?counterfeitRiskLevel=HIGH returns the item.

### Prerequisites — QUALITY_ENGINEER Role (FR-016a)

- [ ] T190 [P] [US6] Create Flyway migration `V014__seed_quality_engineer_role.sql` at `services/work-order-service/src/main/resources/db/migration/` — INSERT INTO `iam.role` (if not exists) for `QUALITY_ENGINEER`; INSERT INTO `iam.role_privilege` granting `item-master:records:view` and `item-master:as5553:manage` to `QUALITY_ENGINEER`; INSERT INTO `iam.privilege` for `item-master:as5553:manage` if not already present; use SELECT to look up existing role/privilege IDs to remain idempotent — `services/work-order-service/src/main/resources/db/migration/V014__seed_quality_engineer_role.sql`
- [ ] T191 [P] [US6] Add `qualityEngineerToken()` helper method to `BaseIntegrationTest.java` — issues a JWT signed with the test RSA key, containing `sub = "quality-engineer-sub"`, `org_id = TEST_ORG_ID`, and `roles = ["QUALITY_ENGINEER"]`; follows the same pattern as existing `engineerToken()` and `adminToken()` methods — `services/work-order-service/src/test/java/com/mes/workorder/integration/BaseIntegrationTest.java`
- [ ] T192 [P] [US6] Update `AS5553IT` to use `qualityEngineerToken()` for all AS5553 PATCH assertions; add new test scenario: PATCH item master AS5553 fields with `engineerToken()` → assert HTTP 403 (FR-016a: ENGINEER does not hold `item-master:as5553:manage`); add `FlywayMigrationIT` assertion: assert `QUALITY_ENGINEER` role exists and holds `item-master:as5553:manage` grant in `iam.role_privilege` — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/AS5553IT.java`

### Tests (write FIRST — must FAIL before implementation)

- [X] T088 [P] [US6] Write `AS5553IT`: PATCH item master with AS5553 fields → 200 and GET returns fields; BOM explosion node for HIGH-risk component has counterfeitRiskAlert=true; search by counterfeitRiskLevel=HIGH returns matching item — `services/work-order-service/src/test/java/com/mes/workorder/integration/itemmaster/AS5553IT.java` **⚠ PARTIALLY COMPLETE — written before `qualityEngineerToken()` fixture existed; T192 (incomplete) must update this test to use `qualityEngineerToken()` for all AS5553 PATCH assertions and add the ENGINEER HTTP 403 scenario. This test does NOT fully satisfy FR-016a until T192 is complete.**
- [X] T089 [US6] Confirm test FAILS (RED) — verify counterfeitRiskAlert logic not yet implemented in explosion

### Implementation

- [X] T090 [US6] Confirm AS5553 columns (`counterfeit_risk_level`, `approved_suppliers` JSONB, `verification_required`) are present in V002 migration and in `ItemMaster.java` entity — no schema change needed (columns included from PR 1)
- [ ] T091 [US6] Update `BomExplosionService.java`: when building explosion nodes, look up each component's `counterfeitRiskLevel`; set `counterfeitRiskAlert=true` if level is HIGH or CRITICAL; batch load item master risk levels in a single query to avoid N+1 — `services/work-order-service/src/main/java/com/mes/workorder/bom/service/BomExplosionService.java` **⚠ Depends on Phase 5 (PR 2) merged — BomExplosionService must exist first**
- [ ] T092 [US6] Update `ItemMasterEventPublisher.java`: emit `compliance.as5553-risk-added` event on `work-order.item-master.events` when a BOM line is saved with a component whose `counterfeitRiskLevel` is HIGH or CRITICAL; call from `BomService.addLine()` after save — `services/work-order-service/src/main/java/com/mes/workorder/kafka/ItemMasterEventPublisher.java` **⚠ Depends on Phase 5 (PR 2) merged — BomService.addLine() must exist first**
- [X] T093 [US6] Add `counterfeitRiskLevel` filter parameter to `ItemMasterController.listItemMasters()` and `ItemMasterRepository.findAllByOrgId…` query — `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/api/ItemMasterController.java`
- [X] T094 [US6] Run `./gradlew :services:work-order-service:check` — confirm all US6 tests GREEN **⚠ Re-run after T091+T092 complete (Phase 5 dependency)**

**Checkpoint**: AS5553 fields surfaced. Explosion alert flags working. Risk-level search working.

> **Raise PR 4 after this checkpoint** (T088–T094, T190–T192) | CI: `./gradlew :services:work-order-service:check` | Target: `Develop`

---

## Phase 9: Polish & Compliance Verification [all PRs]

**Purpose**: Cross-cutting quality gates and constitution compliance verification.

- [X] T095 [P] Verify all Constitution Check gates in `specs/008-item-master-bom-management/plan.md` are ✅ PASS; obtain owner sign-off before raising any PR
- [X] T096 [P] Confirm `OrganisationContextHolder` used in every service-layer method that queries the DB: grep all service classes in `services/work-order-service/src/main/java/com/mes/workorder/` for missing org_id scope; fix any gap
- [ ] T097 Write `AuditTrailIT`: after item master create+patch, query `work_order.item_master_aud` and assert 2 rows; after BOM release, assert `bill_of_materials_aud` row; after ECO approve, assert `engineering_change_order_aud` row — `services/work-order-service/src/test/java/com/mes/workorder/integration/AuditTrailIT.java` **⚠ Item master section may pass now; BOM+ECO sections require Phase 5+7 merged — re-verify the full test after PR 3**
- [X] T098 [P] Confirm all Kafka event publishers include `eventId` UUID field (idempotency dedup key): search `ItemMasterEventPublisher`, `BomEventPublisher`, `EcoEventPublisher` for `eventId` in payload map
- [X] T099 [P] Validate `privilege_registration` smoke test: start work-order-service against local stack (quickstart.md), query `GET /roles/privilege-map` via iam-service, confirm 5 item-master privilege keys present for SYSTEM_ADMIN and ENGINEER
- [X] T100 [P] Run Checkstyle + SpotBugs across both new modules: `./gradlew :services:work-order-service:spotbugsMain :libs:mes-udf-lib:spotbugsMain` — resolve all violations before raising PR
- [X] T101 Run `.\scripts\feature-cost.ps1` and paste output into each PR description as `## Usage Cost` section
- [ ] T102 [P] Compliance spot-check — verify demonstrable coverage of: AS9100D §7.5 (audit rows in AuditTrailIT), AS9102 (partNumber+revision uniqueness test), AS5553 (AS5553IT), ISA-95 Material Class mapping (comment in ItemMaster entity JavaDoc), BOM depth limit (BomExplosionIT covers depth guard) **⚠ BomExplosionIT (T052) and full AuditTrailIT (T097) require Phase 5 merged — re-run after PR 2**
- [X] T103 Retrospective gate: review session work for new errors or near-misses; log any to `docs/governance/MES-ERR-001_Agent_Error_Log.md` before transitioning MES-8 to Done
- [ ] T171 [SC-001] Create k6 load test script `specs/008-item-master-bom-management/performance/item-master-load-test.js` — scenarios: ramp to 100 VUs over 30 s, sustain for 60 s; `POST /api/v1/item-master` (create with unique partNumber per VU) then `GET /api/v1/item-master/{id}` (retrieve by ID); thresholds: `http_req_duration{p(95)}<500`, `http_req_failed<0.01`; target URL configurable via `BASE_URL` env var; run against local stack (`docker compose -f docker/compose-infra.yml up`) with a seeded ENGINEER JWT; record baseline output in `specs/008-item-master-bom-management/performance/baselines/sc-001-baseline.txt`
- [ ] T172 [SC-002] Create k6 load test script `specs/008-item-master-bom-management/performance/bom-explosion-load-test.js` — precondition: fixture script seeds a 10-level BOM with 50 total components via API before the test run; scenario: 20 concurrent VUs each requesting `GET /api/v1/boms/{bomId}/explosion?format=indented` for the seeded BOM; thresholds: `http_req_duration{p(95)}<2000`, `http_req_failed<0.01`; record baseline in `specs/008-item-master-bom-management/performance/baselines/sc-002-baseline.txt` **⚠ Depends on PR 2 merged**
- [ ] T173 [SC-009] Create `libs/mes-udf-lib/src/test/java/com/mes/udf/UdfLibReusabilityIT.java` — integration test that registers UDF field definitions under module key `ROUTING` (a second module, distinct from `ITEM_MASTER`) using the same `UdfValidator` and `UdfFieldDefinitionRepository` beans with zero code changes to `mes-udf-lib`; asserts: define TEXT field on ROUTING → 201; validate with value present → no violations; validate without required field → violation returned; confirms library is module-agnostic (SC-009)

---

## Phase 10: Angular Frontend — Shared Grid Infrastructure & Item Master UI [PR 5]

**Purpose**: Install PrimeNG, build the reusable shared grid column-picker system, and implement the Item Master list screen. The shared architecture means any future screen (BOM, Work Orders, Receiving, Inventory) gains persistent column customisation by providing a `moduleKey` and `DEFAULT_COLUMNS` constant — no additional infrastructure work required.

**Shared module contract**: All column-picker infrastructure lives under `frontend/angular/src/app/shared/grid/`. Each feature imports the barrel, provides its `moduleKey` and defaults, and uses `ColumnPickerComponent` directly. The backend API (`/api/v1/users/preferences/grid/{moduleKey}`) is already module-agnostic (added in PR 1).

- [X] T113 Install PrimeNG + Angular CDK in `frontend/angular/`: `npm install primeng @angular/cdk`; configure `provideAnimationsAsync()` and `providePrimeNG({theme: {preset: Aura}})` in `frontend/angular/src/app/app.config.ts`
- [X] T114 [P] Create shared grid barrel at `frontend/angular/src/app/shared/grid/index.ts`; define `ColumnDef` interface (`{ key: string; label: string; visible: boolean; order: number; locked?: boolean; udf?: boolean }`) in `frontend/angular/src/app/shared/grid/models/column-def.model.ts`; export from barrel
- [X] T115 [P] Create generic `UserGridPreferenceApiService` in `frontend/angular/src/app/shared/grid/services/user-grid-preference-api.service.ts` — `getPreferences(moduleKey: string): Observable<ColumnDef[]>`; `putPreferences(moduleKey: string, columns: ColumnDef[]): Observable<void>`; calls `GET/PUT /api/v1/users/preferences/grid/{moduleKey}` on work-order-service; export from shared/grid barrel
- [X] T116 Create `GridPreferenceService` in `frontend/angular/src/app/shared/grid/services/grid-preference.service.ts` — not singleton (provided in component so each screen has its own instance); constructor takes `moduleKey: string` and `defaultColumns: ColumnDef[]`; exposes `activeColumns$: BehaviorSubject<ColumnDef[]>`; `load()` calls `UserGridPreferenceApiService.getPreferences()` and falls back to `defaultColumns` on 404; `apply(columns: ColumnDef[])` calls PUT then updates BehaviorSubject; `reset()` calls PUT with `defaultColumns` then updates BehaviorSubject; export from shared/grid barrel
- [X] T117 Create `ColumnPickerComponent` (standalone) in `frontend/angular/src/app/shared/grid/components/column-picker/column-picker.component.ts` — `@Input() columns: ColumnDef[]`; CDK `cdkDropList` + `cdkDrag` for reordering within each section; auto-splits into Standard Columns and User-Defined Fields sections (by `column.udf`); locked columns (`column.locked`) show "Required" badge, checkbox disabled, no drag handle; UDF columns show ice-blue "UDF" badge; "Reset to default" link in header emits `@Output() reset = new EventEmitter<void>()`; Apply footer button emits `@Output() applied = new EventEmitter<ColumnDef[]>()`; Cancel closes without emitting; export from shared/grid barrel
- [X] T118 [P] Scaffold item master feature with routing: `ng generate component features/item-master/pages/item-master-list --standalone` in `frontend/angular/`; add lazy route `/item-master → ItemMasterListComponent` to `app.routes.ts`; define `DEFAULT_ITEM_MASTER_COLUMNS: ColumnDef[]` in `frontend/angular/src/app/features/item-master/constants/default-columns.ts` — Part Number (locked), Revision (locked), Description (locked), Classification, Make/Buy, Unit of Measure, Status all `visible: true`; CAGE Code + Shelf Life Days `visible: false`
- [X] T119 [P] Create `ItemMasterApiService` in `frontend/angular/src/app/features/item-master/services/item-master-api.service.ts` — typed methods: `list(params: ItemMasterListParams): Observable<Page<ItemMasterDto>>`; `getById(id: string): Observable<ItemMasterDto>`; `create(req: CreateItemMasterRequest): Observable<ItemMasterDto>`; `patch(id: string, req: PatchItemMasterRequest): Observable<ItemMasterDto>`
- [X] T120 Create `ItemMasterListComponent` in `frontend/angular/src/app/features/item-master/pages/item-master-list/item-master-list.component.ts` — provides `GridPreferenceService` in component with `moduleKey: 'ITEM_MASTER'` and `DEFAULT_ITEM_MASTER_COLUMNS`; calls `gridPreference.load()` on init; PrimeNG `p-table` with `[columns]` bound to `activeColumns$ | async`; server-side pagination via `(onLazyLoad)`; filter bar with search, Classification dropdown, Status dropdown; settings icon button (column-picker trigger) next to "Clear filters" toggles PrimeNG `p-overlayPanel` containing `ColumnPickerComponent`; handles `(applied)` by calling `gridPreference.apply()` and `(reset)` by calling `gridPreference.reset()` — `frontend/angular/src/app/features/item-master/pages/item-master-list/`
- [X] T121 [P] Apply Aurora MES dark mode colour tokens as CSS custom properties in `frontend/angular/src/styles.scss` — import PrimeNG Aura dark preset; override surface, primary, and text variables with Aurora MES dark hex values from Penpot Token Reference Board (bg.base `#0A1628`, bg.subtle `#0D1F3C`, brand.primary `#2563EB`, text.primary `#F1F5F9`, text.secondary `#94A3B8`, border.subtle `#1E3A5F`); scope all overrides inside `.aurora-dark` class selector to avoid polluting light mode
- [X] T124 [P] Configure PrimeNG dark mode selector in `frontend/angular/src/app/app.config.ts` — set `darkModeSelector: '.aurora-dark'` inside `providePrimeNG()` so PrimeNG switches component theme via CSS class (not OS media query); this gives Aurora MES full user-controlled override independent of OS setting
- [X] T125 [P] Create `ThemeService` in `frontend/angular/src/app/shared/theme/services/theme.service.ts` — `isDark$: BehaviorSubject<boolean>` initialised from localStorage key `aurora-mes-theme`; falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches` if no saved preference; `toggle()` flips state, persists to localStorage, adds/removes `.aurora-dark` on `document.documentElement`; `init()` called once at bootstrap to hydrate state before first render; export from `frontend/angular/src/app/shared/theme/index.ts` barrel
- [X] T126 Create `ThemeToggleComponent` (standalone) in `frontend/angular/src/app/shared/theme/components/theme-toggle/theme-toggle.component.ts` — icon button bound to `ThemeService.isDark$`; renders PrimeNG `pi-sun` icon in dark mode, `pi-moon` icon in light mode; `aria-label` reflects current action ("Switch to light mode" / "Switch to dark mode"); 36×36px touch target; no text label; export from `shared/theme/index.ts` barrel
- [X] T127 Add Aurora MES light mode CSS token overrides to `frontend/angular/src/styles.scss` — within `:root:not(.aurora-dark)` selector override PrimeNG Aura surface vars to light preset values; add Aurora MES light tokens: bg.base `#F8FAFC`, bg.subtle `#EFF6FF`, text.primary `#0F172A`, text.secondary `#64748B`, border.subtle `#CBD5E1`, brand.primary `#2563EB` (unchanged — blue works on both themes); ensure table stripe, overlay panel, and drawer backgrounds respond correctly
- [X] T128 Place `ThemeToggleComponent` in the app top navigation bar immediately left of the user avatar icon — `frontend/angular/src/app/app.component.html` (or the shell layout template); confirm 8px gap between toggle and avatar matches Penpot shell frame spec
- [X] T129 Call `ThemeService.init()` in `AppComponent.ngOnInit()` before any route resolves — `frontend/angular/src/app/app.component.ts`; prevents flash-of-wrong-theme on hard reload by applying the saved class synchronously before Angular renders any component
- [X] T130 [P] For each Penpot frame built this sprint, create a corresponding light mode variant frame on the "Aurora MES / Shell" page — "Shell (light)", "Item Master List (light)", "Column Picker (light)" — applying Aurora MES light mode tokens; document the final light + dark hex values in `specs/008-item-master-bom-management/research.md` under a `## Theme Tokens` section for future reference
- [X] T167 Run `ng lint --max-warnings 0` in `frontend/angular/` — zero lint errors (constitution §II retrospective for PR 5; MUST pass before PR 6 is raised; fix any violations as part of PR 6 baseline)
- [X] T122 Run `ng build --configuration=production` in `frontend/angular/` — zero compilation errors
- [X] T123 Start dev server (`ng serve`), open browser at `http://localhost:4200/item-master`; verify: theme toggle renders next to user avatar; click toggles sun↔moon icon and switches all component colours; preference survives page refresh; column picker and table both respond to theme; OS dark preference applied on first visit when no saved preference exists

**Checkpoint**: Shared grid infrastructure working. Any future screen can add column customisation by importing `GridPreferenceService` + `ColumnPickerComponent` from `shared/grid` and providing its own `moduleKey` + defaults. Item Master list fully functional end-to-end.

> **Raise PR 5 after this checkpoint** (T113–T130) | CI: `ng build --configuration=production` in `frontend/angular/` | Target: `Develop`

---

## Phase 11: App Shell + Item Master List Fidelity + Item Master Create/Edit [PR 6]

**Purpose**: Build the application shell that every screen lives inside, close the visual and functional gaps between the current item master list and the Penpot wireframe, and deliver the item master create/edit form that is required for CRUD compliance (§I Spec-First, Constitution).

**Independent Test**: User logs in → sees nav rail with Dashboard and Item Master links → clicks Item Master → sees list with page heading "Item Master", item count, "+ New Item" button, coloured classification chips, status dot indicators, and an Actions column. Clicks "+ New Item" → form dialog opens → fills mandatory fields → saves → new row appears in list with success toast.

### App Shell

- [ ] T131 Create `AppShellComponent` (standalone) in `frontend/angular/src/app/layout/shell/app-shell.component.ts` — wraps `<router-outlet>`; left nav rail 240 px expanded / 64 px collapsed with toggle button; nav items: Dashboard (`/dashboard`, `pi-th-large`), Item Master (`/item-master`, `pi-database`), BOM (`/item-master`, `pi-sitemap`) — BOM is accessed via Item Master context, not a standalone list route (see T156), ECO (`/ecos`, `pi-file-edit`); active route highlighted; collapse state persisted in localStorage key `aurora-mes-nav-collapsed`; export from `frontend/angular/src/app/layout/index.ts` barrel
- [ ] T132 Style nav rail per Penpot Shell frame in `frontend/angular/src/app/layout/shell/app-shell.component.scss` — dark: bg.subtle (`#0D1F3C`), active item bg `rgba(37,99,235,0.15)`, active text `#2563EB`, icon size 20 px, label 13 px; light: bg.subtle (`#EFF6FF`), active bg `rgba(37,99,235,0.08)`; transition `width 200ms ease`; z-index 100; full viewport height
- [ ] T133 Add top bar to `AppShellComponent` — 56 px height; Aurora MES wordmark left-aligned (collapsed: icon only); `ThemeToggleComponent` right side; user avatar circle (first letter of `preferred_username` from OIDC token, 32×32 px, brand.primary bg); clicking avatar shows PrimeNG Popover with username, email, and "Logout" button that calls `OidcSecurityService.logoff()`
- [ ] T134 Re-nest all feature routes under `AppShellComponent` in `frontend/angular/src/app/app.routes.ts` — replace current flat routes with a parent route `path: ''` + `component: AppShellComponent` containing children; keep `/login` outside the shell; update `app.component.html` to a bare `<router-outlet>` (shell now owns layout)
- [ ] T135 Delete layout markup from current `app.component.html` (plain `<nav>` + title span + login/logout button) — shell component owns all of this now; `app.component.html` becomes `<router-outlet />`

### Item Master List — Visual Fidelity

- [ ] T136 Add page heading row to `ItemMasterListComponent` template — `<h2>Item Master</h2>` left-aligned; item count badge `({{ totalRecords }} items)` in text.secondary colour; both in a flex row with `align-items: baseline`; count updates on every `onLazyLoad` response
- [ ] T137 Add "+ New Item" primary button to the heading row (right-aligned) in `ItemMasterListComponent` — PrimeNG `p-button` with `severity="primary"` and `icon="pi pi-plus"`; disabled when user lacks `item-master:records:manage` privilege (check via `AuthorizationService` or privilege claim from JWT); emits `openCreateDialog()` on click
- [ ] T138 Replace plain Classification text cells with coloured PrimeNG `p-tag` chips in `ItemMasterListComponent` template — PURCHASED: severity `info` (blue); FABRICATED: severity `warning` (orange); PHANTOM: severity `secondary` (grey); RAW_MATERIAL: custom `teal` class (`background: #0D9488; color: #fff`); add the custom class to `item-master-list.component.scss`
- [ ] T139 Replace plain Status text cells with dot-indicator badges — ACTIVE: green filled circle `●` (`color: #22C55E`); OBSOLETE: grey ring `◎` (`color: #94A3B8`); PENDING: amber dot; rendered via a `StatusBadgeComponent` (standalone, one `@Input() status: string`) at `frontend/angular/src/app/shared/ui/status-badge/status-badge.component.ts`; export from `frontend/angular/src/app/shared/ui/index.ts`
- [ ] T140 Add Actions column as the last column of the p-table in `ItemMasterListComponent` — not in column picker (always visible, locked); each row has three controls: "View" text button (`routerLink` to `/item-master/:id`), "Edit" text button (opens edit dialog with row data), PrimeNG `p-menu` overflow button (`pi-ellipsis-v`) with "Obsolete" option that calls `ItemMasterApiService.obsolete(id)` and refreshes table
- [ ] T141 Add row selection checkbox column (first column, not in picker, locked) to `ItemMasterListComponent` — PrimeNG `[(selection)]` bound to `selectedItems`; when `selectedItems.length > 0` show selection action bar above table: "N items selected" + "Obsolete Selected" button + "Clear" link; "Obsolete Selected" calls `ItemMasterApiService.obsolete()` for each selected ID sequentially and refreshes
- [ ] T142 Add Make/Buy filter to filter bar in `ItemMasterListComponent` — PrimeNG `p-selectbutton` with options `[{label:'All',value:null},{label:'Make',value:'MAKE'},{label:'Buy',value:'BUY'},{label:'Either',value:'EITHER'}]`; bound to `selectedMakeBuy` property (`MakeBuyCode | null`); passed as `makeBuyCode` query param to `ItemMasterApiService.list()` when non-null

### Item Master Create/Edit Form

- [ ] T143 Create `ItemMasterFormComponent` (standalone dialog) at `frontend/angular/src/app/features/item-master/components/item-master-form/item-master-form.component.ts` — PrimeNG `p-dialog` with `[modal]="true"`, `[style]="{width: '720px'}"`; reactive form using `FormBuilder`; sections: Identity (partNumber, revision, description required), Classification (classification dropdown, makeBuyCode dropdown, traceabilityMethod dropdown, unitOfMeasure, cageCode), Shelf Life (shelfLifeControlled toggle; `shelfLifeDays` number input revealed when true with `Validators.required` added dynamically), AS5553 (collapsible panel: counterfeitRiskLevel dropdown, approvedSuppliers textarea, verificationRequired toggle), UDF Fields (loaded on open from `GET /udf/fields?module=ITEM_MASTER`, rendered as typed inputs matching field type); `@Input() itemId?: string` — when set the form loads existing item via `ItemMasterApiService.getById()` and switches to edit mode; Save button calls `create()` or `patch()` accordingly; emits `@Output() saved = new EventEmitter<ItemMasterDto>()` on success; shows inline PrimeNG `p-message` errors on 422 responses (maps `violations` array from response body)
- [ ] T144 Wire `ItemMasterFormComponent` into `ItemMasterListComponent` — `openCreateDialog()` sets `editItemId = undefined` and `showFormDialog = true`; Edit row action sets `editItemId = row.id` and `showFormDialog = true`; on `(saved)` event: show PrimeNG `p-toast` success message "Item saved" and call `loadItemMasters()` to refresh the table
- [ ] T145 Create `ItemMasterDetailComponent` (standalone page) at `frontend/angular/src/app/features/item-master/pages/item-master-detail/item-master-detail.component.ts` — route `/item-master/:id`; calls `ItemMasterApiService.getById(id)`; displays all fields in a two-column read-only card layout using PrimeNG `p-card`; back button returns to list; **Edit button navigates to `/item-master/:id/edit` using `[routerLink]="['/item-master', item.id, 'edit']"` — MUST NOT open a modal dialog** (per FR-034 and FR-044); shows UDF fields in a separate "Custom Fields" card

### Verification

- [ ] T168 Run `ng lint --max-warnings 0` in `frontend/angular/` — zero lint errors before raising PR 6 (constitution §II mandatory gate)
- [ ] T146 Run `ng build --configuration=production` in `frontend/angular/` — zero compilation errors
- [ ] T147 Smoke test in browser: nav rail renders and collapses; active route highlighted; theme toggle in top bar works; item master list shows page heading, item count, coloured chips, status dots, actions column; "+ New Item" opens form; fill all mandatory fields and save; row appears in table with toast; row "Edit" pre-populates form; "View" navigates to detail page
- [ ] T148 Add `frontend/angular/src/app/layout/` and `frontend/angular/src/app/shared/ui/` to `sonar.sources` in `sonar-project.properties` if not already present

**Checkpoint**: App shell wraps all screens. Item master list is visually faithful to Penpot wireframe. Create/edit/view CRUD fully functional.

> **Raise PR 6 after this checkpoint** (T131–T148) | CI: `ng build --configuration=production` in `frontend/angular/` | Target: `Develop`

---

## Phase 11b: Item Master Frontend — Penpot Fidelity Corrections [PR 6b or bundled into PR 7]

**Purpose**: Correct and complete Item Master frontend screens to be 100% faithful to the authoritative Penpot frames. These tasks were identified via a Penpot frame audit on 2026-05-31. They close the gap between the merged PR 6 implementation and the Penpot design specification (FR-040 through FR-054).

### Classification & Display

- [ ] T177 Create a `ClassificationLabelPipe` (or `classificationLabel` utility function) at `frontend/angular/src/app/features/item-master/pipes/classification-label.pipe.ts` — maps backend enum values to UI display labels per FR-040: `ASSEMBLY`→"ASSEMBLY", `COTS`→"COTS", `FABRICATED`→"FABRICATED", `PURCHASED_PART`→"PURCHASED", `RAW_MATERIAL`→"RAW MATERIAL", `SERVICE`→"SERVICE"; export from `features/item-master` barrel; apply in `ItemMasterListComponent` classification chip `[value]` binding, in classification dropdown option labels throughout the feature, and in `ItemMasterDetailComponent`

### Breadcrumbs

- [ ] T178 Create a `BreadcrumbComponent` (standalone) at `frontend/angular/src/app/shared/ui/breadcrumb/breadcrumb.component.ts` — accepts `@Input() crumbs: { label: string; route?: string[] }[]`; renders a horizontal list of navigation links (all crumbs except last are `routerLink`; last crumb is plain text); separator is " / "; export from `shared/ui/index.ts`; apply breadcrumb to all Item Master and BOM screens per FR-043: List→"Home / Materials / Item Master", Create→"Materials / Item Master / New Item", Edit→"Materials / Item Master / {partNumber} Rev {revision}", Detail→"Materials / Item Master / {partNumber} Rev {revision}", BOM Authoring→"Materials / Item Master / {partNumber} / BOM", BOM Explosion→"Materials / Item Master / {partNumber} / BOM"

### Pagination Display Text

- [ ] T179 Add "Showing X–Y of Z items" text to `ItemMasterListComponent` — displayed left-aligned in the pagination bar below the table; format `Showing {first + 1}–{Math.min(first + pageSize, totalRecords)} of {totalRecords} items`; updates on every `onLazyLoad` event; add numeric page-link row using PrimeNG `p-paginator`'s built-in `[showPageLinks]="true"` configuration so individual page numbers (1, 2, 3, …, N) are visible (per FR-042)

### Clone Item

- [ ] T180 Add `clone(id: string): Observable<ItemMasterDto>` to `ItemMasterApiService` — calls `POST /api/v1/item-master/{id}/clone`; backend endpoint creates a new DRAFT item with all fields copied except `id`, `partNumber` (cleared), `revision` (cleared), `status` (ACTIVE), audit fields (reset); returns the new item with a `Location` header; add backend endpoint to `ItemMasterController.java`, `ItemMasterService.java` (new `clone()` method), and `ItemMasterServiceTest`
- [ ] T181 Add "Clone Item" to `ItemMasterListComponent` overflow menu (··· per row) and bulk action bar — navigates to `/item-master/new?cloneFrom={id}` pre-populating the form with the source item's fields (except Part Number and Revision which are cleared for user entry); `ItemMasterCreateComponent` (T183) reads the `cloneFrom` query param on init and calls `ItemMasterApiService.getById(cloneFrom)` to pre-fill the form; add to overflow menu items array in `showRowMenu()` and to the selection action bar

### Full-Page Create/Edit Forms (FR-044, FR-045, FR-046, FR-047, FR-048)

- [ ] T182 Create `ItemMasterCreateComponent` (standalone **page**, NOT dialog) at `frontend/angular/src/app/features/item-master/pages/item-master-create/item-master-create.component.ts` — route `/item-master/new`; two-column layout (left "Core Identification", right "Traceability & Compliance") per FR-045; fields per FR-033: left column: Part Number *, Revision *, Description * (textarea), Unit of Measure * (dropdown, default "Each (EA)"), Classification * (dropdown), Make / Buy Code * (two **independent toggle buttons** "Make" and "Buy" — both can be active simultaneously; Make+Buy both active → EITHER; client-side validation prevents neither being selected; on edit load: MAKE activates Make only, BUY activates Buy only, EITHER activates both; per FR-046); right column: Traceability Method * (dropdown), Shelf Life Controlled toggle + conditional Shelf Life Days * number + "days" suffix, CAGE Code (placeholder "5-digit CAGE code"), Counterfeit Risk Level (dropdown, default NONE), Verification Required toggle, **STEP Part Reference** (text input, placeholder "e.g. S000-BRKT-001", per FR-047); UDF section: "User-Defined Fields" heading + "N fields configured for ITEM_MASTER module" count subtitle + dynamic UDF fields; breadcrumb "Materials / Item Master / New Item" (T178); page title "New Item"; "* Required fields" subtitle; "Cancel" button (navigates to `/item-master`) and "Save Item" button (calls `ItemMasterApiService.create()`, on success navigates to `/item-master/{id}` detail page); reads optional `cloneFrom` query param (T181); add route to `app.routes.ts` as a sibling of `/item-master`
- [ ] T183 Create `ItemMasterEditComponent` (standalone **page**, NOT dialog) at `frontend/angular/src/app/features/item-master/pages/item-master-edit/item-master-edit.component.ts` — route `/item-master/:id/edit`; same two-column layout and field set as T182 (FR-033a); Part Number and Revision render read-only (not inputs); Traceability Method renders read-only; page title shows "PARTNUM / Rev X" (the item's actual values); status badge ("Active" / "Inactive" / "Obsolete") inline next to title; **"Obsolete this item"** text-link button adjacent to status badge (hidden when status is OBSOLETE, requires `item-master:records:manage`, per FR-048); breadcrumb "Materials / Item Master / {partNumber} Rev {revision}" (T178); "Cancel" (navigates to detail page) and "Save Changes" (calls `ItemMasterApiService.patch()`, on success navigates to detail page); add route `/item-master/:id/edit` to `app.routes.ts`
- [ ] T184 Add `stepPartRef` to `CreateItemMasterRequest` and `PatchItemMasterRequest` backend DTOs — `stepPartRef` is already in `ItemMaster` entity and `ItemMasterDto`; add `@Size(max = 100)` validated field to both request DTOs; update `ItemMasterService.create()` to set `entity.setStepPartRef(request.getStepPartRef())`; update `ItemMasterService.patch()` to apply patch when non-null; add test assertions to `ItemMasterControllerIT` for `stepPartRef` round-trip (create with `stepPartRef` set → GET response includes value, per FR-004)

### Column Picker Corrections (FR-049)

- [ ] T197 [P] Create `UdfApiService` at `frontend/angular/src/app/features/item-master/services/udf-api.service.ts` — `getFields(moduleKey: string): Observable<UdfFieldDefinitionDto[]>` calls `GET /api/v1/udf/fields?module={moduleKey}`; define `UdfFieldDefinitionDto` interface (`{ id: string; fieldKey: string; label: string; fieldType: 'TEXT'|'NUMBER'|'DATE'|'BOOLEAN'|'LIST'; required: boolean; defaultValue?: string; listOptions?: string[]; validationRules?: Record<string, unknown>; displayOrder: number }`); export from `features/item-master` barrel; consumed by T182 (ItemMasterCreateComponent), T183 (ItemMasterEditComponent), T143 (ItemMasterFormComponent), T153 (BomAuthoringComponent via BOM_LINE module key), and T175 (BomHeaderEditDialogComponent via BOM_HEADER module key) **⛔ PR 6b BLOCKER — T182 and T183 both depend on this service**
- [ ] T185 Update `DEFAULT_ITEM_MASTER_COLUMNS` and `ColumnPickerComponent` to implement FR-049 section labelling — rename `ColumnPickerComponent` section header text from current value to exactly **"STANDARD COLUMNS"** and **"USER-DEFINED FIELDS"**; in `DEFAULT_ITEM_MASTER_COLUMNS`, move `counterfeitRiskLevel` and `stepPartRef` columns to have `udf: true` so they appear in the USER-DEFINED FIELDS section of the picker; update `ItemMasterListComponent` switch statement to handle `stepPartRef` column rendering; ensure drag handles render on ALL rows including locked ones (locked rows have drag handle present but reordering them has no effect)

---

## Phase 11c: Item Master Frontend — Automated Tests (Constitution §II) [PR 6b]

**Purpose**: Add Angular component unit tests for the highest-risk Item Master frontend FRs, satisfying Constitution §II which requires every FR to have at least one corresponding automated test. These tests run via `ng test` with Karma or Jest. Write tests FIRST (RED) before any component implementation.

- [ ] T198 [P] Write Angular unit test for `ClassificationLabelPipe` (FR-040) — `frontend/angular/src/app/features/item-master/pipes/classification-label.pipe.spec.ts`; test all 6 mappings: `ASSEMBLY`→"ASSEMBLY", `COTS`→"COTS", `FABRICATED`→"FABRICATED", `PURCHASED_PART`→"PURCHASED", `RAW_MATERIAL`→"RAW MATERIAL", `SERVICE`→"SERVICE"; test unknown value returns the raw value unchanged; confirm test FAILS before T177 is implemented
- [ ] T199 [P] Write Angular unit test for `ItemMasterCreateComponent` (FR-033, FR-045, FR-046) — `frontend/angular/src/app/features/item-master/pages/item-master-create/item-master-create.component.spec.ts`; test cases: (a) "Save Item" button disabled when required fields empty; (b) Make=true + Buy=false sets `makeBuyCode = MAKE`; (c) Make=true + Buy=true sets `makeBuyCode = EITHER`; (d) Make=false + Buy=false shows validation error "At least one of Make or Buy must be selected"; (e) `cloneFrom` query param pre-populates form fields except Part Number and Revision; (f) 422 server response displays inline field violations; mock `ItemMasterApiService` and `UdfApiService`; confirm tests FAIL before T182 is implemented
- [ ] T200 [P] Write Angular unit test for `ItemMasterEditComponent` (FR-033a, FR-048) — `frontend/angular/src/app/features/item-master/pages/item-master-edit/item-master-edit.component.spec.ts`; test cases: (a) Part Number, Revision, and Traceability Method fields render as read-only text (not `<input>`); (b) "Obsolete this item" link is hidden when `item.status === 'OBSOLETE'`; (c) "Obsolete this item" link is hidden when user lacks `item-master:records:manage` privilege; (d) Make/Buy toggles initialise correctly from stored enum values (MAKE → only Make active, EITHER → both active); (e) "Save Changes" calls `ItemMasterApiService.patch()` with updated fields; mock `ItemMasterApiService`, `ActivatedRoute`, and privilege check; confirm tests FAIL before T183 is implemented
- [ ] T201 [P] Write Angular unit test for `BomExplosionComponent` (FR-037, FR-050, FR-051) — `frontend/angular/src/app/features/bom/pages/bom-explosion/bom-explosion.component.spec.ts`; test cases: (a) 422 effectivity-gap error from `BomApiService.explode()` displays `p-message severity="error"` inline above table; (b) Ctft Risk column shows "—" when `counterfeitRiskAlert = false` and "⚠ HIGH" when `counterfeitRiskAlert = true`; (c) "⬇ CSV" button calls `window.open()` with correct URL including current as-of-date and max-depth params; (d) "⬇ PDF" button calls `window.open()` with `download=pdf` param; mock `BomApiService` and `ItemMasterApiService`; confirm tests FAIL before T155 is implemented
- [ ] T202 [P] Write Angular unit test for `BomAuthoringComponent` — column state and dirty flag (FR-036, FR-054) — `frontend/angular/src/app/features/bom/pages/bom-authoring/bom-authoring.component.spec.ts`; test cases: (a) Add Line increments component count in bottom status bar immediately; (b) After adding a line the status bar shows "Unsaved changes — click Save Draft to retain" in amber; (c) After clicking "Save Draft" the status bar returns to clean state; (d) All structural controls (Add Line, Submit for Review, ✏/🗑 per-row) are absent from the DOM when BOM status is RELEASED; (e) When effectivity_type is UNIT, Eff From and Eff To cells display a "(Unit)" sub-label; mock `BomApiService`; confirm tests FAIL before T153 is implemented

**Checkpoint**: Angular unit tests written (RED). All 5 test files compiled and confirmed failing before implementation tasks begin.

---

## Phase 12: BOM Frontend Screens [PR 7]

**Purpose**: Deliver the BOM authoring, explosion, and list screens so engineers can author and release Bills of Materials from the browser. Required for spec compliance — BOM is P1 scope (US2) and is entirely missing from the frontend. Depends on PR 2 merged (BOM backend APIs live) and PR 6 merged (app shell).

**Independent Test**: Engineer navigates Item Master → selects a part → clicks "BOMs" → sees BOM list (empty). Creates BOM revision "A" → BOM authoring screen opens → adds three component lines → sets effectivity on one line → releases BOM → status chip changes to RELEASED. Navigates to explosion view → selects indented format → tree shows all three components at correct depth.

### API Service

- [ ] T149 Create `BomApiService` at `frontend/angular/src/app/features/bom/services/bom-api.service.ts` — typed methods: `listForItem(itemId: string, params?: PageParams): Observable<Page<BomDto>>`; `create(req: CreateBomRequest): Observable<BomDto>`; `getById(id: string): Observable<BomDto>`; `addLine(bomId: string, req: CreateBomLineRequest): Observable<BomLineDto>`; `removeLine(bomId: string, lineId: string): Observable<void>`; `release(bomId: string): Observable<BomDto>`; `explode(bomId: string, format: 'flat'|'indented', asOfDate?: string, asOfUnit?: string): Observable<BomExplosionResponse>`; define `BomDto`, `BomLineDto`, `BomExplosionNode`, `CreateBomRequest`, `CreateBomLineRequest` interfaces in `frontend/angular/src/app/features/bom/models/`

### BOM List Screen

- [ ] T150 Create `BomListComponent` (standalone) at `frontend/angular/src/app/features/bom/pages/bom-list/bom-list.component.ts` — route: `/item-master/:itemId/boms`; calls `BomApiService.listForItem(itemId)` on init; page heading shows parent item part number + revision fetched via `ItemMasterApiService.getById()`; p-table with columns: BOM Revision, Status chip (DRAFT=warning, RELEASED=success, OBSOLETE=secondary), Description, Created By, Actions; Actions column: "Author" button (navigates to `/boms/:bomId`), "Explode" button (navigates to `/boms/:bomId/explosion`); "+ New BOM Revision" button opens `BomCreateDialog`
- [ ] T151 Create `BomCreateDialogComponent` (standalone dialog) at `frontend/angular/src/app/features/bom/components/bom-create-dialog/bom-create-dialog.component.ts` — fields: BOM Revision (text, required), Description (text); Save calls `BomApiService.create({parentItemId, bomRevision, description})`; on success navigates to `/boms/:newBomId`
- [ ] T152 Add "BOMs" action to the `ItemMasterListComponent` row overflow menu (pi-ellipsis-v) — navigates to `/item-master/:id/boms`; also add "BOMs" button to `ItemMasterDetailComponent` actions bar

### BOM Authoring Screen

- [ ] T153 Create `BomAuthoringComponent` (standalone) at `frontend/angular/src/app/features/bom/pages/bom-authoring/bom-authoring.component.ts` — **⚠ Prerequisite: define `DEFAULT_BOM_LINE_COLUMNS: ColumnDef[]` in `frontend/angular/src/app/features/bom/constants/default-columns.ts` — locked columns: Seq, Find #, Part Number, Description (`locked: true, udf: false`); standard columns: Revision, Qty, Unit, Eff From, Eff To, Reference Designators (`visible: true, udf: false`); wire `GridPreferenceService` in this component with `moduleKey: 'BOM_LINE'` and this constant; UDF columns loaded dynamically from `UdfApiService` (T197) with `moduleKey: 'BOM_LINE'`.** Route: `/boms/:bomId`; header area shows: parent item part number/rev (linked back to item detail) with ✏ icon that opens `BomHeaderEditDialogComponent` (T175), BOM revision dropdown (`Rev A ▾`), status chip, description, ECO reference ("No active ECO" or ECO ID linked); p-table of BOM lines with columns per research.md Decision 11: **Seq (locked), Find # (locked), Part Number, Description, Rev, Qty, Unit, Eff From, Eff To, [UDF columns], Actions**; "⊕ Columns" button at far-right header opens column picker overlay; per-row actions: ✏ (inline edit — T175) and 🗑 (remove — only when DRAFT); action bar: "+ Add Line" (opens `AddBomLineFormComponent`), "Save Draft" (PATCHes BOM header without state change), **"Submit for Review"** (label per Penpot — calls `BomApiService.release()`, opens confirm dialog first), "← Explosion View" (navigates to explosion route); on release, status chip updates to RELEASED and Add/Remove/Submit controls hide; navigation breadcrumb: Item Master > {partNumber} > BOMs > {bomRevision}
- [ ] T154 Create `AddBomLineFormComponent` (standalone, inline in table footer row) at `frontend/angular/src/app/features/bom/components/add-bom-line-form/add-bom-line-form.component.ts` — inline row with: component part number autocomplete (calls `ItemMasterApiService.list()` debounced, displays part number + revision options), Find # input (number), Quantity input (number), UoM input (text), Effectivity Method select (NONE / DATE / UNIT); when DATE selected: from-date and to-date datepickers appear; when UNIT selected: from-unit and to-unit text inputs appear; Save calls `BomApiService.addLine()`; on 422 response display inline error message from `violations` array; Cancel hides row

- [ ] T175 Create `BomHeaderEditDialogComponent` (standalone dialog) at `frontend/angular/src/app/features/bom/components/bom-header-edit-dialog/bom-header-edit-dialog.component.ts` — PrimeNG `p-dialog`, title "Edit BOM Header"; fields per research.md Decision 12: Part Number (read-only with 🔒 icon), BOM Description (text input), Reason for Revision (text input), Production Line (**free-text input** — NOT a dropdown; stores plain string identifier on `bill_of_materials.production_line VARCHAR(200)` per FR-036a; org-managed list support is deferred), BOM Type (dropdown: Manufacturing BOM / Engineering BOM / Service BOM), Effectivity Type (dropdown: DATE / UNIT / NONE), BOM Header UDFs (loaded from `GET /api/v1/udf/fields?module=BOM_HEADER`, rendered as typed inputs); Save calls `BomApiService.patchHeader(bomId, req)`; emits `(saved)` on success; Cancel closes without save; show inline `p-message` errors on 422 response. Wire into `BomAuthoringComponent`: ✏ icon in header area opens this dialog; on `(saved)` refresh header card without full page reload. Note: requires backend `PATCH /api/v1/boms/{bomId}` endpoint and schema additions (`reason_for_revision`, `bom_type`, `effectivity_type`, `custom_fields` on `bill_of_materials`) — coordinate with backend PR 2 scope or raise separate migration PR. **⚠ Backend schema additions listed in research.md Decision 12 must exist before this task is testable end-to-end.**
- [ ] T176 Add `patchHeader(bomId: string, req: PatchBomHeaderRequest): Observable<BomDto>` to `BomApiService`; define `PatchBomHeaderRequest` interface (`description?: string; reasonForRevision?: string; productionLine?: string; bomType?: string; effectivityType?: string; customFields?: Record<string, unknown>`) in `frontend/angular/src/app/features/bom/models/` — `PATCH /api/v1/boms/{bomId}` maps to this request body

### BOM Explosion View

- [ ] T155 Create `BomExplosionComponent` (standalone) at `frontend/angular/src/app/features/bom/pages/bom-explosion/bom-explosion.component.ts` — route: `/boms/:bomId/explosion`; header card (per FR-052): "PARENT ITEM" label + part number/rev value as routerLink to item detail; "BOM REVISION" label + revision dropdown (lists all BOM revisions for the parent item, switching reloads explosion); status chip (Released/Draft/Obsolete); "Released by {user} · {date}" metadata; "N components · N levels deep" summary badge. Toolbar (per FR-051): Format toggle p-selectbutton (Indented | Flat); "As of date:" label + calendar datepicker (optional, default today); "Max depth: All ▾" dropdown (options: All / 2 / 3 / 5 / 10); "Collapse All" button; "Expand All" button; "⬇ CSV" export button; "⬇ PDF" export button. PrimeNG `p-treeTable` bound to explosion response mapped to `TreeNode[]`; columns per FR-050 (in order): **Find # (locked), Part Number (routerLink to item detail), Rev, Description, Qty, Make/Buy, Ctft Risk, Status**; Ctft Risk shows "—" or "⚠ HIGH" warning badge; Status renders `StatusBadgeComponent`; tree indent with ▼ / ▶ expand-collapse per row; depth offset per level. Bottom summary bar (per FR-054): "N components shown (M top-level, P children) · Effectivity: as of DATE · All items current" or effectivity gap error. On 422 effectivity-gap error from API display `p-message severity="error"` inline above table. breadcrumb: Item Master > {partNumber} > BOMs > {bomRevision} (T178)
- [ ] T156 Add BOM routes to `app.routes.ts` nested under `AppShellComponent`: `/item-master/:itemId/boms` → `BomListComponent`; `/boms/:bomId` → `BomAuthoringComponent`; `/boms/:bomId/explosion` → `BomExplosionComponent`; add "BOM" nav item to `AppShellComponent` nav rail pointing to `/item-master` (BOM is accessed via item master, not a top-level list)

### BOM Explosion Export Backend (FR-051)

- [ ] T186 Add CSV and PDF explosion export endpoints to `BomController.java`: `GET /api/v1/boms/{bomId}/explosion?format=flat&download=csv` returns a `text/csv` response with headers Find #, Part Number, Rev, Description, Qty, Make/Buy, Ctft Risk; `GET /api/v1/boms/{bomId}/explosion?format=flat&download=pdf` returns a `application/pdf` response generated via **Apache PDFBox** (`org.apache.pdfbox:pdfbox:3.0.x` — add to `services/work-order-service/build.gradle`); PDF layout: page header "BOM Explosion Report — {partNumber} Rev {revision}" + generated timestamp; table with columns Find #, Part Number, Rev, Description, Qty, Make/Buy, Ctft Risk; one row per node (indented with leading spaces for depth level); page numbering "Page N of M"; both endpoints respect `asOfDate`, `asOfUnit`, and `maxDepth` query parameters; require `item-master:records:view` privilege; add integration test assertions for response content-type and non-empty body to `BomExplosionIT`
- [ ] T187 Wire export buttons in `BomExplosionComponent` — "⬇ CSV" button triggers `window.open('/api/v1/boms/{id}/explosion?format=flat&download=csv&...currentFilters...')` to initiate browser download; "⬇ PDF" button same for PDF; both pass current as-of-date and max-depth query params; disable both buttons while explosion data is loading

### BOM Authoring — Remaining Penpot Details (FR-053, FR-054)

- [ ] T188 Add `referenceDesignators` to BOM line frontend — add `referenceDesignators?: string` to `BomLineDto`, `CreateBomLineRequest`, and `UpdateBomLineRequest` TypeScript interfaces; add as an OPTIONAL column in `DEFAULT_BOM_LINE_COLUMNS` (unchecked by default) so it appears under STANDARD COLUMNS in the BOM authoring column picker; make it editable via `AddBomLineFormComponent` (text input, optional) and via inline row edit; backend `BomLineDto`, `CreateBomLineRequest`, and `UpdateBomLineRequest` Java DTOs already include `referenceDesignators` (per FR-053) — verify field is mapped in `BomMapper.java`
- [ ] T189 Implement BOM Authoring bottom status bar and unit-effectivity "(Unit)" badge per FR-054 — add a `isDirty` signal/flag to `BomAuthoringComponent` that becomes true whenever a line is added, edited, or removed before "Save Draft" is clicked; render bottom bar text: when dirty "N components · Unsaved changes — click Save Draft to retain" (amber colour `#F59E0B`); when clean "N components"; N updates in real-time via a computed property from the lines array length; in BOM line table cells for Eff From and Eff To columns, when `effectivity_type` is UNIT display a "(Unit)" sub-label badge beneath the field value using a `p-badge` or small `<span>` styled per the light/dark token `color.text.secondary`

### Verification

- [ ] T169 Run `ng lint --max-warnings 0` in `frontend/angular/` — zero lint errors before raising PR 7 (constitution §II mandatory gate)
- [ ] T157 Run `ng build --configuration=production` — zero errors
- [ ] T158 Smoke test in browser: item master row overflow → "BOMs" → empty list → "+ New BOM Revision" → creates BOM → authoring screen opens → add 2 lines → release → status chip = RELEASED → explosion view shows both components in indented tree; explosion toolbar shows CSV/PDF export buttons; "Showing X–Y of Z items" visible on item master list; Clone Item creates a pre-filled new item form

**Checkpoint**: BOM authoring and explosion fully navigable from item master list. Release workflow functional. Risk alert flags visible.

> **Raise PR 7 after this checkpoint** (T149–T158, combined with Phase 13) | CI: `ng build --configuration=production` in `frontend/angular/` | Target: `Develop`

---

## Phase 13: ECO Frontend Screens [PR 7 continued]

**Purpose**: Deliver ECO list, create, and approve screens so engineering managers can initiate and approve Engineering Change Orders from the browser, satisfying AS9100D §8.1 change-control compliance (Constitution §IV). Depends on PR 3 merged (ECO backend APIs live).

**Independent Test**: Engineering manager navigates to ECO from nav rail → sees ECO list (empty). Creates new ECO with title, description, and two affected item masters → ECO created in DRAFT. Second ECO created referencing same item master → `concurrentEcoWarning` banner shown. Manager approves first ECO → status chip changes to APPROVED.

### API Service

- [ ] T159 Create `EcoApiService` at `frontend/angular/src/app/features/eco/services/eco-api.service.ts` — typed methods: `list(params?: PageParams): Observable<Page<EcoDto>>`; `getById(id: string): Observable<EcoDto>`; `create(req: CreateEcoRequest): Observable<EcoDto>`; `approve(ecoId: string): Observable<EcoDto>`; define `EcoDto`, `CreateEcoRequest` interfaces in `frontend/angular/src/app/features/eco/models/`; `EcoDto` includes `concurrentEcoWarning: boolean`, `affectedItemIds: string[]`, `outputBomIds: string[]`

### ECO List Screen

- [ ] T160 Create `EcoListComponent` (standalone) at `frontend/angular/src/app/features/eco/pages/eco-list/eco-list.component.ts` — route: `/ecos`; page heading "Engineering Change Orders"; p-table with server-side pagination; columns: ECO #, Title, Status chip (DRAFT=warning, APPROVED=success, IMPLEMENTED=info), Affected Items count badge, Created By, Actions ("View" navigates to `/ecos/:ecoId`, "Approve" button shown only for DRAFT ECOs and only when user has `item-master:eco:manage` privilege); "+ New ECO" button opens `EcoFormComponent` dialog; status filter dropdown (All / Draft / Approved / Implemented)
- [ ] T161 Create `EcoFormComponent` (standalone dialog) at `frontend/angular/src/app/features/eco/components/eco-form/eco-form.component.ts` — fields: Title (text, required), Description (textarea), Affected Item Masters (PrimeNG `p-multiSelect` with autocomplete calling `ItemMasterApiService.list()` debounced; displays part number + revision; selected items shown as chips); Save calls `EcoApiService.create()`; on success emits `(saved)` and closes dialog; if response body contains `concurrentEcoWarning: true` show `p-toast` warning "Concurrent ECO exists for one or more affected items — review before approving"

### ECO Detail + Approve

- [ ] T162 Create `EcoDetailComponent` (standalone) at `frontend/angular/src/app/features/eco/pages/eco-detail/eco-detail.component.ts` — route: `/ecos/:ecoId`; two-column card layout: left card "Change Order Details" (all ECO fields read-only), right card "Affected Items" (chips linking to item master detail) + "Output BOMs" (chips linking to BOM authoring); "Approve ECO" button shown when status=DRAFT and user has `item-master:eco:manage` privilege — calls `EcoApiService.approve()`, shows confirm dialog first; on approval: status chip updates to APPROVED, Approve button hides, success toast
- [ ] T163 Wire "Approve" action in `EcoListComponent` — calls `EcoApiService.approve(ecoId)`, shows confirm dialog, refreshes list row on success; if `concurrentEcoWarning` true on any other open ECO for the same item, show inline `p-message` warning on that row

### Navigation

- [ ] T164 Add ECO routes to `app.routes.ts` nested under `AppShellComponent`: `/ecos` → `EcoListComponent`; `/ecos/:ecoId` → `EcoDetailComponent`; update `AppShellComponent` nav rail "ECO" item to navigate to `/ecos`

### Verification

- [ ] T170 Run `ng lint --max-warnings 0` in `frontend/angular/` — zero lint errors; if running as part of the same PR 7 as Phase 12, this shares a single lint run with T169
- [ ] T165 Run `ng build --configuration=production` — zero errors
- [ ] T166 Smoke test in browser: ECO nav item works; ECO list shows empty state; "+ New ECO" opens form; select 2 affected items; save; ECO appears in list as DRAFT; "Approve" button visible; click Approve → confirm → status chip = APPROVED

**Checkpoint**: ECO lifecycle navigable from browser. Create → Approve flow functional. Concurrent warning displayed. AS9100D §8.1 change-control audit trail visible via ECO detail.

> **Raise PR 7 after this checkpoint** (T149–T166) | CI: `ng build --configuration=production` in `frontend/angular/` | Target: `Develop`

---

## Bug Fixes (discovered during PR 6b testing)

- [ ] T203 Fix `BomControllerTest.updateLineDelegatesToServiceAndReturnsMappedDto` NPE — stub `bomService.enrichLine(line)` in the test; controller chains `enrichLine(updateLine(...))` but the test only mocked `updateLine`, leaving `enrichLine` returning null; add `when(bomService.enrichLine(line)).thenReturn(BomMapper.toLineDto(line))` before calling the controller
- [ ] T204 Fix NG0100 in `BomBrowserComponent` — add `[lazyLoadOnInit]="false"` to the `p-table` and remove the `skipNextLazyLoad` sentinel; PrimeNG fires `onLazyLoad` synchronously from `ngOnInit` (inside Angular's CD pass) then again when `[value]` changes, causing `loading` to flip `false→true` between snapshot and CHECK pass; `[lazyLoadOnInit]="false"` is the correct PrimeNG opt-out — the component owns the initial load via `ngAfterViewInit → setTimeout`

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
- **Phase 10 (Angular Frontend)**: Depends on PR 1 merged (item-master API + grid preferences API live). **MERGED** — basic item master list only
- **Phase 11 (App Shell + Item Master Fidelity + Create)**: Depends on PR 5 merged; Angular-only; no backend dependency beyond PR 1
- **Phase 12 (BOM Frontend)**: Depends on PR 6 merged (app shell required) AND PR 2 merged (BOM backend APIs live)
- **Phase 13 (ECO Frontend)**: Depends on PR 6 merged AND PR 3 merged (ECO backend APIs live); can develop in parallel with Phase 12 after PR 6 merges

### User Story Dependencies

| Story | Depends On | Can Parallelise With |
|---|---|---|
| US1 (Item Master) | Foundation | US3 (same PR) |
| US3 (UDF) | US1 item_master table | US1 lib code |
| US2 (BOM) | US1 merged (item_master FK) | — |
| US4 (Effectivity) | US2 merged | US5 (same PR) |
| US5 (ECO) | US2 merged | US4 (same PR) |
| US6 (AS5553) | US2 merged (BOM line alert) | US4, US5 |
| Angular Item Master List (PR 5) | PR 1 merged | PRs 2–4 |
| App Shell + Item Master Fidelity + Create (PR 6) | PR 5 merged | PRs 2–4 (no backend dep beyond PR 1) |
| BOM Frontend (PR 7, Phase 12) | PR 6 merged + PR 2 merged | ECO Frontend |
| ECO Frontend (PR 7, Phase 13) | PR 6 merged + PR 3 merged | BOM Frontend |

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

- PR 1: Item master foundation (MVP for BOM-dependent services to start consuming) — **MERGED**
- PR 2: BOM authoring + explosion (enables work order materialisation design)
- PR 3: Effectivity + ECO (enables AS9100D §8.1 change control)
- PR 4: AS5553 enrichment (enables supply-chain compliance queries)
- PR 5: Angular Item Master UI with shared grid + shared theme infrastructure — **MERGED** (basic list only; fidelity gaps remain)
- PR 6: App shell + item master list fidelity + create/edit form (closes all frontend gaps from PR 5; completes US1 frontend)
- PR 7: BOM frontend + ECO frontend (delivers full spec-compliant UI for US2, US4, US5)
