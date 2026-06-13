# Tasks: Labour Resources & Skills

**Input**: Design documents from `/specs/011-labour-resources-skills/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/labour-api.md

**Tests**: REQUIRED — Constitution §II (TDD): test tasks precede implementation in every story.

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup (scaffold) + US1 (employees) + US2 (skills) | T001–T025 | `./gradlew :services:labour-service:check` | Scaffold bundled with first two aggregates' ITs as coverage anchor; settings.gradle/compose/gateway/sonar updates |
| PR 2 | US3 (certifications + bulk qualification API) | T026–T035 | `./gradlew :services:labour-service:check` | State-derivation unit tests + gating ITs; cross-service contract for MES-10 |
| PR 3 | US4 (training records) | T036–T041 | `./gradlew :services:labour-service:check` | Independent of PR 2 except scaffold |
| PR 4 | US5 (frontend Labour area + expiry dashboard) + compliance | T042–T054 | `npm run lint && npm run test` + gateway smoke | ModuleKey additions ride along; ERR-MES-059/078 checks |

**Sequencing note**: PR 2 must merge before MES-10's skill-gating integration verification. PR 3 can be raised in parallel with PR 2 (different aggregates) but after PR 1 merges.

---

## Phase 1: Setup — labour-service scaffold [PR 1]

- [ ] T001 Add `include 'services:labour-service'` to settings.gradle and create services/labour-service/build.gradle mirroring engineering-service (deps: lib-common-security, lib-common-audit, mes-udf-lib, hypersistence-utils; test {} with api.version=1.41 + DOCKER_HOST per ERR-MES-036/037)
- [ ] T002 Create LabourServiceApplication.java + application.yml (port 8098, schema labour, KC resource-server, expiry-warning-days config) in services/labour-service/src/main/resources
- [ ] T003 Create config package: AppConfig.java (AuditorAware<String> bean — ERR-MES-062), SecurityConfig.java, LabourSecurityProperties.java in services/labour-service/src/main/java/com/mes/labour/config/
- [ ] T004 [P] Create api/GlobalExceptionHandler.java (standard shapes incl. Udf handlers) in services/labour-service/src/main/java/com/mes/labour/api/
- [ ] T005 [P] Create audit package (LabourRevisionEntity + listener) in services/labour-service/src/main/java/com/mes/labour/audit/
- [ ] T006 [P] Create Flyway migrations V001__create_labour_schema.sql, V002__create_employee_skill_certification_training.sql, V003__add_envers_tables.sql (revend/revend_tstmp; partial unique iam_user_id index; indexes per data-model.md) in services/labour-service/src/main/resources/db/migration/
- [ ] T007 [P] Create Dockerfile + labour-service block in docker/compose-infra.yml (port 8098, LABOUR_DB_URL, healthcheck) + LABOUR_SERVICE_URL env on gateway
- [ ] T008 [P] Add gateway route `/api/v1/labour/**` → labour-service:8098 in services/gateway-service/src/main/resources/application.yml
- [ ] T009 [P] Add labour-service paths to sonar.sources and sonar.tests in sonar-project.properties
- [ ] T010 [P] Add EMPLOYEE, SKILL, CERTIFICATION to libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java
- [ ] T011 Register privilege manifest (labour:employee/skill/certification/training/qualification keys per contract) in services/labour-service/src/main/java/com/mes/labour/config/
- [ ] T012 Create BaseIntegrationTest (Testcontainers PG + KC, locally-signed JWTs per ERR-MES-024, jwk-set-uri per ERR-MES-068) in services/labour-service/src/test/java/com/mes/labour/integration/

**Checkpoint**: `./gradlew :services:labour-service:check` compiles, context loads, Flyway applies.

---

## Phase 2: User Story 1 — Employees (P1) [PR 1] 🎯 MVP

**Goal**: Employee register with org-scoped uniqueness, IAM link, activate/deactivate, audited.

**Independent Test**: Create employee → list/search → edit → deactivate; duplicate number 409; lookup by IAM user id.

### Tests (write first, confirm failing)

- [ ] T013 [P] [US1] IT: create 201 + audit row; duplicate employee_number 409; duplicate iam_user_id 409 in services/labour-service/src/test/java/com/mes/labour/integration/employee/EmployeeControllerIT.java
- [ ] T014 [P] [US1] IT: list paged + search (number/name/email) + status filter; get by id; PATCH fields incl. status + iam link set/clear (same class)
- [ ] T015 [P] [US1] IT: by-iam-user lookup 200/404; unauthenticated 401; no privilege 403; cross-org isolation 404 (same class)

### Implementation

- [ ] T016 [P] [US1] Create Employee entity + EmploymentStatus enum + repository per data-model.md in services/labour-service/src/main/java/com/mes/labour/employee/domain/ and .../repository/
- [ ] T017 [US1] Implement EmployeeService (CRUD, uniqueness guards, iam-link uniqueness, status transitions) in .../employee/service/
- [ ] T018 [US1] Create DTOs + mapper + EmployeeController per contract (excluding /profile and /training endpoints — those land with US3/US4 whose data they aggregate) in .../employee/api/
- [ ] T019 [US1] Run `./gradlew :services:labour-service:check`; log/fix defects

**Checkpoint**: Employees CRUD complete.

---

## Phase 3: User Story 2 — Skill catalogue (P1) [PR 1]

**Goal**: Org-scoped skill catalogue with validity periods, deactivation semantics, stable consumer read contract.

**Independent Test**: Create skill with validity → list/search → deactivate → new certification blocked (verified in US3), bulk fetch by ids works.

### Tests (write first, confirm failing)

- [ ] T020 [P] [US2] IT: create 201; duplicate skill_code 409; PATCH incl. deactivate; list with ?ids= bulk and ?active= filter in services/labour-service/src/test/java/com/mes/labour/integration/skill/SkillControllerIT.java
- [ ] T021 [P] [US2] IT: minimal stable DTO shape on GET /skills/{id} (id, code, name, active, certificationRequired); org isolation (same class)

### Implementation

- [ ] T022 [P] [US2] Create Skill entity + repository in .../skill/domain/ and .../repository/
- [ ] T023 [US2] Implement SkillService + DTOs + SkillController per contract in .../skill/
- [ ] T024 [US2] Run `./gradlew :services:labour-service:check`; log/fix defects
- [ ] T025 [US2] Pre-PR retrospective spot-check (ERR-MES-001 categories: Flyway, Envers, JPA auditing, privilege registration) and fix any violations

**Checkpoint**: Employees + Skills complete with scaffold — coverage anchor satisfied.

> **Raise PR 1 after this checkpoint** (T001–T025) | CI: `./gradlew :services:labour-service:check` | Target: `Develop`

---

## Phase 4: User Story 3 — Certifications + qualification evaluation (P1) [PR 2]

**Goal**: Award/revoke certifications, derived state (ACTIVE/EXPIRING_SOON/EXPIRED/REVOKED), bulk qualification API for MES-10/MES-9.

**Independent Test**: Award with past expiry → EXPIRED; within window → EXPIRING_SOON; revoke → gating false; bulk evaluate returns per-skill statuses.

### Tests (write first, confirm failing)

- [ ] T026 [P] [US3] Unit: CertificationStateCalculator boundary dates (expiry today, window edge, null expiry = never-expires, revoked precedence) in services/labour-service/src/test/java/com/mes/labour/certification/service/CertificationStateCalculatorTest.java
- [ ] T027 [P] [US3] Unit: governing-certification selection (latest expiry wins, never-expires ranked highest, award_date tiebreak per ERR-MES-082 analogue) in GoverningCertificationTest.java
- [ ] T028 [P] [US3] IT: award 201 with defaulted expiry (award + validityMonths); duplicate (emp,skill,awardDate) 409; inactive skill 422; inactive employee 422 in services/labour-service/src/test/java/com/mes/labour/integration/certification/CertificationControllerIT.java
- [ ] T029 [P] [US3] IT: revoke with reason → state REVOKED + audit; revoke without reason 422; renewal keeps history, newest governs (same class)
- [ ] T030 [P] [US3] IT: evaluate — HELD_ACTIVE / EXPIRING_SOON (qualifies) / EXPIRED / REVOKED / NOT_HELD / SKILL_INACTIVE cases; INACTIVE employee employeeActive=false; by iamUserId; empty skillIds → empty results; exactly-one-of employeeId/iamUserId 400 in QualificationEvaluationIT.java

### Implementation

- [ ] T031 [P] [US3] Create Certification entity + repository (incl. governing-cert query) in .../certification/domain/ and .../repository/
- [ ] T032 [US3] Implement CertificationStateCalculator (final utility — ERR-MES-070) + CertificationService (award with expiry defaulting, revoke) in .../certification/service/
- [ ] T033 [US3] Implement QualificationService (bulk evaluate, single query no N+1) + evaluate endpoint + DTOs in .../certification/
- [ ] T034 [US3] Add certification endpoints (list with state/expiringWithinDays filters, get with state) + CertificationController, plus employee competency-profile endpoint GET /employees/{id}/profile (aggregates certifications + states; IT added here) in .../certification/api/ and .../employee/api/
- [ ] T035 [US3] Run `./gradlew :services:labour-service:check`; log/fix defects

**Checkpoint**: Gating contract live for MES-10.

> **Raise PR 2 after this checkpoint** (T026–T035) | CI: `./gradlew :services:labour-service:check` | Target: `Develop`

---

## Phase 5: User Story 4 — Training records (P2) [PR 3]

**Goal**: Training events with attendees, outcomes, skill links; evidence trail per employee.

**Independent Test**: Record event for two employees against a skill; both appear in employees' training history; certification shows supporting training.

### Tests (write first, confirm failing)

- [ ] T036 [P] [US4] IT: create event with attendees + skillIds → one attendance row per employee; employee training history endpoint; outcome change audited (before/after in _aud) in services/labour-service/src/test/java/com/mes/labour/integration/training/TrainingEventIT.java
- [ ] T037 [P] [US4] IT: certification detail lists supporting training records (employee + linked skill match) (same class)

### Implementation

- [ ] T038 [P] [US4] Create TrainingEvent, TrainingAttendance entities + join table + repositories per data-model.md in .../training/domain/ and .../repository/
- [ ] T039 [US4] Implement TrainingService + DTOs + TrainingController per contract, incl. employee training-history endpoint GET /employees/{id}/training in .../training/
- [ ] T040 [US4] Wire supporting-training lookup into certification detail DTO in .../certification/service/
- [ ] T041 [US4] Run `./gradlew :services:labour-service:check`; log/fix defects

**Checkpoint**: Training evidence trail complete.

> **Raise PR 3 after this checkpoint** (T036–T041) | CI: `./gradlew :services:labour-service:check` | Target: `Develop`

---

## Phase 6: User Story 5 — Frontend Labour area (P2) [PR 4]

**Goal**: Labour sidebar area: Employees (list/detail with competency profile + training history), Skills, Certifications with expiry-window dashboard filter.

**Independent Test**: Create employee/skill/certification through UI; profile shows state badges; expiry filter lists certifications in window.

### Tests

- [ ] T042 [P] [US5] Vitest: employee-list component (load/search/column picker UDF merge) in frontend/angular/src/app/features/labour/pages/employee-list/employee-list.component.spec.ts
- [ ] T043 [P] [US5] Vitest: certification-list expiry filter + state badge rendering in .../pages/certification-list/certification-list.component.spec.ts

### Implementation

- [ ] T044 [P] [US5] Create LabourApiService + DTO interfaces (customFields included) in frontend/angular/src/app/features/labour/services/labour-api.service.ts
- [ ] T045 [US5] Create employee-list + employee-detail pages (competency profile with state badges, training history tab; ERR-MES-059 cdr rules, ERR-MES-078 UDF columns) in .../pages/
- [ ] T046 [US5] Create skill-list + skill-detail pages in .../pages/
- [ ] T047 [US5] Create certification-list page with expiringWithinDays filter + award-certification-dialog component in .../pages/ and .../components/
- [ ] T048 [US5] Add routes + sidebar Labour area entries in frontend/angular/src/app/app.routes.ts and sidebar component
- [ ] T049 [US5] `npm run lint && npm run test` green; full-stack gateway smoke; log/fix defects

**Checkpoint**: Labour module usable end-to-end.

---

## Phase 7: Compliance Verification & Defect Closure [PR 4 continued]

- [ ] T050 Verify Constitution Check gates in plan.md all ✅ (human review gate III recorded)
- [ ] T051 [P] Confirm Envers audit rows for all mutations incl. revocation and outcome changes (IT asserts on _aud)
- [ ] T052 [P] Confirm org_id scoping on every repository query; cross-org ITs green
- [ ] T053 [P] Confirm zero gating false-positives test matrix complete (SC-005) and qualification latency < 500 ms on 20-skill evaluate (timed IT)
- [ ] T054 Confirm all logged defects closed; run quickstart.md end-to-end; pre-PR retrospective vs MES-ERR-001 index

> **Raise PR 4 after this checkpoint** (T042–T054) | CI: `npm run lint && npm run test` + `./gradlew :services:labour-service:check` | Target: `Develop`

---

## Dependencies & Execution Order

- Phase 1 blocks all. US1 and US2 are independent of each other (parallel after scaffold) but share PR 1.
- US3 depends on US1 + US2 (FKs to employee + skill).
- US4 depends on US1 (employees); skill links optional → after US2.
- US5 depends on all backend stories.
- Parallel: T004–T010; test tasks per story; T016/T022; T031/T038.

## Implementation Strategy

MVP = PR 1 (employees + skills). PR 2 delivers the cross-epic gating contract — prioritise it immediately after PR 1 so MES-10 can integrate.
