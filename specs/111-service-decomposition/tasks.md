# Tasks: Service Decomposition — Extract Item Master, BOM & ECO (MES-111)

**Branch**: `111-service-decomposition` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Stack**: Java 21 · Spring Boot 3.3 · PostgreSQL 16 · Apache Kafka (KRaft) · Spring Cloud Gateway · Testcontainers
**New modules**: `services/inventory-service/` · `services/engineering-service/`
**Package roots**: `com.mes.inventory` (inventory-service) · `com.mes.engineering` (engineering-service)

**TDD**: Per Constitution §II — write tests FIRST, confirm FAILING before any implementation.
Log all test failures as tracked defects before closing the story.

**Commit convention**: Every task gets its own commit tagged with the task ID. Format:
```
[type](MES-111): description [TXXX]

Ref: MES-111
Task: TXXX
```
Example: `[feat](MES-111): add inventory schema and ItemMaster entity [T025]`
Valid types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `sec`

---

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Phase 1 + 2 | T001–T037 | `./gradlew :services:inventory-service:check` | Scaffold + migrate ItemMaster + BOM to inventory-service. No gateway change yet — inventory-service testable directly on port 8096. SonarCloud anchor for new module. |
| PR 2 | Phase 3 + 4 | T038–T063 | `./gradlew :services:engineering-service:check` | Scaffold + migrate ECO to engineering-service + `bom.released` Kafka consumer. No gateway change yet. Can develop in parallel with PR 1 on a separate sub-branch. |
| PR 3 | Phase 5 | T064–T075 | `./gradlew :services:gateway-service:check :services:platform-service:check` | Gateway cut-over + UserGridPreference migration. Hard dependency on PR 1 AND PR 2 merged. Live cut-over PR — verify all routes healthy before merge. |
| PR 4 | Phase 6 + Verification | T076–T095 | `./gradlew check` (root) | Decommission migrated packages from work-order-service + compliance gates. Depends on PR 3 stable. |

**Sequencing note**: PR 1 and PR 2 can be developed in parallel on separate branches (both cut from `Develop`). PR 3 is a hard dependency on both PR 1 AND PR 2 merged. PR 4 depends on PR 3. All PRs target `Develop`.

---

## Phase 1: Scaffold inventory-service [PR 1]

**Purpose**: Create the `inventory-service` Gradle subproject, wire it into the build/CI/Docker/SonarCloud stack, and establish the `inventory` PostgreSQL schema with all Flyway migrations.

**⚠️ CRITICAL**: Flyway migrations must compile and apply cleanly before Phase 2 domain code can be validated in Testcontainers.

### Scaffolding

