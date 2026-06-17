# Tasks: Manufacturing Routing

**Input**: Design documents from `specs/009-manufacturing-routing/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/
**Tests**: REQUIRED — Constitution §II mandates TDD (tests written first and confirmed failing before implementation; every FR has ≥1 automated test). Manufacturing-data-path code requires Testcontainers integration tests against a real PostgreSQL (no mocked persistence).

**Organization**: By user story (US1–US11), in priority order. Each phase is annotated with its PR (see PR Strategy). Backend service: `services/routing-service` (package `com.mes.routing`, schema `routing`, port 8100). Frontend: `frontend/angular/src/app/features/routing`.

## Format: `[ID] [P?] [Story] Description`
- **[P]**: parallelizable (different files, no incomplete dependencies)
- **[Story]**: user story label (US1–US11); Setup/Foundational/Polish have none

---

## PR Strategy

Single feature branch `009-manufacturing-routing`; PRs target `Develop`. Backend CI anchor: `DOCKER_HOST='npipe:////./pipe/docker_engine' ./gradlew :services:routing-service:check`. Frontend anchor: `npm run lint && npm run build && npm test` (ERR-MES-089).

| PR | Phases / User Stories | CI Anchor | Notes |
|---|---|---|---|
| PR 1 | Setup + Foundational + Settings reference data (US10 backend) | `:services:routing-service:check` | Foundational; new module ships with tests (SonarCloud coverage). Compose DB user in postgres env block (ERR-MES-085). Constitution amendment merged here. |
| PR 2 | Route header + types (US1) + Normal operations (US2) | `:services:routing-service:check` | Depends on PR 1. Core MVP route. |
| PR 3 | Operation resources/standards/consumption (US3) | `:services:routing-service:check` | Depends on PR 2. |
| PR 4 | Advanced operation types (US4) + groups (US5) + steps (US6) | `:services:routing-service:check` | Depends on PR 2/3. |
| PR 5 | Approval + significant-process approvers (US7) + two-tier revisioning + audit (US8) | `:services:routing-service:check` | Depends on PR 2–4. Reuses engineering-service e-sign client; emits `routing.route.approved`. |
| PR 6 | Frontend: route list/header + **grid editor** (US1–US6) + Settings UI (US10) | frontend anchor | Depends on backend PR 1–4. Grid editor = MVP authoring surface. |
| PR 7 | Frontend: **graphical editor** (US11) | frontend anchor | Depends on PR 6. Native Angular flow library. |
| PR 8 | Labour-plan export (US9) backend + frontend | both anchors | P3; depends on PR 3/5. Last. |

**Sequencing note**: US11 (PR 7, P2) is sequenced after the grid editor (PR 6) because both bind to the same route model. US9 (PR 8, P3) is last. PR 7 and PR 8 are the natural deferral points without blocking a usable MVP (PR 1–6).

---

## Phase 1: Setup (Shared Infrastructure) [PR 1]

**Purpose**: Scaffold the new `routing-service` and wire infra.

- [ ] T001 Create `services/routing-service/build.gradle` mirroring engineering-service (lib-common-security, lib-common-audit, mes-udf-lib, web/data-jpa/validation/actuator, flyway, spring-kafka, postgresql, hypersistence-utils, jackson jsr310, springdoc; test: spring-boot-starter-test, testcontainers junit/postgresql/kafka, spring-kafka-test, archunit, lib-common-security testFixtures)
- [ ] T002 Register `:services:routing-service` in root `settings.gradle`
- [ ] T003 [P] Create `RoutingServiceApplication` + base `application.yml` (port 8100, schema `routing`, Flyway, JPA validate, Envers config, Kafka consumer/producer, KC resource-server) in `services/routing-service/src/main/...`
- [ ] T004 [P] Add `sonar.sources`/`sonar.tests` entries for `services/routing-service` to `sonar-project.properties` (CLAUDE.md checklist)
- [ ] T005 Add `processTestResources` copy of iam-service migrations to `iam-bootstrap/` (privilege table priming, pattern from engineering-service)
- [ ] T006 [P] Add `routing-service` to `docker/compose-infra.yml` (port 8100, depends_on postgres/kafka/keycloak healthy — NOT admin-service per ERR-MES-086) and `ROUTING_DB_USER`/`ROUTING_DB_PASSWORD` in the **postgres** service env block + `.env` + `.env.example` (ERR-MES-085)

---

## Phase 2: Foundational (Blocking Prerequisites) [PR 1]

**⚠️ CRITICAL**: Must complete before any user story.

- [ ] T007 Create `V001__routing_baseline.sql` Flyway migration: `routing` schema + audit columns convention + Envers `_aud`/`revinfo` tables (ERR-MES-061); reference-data tables (work_centre, labour_code, labour_plan_type, route_type) with NOT NULL audit columns seeded `'migration'`/`NOW()` (ERR-MES-077)
- [ ] T008 [P] `BaseIntegrationTest` (Testcontainers PostgreSQL + EmbeddedKafka + KC test JWT decoder + PrivilegeCache stub) in `src/test/.../integration/` — extend BaseIntegrationTest directly (ERR-MES-080); jwk-set-uri to Testcontainers issuer, never issuer-uri="" (ERR-MES-068)
- [ ] T009 [P] `AppConfig` with `AuditorAware<String>` bean (ERR-MES-062) and OpenAPI config
- [ ] T010 `SecurityConfig` (resource server, exclude shared `MESSecurityAutoConfiguration` per ERR-MES-038, no `@ConditionalOnMissingBean` in @Import config per ERR-MES-069)
- [ ] T011 Privilege manifest registration on `ApplicationReadyEvent` for `routing:route:view|manage|approve`, `routing:operation:approve`, `routing:settings:view|manage` (auto-grants SYSTEM_ADMIN, ERR-MES-075)
- [ ] T012 [P] `GlobalExceptionHandler` + `RoutingConflictException`/`RoutingNotFoundException` (no inline catch in controllers, ERR-MES-073)
- [ ] T013 Add gateway routes `Path=/api/v1/routes/**` and `Path=/api/v1/routing/**` → `ROUTING_SERVICE_URL` in `services/gateway-service/src/main/resources/application.yml` (domain-specific predicates, §XI)

**Checkpoint**: Service boots, migrates, authenticates.

---

## Phase 3: User Story 10 - Routing reference data / Settings submodule (Priority: P2) [PR 1]

**Goal**: CRUD master data (work centres/machines, labour codes, labour plan types, route types) that routes reference.
**Independent Test**: Create a work centre, labour code and alternate route type; confirm selectable; seeded Standard + in-use entries protected from deletion; org-scoped.

### Tests (write first, confirm failing)
- [ ] T014 [P] [US10] IT `ReferenceDataIT` — CRUD + org-scope + seeded-protected + in-use-delete-409 for work-centres/labour-codes/labour-plan-types/route-types/**significant-process-types**/**suppliers** in `src/test/.../integration/`
- [ ] T015 [P] [US10] Unit tests for reference-data services (validation, deactivate-vs-delete, exactly-one isStandard, significant-process requiredApproverRole) in `src/test/.../referencedata/`

