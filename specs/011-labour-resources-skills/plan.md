# Implementation Plan: Labour Resources & Skills

**Branch**: `011-labour-resources-skills` | **Date**: 2026-06-12 | **Spec**: specs/011-labour-resources-skills/spec.md

**Input**: Feature specification from `/specs/011-labour-resources-skills/spec.md`

## Summary

Scaffold a new `labour-service` microservice (ISA-95 Personnel domain) providing employees, a skill catalogue, certifications with derived expiry state, training records, and a bulk qualification evaluation API consumed by MES-10 (work-instruction gating) and MES-9 (operation skill requirements). Frontend adds a Labour navigation area (Employees, Skills, Certifications) using established list/detail patterns. No new architectural patterns: the service replicates the proven scaffold of `engineering-service` with schema `labour` on port 8098.

## Technical Context

**Language/Version**: Java 21 (Temurin), Spring Boot 3.3; Angular 21 + TypeScript 5.x

**Primary Dependencies**: Spring Data JPA + Hibernate 6 + Envers, Flyway, Spring Security resource server (Keycloak), mes-udf-lib, lib-common-security, lib-common-audit, hypersistence-utils (JSONB customFields)

**Storage**: PostgreSQL 16, new schema `labour` (Flyway-managed, service-owned per Constitution §XI)

**Testing**: JUnit 5 + Testcontainers ITs extending BaseIntegrationTest; unit tests for state derivation (certification state machine) and validators; Vitest frontend

**Target Platform**: Docker Compose stack; gateway route `/api/v1/labour/**` → labour-service:8098

**Project Type**: Web application (new Spring Boot microservice + Angular SPA screens)

**Performance Goals**: Bulk qualification API < 500 ms for 20 skills (SC-002) — single query with IN clause, no N+1

**Constraints**: Certification state always derived at read time (never stored stale); INACTIVE employee fails all gating; org-scoped everything; expiry evaluated on dates not timestamps

**Scale/Scope**: 4 entities, ~20 REST endpoints, 3+ Angular screens, full service scaffold (gradle module, Dockerfile, compose, gateway, sonar, privilege manifest)

## Constitution Check

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — specs/011-labour-resources-skills/spec.md (Jira MES-11) |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS — tasks.md orders tests first per story |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — retrospective gate task |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ✅ PASS — owner reviewed via /speckit-clarify (4 answers recorded) and authorised implementation 2026-06-13 |
| Does the spec include a "Compliance References" section? | IV — Compliance | ✅ PASS — AS9100D §7.2, AS9146, ISA-95 Part 2, CMMC PS |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance | ✅ PASS |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — Envers all entities + `_aud` tables; revocation/training-outcome changes audited |
| Do new data models map to ISA-95 Part 2 object models? | VI — ISA-95/ISA-88 | ✅ PASS — Person (Employee), Personnel Class (Skill), Qualification Test Result (Certification) |
| Is authentication delegated to Keycloak? | VII — Security-First | ✅ PASS — standard resource-server config; optional IAM user link references KC-managed identity |
| Is all data scoped by `organisation_id`? | IX — Multi-Org | ✅ PASS — org_id on all roots; unique constraints org-scoped |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration | ✅ PASS — Bean Validation; bulk qualification API is read-only/idempotent |
| Are shop floor timestamps from source? | X — Data Accuracy | N/A — no machine data |
| Does the service own only its declared domain APIs? | XI — Service Boundary | ✅ PASS — labour-service owns ISA-95 Personnel domain (constitution service table); explicit gateway predicate `/api/v1/labour/**`; own schema, own container |

**Note on skills placement**: the constitution service table lists "Skills" in engineering-service's description but also defines `labour-service` for Labour Resource Tracking; the Epic explicitly assigns this module to labour-service, which matches ISA-95 Part 2 Personnel (Personnel Class ≈ skill, Qualification Test Result ≈ certification). The skill catalogue therefore lives in labour-service; engineering-service (MES-10) only stores references. Flagged for the clarify stage as a constitution-table wording cleanup.

## Project Structure

### Documentation (this feature)

```text
specs/011-labour-resources-skills/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── labour-api.md
└── tasks.md   (by /speckit-tasks)
```

### Source Code (repository root)

```text
services/labour-service/                      # NEW service
├── build.gradle                              # mirrors engineering-service
├── Dockerfile
├── src/main/java/com/mes/labour/
│   ├── LabourServiceApplication.java
│   ├── config/                               # AppConfig (AuditorAware — ERR-MES-062), SecurityConfig,
│   │                                         # LabourSecurityProperties, privilege manifest registration
│   ├── api/GlobalExceptionHandler.java
│   ├── audit/                                # Envers revision entity + listener (copy pattern)
│   ├── employee/{api,api/dto,domain,repository,service}/
│   ├── skill/{api,api/dto,domain,repository,service}/
│   ├── certification/{api,api/dto,domain,repository,service}/   # incl. QualificationService (bulk eval)
│   └── training/{api,api/dto,domain,repository,service}/
├── src/main/resources/
│   ├── application.yml                       # port 8098, schema labour
│   └── db/migration/
│       ├── V001__create_labour_schema.sql
│       ├── V002__create_employee_skill_certification_training.sql
│       └── V003__add_envers_tables.sql       # incl. revend/revend_tstmp (ValidityAuditStrategy)
└── src/test/java/com/mes/labour/...          # BaseIntegrationTest + ITs per controller

settings.gradle                                # + include 'services:labour-service'
docker/compose-infra.yml                       # + labour-service:8098 + LABOUR_SERVICE_URL on gateway
services/gateway-service/.../application.yml   # + /api/v1/labour/** route
sonar-project.properties                       # + labour-service paths in sonar.sources/tests
libs/mes-udf-lib/.../ModuleKey.java            # + EMPLOYEE, SKILL, CERTIFICATION

frontend/angular/src/app/features/labour/
├── pages/employee-list/ + employee-detail/    # detail incl. competency profile + training history
├── pages/skill-list/ + skill-detail/
├── pages/certification-list/                  # incl. expiry-window filter (dashboard view)
├── components/award-certification-dialog/
└── services/labour-api.service.ts
```

**Structure Decision**: New microservice (Epic-mandated, constitution-aligned). Package-per-aggregate (employee/skill/certification/training) mirroring inventory-service's itemmaster/bom split. Certification state is a derived enum computed in a domain service — single source of truth used by both profile reads and bulk qualification.

## Complexity Tracking

No violations. New service is constitutionally required (ISA-95 Personnel domain), not optional complexity.

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup (scaffold) + US1 (employees) + US2 (skills) | T001–T03x | `./gradlew :services:labour-service:check` | Service skeleton has no coverage anchor alone — bundled with first two aggregates' ITs. Includes settings.gradle, compose, gateway, sonar-project.properties updates |
| PR 2 | US3 (certifications + bulk qualification API) | T04x | `./gradlew :services:labour-service:check` | State-derivation unit tests + gating ITs; the cross-service contract consumed by MES-10 PR 3 |
| PR 3 | US4 (training records) | T05x | `./gradlew :services:labour-service:check` | Independent of PR 2 except shared scaffold |
| PR 4 | US5 (frontend Labour area incl. expiry dashboard) | T06x–T07x | `npm run lint && npm run test` + gateway smoke | ModuleKey additions ride along; ERR-MES-059/078 checks |

**Sequencing note**: PR 2 must merge before MES-10's skill-gating PR can run its live integration verification (it consumes the bulk qualification API). MES-10 PR 3 may merge earlier against WireMock contract tests.
