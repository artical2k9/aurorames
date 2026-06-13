# Tasks: Quality Inspection Planning (Control Plans)

**Input**: Design documents from `/specs/012-quality-inspection-planning/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/inspection-plans-api.md

**Tests**: REQUIRED — Constitution §II (TDD): test tasks precede implementation in every story.

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup (scaffold) + US1 (plan header + revision lifecycle) | T001–T025 | `./gradlew :services:quality-service:check` | Scaffold bundled with US1 ITs as coverage anchor; includes settings.gradle/compose/gateway/sonar updates |
| PR 2 | US2 (characteristics incl. expression validator) | T026–T033 | `./gradlew :services:quality-service:check` | Parser unit tests (cycle/reference/limit cases) + characteristic ITs |
| PR 3 | US3 (consumer API + approval gate + Kafka event) | T034–T040 | `./gradlew :services:quality-service:check` | Contract consumed by MES-9; submit-blocks tested here |
| PR 4 | US4 (frontend Quality > Inspection Plans) + compliance | T041–T054 | `npm run lint && npm run test` + gateway smoke | ModuleKey + sidebar additions; ERR-MES-059/078 checks |

**Sequencing note**: PR 2 depends on PR 1; PR 3 depends on PR 2. MES-9 consumes the PR 3 contract.

---

## Phase 1: Setup — quality-service scaffold [PR 1]

- [ ] T001 Add `include 'services:quality-service'` to settings.gradle and create services/quality-service/build.gradle mirroring services/engineering-service/build.gradle (deps: lib-common-security, lib-common-audit, mes-udf-lib, hypersistence-utils; test {} block with api.version=1.41 + DOCKER_HOST forwarding per ERR-MES-036/037)
- [ ] T002 Create QualityServiceApplication.java + application.yml (port 8099, schema quality, Kafka JsonSerializer per ERR-MES-063, KC resource-server config) in services/quality-service/src/main/resources
- [ ] T003 Create config package: AppConfig.java (AuditorAware<String> bean — ERR-MES-062), SecurityConfig.java, QualitySecurityProperties.java in services/quality-service/src/main/java/com/mes/quality/config/
- [ ] T004 [P] Create api/GlobalExceptionHandler.java (copy inventory-service shape: 404/409/422/400 + Udf handlers) in services/quality-service/src/main/java/com/mes/quality/api/
- [ ] T005 [P] Create audit package (QualityRevisionEntity + listener, Envers config) in services/quality-service/src/main/java/com/mes/quality/audit/
- [ ] T006 [P] Create Flyway migrations V001__create_quality_schema.sql, V002__create_inspection_plan_tables.sql, V003__add_envers_tables.sql (incl. revend/revend_tstmp; partial unique DRAFT index; all NOT NULL audit columns) per data-model.md in services/quality-service/src/main/resources/db/migration/
- [ ] T007 [P] Create Dockerfile + add quality-service block to docker/compose-infra.yml (port 8099, QUALITY_DB_URL, healthcheck) + QUALITY_SERVICE_URL env on gateway service
- [ ] T008 [P] Add gateway route `/api/v1/inspection-plans/**` → quality-service:8099 in services/gateway-service/src/main/resources/application.yml
- [ ] T009 [P] Add quality-service paths to sonar.sources and sonar.tests in sonar-project.properties
- [ ] T010 [P] Add INSPECTION_PLAN, INSPECTION_CHARACTERISTIC to libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java
- [ ] T011 Register privilege manifest (quality:inspection-plan:create/read/update/delete/approve, ApplicationReadyEvent handler per ERR-MES-075 pattern) in services/quality-service/src/main/java/com/mes/quality/config/
- [ ] T012 Create BaseIntegrationTest (Testcontainers PostgreSQL + KC, locally-signed RSA JWTs per ERR-MES-024, jwk-set-uri override per ERR-MES-068) in services/quality-service/src/test/java/com/mes/quality/integration/

**Checkpoint**: `./gradlew :services:quality-service:check` compiles, context loads, Flyway applies.

---

## Phase 2: User Story 1 — Plan header + revision lifecycle (P1) [PR 1] 🎯 MVP

**Goal**: Create/read inspection plans with DRAFT → PENDING_APPROVAL → APPROVED lifecycle, auto-draft on edit of approved, one-plan-per-item.

**Independent Test**: Create plan for an item → rev 0 DRAFT; submit + approve; PATCH header → draft rev 1 with copied content; reject path returns to DRAFT.

### Tests (write first, confirm failing)

- [ ] T013 [P] [US1] IT: create plan returns 201 rev 0 DRAFT; duplicate item returns 409; unknown item returns 422 — InventoryServiceClient stubbed via WireMock (no inventory container in IT stack) in services/quality-service/src/test/java/com/mes/quality/integration/inspectionplan/InspectionPlanControllerIT.java
- [ ] T014 [P] [US1] IT: submit → PENDING_APPROVAL; approve → APPROVED with actor metadata; reject(reason) → DRAFT; reject without reason 422 (same IT class)
- [ ] T015 [P] [US1] IT: PATCH header on APPROVED auto-creates draft N+1; one-draft invariant 409; revision history endpoint ordered; display revision resolution (APPROVED > PENDING > DRAFT, rev DESC) (same IT class)
- [ ] T016 [P] [US1] IT: unauthenticated 401; missing privilege 403; org isolation (other-org token sees 404) (same IT class)
- [ ] T017 [P] [US1] IT: delete plan 409 once ever-approved; delete never-approved 204; Envers audit rows written in InspectionPlanLifecycleIT.java

### Implementation

- [ ] T018 [P] [US1] Create domain entities InspectionPlan, InspectionPlanRevision, RevisionStatus (reuse enum semantics), enums (CharacteristicType, CharacteristicSource, RecordingBasis, SampleSizeRule) per data-model.md in services/quality-service/src/main/java/com/mes/quality/inspectionplan/domain/
- [ ] T019 [P] [US1] Create repositories (InspectionPlanRepository, InspectionPlanRevisionRepository) in .../inspectionplan/repository/
- [ ] T020 [US1] Create InventoryServiceClient (RestClient, item existence + part number denorm, forwarded JWT; base URL configurable so ITs point at WireMock) in .../inspectionplan/client/
- [ ] T021 [US1] Implement InspectionPlanService: createPlan, getPlan (revisionNumber/status params + display resolution), patchHeader (auto-draft + full copy), submit/approve/reject/cancelDraft, delete guard, listPlans paged/search in .../inspectionplan/service/
- [ ] T022 [US1] Create DTOs + InspectionPlanMapper in .../inspectionplan/api/dto/
- [ ] T023 [US1] Create InspectionPlanController (endpoints per contracts/inspection-plans-api.md plans & revisions table) in .../inspectionplan/api/
- [ ] T024 [US1] Run `./gradlew :services:quality-service:check` — all US1 tests green, zero lint
- [ ] T025 [US1] Log any test failures as defects per Constitution §II; fix and re-run

**Checkpoint**: Plan lifecycle complete and independently testable.

> **Raise PR 1 after this checkpoint** (T001–T025) | CI: `./gradlew :services:quality-service:check` | Target: `Develop`

---

## Phase 3: User Story 2 — Inspection characteristics (P1) [PR 2]

**Goal**: SPECIFIC/COMMON/CALCULATED characteristics with type-specific validation and expression reference/cycle checking.

**Independent Test**: In a draft plan add one of each type; calculated expression referencing peers validates; bad reference/cycle rejected; edits blocked on non-DRAFT.

### Tests (write first, confirm failing)

- [ ] T026 [P] [US2] Unit tests: ExpressionValidator — grammar (operators, parens, numbers, C-refs, #{tag}), unknown ref, self-ref, 2-node and 3-node cycles, ref to COMMON rejected, valid chains in services/quality-service/src/test/java/com/mes/quality/inspectionplan/service/ExpressionValidatorTest.java
- [ ] T027 [P] [US2] Unit tests: characteristic field-matrix validation (SPECIFIC limits ordering lower ≤ nominal ≤ upper; COMMON requires expectedBoolean; CALCULATED requires expression; cross-type field rejection; FIXED_COUNT requires count ≥ 1) in CharacteristicValidatorTest.java
- [ ] T028 [P] [US2] IT: add/edit/delete characteristics on DRAFT (renumbering via PATCH of characteristicNumber — no separate reorder endpoint); 409 on PENDING/APPROVED; characteristic-number uniqueness 409; delete-with-dependents 409 naming dependents in InspectionCharacteristicIT.java

### Implementation

- [ ] T029 [P] [US2] Create InspectionCharacteristic entity + repository per data-model.md in .../inspectionplan/domain/ and .../repository/
- [ ] T030 [US2] Implement ExpressionValidator (recursive-descent parser per research.md R1, final utility class) in .../inspectionplan/service/
- [ ] T031 [US2] Implement CharacteristicService (CRUD + field-matrix validation + dependency guard + copy-on-revision wiring into patchHeader/createRevision incl. customFields) in .../inspectionplan/service/
- [ ] T032 [US2] Add characteristic endpoints + DTOs to controller/mapper per contract in .../inspectionplan/api/
- [ ] T033 [US2] Run `./gradlew :services:quality-service:check`; log/fix defects

**Checkpoint**: Characteristics fully functional on the draft lifecycle.

> **Raise PR 2 after this checkpoint** (T026–T033) | CI: `./gradlew :services:quality-service:check` | Target: `Develop`

---

## Phase 4: User Story 3 — Approval gate + consumer API + events (P1) [PR 3]

**Goal**: Submit-blocks (empty plan, invalid expressions); approved-plan consumer endpoints; Kafka approval event.

**Independent Test**: Status endpoint reports not-approved for draft-only plan; after approve returns revision content; submit with 0 characteristics 422.

### Tests (write first, confirm failing)

- [ ] T034 [P] [US3] IT: submit with zero characteristics 422; submit with invalid expression 422 (deleting referenced char path); approve happy path in InspectionPlanApprovalGateIT.java
- [ ] T035 [P] [US3] IT: by-item/{id}/approved 404 NO_APPROVED_PLAN for draft-only; returns latest approved with characteristics for approved+draft history; by-item/{id}/status shape in ConsumerContractIT.java
- [ ] T036 [P] [US3] IT: Kafka quality.inspection-plan.approved event published on approve with all fields (Testcontainers Kafka consumer assert) in InspectionPlanEventIT.java

### Implementation

- [ ] T037 [US3] Add submit-validation (characteristic count + revalidate all expressions) to InspectionPlanService.submit in .../inspectionplan/service/
- [ ] T038 [P] [US3] Implement ConsumerController (by-item approved + status endpoints) + DTOs in .../inspectionplan/api/
- [ ] T039 [P] [US3] Implement QualityEventPublisher (Kafka, JsonSerializer) + publish on approve in services/quality-service/src/main/java/com/mes/quality/kafka/
- [ ] T040 [US3] Run `./gradlew :services:quality-service:check`; log/fix defects

**Checkpoint**: MES-9 consumer contract live.

> **Raise PR 3 after this checkpoint** (T034–T040) | CI: `./gradlew :services:quality-service:check` | Target: `Develop`

---

## Phase 5: User Story 4 — Frontend Quality > Inspection Plans (P2) [PR 4]

**Goal**: Sidebar module, list screen, detail/authoring screen with revision selector/history and type-specific characteristic forms.

**Independent Test**: Navigate Quality > Inspection Plans, create plan, author 3 characteristic types, submit/approve, view history revision.

### Tests (write first where applicable)

- [ ] T041 [P] [US4] Vitest: inspection-plan-list component (load, search, column picker UDF merge) in frontend/angular/src/app/features/inspection-plans/pages/inspection-plan-list/inspection-plan-list.component.spec.ts
- [ ] T042 [P] [US4] Vitest: characteristic-editor type-switching form validation (SPECIFIC limits, COMMON boolean, CALCULATED expression required) in .../components/characteristic-editor/characteristic-editor.component.spec.ts

### Implementation

- [ ] T043 [P] [US4] Create InspectionPlanApiService + DTO interfaces (incl. customFields) in frontend/angular/src/app/features/inspection-plans/services/inspection-plan-api.service.ts
- [ ] T044 [US4] Create inspection-plan-list page (GridPreferenceService + UDF load per ERR-MES-078, getCellValue, cdr.detectChanges per ERR-MES-059, Lucide icon buttons) in .../pages/inspection-plan-list/
- [ ] T045 [US4] Create inspection-plan-detail page (header form, revision dropdown + history table with working View, workflow buttons with confirm/toast + reload-after-action) in .../pages/inspection-plan-detail/
- [ ] T046 [US4] Create characteristic-editor component (grid + type-specific dialogs) in .../components/characteristic-editor/
- [ ] T047 [US4] Add route + sidebar entry Quality > Inspection Plans in frontend/angular/src/app/app.routes.ts and sidebar component
- [ ] T048 [US4] `npm run lint && npm run test` green; full-stack smoke via gateway (create→author→approve in browser)
- [ ] T049 [US4] Log/fix defects

**Checkpoint**: Module usable end-to-end in the UI.

---

## Phase 6: Compliance Verification & Defect Closure [PR 4 continued]

- [x] T050 Verify Constitution Check gates in plan.md all ✅ (gate III recorded PASS — owner approved via /speckit-clarify)
- [x] T051 [P] Confirm Envers audit rows for plan/revision/characteristic mutations — `InspectionPlanLifecycleIT.mutationsWriteEnversAuditRows()` queries `inspection_plan_aud` + `inspection_plan_revision_aud`
- [x] T052 [P] Confirm org_id scoping on every repository query — all `InspectionPlanRepository` queries scoped by `orgId`; cross-org `InspectionPlanControllerIT.otherOrgCannotSeePlan404()`
- [x] T053 [P] Confirm privilege keys registered and SYSTEM_ADMIN auto-granted — V004 seeds 5 `quality:inspection-plan:*` keys, grants all to SYSTEM_ADMIN + read to ENGINEER
- [x] T054 Confirm all logged defects closed; pre-PR retrospective vs MES-ERR-001 index categories complete (ERR-061/062/063/060/070/059/078/079/080/077/016 spot-checked)

> **Raise PR 4 after this checkpoint** (T041–T054) | CI: `npm run lint && npm run test` + `./gradlew :services:quality-service:check` | Target: `Develop`

---

## Dependencies & Execution Order

- Phase 1 → blocks everything (scaffold).
- US1 (Phase 2) → blocks US2 (characteristics live on revisions) → blocks US3 (submit validation + consumer reads characteristics).
- US4 (frontend) depends on US1–US3 endpoints; can start screens in parallel after PR 1 merges.
- Parallel: T004–T010 (different files); test tasks within each story; T038/T039.

## Implementation Strategy

MVP = PR 1 (lifecycle). Each PR is independently deployable and SonarCloud-green. MES-9 unblocked after PR 3.