### Implementation
- [ ] T016 [P] [US10] Entities WorkCentre, LabourCode, LabourPlanType, RouteType, **SignificantProcessType (code, name, requiredApproverRole)**, **Supplier (code, name)** (`@Audited`, org-scoped) in `referencedata/domain/`
- [ ] T017 [P] [US10] Repositories (org-scoped finders) in `referencedata/repository/`
- [ ] T018 [US10] `ReferenceDataService` (CRUD, uniqueness, deactivate-on-in-use, seeded protection; exactly-one isStandard; significant-process requiredApproverRole) in `referencedata/service/`
- [ ] T019 [US10] DTOs + `ReferenceDataController` (`/api/v1/routing/{work-centres|labour-codes|labour-plan-types|route-types|significant-process-types|suppliers}`) in `referencedata/api/`
- [ ] T020 [US10] Seed migration rows: route_type Standard (protected), labour_plan_type Machine/People/OSP in V001/V002

**Checkpoint**: Reference data manageable via API; PR 1 complete.

> **Raise PR 1 after this checkpoint** (T001–T020) | CI: `DOCKER_HOST='npipe:////./pipe/docker_engine' ./gradlew :services:routing-service:check` | Target: `Develop`

---

## Phase 4: User Story 1 - Route header + types (Priority: P1) 🎯 MVP [PR 2]

**Goal**: Create/manage route headers linked to part/rev, route type, BOM rev, inspection-plan rev, org; one-Standard-per-part/rev.
**Independent Test**: Create a Standard route; second Standard for same part/rev → 409; alternate type accepted; org-isolated.

