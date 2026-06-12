# Implementation Plan: Quality Inspection Planning (Control Plans)

**Branch**: `012-quality-inspection-planning` | **Date**: 2026-06-12 | **Spec**: specs/012-quality-inspection-planning/spec.md

**Input**: Feature specification from `/specs/012-quality-inspection-planning/spec.md`

## Summary

Scaffold a new `quality-service` microservice (planning domain) providing revision-controlled inspection plans (control plans) per item master: a plan header + characteristics of three types (SPECIFIC numeric-toleranced, COMMON boolean per-lot, CALCULATED expression-based), with the proven DRAFT → PENDING_APPROVAL → APPROVED lifecycle, an expression validator (reference + cycle checking), a consumer read API for MES-9 route creation and work-order release gating, and a Kafka approval event. Frontend adds Quality > Inspection Plans (list + detail/authoring mirroring BOM authoring UX). Schema `quality`, port 8099.

## Technical Context

**Language/Version**: Java 21 (Temurin), Spring Boot 3.3; Angular 21 + TypeScript 5.x

**Primary Dependencies**: Spring Data JPA + Hibernate 6 + Envers, Flyway, Spring Security resource server (Keycloak), mes-udf-lib, lib-common-security, hypersistence-utils (JSONB), Kafka (JsonSerializer — ERR-MES-063)

**Storage**: PostgreSQL 16, new schema `quality` (service-owned per §XI)

**Testing**: JUnit 5 + Testcontainers ITs extending BaseIntegrationTest; unit tests for the expression parser/validator (reference resolution, cycle detection, limit validation); Vitest frontend

**Target Platform**: Docker Compose; gateway route `/api/v1/inspection-plans/**` → quality-service:8099

**Project Type**: Web application (new Spring Boot microservice + Angular SPA screens)

**Performance Goals**: Approved-plan consumer API < 500 ms for 50 characteristics (SC-005)

**Constraints**: One plan per item per org; approved revisions immutable; expression grammar restricted (no code execution); zero invalid expressions reach APPROVED (validated at save AND submit)

**Scale/Scope**: 3 entities + expression value object, ~16 REST endpoints, 2 Angular screens + characteristic type-specific forms, full service scaffold

## Constitution Check

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — specs/012-quality-inspection-planning/spec.md (Jira MES-12) |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — retrospective gate task |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ⏳ Pending owner review (clarify stage) |
| Does the spec include a "Compliance References" section? | IV — Compliance | ✅ PASS — AS9100D §8.6, AS9103, AS9145, QIF, AS9102 partial |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance | ✅ PASS |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — Envers all entities + `_aud`; approval metadata on revision rows |
| Do new data models map to ISA-95 Part 2 object models? | VI — ISA-95/ISA-88 | ✅ PASS — characteristics map to ISA-95 test specifications within Operations Definition |
| Is authentication delegated to Keycloak? | VII — Security-First | ✅ PASS — standard resource-server config |
| Is all data scoped by `organisation_id`? | IX — Multi-Org | ✅ PASS |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration | ✅ PASS — Bean Validation; Kafka approval event idempotent by (planId, revision) |
| Are shop floor timestamps from source? | X — Data Accuracy | N/A — planning only; gauge calibration gating is execution-time (deferred DEF-002) |
| Does the service own only its declared domain APIs? | XI — Service Boundary | ✅ PASS — quality-service owns Quality & Inspection domain (constitution table); explicit gateway predicate; own schema/container; item data referenced by id only, no cross-schema queries |

## Project Structure

### Documentation (this feature)

```text
specs/012-quality-inspection-planning/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── inspection-plans-api.md
└── tasks.md   (by /speckit-tasks)
```

### Source Code (repository root)

```text
services/quality-service/                     # NEW service
├── build.gradle
├── Dockerfile
├── src/main/java/com/mes/quality/
│   ├── QualityServiceApplication.java
│   ├── config/                               # AppConfig (AuditorAware), SecurityConfig, properties,
│   │                                         # privilege manifest registration
│   ├── api/GlobalExceptionHandler.java
│   ├── audit/                                # Envers revision entity + listener
│   ├── inspectionplan/
│   │   ├── api/                              # InspectionPlanController, ConsumerController
│   │   │   └── dto/
│   │   ├── domain/                           # InspectionPlan, InspectionPlanRevision,
│   │   │                                     # InspectionCharacteristic, CharacteristicType,
│   │   │                                     # CharacteristicSource, RecordingBasis, SampleSizeRule
│   │   ├── repository/
│   │   ├── service/                          # InspectionPlanService, ExpressionValidator,
│   │   │                                     # CharacteristicService
│   │   └── client/                           # InventoryServiceClient (item existence/denorm)
│   └── kafka/                                # QualityEventPublisher (plan approved)
├── src/main/resources/
│   ├── application.yml                       # port 8099, schema quality
│   └── db/migration/
│       ├── V001__create_quality_schema.sql
│       ├── V002__create_inspection_plan_tables.sql
│       └── V003__add_envers_tables.sql
└── src/test/java/com/mes/quality/...

settings.gradle                                # + include 'services:quality-service'
docker/compose-infra.yml                       # + quality-service:8099 + QUALITY_SERVICE_URL on gateway
services/gateway-service/.../application.yml   # + /api/v1/inspection-plans/** route
sonar-project.properties                       # + quality-service paths
libs/mes-udf-lib/.../ModuleKey.java            # + INSPECTION_PLAN, INSPECTION_CHARACTERISTIC

frontend/angular/src/app/features/inspection-plans/
├── pages/inspection-plan-list/
├── pages/inspection-plan-detail/              # header + revision selector/history + characteristics grid
├── components/characteristic-editor/          # type-specific forms (SPECIFIC/COMMON/CALCULATED)
└── services/inspection-plan-api.service.ts    # sidebar: Quality > Inspection Plans
```

**Structure Decision**: New microservice for the Quality & Inspection domain (constitution service table). Revision lifecycle copied from the BOM pattern (root + revision + child rows, auto-draft on edit of approved, copy-on-revision). The expression engine is a small hand-rolled recursive-descent parser over a restricted grammar — a `final` utility class with exhaustive unit tests (ERR-MES-070 pattern), no external scripting library.

## Complexity Tracking

No violations. The expression parser is deliberately minimal (4 operators + parentheses + refs); adopting a scripting engine (e.g. SpEL/MVEL) was rejected on security grounds (arbitrary code execution risk on a quality record path).

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup (scaffold) + US1 (plan header + revision lifecycle) | T001–T03x | `./gradlew :services:quality-service:check` | Scaffold bundled with US1 ITs as coverage anchor; includes settings.gradle/compose/gateway/sonar updates |
| PR 2 | US2 (characteristics incl. expression validator) | T04x | `./gradlew :services:quality-service:check` | Parser unit tests (cycle/reference/limit cases) + characteristic ITs |
| PR 3 | US3 (consumer API + approval gate + Kafka event) | T05x | `./gradlew :services:quality-service:check` | Contract consumed by MES-9; submit-blocks (zero characteristics, invalid refs) tested here |
| PR 4 | US4 (frontend Quality > Inspection Plans) | T06x–T07x | `npm run lint && npm run test` + gateway smoke | ModuleKey + sidebar additions; ERR-MES-059/078 checks |

**Sequencing note**: PR 2 depends on PR 1 (characteristics live on revisions). PR 3 depends on PR 2 (submit validation needs the validator). MES-9 consumes the PR 3 contract; it is the last hard prerequisite for starting MES-9.
