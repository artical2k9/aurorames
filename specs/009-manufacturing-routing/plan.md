# Implementation Plan: Manufacturing Routing

**Branch**: `009-manufacturing-routing` | **Date**: 2026-06-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/009-manufacturing-routing/spec.md` (Jira Epic MES-9)

## Summary

Manufacturing Routing defines how a part is made: a **route header** (linked to part/revision, route type, BOM revision, inspection-plan revision, organisation), an ordered set of **route operations** with full resource/standard/consumption profiles, optional **operation groups** and **operation steps**, two-tier **route + operation revisioning** with e-signature approval (including additional approvers for significant processes), and a **labour-plan export**. Routes are authored in two interchangeable front-end views (tabular grid + visual graphical) and are the prerequisite for Work Orders (MES-14) and Shop Floor Tracking (MES-15).

**Technical approach**: a new dedicated `routing-service` (ISA-95 Operations Definition domain) following the established Spring Boot 3.3 + JPA + Flyway + Hibernate Envers pattern, owning the `routing` PostgreSQL schema. It reuses the Keycloak e-signature approval client pattern from `engineering-service` (MES-10), consumes Item/BOM/inspection-plan references via REST (read) and Kafka events (for revision-supersede awareness), and emits a `routing.route.approved` event for downstream consumption. A new **routing Settings submodule** provides interim master data (work centres/machines, labour codes, labour plan types, route types, significant-process types with required approver role, and an interim supplier list for OSP) until the equipment-master (DEF-004) and supplier/OSP-procurement (DEF-002) epics. The Angular frontend adds a routing feature area with a grid editor and a graphical editor built on a native Angular flow library (React Flow used as a research reference only).

## Technical Context

**Language/Version**: Java 21 LTS (backend), TypeScript 5.x / Angular 21 (frontend)

**Primary Dependencies**: Spring Boot 3.3 (Web, Data JPA, Validation, Actuator), Hibernate 6 + Envers, Flyway, Spring Kafka, SpringDoc OpenAPI 3.1, `lib-common-security`, `lib-common-audit`, `mes-udf-lib`; Angular 21 standalone + Signals, PrimeNG, plus a native Angular flow/diagram library for the graphical view (candidate: `@foblex/flow` — confirmed in research.md)

**Storage**: PostgreSQL 16, dedicated `routing` schema (own Flyway history); JSONB for `custom_fields`

**Testing**: JUnit 5 + Mockito + Testcontainers (real PostgreSQL; EmbeddedKafka where events are involved), ArchUnit; Angular Vitest + ESLint; `./gradlew check` gate (Checkstyle + SpotBugs + unit/IT)

**Target Platform**: Linux server (Docker container), behind Spring Cloud Gateway; Angular SPA via Nginx

**Project Type**: Web application — Spring Boot microservice backend + Angular frontend feature area

**Performance Goals**: Interactive authoring (route load/save < 1s for routes up to ~200 operations); graphical-view render of a 200-operation route without UI stall

**Constraints**: Org-scoped data isolation (§IX); immutable audit via Envers (§V); e-signature approval (§IV/§X); §XI service-boundary integrity (routing is its own service/schema/container); Angular change-detection rules (ERR-MES-059); frontend build+test CI gate (ERR-MES-089)

**Scale/Scope**: 11 user stories; ~15 backend entities; ~5 controllers; one new microservice + one Angular feature area with two editors and a Settings submodule

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — spec.md reviewed/refined with owner across this session |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS (enforced by `/speckit-tasks`; PR Strategy anchors each story on a `check` gate) |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS (tasks template includes defect-registration; lint/test failures logged to MES-ERR-001) |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ⏳ PENDING — this plan is presented for owner approval before `/speckit-tasks` |
| Does the spec include a "Compliance References" section? | IV — Compliance by Design | ✅ PASS |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance by Design | ✅ PASS — AS9100D §8.5.1, ISA-95 Part 2, ISA-88, AS9102/NIST Partial |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — Hibernate Envers on all entities + e-signature approval records |
| Do new data models map to ISA-95 Part 2 object models (where applicable)? | VI — ISA-95/ISA-88 | ✅ PASS — route = Operations Definition / Process Segment; operations/steps mirror ISA-88 procedure→operation→step |
| Is authentication delegated to Keycloak (no bespoke auth)? | VII — Security-First | ✅ PASS — Spring Security resource server + `lib-common-security`; e-sign via KC |
| Is all data scoped by `organisation_id` with no cross-org leakage? | IX — Multi-Org Isolation | ✅ PASS — every entity org-scoped; `JwtClaimsExtractor.orgId()` |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration Integrity | ✅ PASS — Kafka consumers idempotent; Bean Validation on all DTOs; labour-plan export schema-defined |
| Are shop floor timestamps from source (not synthetic)? | X — Data Accuracy | N/A at authoring — routing defines metadata; execution timestamps belong to MES-15. Approval timestamps are server-recorded at the e-sign event |
| Does the new service own exactly one ISA-95 domain (no catch-all routes)? | XI — Service Boundary Integrity | ⚠ REQUIRES CONSTITUTION AMENDMENT — see Complexity Tracking. New `routing-service` (19th service) added to the service table; gateway uses domain-specific predicates `/api/v1/routes/**` and `/api/v1/routing/**` |

**Pre-Phase-0 result**: PASS, conditional on the constitution amendment adding `routing-service` (owner approved the dedicated-service decision during planning; amendment drafted in this plan — see Complexity Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/009-manufacturing-routing/
├── spec.md              # Approved specification
├── plan.md              # This file
├── research.md          # Phase 0 decisions
├── data-model.md        # Phase 1 entities
├── quickstart.md        # Phase 1 run/verify guide
├── contracts/           # Phase 1 API contracts
│   ├── routes-api.md
│   └── routing-reference-data-api.md
└── tasks.md             # Phase 2 (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
services/routing-service/                     # NEW microservice (port 8100, schema `routing`)
├── build.gradle                              # mirrors engineering-service deps
└── src/main/java/com/mes/routing/
    ├── config/                               # AppConfig (AuditorAware), security, OpenAPI
    ├── route/                                # route header + operations + groups + steps
    │   ├── api/ (+ dto/)
    │   ├── domain/
    │   ├── repository/
    │   └── service/
    ├── approval/                             # submit/approve/reject + significant-process approvers
    │   ├── api/ (+ dto/)
    │   ├── client/                           # KC e-signature client (pattern from engineering-service)
    │   ├── domain/
    │   └── service/
    ├── referencedata/                        # Settings submodule: work-centres, labour-codes,
    │   ├── api/ (+ dto/)                      #   labour-plan-types, route-types,
    │                                          #   significant-process-types, suppliers
    │   ├── domain/
    │   ├── repository/
    │   └── service/
    ├── export/                               # labour-plan export
    └── kafka/                                # consume bom/inspection-plan supersede; emit route.approved
└── src/main/resources/db/migration/          # V001__routing_baseline.sql ...

services/gateway-service/                      # add routes: /api/v1/routes/**, /api/v1/routing/**
docker/compose-infra.yml                       # add routing-service + routing DB user/schema
keycloak/mes-realm.json                        # (no new client — reuse mes-signature-verify e-sign client)

frontend/angular/src/app/features/routing/     # NEW Angular feature area
├── pages/
│   ├── route-list/
│   ├── route-detail/                          # header + dual-view editor host
│   └── settings/                              # routing reference-data submodule
├── components/
│   ├── operation-grid-editor/                 # tabular view (default)
│   ├── operation-graph-editor/                # visual graphical view (flow library)
│   └── ... (dialogs)
├── services/                                  # routing-api.service.ts, reference-data-api.service.ts
└── models/

libs/  (no new shared lib; reuse lib-common-security/audit, mes-udf-lib)
```

**Structure Decision**: New `routing-service` Gradle subproject mirroring `engineering-service` (the most recent service with the e-signature/approval and media patterns). One Angular feature area `features/routing` with the grid and graph editors over a single shared route model, plus a Settings submodule for reference data. Reference data and routes share the `routing` schema/service (same ISA-95 Operations-Definition domain — no §XI violation).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| New `routing-service` not in the constitution's 18-service table (§XI / Technology Stack) | Owner chose a dedicated service: routing (ISA-95 Operations Definition) is a distinct domain from Shop Floor Tracking (execution) and Work Instructions; bundling into `shopfloor-service` or `engineering-service` would violate §XI one-domain-per-service and recreate the catch-all problem that motivated MES-111 | Reusing `shopfloor-service` conflates definition with execution and contradicts §XI; reusing `engineering-service` contradicts the epic's explicit "separate from Work Instructions". A **constitution amendment** (MINOR) adds `routing-service` as the 19th service — drafted for owner ratification alongside this plan |
| Interim reference-data master (work centres/machines, labour codes) inside routing-service | No equipment/work-centre master service exists yet; operations must reference real, validated resources | Free-text codes give no referential integrity and break downstream scheduling; building the full equipment-master domain now is out of scope (DEF-004). The interim Settings submodule is the minimal viable master, owned by routing until DEF-004 extracts it |
| Graphical (Visio-style) editor in addition to the grid editor | Owner requirement (US11) — both views must support full CRUD + grouping over one model | A single grid view is simpler but does not meet the requirement; the graph view is isolated to one Angular component over the shared model so it does not complicate the backend |

## PR Strategy

Single feature branch `009-manufacturing-routing`; PRs target `Develop`. Backend CI anchor: `DOCKER_HOST='npipe:////./pipe/docker_engine' ./gradlew :services:routing-service:check`. Frontend anchor: `npm run lint && npm run build && npm test` (ERR-MES-089 gate). New module must ship with tests in its first PR or SonarCloud new-code coverage fails (ERR-MES-070/082).

| PR | Phases / User Stories | CI Anchor | Notes |
|---|---|---|---|
| PR 1 | Service scaffold + Settings reference data (US10) + infra (schema, compose, gateway routes, privileges) | `:services:routing-service:check` | Foundational; bundles setup with US10 so the new module has test coverage from day one. Adds `routing-service` to compose with its DB user in the **postgres** env block (ERR-MES-085). Constitution amendment merged here. |
| PR 2 | Route header + types (US1) + Normal operations & sequencing (US2) | `:services:routing-service:check` | Depends on PR 1 (reference data: route types, work centres). Core MVP route. |
| PR 3 | Operation resources, standards & consumption (US3) | `:services:routing-service:check` | Depends on PR 2. References BOM lines (inventory), inspection characteristics (quality), skills (labour), work instructions (engineering) via read APIs. |
| PR 4 | Advanced operation types + groups + steps (US4, US5, US6) | `:services:routing-service:check` | Depends on PR 2/3. Derived-vs-toggle classification, mutually-exclusive subset, group/step typing. |
| PR 5 | Approval workflow + significant-process approvers (US7) + two-tier revisioning + audit (US8) | `:services:routing-service:check` | Depends on PR 2–4. Reuses engineering-service e-sign client. Emits `routing.route.approved`. |
| PR 6 | Frontend: route list + header + **grid editor** (US1–US6 surface) + reference-data Settings UI (US10) | `npm run lint && npm run build && npm test` | Depends on backend PRs 1–4. Grid editor is the default view and the MVP authoring surface. |
| PR 7 | Frontend: **graphical editor** (US11) over the shared route model | `npm run lint && npm run build && npm test` | Depends on PR 6. Higher-effort; native Angular flow library. |
| PR 8 | Labour-plan export (US9) — backend + frontend | both anchors | P3; depends on PR 3/5. Smallest, last. |

**Sequencing note**: US11 (graphical editor, PR 7) is P2 but is intentionally sequenced after the grid editor (PR 6) because both bind to the same route model — the model and grid must be stable first. US9 (PR 8, P3) is last. If effort runs long, PR 7 and PR 8 are the natural deferral points without blocking a usable routing MVP (PRs 1–6).