### Tests (write first, confirm failing)
- [ ] T021 [P] [US1] IT `RouteHeaderIT` — create/list/get/patch, one-Standard-per-part/rev 409, alternate accepted, org isolation, reason-for-revision required
- [ ] T022 [P] [US1] Unit tests `RouteService` header logic

### Implementation
- [ ] T023 [P] [US1] `Route` entity (status, revision, pinned bomRevisionId/inspectionPlanRevisionId, routeTypeId, customFields) in `route/domain/`
- [ ] T024 [P] [US1] `RouteRepository` (org-scoped; existsStandardForPart) in `route/repository/`
- [ ] T025 [US1] `RouteService.create/get/list/patch` with one-Standard constraint + pinned-revision rules in `route/service/`
- [ ] T026 [US1] DTOs + `RouteController` (`POST/GET/PATCH /api/v1/routes`, `/revisions`, `/draft`); GET returns `bomRevisionSuperseded`/`inspectionPlanRevisionSuperseded` flags in `route/api/`
- [ ] T026a [P] [US1] IT + consumer: upstream `*.revision.superseded` Kafka consumer (idempotent) sets the superseded flags on affected routes without auto-updating the pinned revision (FR-004f) in `kafka/` (EmbeddedKafka test)

**Checkpoint**: Draft routes create/list/get with type constraints; stale-reference indicator driven by the supersede consumer.

---

## Phase 5: User Story 2 - Normal operations & sequencing (Priority: P1) [PR 2]

**Goal**: Add/sequence Normal operations (operation number unique, sequence number).
**Independent Test**: Add ops 10/20/30; duplicate operation number rejected; reorder/renumber/delete in draft.

### Tests (write first)
- [ ] T027 [P] [US2] IT `RouteOperationIT` — add/list/reorder/delete, duplicate-operation-number 409, derived-type ordering
- [ ] T028 [P] [US2] Unit tests operation sequencing/derived-type logic

### Implementation
- [ ] T029 [P] [US2] `RouteOperation` entity (operationNumber, sequenceNumber, optional/osp toggles, `significantProcessTypeId?`, `supplierId?` (OSP), groupId, clocking, operationRevision) in `route/domain/`
- [ ] T030 [US2] `RouteOperationService` add/edit/resequence/delete + derived **Normal**/Parallel computation; operation DTO exposes derived type + ME-set membership (FR-009b) in `route/service/`
- [ ] T031 [US2] Operation endpoints under `RouteController` (`/operations`) in `route/api/`

**Checkpoint**: Routes hold ordered Normal operations.

> **Raise PR 2 after this checkpoint** (T021–T031) | CI: `:services:routing-service:check` | Target: `Develop`

---

## Phase 6: User Story 3 - Operation resources, standards & consumption (Priority: P1) [PR 3]

**Goal**: Attach resources, labour standards, material consumption, quality vars, tooling, skills, work instruction, STEP-file, significant-process flag.
**Independent Test**: Full profile persists; references validated against owning services/Settings master.

### Tests (write first)
- [ ] T032 [P] [US3] IT `OperationDetailIT` — labour plan (Setup/Run/Inspection/Transport × basis), material mandatory, skill ref, work-centre ref, significant-process flag
- [ ] T033 [P] [US3] Unit tests labour-plan/basis + reference-validation logic

### Implementation
- [ ] T034 [P] [US3] Entities OperationResource, LabourPlanLine (`labourActivityType` ∈ Setup/Run/Inspection/Transport + `labourPlanTypeId` Machine/People/OSP + basis), MaterialConsumption, QualityVariableRequirement, ToolingRequirement, SkillRequirement, WorkInstructionLink, StepFileReference in `route/domain/`
- [ ] T035 [P] [US3] Repositories for the above in `route/repository/`
- [ ] T036 [US3] `OperationDetailService` (CRUD per detail type; validate workCentreId/labourCodeId/significantProcessTypeId/supplierId against Settings; validate bomLineId/inspectionCharacteristicId/skillId/workInstructionId via owning-service read clients; OSP classification when an OSP-type labour plan line is present) in `route/service/`
- [ ] T037 [P] [US3] Read-client wrappers for inventory(BOM line)/quality(characteristic)/labour(skill)/engineering(work-instruction) in `route/client/` (no cross-schema queries, §XI)
- [ ] T038 [US3] Detail endpoints under `RouteController` (`/operations/{opId}/{resources|labour-plan|materials|quality-variables|tooling|skills|work-instruction|step-file}`)

