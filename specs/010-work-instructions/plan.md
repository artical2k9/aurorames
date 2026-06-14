# Implementation Plan: Work Instructions

**Branch**: `010-work-instructions` | **Date**: 2026-06-12 | **Spec**: specs/010-work-instructions/spec.md

**Input**: Feature specification from `/specs/010-work-instructions/spec.md`

## Summary

Add a Work Instructions module to the existing `engineering-service`: revision-controlled, step-based instruction documents with media attachments, skill-requirement references to `labour-service`, and an approval workflow that records a 21 CFR Part 11-style electronic signature (password re-authentication against Keycloak at signing). Frontend adds an Engineering > Work Instructions area following the established list/detail patterns. The revision lifecycle reuses the DRAFT → PENDING_APPROVAL → APPROVED semantics proven in Item Master and BOM (MES-114).

## Technical Context

**Language/Version**: Java 21 (Temurin), Spring Boot 3.3; Angular 21 + TypeScript 5.x frontend

**Primary Dependencies**: Spring Data JPA + Hibernate 6 + Envers, Flyway, Spring Security OAuth2 resource server (Keycloak), mes-udf-lib (UDFs), lib-common-security (JwtClaimsExtractor), hypersistence-utils (JSONB), Kafka (event publishing), PrimeNG + Lucide (frontend)

**Storage**: PostgreSQL 16, schema `engineering` (existing, owned by engineering-service); media binaries in MinIO (new S3-compatible compose service, bucket `wi-media` — owner clarification 2026-06-12, see research.md R3)

**Testing**: JUnit 5 + Testcontainers (PostgreSQL + KC) integration tests extending BaseIntegrationTest; unit tests for services/validators; Vitest for Angular

**Target Platform**: Docker Compose stack (existing infra), gateway-routed `/api/v1/work-instructions/**` → engineering-service

**Project Type**: Web application (Spring Boot microservice + Angular SPA)

**Performance Goals**: Qualification evaluation < 2 s including labour-service call (SC-003); list endpoints paginated; media downloads streamed (no full in-memory buffering for video)

**Constraints**: Approved revisions immutable; e-signature records append-only; media size limit configurable (default 100 MB video, 20 MB image/PDF); fail-closed qualification when labour-service unreachable

**Scale/Scope**: ~6 new entities, ~18 REST endpoints, 3 Angular screens (list, detail/authoring, step editor), 1 new gateway route block

## Constitution Check

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — specs/010-work-instructions/spec.md imported from Jira MES-10 |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS — tasks.md will order IT/unit tests before implementation per story |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — retrospective gate task included (MES-ERR-001 process) |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ⏳ Pending owner review (clarify stage before implementation) |
| Does the spec include a "Compliance References" section? | IV — Compliance by Design | ✅ PASS |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance by Design | ✅ PASS — AS9100D §7.5, 21 CFR Part 11 §11.10/§11.50/§11.70, NIST 800-171 |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — Envers on all entities incl. `_aud` tables; signature records append-only |
| Do new data models map to ISA-95 Part 2 object models (where applicable)? | VI — ISA-95/ISA-88 | ✅ PASS — Work instruction = Operations Definition documented information; skill refs map to Personnel qualification |
| Is authentication delegated to Keycloak (no bespoke auth)? | VII — Security-First | ✅ PASS — e-signature re-auth uses Keycloak Direct Access Grant verification, no local credential store (research.md R1) |
| Is all data scoped by `organisation_id` with no cross-org leakage? | IX — Multi-Org Isolation | ✅ PASS — org_id on WorkInstruction root; all queries org-scoped |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration Integrity | ✅ PASS — REST validated via Bean Validation; labour-service consumed read-only |
| Are shop floor timestamps from source (not synthetic)? | X — Data Accuracy | N/A — no machine data; signature timestamps server-side UTC |
| Does the service own only its declared domain APIs? | XI — Service Boundary | ✅ PASS — Work Instructions belongs to engineering-service per constitution service table; gateway gets explicit `/api/v1/work-instructions/**` predicate |

## Project Structure

### Documentation (this feature)

```text
specs/010-work-instructions/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── work-instructions-api.md
└── tasks.md   (created by /speckit-tasks)
```

### Source Code (repository root)

```text
services/engineering-service/
├── src/main/java/com/mes/engineering/
│   ├── workinstruction/
│   │   ├── api/                  # WorkInstructionController, WorkInstructionRevisionController
│   │   │   └── dto/              # DTOs + mapper
│   │   ├── domain/               # WorkInstruction, WorkInstructionRevision, WorkInstructionStep,
│   │   │                         # MediaAttachment, SkillRequirement, ElectronicSignature, RevisionStatus
│   │   ├── repository/
│   │   ├── service/              # WorkInstructionService, SignatureService, QualificationService,
│   │   │                         # MediaStorageService
│   │   └── client/               # LabourServiceClient (REST, fail-closed)
│   └── (existing eco/, config/, kafka/, filter/)
├── src/main/resources/db/migration/
│   ├── V007__create_work_instruction_tables.sql
│   └── V008__seed_work_instruction_privileges.sql   (if seed needed; manifest auto-grants)
└── src/test/java/com/mes/engineering/integration/workinstruction/

frontend/angular/src/app/features/work-instructions/
├── pages/work-instruction-list/
├── pages/work-instruction-detail/    # header + revision selector + history + steps
├── components/step-editor/           # step CRUD + media upload
├── components/signature-dialog/      # password re-auth modal
└── services/work-instruction-api.service.ts

services/gateway-service/src/main/resources/application.yml   # add /api/v1/work-instructions/** route
libs/mes-udf-lib/.../ModuleKey.java                           # add WORK_INSTRUCTION, WORK_INSTRUCTION_STEP
```

**Structure Decision**: Extend `engineering-service` with a `workinstruction` package mirroring the proven `bom`/`itemmaster` package shapes from inventory-service (api/dto/domain/repository/service). No new microservice. Media stored in MinIO via a `MediaStorageService` abstraction (MinIO Java SDK behind an interface; Testcontainers MinIO in ITs).

## Complexity Tracking

No constitutional violations. Single-approver inline workflow is a conscious v1 simplification; multi-stage approval is deferred to MES-112 (recorded as DEF-002 in the spec).

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup + US1 (authoring) + US2 (approval/e-sign) | T001–T03x | `./gradlew :services:engineering-service:check` | Schema migration + entities + revision workflow + signature service; e-sign IT covers KC re-auth path. Coverage anchor for all new packages |
| PR 2 | US3 (media) | T04x | `./gradlew :services:engineering-service:check` | Multipart upload + storage service + download auth; depends on PR 1 |
| PR 3 | US4 (skill gating defs + evaluation) | T05x | `./gradlew :services:engineering-service:check` | LabourServiceClient stub contract; fail-closed tests; can merge before MES-11 ships using WireMock contract tests |
| PR 4 | US5 (frontend list/detail/authoring + signature dialog) | T06x–T07x | `npm run lint && npm run test` (frontend) + full-stack smoke via gateway | Includes gateway route + ModuleKey additions (backend bits ride along); ERR-MES-059/078 checks |

**Sequencing note**: PR 3 (skill gating) integrates against the labour-service API contract defined in MES-11's spec; if MES-11 implementation has not merged when PR 3 is raised, contract tests run against WireMock and a follow-up integration verification task executes after MES-11 merges.