- [ ] T001 Add `:services:inventory-service` to `settings.gradle` (single line include)
- [ ] T002 Create `services/inventory-service/build.gradle` — Spring Boot plugin; deps: lib-common-security, lib-common-audit, mes-udf-lib, spring-boot-starter-web/data-jpa/validation/actuator, flyway-core, flyway-database-postgresql, spring-kafka, postgresql (runtime), springdoc-openapi-webmvc, spring-boot-admin-starter-client, `org.apache.pdfbox:pdfbox:3.0.3`; testImplementation: spring-boot-starter-test, testcontainers-junit-jupiter, testcontainers-postgresql, testcontainers-kafka
- [ ] T003 [P] Create `services/inventory-service/src/main/java/com/mes/inventory/InventoryServiceApplication.java` — `@SpringBootApplication`, `@EnableJpaAuditing`; `ApplicationReadyEvent` listener stub calling `PrivilegeRegistryClient.register()` with item-master privilege keys; package `com.mes.inventory`
- [ ] T004 [P] Create `services/inventory-service/src/main/resources/application.yml` — port 8096; datasource pointing at `inventory` schema; `spring.flyway.schemas=inventory`, `spring.flyway.default-schema=inventory`; Keycloak issuer URI; IAM service URL; Kafka bootstrap; Spring Boot Admin client URL; autoconfigure exclusions matching `work-order-service` pattern
- [ ] T005 [P] Create `services/inventory-service/Dockerfile` — multi-stage Eclipse Temurin 21; copy pattern from `services/work-order-service/Dockerfile` verbatim, updating JAR name
- [ ] T006 [P] Add `inventory-service` service block to `docker/compose-infra.yml` — port 8096:8096; env vars (`DATASOURCE_URL` with `inventory` schema suffix, `KEYCLOAK_ISSUER_URI`, `IAM_SERVICE_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `ADMIN_SERVICE_URL`); depends_on: postgres, kafka, keycloak; healthcheck `wget -qO- http://localhost:8096/actuator/health || exit 1`
- [ ] T007 [P] Add `inventory-service` to `docker/compose-prod.yml` — image `ghcr.io/artical2k9/mes-inventory-service:${TAG}`; same env pattern; add to `docker/compose-local-override.yml`
- [ ] T008 [P] Add `inventory-service` to `.github/workflows/publish.yml` image build matrix — same entry pattern as `work-order-service`
- [ ] T009 [P] Add `services/inventory-service/src/main/java` and `services/inventory-service/src/test/java` to `sonar-project.properties` (`sonar.sources` and `sonar.tests` respectively)
- [ ] T010 Create `services/inventory-service/src/main/java/com/mes/inventory/config/SecurityConfig.java` — `@Configuration`, `@EnableMESSecurity`, `@EnableConfigurationProperties`; `@Bean @Order(1)` internal webhook filter chain for `/internal/**`; mirrors `services/work-order-service/src/main/java/com/mes/workorder/config/SecurityConfig.java`

### Flyway Migrations (inventory schema)

- [ ] T011 [P] Create `services/inventory-service/src/main/resources/db/migration/V001__create_inventory_schema.sql` — `CREATE SCHEMA IF NOT EXISTS inventory;`
- [ ] T012 [P] Create `V002__create_item_master.sql` — copy DDL from `work-order-service/V002__create_item_master.sql`; replace all `work_order.` table/index prefixes with `inventory.`; same constraints, indexes, CHECK clauses, UNIQUE(org_id, part_number, revision)
- [ ] T013 [P] Create `V003__create_bom_tables.sql` — copy from `work-order-service/V003__create_bom_tables.sql`; replace prefix `work_order.` → `inventory.`; `eco_id UUID` column is plain (no FK to engineering schema — cross-service reference only)
- [ ] T014 [P] Create `V004__create_udf_field_definition.sql` — copy from `work-order-service/V005__create_udf_field_definition.sql`; replace prefix; note: module keys scoped to ITEM_MASTER, BOM_LINE, BOM_HEADER only
- [ ] T015 [P] Create `V005__add_envers_tables.sql` — copy from `work-order-service/V006__add_envers_tables.sql`; replace all `work_order.` with `inventory.`; creates `inventory.revinfo`, `inventory.item_master_aud`, `inventory.bill_of_materials_aud`, `inventory.bom_line_aud`
- [ ] T016 [P] Create `V006__add_envers_revend_columns.sql` — copy from `work-order-service/V011__add_envers_revend_columns.sql`; replace prefix
- [ ] T017 [P] Create `V007__seed_item_master_privileges.sql` — copy from `work-order-service/V007__seed_item_master_privileges.sql`; INSERT into `iam.privilege` and `iam.role_privilege` unchanged (target IAM schema, not inventory)
- [ ] T018 [P] Create `V008__add_bom_header_edit_fields.sql` — copy from `work-order-service/V013__add_bom_header_edit_fields.sql`; replace prefix
- [ ] T019 [P] Create `V009__add_bom_header_edit_fields_aud.sql` — copy from `work-order-service/V014__add_bom_header_edit_fields_aud.sql`; replace prefix

**Checkpoint**: `./gradlew :services:inventory-service:build -x test` compiles zero errors; `InventoryServiceApplication` starts in a Testcontainers container; Flyway applies all V001–V009 cleanly.

---

## Phase 2: Migrate ItemMaster + BOM to inventory-service [PR 1]

**Purpose**: Move all domain code from `work-order-service` to `inventory-service`. Write integration tests against the new package root before moving any code.

**Goal**: `ItemMasterControllerIT`, `BomControllerIT`, and `BomReleasedEventIT` all GREEN; `inventory-service` reachable on port 8096 independently of `work-order-service`.

**Independent Test**: Stop `work-order-service`. `GET http://localhost:8096/api/v1/item-master` returns item list. `grep -r "@KafkaListener" services/inventory-service/src/main/java` returns zero results (FR-013 producer-only constraint).

### Integration Tests — write FIRST, confirm FAILING (RED)

- [ ] T020 Create `services/inventory-service/src/test/java/com/mes/inventory/integration/BaseIntegrationTest.java` — Testcontainers pattern: PostgreSQL 16 container (`@Container`), Kafka container; `@DynamicPropertySource` registering `spring.datasource.url` (with `inventory` schema suffix), `spring.kafka.bootstrap-servers`, `mes.security.iam-service-url` mock, Keycloak issuer URI; `systemProperty 'api.version', '1.41'`; `DOCKER_HOST` env forwarding; RSA JWT helper (`generateTestToken(orgId, roles)`) matching `work-order-service/BaseIntegrationTest` pattern
- [ ] T021 [P] Create `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemMasterControllerIT.java` — migrate all existing test cases from `services/work-order-service/src/test/java/com/mes/workorder/integration/ItemMasterControllerIT.java`; update `@DynamicPropertySource` and package imports to `com.mes.inventory`; all test methods pass when implementation present
- [ ] T022 [P] Create `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomControllerIT.java` — migrate all existing test cases from `work-order-service`; verify `updateLine` delegates to `enrichLine` (covers BomControllerTest T203 regression from MES-8)
- [ ] T023 [P] Create `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomReleasedEventIT.java` — creates a BOM with a non-null `ecoId`; calls `POST /api/v1/boms/{bomId}/release`; asserts a `bom.released` Kafka message arrives on the topic containing `bomId`, `ecoId` (non-null), `orgId`, `parentItemId`, `bomRevision` — all as JSON fields (Q2: JSON serialisation confirmed)
- [ ] T024 Confirm T021–T023 **FAIL** (RED) before any domain code is moved — run `./gradlew :services:inventory-service:test` and screenshot/log failures

### Domain code migration — ItemMaster (GREEN after T024 fails)

- [ ] T025 [P] Copy `itemmaster/domain/` → `com.mes.inventory.itemmaster.domain`; update `@Table(schema = "inventory")` on `ItemMaster`; update all package declarations; `@Audited` and `@EntityListeners` unchanged
- [ ] T026 [P] Copy `itemmaster/repository/ItemMasterRepository.java` → `com.mes.inventory.itemmaster.repository`; update package declarations and imports
- [ ] T027 [P] Copy `itemmaster/service/ItemMasterService.java` → `com.mes.inventory.itemmaster.service`; update all imports to `com.mes.inventory.*`

### Domain code migration — BOM

- [ ] T028 [P] Copy `bom/domain/` → `com.mes.inventory.bom.domain`; `@Table(schema = "inventory")` on `BillOfMaterials` and `BomLine`; `eco_id` field is plain `UUID` — remove any `@ManyToOne` or FK annotation if present (cross-service reference, UUID-only per Q1); `@Audited` unchanged
- [ ] T029 [P] Copy `bom/repository/` → `com.mes.inventory.bom.repository`; update imports
- [ ] T030 [P] Copy `bom/service/EffectivityValidator.java` → `com.mes.inventory.bom.service`; update imports
- [ ] T031 [P] Copy `bom/service/BomExplosionService.java` → `com.mes.inventory.bom.service`; update imports
- [ ] T032 [P] Copy `bom/service/BomExportService.java` → `com.mes.inventory.bom.service`; update imports
- [ ] T033 Copy and **modify** `bom/service/BomService.java` → `com.mes.inventory.bom.service` — **REMOVE** `EcoService` import and injection entirely; in `releaseBom()`, after saving the BOM call `bomEventPublisher.publishReleased(saved)` with the full event payload including `ecoId` from `saved.getEcoId()`; the method ends there — no direct cross-service call remains

### Domain code migration — API, Kafka publishers

- [ ] T034 [P] Copy `itemmaster/api/` → `com.mes.inventory.itemmaster.api`; update all imports; privilege constants (`item-master:records:view` etc.) unchanged
- [ ] T035 [P] Copy `bom/api/` → `com.mes.inventory.bom.api`; update all imports
- [ ] T036 Copy and **modify** `kafka/BomEventPublisher.java` → `com.mes.inventory.kafka` — update `publishReleased()` to include `ecoId`, `parentItemId`, `bomRevision` in the JSON payload (see `data-model.md` §Kafka Events); verify JSON serialisation via `JsonSerializer` (Q2 confirmed); **field names MUST exactly match the `BomReleasedEvent` POJO in engineering-service (T063): `bomId`, `ecoId`, `orgId`, `parentItemId`, `bomRevision` — any mismatch will silently deserialise as null**
- [ ] T037 [P] Copy `kafka/ItemMasterEventPublisher.java` → `com.mes.inventory.kafka`; update package declarations
- [ ] T037a Add ArchUnit dependency to `services/inventory-service/build.gradle` (`testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'`); create `services/inventory-service/src/test/java/com/mes/inventory/architecture/InventoryArchitectureTest.java` — `@AnalyzeClasses(packages = "com.mes.inventory")`; single `@ArchTest`: `noClasses().should().beAnnotatedWith(KafkaListener.class).as("inventory-service must be producer-only (FR-013): @KafkaListener is prohibited")`; this converts the manual T088 grep gate into a build-time CI failure

**Checkpoint**: `./gradlew :services:inventory-service:check` passes with T021–T023 GREEN; zero Checkstyle/SpotBugs violations; `grep -r "@KafkaListener" services/inventory-service/src/main/java` returns zero results (FR-013 producer-only gate).

> **Raise PR 1 after this checkpoint** (T001–T037) | CI: `./gradlew :services:inventory-service:check` | Target: `Develop`

---

## Phase 3: Scaffold engineering-service [PR 2]

**Purpose**: Create the `engineering-service` Gradle subproject and establish the `engineering` PostgreSQL schema with all Flyway migrations.

### Scaffolding

- [ ] T038 Add `:services:engineering-service` to `settings.gradle`
- [ ] T039 Create `services/engineering-service/build.gradle` — same deps pattern as inventory-service; no PDFBox; same testImplementation dependencies
- [ ] T040 [P] Create `services/engineering-service/src/main/java/com/mes/engineering/EngineeringServiceApplication.java` — `@SpringBootApplication`, `@EnableJpaAuditing`; `ApplicationReadyEvent` listener stub for ECO privilege registration; package `com.mes.engineering`
- [ ] T041 [P] Create `services/engineering-service/src/main/resources/application.yml` — port 8097; datasource `engineering` schema; Flyway pointing at `engineering` schema; Keycloak issuer URI; Kafka bootstrap (consumer group `engineering-service`); Spring Boot Admin client URL; autoconfigure exclusions
- [ ] T042 [P] Create `services/engineering-service/Dockerfile` — multi-stage Eclipse Temurin 21; same pattern as inventory-service Dockerfile
- [ ] T043 [P] Add `engineering-service` block to `docker/compose-infra.yml` — port 8097:8097; same env pattern as inventory-service; depends_on postgres, kafka, keycloak; healthcheck on port 8097
- [ ] T044 [P] Add `engineering-service` to `docker/compose-prod.yml` (image `ghcr.io/artical2k9/mes-engineering-service:${TAG}`); add to `docker/compose-local-override.yml`
- [ ] T045 [P] Add `engineering-service` to `.github/workflows/publish.yml` image build matrix
- [ ] T046 [P] Add `services/engineering-service/src/main/java` and `src/test/java` paths to `sonar-project.properties`
- [ ] T047 Create `services/engineering-service/src/main/java/com/mes/engineering/config/SecurityConfig.java` — identical pattern to `inventory-service/SecurityConfig.java`

### Flyway Migrations (engineering schema)

- [ ] T048 [P] Create `services/engineering-service/src/main/resources/db/migration/V001__create_engineering_schema.sql` — `CREATE SCHEMA IF NOT EXISTS engineering;`
- [ ] T049 [P] Create `V002__create_eco_tables.sql` — copy DDL from `work-order-service/V004__create_eco_tables.sql` + `V012__create_eco_output_bom.sql`; replace prefix `work_order.` → `engineering.`; `affected_item_id` in `eco_affected_item` is plain UUID (no FK to inventory schema — Q1 UUID-only confirmed); `bom_id` in `eco_output_bom` is plain UUID (no FK to inventory schema)
- [ ] T050 [P] Create `V003__create_udf_field_definition.sql` — same structure as inventory V004; module_key = 'ECO' only
- [ ] T051 [P] Create `V004__add_envers_tables.sql` — `engineering.revinfo`, `engineering.engineering_change_order_aud`; copy pattern from work-order-service/V006 with prefix replacement
- [ ] T052 [P] Create `V005__add_envers_revend_columns.sql` — copy from work-order-service/V011; replace prefix
- [ ] T052a [P] Create `V006__seed_eco_privileges.sql` — INSERT into `iam.privilege` for `item-master:eco:manage` if not already present (use `INSERT … ON CONFLICT DO NOTHING`); INSERT into `iam.role_privilege` granting it to SYSTEM_ADMIN and ENGINEER; use SELECT to look up role IDs by name — mirrors work-order-service/V007 pattern for the ECO privilege key only

**Checkpoint**: `./gradlew :services:engineering-service:build -x test` compiles zero errors; Flyway V001–V006 apply cleanly in Testcontainers.

---

## Phase 4: Migrate ECO to engineering-service + bom.released consumer [PR 2]

**Purpose**: Move all ECO domain code from `work-order-service` to `engineering-service` and wire the `bom.released` Kafka consumer that replaces the direct `EcoService.addOutputBom()` call.

**Goal**: `EcoControllerIT` and `BomReleasedConsumerIT` GREEN; `engineering-service` reachable on port 8097 independently of `inventory-service`.

**Independent Test**: Stop `inventory-service`. `GET http://localhost:8097/api/v1/ecos` returns ECO list. Produce a `bom.released` Kafka event with a known `ecoId`; `GET /api/v1/ecos/{ecoId}` shows `outputBomIds` populated.

### Integration Tests — write FIRST, confirm FAILING (RED)

- [ ] T053 Create `services/engineering-service/src/test/java/com/mes/engineering/integration/BaseIntegrationTest.java` — same Testcontainers pattern as inventory-service; `engineering` schema; Kafka container; RSA JWT helper
- [ ] T054 [P] Create `services/engineering-service/src/test/java/com/mes/engineering/integration/eco/EcoControllerIT.java` — migrate all existing test cases from `services/work-order-service/src/test/java/com/mes/workorder/integration/eco/EcoControllerIT.java`; update package imports to `com.mes.engineering`
- [ ] T055 [P] Create `services/engineering-service/src/test/java/com/mes/engineering/integration/eco/BomReleasedConsumerIT.java` — produce a `bom.released` JSON Kafka message with fields: `bomId`, `ecoId` (known UUID), `orgId`, `parentItemId`, `bomRevision`; await consumer processing (poll with timeout); assert `GET /api/v1/ecos/{ecoId}` response body contains `ecoId` in `outputBomIds`; consumer must be idempotent (publish same message twice, assert `outputBomIds` has exactly one entry)
- [ ] T056 Confirm T054–T055 **FAIL** (RED) before domain code is moved

### Domain code migration — ECO

- [ ] T057 [P] Copy `eco/domain/` → `com.mes.engineering.eco.domain`; `@Table(schema = "engineering")` on `EngineeringChangeOrder`; `affectedItemIds` stored in `eco_affected_item` as plain UUID rows (no FK to inventory schema); `outputBomIds` stored in `eco_output_bom` as plain UUID rows (no FK); `@Audited` unchanged; `EcoDto.outputBomIds` is `List<UUID>` only — no cross-service enrichment (Q1 confirmed)
- [ ] T058 [P] Copy `eco/repository/EcoRepository.java` → `com.mes.engineering.eco.repository`; update imports
- [ ] T059 Copy and **modify** `eco/service/EcoService.java` → `com.mes.engineering.eco.service` — retain `addOutputBom(UUID ecoId, UUID bomId)` method; make it idempotent: check if `bomId` already present in `outputBomIds` before adding; update imports to `com.mes.engineering.*`
- [ ] T060 [P] Copy `eco/api/` → `com.mes.engineering.eco.api`; update all imports
- [ ] T061 [P] Copy `kafka/EcoEventPublisher.java` → `com.mes.engineering.kafka`; update package declarations; JSON serialisation unchanged (Q2 confirmed)
- [ ] T062 Create `services/engineering-service/src/main/java/com/mes/engineering/kafka/BomReleasedEventHandler.java` — `@Component`; `@KafkaListener(topics = "bom.released", groupId = "engineering-service")`; `@Payload BomReleasedEvent event` (POJO with `bomId`, `ecoId`, `orgId`, `parentItemId`, `bomRevision` — JSON deserialised via `JsonDeserializer`); if `event.getEcoId() != null` call `ecoService.addOutputBom(event.getEcoId(), event.getBomId())`; if ECO not found log WARN and skip (idempotent); annotate class with a comment: "engineering-service MAY have Kafka consumers — only inventory-service is producer-only per FR-013"
- [ ] T063 Create `services/engineering-service/src/main/java/com/mes/engineering/kafka/BomReleasedEvent.java` — plain POJO with `bomId`, `ecoId`, `orgId`, `parentItemId`, `bomRevision` fields; Jackson `@JsonIgnoreProperties(ignoreUnknown = true)`; no-arg constructor; getters/setters; **field names MUST exactly match what `BomEventPublisher` (inventory-service T036) serialises — verify against `data-model.md` §Kafka Events canonical payload definition**

**Checkpoint**: `./gradlew :services:engineering-service:check` passes with T054–T055 GREEN; zero Checkstyle/SpotBugs violations; BOM release Kafka event consumed correctly.

> **Raise PR 2 after this checkpoint** (T038–T063) | CI: `./gradlew :services:engineering-service:check` | Target: `Develop`

---

## Phase 5: Gateway cut-over + UserGridPreferences migration [PR 3]

**Purpose**: Replace the catch-all `/api/v1/**` gateway predicate with domain-specific routes. Migrate `UserGridPreference` to `platform-service`. This is the live cut-over PR — depends on PR 1 AND PR 2 merged.

**Goal**: All Angular screens load from the correct domain services. Gateway has no catch-all `/api/v1/**` predicate.

**Independent Test**:
```
curl http://localhost:8082/api/v1/item-master         → 200 from inventory-service
curl http://localhost:8082/api/v1/boms                → 200 from inventory-service
curl http://localhost:8082/api/v1/udf/fields          → 200 from inventory-service
curl http://localhost:8082/api/v1/ecos                → 200 from engineering-service
curl http://localhost:8082/api/v1/users/preferences/grid/ITEM_MASTER → 200 from platform-service
```

### platform-service: UserGridPreference — TDD (RED first)

- [ ] T064 [P] [US3] Write `UserGridPreferenceControllerIT` in `platform-service` test module — `GET /api/v1/users/preferences/grid/{module}` returns 200 with empty config for new user; `PUT` saves column config; second `GET` returns saved config; uses existing Testcontainers base class in `platform-service`
- [ ] T065 [US3] Confirm T064 **FAILS** (RED) before implementation

### platform-service: UserGridPreference — implementation (GREEN)

- [ ] T066 [US3] Add Flyway migration to `platform-service` — first run `ls services/platform-service/src/main/resources/db/migration/` to determine the next version number (V = highest existing + 1), then create `V{N}__create_user_grid_preferences.sql`; create `platform.user_grid_preferences` table with same DDL as current `work_order.user_grid_preferences` (id UUID PK, org_id, user_id, module_key, column_config JSONB, updated_at, UNIQUE(org_id, user_id, module_key))
- [ ] T067 [P] [US3] Copy `preferences/domain/UserGridPreference.java` → `com.mes.platform.preferences.domain`; `@Table(schema = "platform")`; update package declarations
- [ ] T068 [P] [US3] Copy `preferences/repository/UserGridPreferenceRepository.java` → `com.mes.platform.preferences.repository`; update imports
- [ ] T069 [P] [US3] Copy `preferences/service/UserGridPreferenceService.java` → `com.mes.platform.preferences.service`; update imports
- [ ] T070 [US3] Copy `preferences/api/UserGridPreferenceController.java` → `com.mes.platform.preferences.api`; update imports; verify JWT extraction matches existing `platform-service` pattern; endpoint path `GET/PUT /api/v1/users/preferences/grid/{module}` unchanged
- [ ] T071 Run `./gradlew :services:platform-service:check` — T064 GREEN; zero violations

### Gateway routing — replace catch-all with domain predicates

- [ ] T072 Update `services/gateway-service/src/main/resources/application.yml` — replace the single `work-order-service` route block (`Path=/api/v1/**`) with six domain-specific routes (see `data-model.md` §Gateway Routing section for exact YAML):
  1. `inventory-service-item-master`: `Path=/api/v1/item-master/**` → `${INVENTORY_SERVICE_URL:http://inventory-service:8096}`
  2. `inventory-service-boms`: `Path=/api/v1/boms/**` → `${INVENTORY_SERVICE_URL}`
  3. `inventory-service-udf`: `Path=/api/v1/udf/**` → `${INVENTORY_SERVICE_URL}`
  4. `engineering-service-ecos`: `Path=/api/v1/ecos/**` → `${ENGINEERING_SERVICE_URL:http://engineering-service:8097}`
  5. `platform-service-preferences`: `Path=/api/v1/users/**` → `${PLATFORM_SERVICE_URL:http://platform-service:8090}`
  6. `work-order-service` (narrowed): `Path=/api/v1/work-orders/**` → `${WORK_ORDER_SERVICE_URL:http://work-order-service:8095}` (Q4: narrowed, not removed)
  All routes use `StripPrefix=0`
- [ ] T073 Add `INVENTORY_SERVICE_URL` and `ENGINEERING_SERVICE_URL` to `docker/compose-infra.yml` gateway service `environment:` block and to `.env.example`
- [ ] T074 Run `./gradlew :services:gateway-service:check`
- [ ] T075 Manual smoke test (required gate before PR 3 merge — document results in PR description):
  - `curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/item-master` → 200, body from `inventory-service`
  - `curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/boms` → 200, from `inventory-service`
  - `curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/udf/fields` → 200, from `inventory-service`
  - `curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/ecos` → 200, from `engineering-service`
  - `curl -H "Authorization: Bearer <token>" http://localhost:8082/api/v1/users/preferences/grid/ITEM_MASTER` → 200, from `platform-service`
  - Confirm `work-order-service` logs show ZERO requests for `/api/v1/item-master`, `/api/v1/boms`, `/api/v1/ecos`
  - Confirm Angular frontend loads Item Master, BOM, and ECO screens on first click with no console errors

**Checkpoint**: All smoke-test curls return 200 from correct services; Angular frontend clean; no catch-all predicate in gateway config.

> **Raise PR 3 after this checkpoint** (T064–T075) | CI: `./gradlew :services:gateway-service:check :services:platform-service:check` | Target: `Develop`

---

## Phase 6: Decommission migrated packages from work-order-service [PR 4]

**Purpose**: Remove all migrated domain code from `work-order-service`. Leave only infrastructure scaffolding for future Work Orders & Scheduling. Depends on PR 3 merged and verified stable.

**Goal**: `work-order-service` compiles with zero domain references to ItemMaster, BOM, ECO, or Preferences.

**Independent Test**: `grep -r "itemmaster\|BomController\|EcoController\|UserGridPreference" services/work-order-service/src/main/java` returns zero results. Gateway `work-order-service` route is `Path=/api/v1/work-orders/**` only.

### Source code removal

- [ ] T076 [US4] Delete `services/work-order-service/src/main/java/com/mes/workorder/itemmaster/` package directory entirely
- [ ] T077 [US4] Delete `services/work-order-service/src/main/java/com/mes/workorder/bom/` package directory entirely
- [ ] T078 [US4] Delete `services/work-order-service/src/main/java/com/mes/workorder/eco/` package directory entirely
- [ ] T079 [US4] Delete `services/work-order-service/src/main/java/com/mes/workorder/preferences/` package directory entirely
- [ ] T080 [US4] Delete `services/work-order-service/src/main/java/com/mes/workorder/kafka/BomEventPublisher.java`, `EcoEventPublisher.java`, `ItemMasterEventPublisher.java`
- [ ] T081 [US4] Clean `services/work-order-service/src/main/resources/application.yml` — remove item-master/bom/eco Kafka topic references; remove UDF auto-config references; retain schema `work_order`, port 8095, and any infrastructure config
- [ ] T082 [US4] Add comment migration `services/work-order-service/src/main/resources/db/migration/V015__note_domains_migrated.sql` — content: `-- Item Master (V002), BOM (V003), ECO (V004, V012), UDF (V005), and user_grid_preferences (V008) domains migrated to inventory-service, engineering-service, and platform-service in MES-111. work-order-service retains work_order schema for future Work Orders domain.`; V015 = next version after existing V014; prevents Flyway version gap warning on fresh installs
- [ ] T083 [US4] Delete migrated test classes from `work-order-service/src/test/` — `ItemMasterControllerIT.java`, `BomControllerIT.java`, `EcoControllerIT.java`, `BomControllerTest.java`, `BomExportServiceTest.java`, `BomServiceTest.java`; keep `BaseIntegrationTest.java` (still needed for future work-order tests)
- [ ] T084 [US4] Run `./gradlew :services:work-order-service:check` — compiles zero errors; all remaining tests pass; verify `grep -r "itemmaster\|BomController\|EcoController\|UserGridPreference" services/work-order-service/src/main/java` returns zero results

**Checkpoint**: `work-order-service` is a clean slate for Work Orders; gateway has no catch-all predicate; all previously-working API endpoints still respond correctly via gateway.

---

## Phase 7: Verification [PR 4 continued]

**Purpose**: Final quality gates across all affected services before PR 4 is raised.

- [ ] T085 Run `./gradlew check` from repo root — all subprojects pass; zero test failures across inventory-service, engineering-service, platform-service, gateway-service, work-order-service
- [ ] T086 [P] Run `ng build --configuration=production` in `frontend/angular/` — zero errors (no URL changes were made; angular frontend is unchanged)
- [ ] T087 [P] Run `ng lint --max-warnings 0` in `frontend/angular/` — zero lint errors
- [ ] T088 Verify SC-008: `grep -r "@KafkaListener" services/inventory-service/src/main/java` returns zero results — producer-only constraint confirmed (FR-013)
- [ ] T089 Manual end-to-end smoke test — start full stack; stop `work-order-service`; navigate full BOM authoring flow (item master → select item → BOM browser → create BOM → add lines → release BOM); verify `engineering-service` log shows "Added output BOM {id} to ECO {id}"; confirm zero errors in Angular console
- [ ] T090 SonarCloud verification — run `gh pr checks <pr-4-number>`; both `SonarCloud Code Analysis` and `SonarCloud Analysis` checks must show `pass` for inventory-service and engineering-service; new code coverage ≥ 80%; zero quality gate failures

> **Raise PR 4 after this checkpoint** (T076–T090) | CI: `./gradlew check` | Target: `Develop`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (inventory-service scaffold)**: No dependencies — start immediately
- **Phase 2 (ItemMaster + BOM migration)**: Depends on Phase 1 completion
- **Phase 3 (engineering-service scaffold)**: No dependencies — can start in parallel with Phase 1+2
- **Phase 4 (ECO migration)**: Depends on Phase 3 completion
- **Phase 5 (gateway cut-over)**: Hard dependency on BOTH PR 1 (Phase 1+2) AND PR 2 (Phase 3+4) merged to Develop
- **Phase 6 (decommission)**: Depends on Phase 5 (PR 3) merged and stable
- **Phase 7 (verification)**: Runs at end of Phase 6, bundled in PR 4

### User Story Dependencies

| Story | Phases | Depends On | Can Parallelise With |
|---|---|---|---|
| US1 (inventory-service) | Phase 1 + 2 | None | US2 (Phase 3 + 4) |
| US2 (engineering-service) | Phase 3 + 4 | None | US1 (Phase 1 + 2) |
| US3 (UserGridPreferences) | Phase 5 (part) | PR 1 + PR 2 merged | Gateway update (Phase 5 remainder) |
| US4 (decommission) | Phase 6 + 7 | PR 3 merged | — |

### Within Each Phase

```
Tests (RED) → Domain entities → Repositories → Services → Controllers/API → Kafka → Verify (GREEN)
```

---

## Parallel Opportunities

### Phase 1 + Phase 3 (can run simultaneously on separate branches)

```
Branch: 111-inventory-scaffold   →  T001–T037 (PR 1)
Branch: 111-engineering-scaffold →  T038–T063 (PR 2)
```

### Within Phase 1 (T003–T009 all parallelizable)

```
T003 InventoryServiceApplication.java
T004 application.yml
T005 Dockerfile
T006 compose-infra.yml entry
T007 compose-prod.yml entry
T008 publish.yml matrix
T009 sonar-project.properties
T011–T019 Flyway migrations (all independent files)
```

### Within Phase 2 (after T020 BaseIntegrationTest)

```
T021 ItemMasterControllerIT   ─┐
T022 BomControllerIT          ─┤  [P] write tests in parallel
T023 BomReleasedEventIT       ─┘

T025 itemmaster/domain        ─┐
T026 itemmaster/repository    ─┤
T028 bom/domain               ─┤  [P] migrate entity packages in parallel
T029 bom/repository           ─┤
T030 EffectivityValidator     ─┤
T031 BomExplosionService      ─┤
T032 BomExportService         ─┘

T034 itemmaster/api           ─┐
T035 bom/api                  ─┤  [P] after services complete
T037 ItemMasterEventPublisher ─┘
```

---

## Implementation Strategy

### MVP Scope (PR 1 only)

1. Complete Phase 1: Scaffold inventory-service (T001–T019)
2. Complete Phase 2: Migrate ItemMaster + BOM (T020–T037)
3. **STOP and VALIDATE**: `./gradlew :services:inventory-service:check` GREEN; inventory-service starts; Item Master API reachable on port 8096
4. Raise PR 1 — this alone fixes the fault-isolation problem for Item Master and BOM

### Incremental Delivery

1. PR 1 merged → Item Master + BOM isolated from work-order-service Kafka churn ✓
2. PR 2 merged → ECO isolated; BOM→ECO coupling replaced by Kafka event ✓
3. PR 3 merged → Gateway cut-over; catch-all predicate removed; UserGridPreference migrated ✓
4. PR 4 merged → work-order-service clean; SC-003 fully satisfied ✓

---

## Phase 8: Compliance Verification & Defect Closure [PR 4 continued]

**Purpose**: Validate all Constitution compliance gates before marking MES-111 done. Mandatory per Constitution §II and §IV.

- [ ] T091 Verify all Constitution Check gates in `plan.md` are ✅ PASS — focus on §XI (no catch-all), §VIII (Kafka idempotency), §IX (org_id on all entities), §VII (Keycloak on new services)
- [ ] T092 [P] Confirm Envers audit tables exist and capture mutations in both `inventory` and `engineering` schemas — run one create + one update for ItemMaster and ECO in integration tests; assert `*_aud` rows created
- [ ] T093 [P] Confirm `org_id` scoping on all new service endpoints — verify `ItemMasterControllerIT` and `EcoControllerIT` include a cross-org isolation test (request with org A token cannot access org B data)
- [ ] T094 [P] Confirm `bom.released` consumer idempotency test passes (`BomReleasedConsumerIT` publishes same event twice; `outputBomIds` has exactly one entry)
- [ ] T095 Run error log retrospective gate (CLAUDE.md mandatory) — review session work for new errors or near-misses; add entries to `docs/governance/MES-ERR-001_Agent_Error_Log.md`; promote any with clear root cause to ARCHIVE + Index