**Checkpoint**: Operations are execution-ready.

> **Raise PR 3 after this checkpoint** (T032–T038) | CI: `:services:routing-service:check` | Target: `Develop`

---

## Phase 7: User Story 4 - Advanced operation types & flow control (Priority: P2) [PR 4]

**Goal**: Optional/OSP toggles; derived Parallel; Mutually-Exclusive subset selection within a parallel sequence.
**Independent Test**: Parallel derived from shared sequence; ME toggle only within parallel; subset {30,40} of {20,30,40} stored, 20 stays parallel; ME on unique sequence rejected.

### Tests (write first)
- [x] T039 [P] [US4] IT `OperationTypeIT` — derived parallel, ME-subset, ME-on-non-parallel 422, OSP requires an OSP labour resource (422 if missing), optional OSP supplier persisted
- [x] T040 [P] [US4] Unit tests `MutuallyExclusiveSet` membership rules

### Implementation
- [x] T041 [P] [US4] `MutuallyExclusiveSet` entity (level=OPERATION, sequenceNumber, memberIds) in `route/domain/`
- [x] T042 [US4] Extend `RouteOperationService`: ME subset validation (⊆ parallel set, ≥2 members); OSP requires an OSP-type labour resource, optional `supplierId` persisted (no ospSource; PO/event are MES-15, FR-009c) in `route/service/`
- [x] T043 [US4] `PUT /api/v1/routes/{id}/mutually-exclusive-sets` + OSP/optional toggle handling in `route/api/`

**Checkpoint**: Advanced operation flow control enforced.

---

## Phase 8: User Story 5 - Operation groups + group-level types (Priority: P2) [PR 4]

**Goal**: Group operations by group sequence number; group types Standard/Optional/Parallel/Mutually-Exclusive (same derived/toggle mechanics).
**Independent Test**: Group resequences as a unit; parallel group set derived; group-level ME subset; optional group skippable.

### Tests (write first)
- [x] T044 [P] [US5] IT `OperationGroupIT` — grouping, group resequence, parallel group set, group-level ME subset

### Implementation
- [x] T045 [P] [US5] `OperationGroup` entity (groupSequenceNumber, optional toggle) in `route/domain/`
- [x] T046 [US5] Group logic in `RouteOperationService` (group derive/types, MutuallyExclusiveSet level=GROUP) in `route/service/`
- [x] T047 [US5] `PUT /api/v1/routes/{id}/groups` in `route/api/`

**Checkpoint**: Groups behave as typed blocks.

---

## Phase 9: User Story 6 - Operation steps (Priority: P2) [PR 4]

**Goal**: Break operations into steps with the same type mechanics (step level).
**Independent Test**: Steps ordered by step sequence; parallel step set; step-level ME subset.

### Tests (write first)
- [x] T048 [P] [US6] IT `OperationStepIT` — step CRUD, derived parallel step set, step-level ME

### Implementation
- [x] T049 [P] [US6] `OperationStep` entity (stepNumber, stepSequenceNumber, optional) in `route/domain/`
- [x] T050 [US6] Step logic in service (derive/types, MutuallyExclusiveSet level=STEP) + `/operations/{opId}/steps` endpoints

**Checkpoint**: Steps mirror operation flow control.

> **Raise PR 4 after this checkpoint** (T039–T050) | CI: `:services:routing-service:check` | Target: `Develop`

---

## Phase 10: User Story 7 - Approval + significant-process approvers (Priority: P2) [PR 5]

**Goal**: Submit/approve/reject via e-signature; significant-process routes require additional SME approver(s); approved routes immutable.
**Independent Test**: Standard route approves with standard approver; significant-process route blocked until SME approver signs; editing approved route rejected.

### Tests (write first)
- [x] T051 [P] [US7] IT `RouteApprovalIT` — submit→approve happy path, significant-process gate (each distinct significant-process-type's requiredApproverRole satisfied once), edit-approved-rejected, `routing.route.approved` emitted (EmbeddedKafka)
- [x] T052 [P] [US7] Unit tests approval state machine + significant-process approver resolution (distinct types → required roles)

### Implementation
- [x] T053 [P] [US7] `ApprovalRecord` entity (subjectType, actor, actorRole, meaning, isSignificantProcessApprover, significantProcessTypeId?, immutable) in `approval/domain/`
- [x] T054 [P] [US7] KC e-signature verify client (pattern from engineering-service) in `approval/client/`
- [x] T055 [US7] `ApprovalService` (submit/approve/reject; significant-process resolution = distinct SignificantProcessTypes on route → each requiredApproverRole satisfied once by an e-signed approval from a role holder, plus standard approval) in `approval/service/`
- [x] T056 [US7] Approval endpoints (`/submit`, `/approve`, `/reject`) + `routing.route.approved` Kafka producer (JsonSerializer per ERR-MES-063) in `approval/api/` + `kafka/`

**Checkpoint**: Routes gated by e-signature.

---

## Phase 11: User Story 8 - Two-tier revisioning + audit (Priority: P2) [PR 5]

**Goal**: Route revision (structural) vs operation revision (content-only, operation-level approval); audit history groups operation revisions under governing route revision.
**Independent Test**: Route revision allows structural edits + route approval; operation revision locks sequence/grouping + operation approval; audit view re-anchors on next route approval.

### Tests (write first)
- [x] T057 [P] [US8] IT `RouteRevisionIT` — route revision structural edits + approval
- [x] T058 [P] [US8] IT `OperationRevisionIT` — content-only operation revision + operation-level approval + audit grouping/re-anchor

### Implementation
- [x] T059 [US8] Route-revision flow (`POST /routes/{id}/revisions`, governingRouteRevision pinning) in `route/service/`
- [x] T060 [US8] Operation-revision flow (`POST /operations/{opId}/revision`, `POST /operations/{opId}/approve`, lock sequence/grouping) in `route/service/` + `approval/service/`
- [x] T061 [US8] Audit-history endpoint grouping operation revisions under governing route revision (Envers query) in `route/api/`

**Checkpoint**: Two-tier revisioning with audit.

> **Raise PR 5 after this checkpoint** (T051–T061) | CI: `:services:routing-service:check` | Target: `Develop`

---

## Phase 12: User Story 6→1 Frontend - grid editor + Settings UI (Priority: P1/P2) [PR 6]

**Goal**: Angular routing feature area: route list, header form (route type, part search), tabular **grid editor** for operations/groups/steps with type indicators + ME-subset dialog, and Settings reference-data UI.
**Independent Test**: Author a route end-to-end in the grid (header, ops, groups, steps, types) and approve; manage reference data in Settings; build+test green.

### Tests (write first — Vitest; ERR-MES-082 keep specs in sync)
- [x] T062 [P] [US1] Spec for `route-list` + `routing-api.service` (list/create/part-search) in `features/routing/...spec.ts`
- [x] T063 [P] [US3] Spec for grid editor type-indicator + ME-subset dialog logic
- [x] T064 [P] [US10] Spec for reference-data Settings service/components

### Implementation
- [x] T065 [P] [US1] `routing.model.ts` (Route, Operation, Group, Step, ME-set, reference-data DTOs incl. `customFields`) in `features/routing/models/`
- [x] T066 [P] [US1] `routing-api.service.ts` + `reference-data-api.service.ts` in `features/routing/services/`
- [x] T067 [US1] `route-list` page (column picker + UDF load per ERR-MES-078; cdr.detectChanges per ERR-MES-059) in `features/routing/pages/route-list/`
- [x] T068 [US1] `route-detail` page: header form with route-type select + part-number autocomplete (pattern from inspection-plan/BOM) in `pages/route-detail/`
- [x] T069 [US3] `operation-grid-editor` component: add/edit/delete/group ops & steps; type indicator (derived **Normal**/Parallel + Optional/ME/OSP toggles); significant-process-type select; OSP supplier select; ME-subset selection dialog in `components/operation-grid-editor/`
- [x] T070 [US7] Approval actions (submit/approve/reject, significant-process approver UI) in route-detail
- [x] T071 [US10] Settings reference-data UI (work centres, labour codes, labour plan types, route types, significant-process types w/ approver role, suppliers) in `pages/settings/`
- [x] T072 [US1] Register routing routes + Settings submodule nav entry in app routes/shell

**Checkpoint**: Full routing authorable via grid + Settings.

> **Raise PR 6 after this checkpoint** (T062–T072) | CI: `npm run lint && npm run build && npm test` | Target: `Develop`

---

## Phase 13: User Story 11 - Graphical (Visio-style) editor (Priority: P2) [PR 7]

**Goal**: Visual graphical editor over the same route model with full CRUD + grouping; renders sequence/parallel/ME/groups/OSP/significant-process; round-trips with grid.
**Independent Test**: Author/edit in graphical view; structure matches grid; edits round-trip; no divergence (SC-008).

### Tests (write first)
- [ ] T073 [P] [US11] Spec for graph↔model binding (add/edit/delete/group reflects in shared signal state)

### Implementation
- [ ] T074 [US11] Evaluate & add native Angular flow library (confirm `@foblex/flow` MIT/maintenance/bundle/CD-compat per research R4; fallback ngx-graph); add dependency (pin per ERR-MES Angular version rules)
- [ ] T075 [US11] `operation-graph-editor` component bound to the same route signal model as the grid in `components/operation-graph-editor/`
- [ ] T076 [US11] Render sequence/parallel branches/ME sets/groups/OSP/significant-process consistent with type indicators (FR-009b)
- [ ] T077 [US11] Add/edit/delete/group from the graph + view-switch control on route-detail (no divergence)

**Checkpoint**: Dual-view authoring complete.

> **Raise PR 7 after this checkpoint** (T073–T077) | CI: `npm run lint && npm run build && npm test` | Target: `Develop`

---

## Phase 14: User Story 9 - Labour-plan export (Priority: P3) [PR 8]

**Goal**: Export an approved route's labour plan for external schedulers/ERP.
**Independent Test**: Export lists per-operation Setup/Run/Inspection/Transport × basis × labour plan type in a schema-defined format.

### Tests (write first)
- [ ] T078 [P] [US9] IT `LabourPlanExportIT` — export content/shape for an approved route
- [ ] T079 [P] [US9] Frontend spec for export action

### Implementation
- [ ] T080 [US9] `LabourPlanExportService` + `GET /api/v1/routes/{id}/labour-plan/export` (schema-defined JSON/CSV) in `export/`
- [ ] T081 [US9] Frontend export button + download in route-detail

**Checkpoint**: Labour plan exportable.

> **Raise PR 8 after this checkpoint** (T078–T081) | CI: both anchors | Target: `Develop`

---

## Phase 15: Polish & Cross-Cutting Concerns

- [ ] T082 [P] Update `docs/` / OpenAPI annotations for routing endpoints
- [ ] T083 [P] ArchUnit test enforcing package/dependency boundaries (no cross-schema, §XI)
- [ ] T084 Performance check: route load/save < 1s and graph render for ~200-operation route (SC perf)
- [ ] T085 Run `quickstart.md` end-to-end validation via gateway (port 8082, ERR-MES-067)

---

## Phase 16: Compliance Verification & Defect Closure (Mandatory — §IV/§II)

- [ ] T086 Verify all Constitution Check gates in plan.md are ✅ PASS (incl. constitution amendment merged)
- [ ] T087 [P] Confirm Envers audit entries exist for all routing data mutations + immutable ApprovalRecord (§V)
- [ ] T088 [P] Confirm `routing.route.approved` consumer-side idempotency + schema validation (replay test, §VIII)
- [ ] T089 [P] Confirm `organisation_id` scoping on every routing entity (no cross-org leakage, §IX)
- [ ] T090 Confirm all test/lint failures during development logged + resolved in MES-ERR-001 (no open defects, §II)
- [ ] T091 Confirm Keycloak privileges registered/auto-granted and frontend build+test CI gate green (ERR-MES-075/089)
- [ ] T092 Compliance spot-check: AS9100D §8.5.1, ISA-95 Part 2, ISA-88, AS9102/NIST mappings in spec addressed

---

## Dependencies & Execution Order

- **Setup (P1) → Foundational (P2) → US10 backend** = PR 1 (foundational; blocks all).
- **US1 → US2** (PR 2) depend on PR 1.
- **US3** (PR 3) depends on PR 2.
- **US4, US5, US6** (PR 4) depend on US2/US3.
- **US7, US8** (PR 5) depend on US2–US6.
- **Frontend grid + Settings** (PR 6) depends on backend PR 1–4 (PR 5 for approval UI).
- **Graphical editor** (PR 7) depends on PR 6 (shared model + grid stable).
- **Export** (PR 8) depends on PR 3/5.

### Within each story
Tests written and FAILING first → entities → repositories → services → endpoints → integration. Models marked [P] parallel; tests marked [P] parallel.

---

## Implementation Strategy

**MVP** = PR 1 + PR 2 + PR 3 + PR 6 (grid) = author and approve a complete Standard route with operations and resources via the grid editor. PR 4/5 add advanced flow control + revisioning; PR 7 (graphical) and PR 8 (export) are the deferral points if effort runs long.

## Notes
- Commit after each task or logical group; one task = one commit referencing MES-9 + `[Txxx]` (CLAUDE.md commit format).
- Tests MUST fail before implementation (§II). Test failures during dev → tracked defects, resolved within the feature.
- Backend on Windows: `DOCKER_HOST='npipe:////./pipe/docker_engine'` for Testcontainers (api.version 1.41).
- Per-PR: pre-PR checklist (sonar sources/tests, deployment steps, usage cost, retrospective spot-check).

---

## Phase 16: Post-MVP gap-closure & design refinements [PR 7+]

Captured from the design review (wireframes in Penpot, Aurora MES / Shell page). Sequenced after the dark+light wireframes are signed off. The route editor is being reworked to a **left operations sidebar + tabbed operation-detail** layout (Overview · Attributes · Resources · BOM · Documents · Properties · Variables · Tools · Skills).

### Route locking & concurrency (FR-031/032/033)
- [ ] T095 [P] Unit + IT: acquire/release lock, holder-only edit, force-unlock privilege, read-only for non-holders
- [ ] T096 `RouteLock` state on route (lockHolder, lockedAt) + acquire/release/force-unlock service; gate every mutating route/operation endpoint on lock ownership
- [ ] T097 Register `routing:route:unlock` privilege (auto-grant SYSTEM_ADMIN, ERR-MES-075) + audit force-unlock
- [x] T098 Frontend: header lock toggle wired (acquire/release), read-only rendering for non-holders, force-unlock action for privileged users

### Operation detail in the tabbed editor (gaps 1–5, 7)
- [x] T099 Rework `route-detail` into sidebar (add/duplicate/delete/search ops) + tabbed detail editor matching the wireframes
- [x] T100 Overview tab = inline-editable Seq · Op# · Description · Work Centre row (replace modal with in-grid add)
- [ ] T101 Attributes tab: behaviour, **Labour Type Direct/Indirect (FR-013a)**, Optional/OSP/Mutual-Excl. toggle switches, significant-process select
- [x] T102 Resources tab: labour-plan editor (Labour Plan Type × Setup/Run/Inspection/Transport + basis) + work centres — closes the OSP dead-end (gap 2)
- [x] T103 BOM / Documents / Tools / Skills tabs: material consumption, WI+STEP, tooling, skill requirements editors
- [x] T104 Variables tab: quality variables editor incl. per-characteristic **Req. Skill(s)** column
- [ ] T105 Operation-revision UI (start / content-edit / submit / approve) + approval-history view (gaps 3, 4); group/step-level ME in the ME dialog (gap 5)
- [x] T106 Route View fixes: part-number column (resolve partId→partNumber), create-revision action icon; Save Draft button + header edit

### Skills at three levels (data model)
- [ ] T107 Extend `SkillRequirement` to be linkable to operation **and** quality variable (`QualityVariableRequirement`) **and** tooling (`ToolingRequirement`); endpoints + ITs

### Cross-service validation (gap 6)
- [ ] T108 Validate external refs (`bomLineId`, `inspectionCharacteristicId`, `skillId`, `workInstructionId`) against inventory/quality/labour/engineering read APIs (outbound clients + WireMock) — scope to available read endpoints

### Custom properties consumer (FR-034)
- [ ] T109 Routing Properties tab consumes **scoped custom properties** (route type / part type) from the Custom Properties engine; degrade gracefully when none scoped. **Depends on the separate Custom Properties epic (below).**

> **Custom Properties engine = separate epic (wider product).** A UDF is global (every screen); a custom property is scoped to a route type, a part type, or variables within a specific inspection plan. This definition+scoping engine spans routing, item-master and inspection-plans and MUST be tracked as its own Jira epic + spec-kit spec — not inside MES-9. Action: raise the epic and run `/speckit-from-jira` for it; MES-9 only consumes it (T109).
