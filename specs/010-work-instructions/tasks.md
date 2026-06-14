# Tasks: Work Instructions

**Input**: Design documents from `/specs/010-work-instructions/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/work-instructions-api.md

**Tests**: REQUIRED — Constitution §II (TDD): test tasks precede implementation in every story.

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Setup + US1 (authoring) + US2 (approval/e-sign) | T001–T029 | `./gradlew :services:engineering-service:check` | Migration + entities + revision workflow + signature service; KC re-auth IT path; coverage anchor for new packages |
| PR 2 | US3 (media) | T030–T036 | `./gradlew :services:engineering-service:check` | Multipart upload + storage service + download auth |
| PR 3 | US4 (skill gating defs + evaluation) | T037–T044 | `./gradlew :services:engineering-service:check` | WireMock contract tests vs labour API; live verification after MES-11 PR 2 merges |
| PR 4 | US5 (frontend incl. signature dialog) + compliance | T045–T059 | `npm run lint && npm run test` + gateway smoke | Gateway route + ModuleKey ride along; ERR-MES-059/078 checks |

**Sequencing note**: PR 3 may merge against WireMock contract tests before MES-11 ships; task T056 (live integration verification) executes after MES-11 PR 2 is on Develop.

---

## Phase 1: Setup — work-instruction module foundations [PR 1]

- [ ] T001 Create Flyway migration V007__create_work_instruction_tables.sql (all 6 tables + `_aud` tables with revend/revend_tstmp + partial unique DRAFT index per data-model.md) in services/engineering-service/src/main/resources/db/migration/
- [ ] T002 [P] Add `mes-signature-verify` confidential client (directAccessGrantsEnabled, sub mapper per ERR-MES-060) to keycloak/mes-realm.json; add MES_SIGNATURE_VERIFY_SECRET to .env.example and compose env for engineering-service (ERR-MES-016)
- [ ] T003 [P] Add WORK_INSTRUCTION, WORK_INSTRUCTION_STEP to libs/mes-udf-lib/src/main/java/com/mes/udf/domain/ModuleKey.java
- [ ] T004 [P] Add gateway route `/api/v1/work-instructions/**` → engineering-service:8097 in services/gateway-service/src/main/resources/application.yml
- [ ] T005 [P] Add `minio` service to docker/compose-infra.yml (minio/minio image, healthcheck, MINIO_ROOT_USER/MINIO_ROOT_PASSWORD in .env + .env.example per ERR-MES-016) + MinIO endpoint/bucket/credential env on engineering-service + media size limit properties (multipart limits + mes.wi.media.*) in services/engineering-service/src/main/resources/application.yml + io.minio:minio dependency in build.gradle and gradle/libs.versions.toml
- [ ] T006 Extend engineering-service privilege manifest with engineering:work-instruction:create/read/update/delete/approve keys in services/engineering-service/src/main/java/com/mes/engineering/config/
- [ ] T007 [P] Add owasp-java-html-sanitizer dependency to services/engineering-service/build.gradle and gradle/libs.versions.toml

**Checkpoint**: Migration applies; realm re-import documented; module skeleton compiles.

---

## Phase 2: User Story 1 — Author a Work Instruction (P1) [PR 1] 🎯 MVP

**Goal**: Header + ordered rich-text steps under DRAFT revision 0; edit/reorder/delete steps; immutability on non-DRAFT.

**Independent Test**: Create instruction with 3 steps, reload, verify order; edit on APPROVED rejected.

### Tests (write first, confirm failing)

- [ ] T008 [P] [US1] IT: create 201 rev 0 DRAFT; duplicate identifier 409; identifier suggestion endpoint in services/engineering-service/src/test/java/com/mes/engineering/integration/workinstruction/WorkInstructionControllerIT.java
- [ ] T009 [P] [US1] IT: add steps 10/20/30 → returned in order; PATCH step; DELETE step; reorder endpoint; step ops on non-DRAFT 409 in WorkInstructionStepIT.java
- [ ] T010 [P] [US1] IT: list paged + search + status filter; get with ?revisionNumber/?revisionStatus; org isolation; 401/403; DELETE 409 once ever-approved, 204 soft-delete for never-approved (FR-019) in WorkInstructionControllerIT.java
- [ ] T011 [P] [US1] Unit: HTML sanitiser config (script tags stripped, allowed formatting preserved) in services/engineering-service/src/test/java/com/mes/engineering/workinstruction/service/HtmlSanitiserTest.java

### Implementation

- [ ] T012 [P] [US1] Create domain entities WorkInstruction, WorkInstructionRevision, WorkInstructionStep, RevisionStatus per data-model.md in services/engineering-service/src/main/java/com/mes/engineering/workinstruction/domain/
- [ ] T013 [P] [US1] Create repositories in .../workinstruction/repository/
- [ ] T014 [US1] Implement WorkInstructionService (create, list/search, get with display-revision resolution + rev DESC tiebreak per ERR-MES-082, identifier suggestion, soft delete guard) in .../workinstruction/service/
- [ ] T015 [US1] Implement StepService (CRUD + reorder, DRAFT-only guard, HTML sanitisation on write) in .../workinstruction/service/
- [ ] T016 [US1] Create DTOs + mapper + WorkInstructionController + step endpoints per contract in .../workinstruction/api/
- [ ] T017 [US1] Run `./gradlew :services:engineering-service:check`; log/fix defects

**Checkpoint**: Authoring complete and independently testable.

---

## Phase 3: User Story 2 — Approval workflow + electronic signature (P1) [PR 1]

**Goal**: Submit/approve/reject lifecycle; approve requires password re-auth via KC; immutable signature records; new-draft copy.

**Independent Test**: Submit draft, approve with correct password → APPROVED + signature record; wrong password → 422, status unchanged; reject path; create revision copies content.

### Tests (write first, confirm failing)

- [ ] T018 [P] [US2] IT: submit → PENDING_APPROVAL with metadata; approve with valid password (Testcontainers KC user) → APPROVED + signature (name/timestamp/meaning); signature retrievable in revision history in WorkInstructionWorkflowIT.java
- [ ] T019 [P] [US2] IT: approve with wrong password → 422 SIGNATURE_VERIFICATION_FAILED, status PENDING_APPROVAL, failed attempt logged; reject(reason) → DRAFT; reject without reason 422 (same class)
- [ ] T020 [P] [US2] IT: approve revision with zero steps 422; content edits on PENDING/APPROVED 409 across header/steps endpoints (same class)
- [ ] T021 [P] [US2] IT: PATCH header on APPROVED auto-creates draft N+1 copying steps + customFields (media/skill copy verified later in their own phases); explicit create-revision endpoint; one-draft 409; cancel draft (same class)
- [ ] T022 [P] [US2] Unit: SignatureService — signer identity from JWT (null-safe sub fallback per ERR-MES-060), meaning recorded, no update/delete repository methods exposed in SignatureServiceTest.java

### Implementation

- [ ] T023 [P] [US2] Create ElectronicSignature entity (append-only) + repository (save/find only) in .../workinstruction/domain/ and .../repository/
- [ ] T024 [US2] Implement KeycloakCredentialVerifier (direct-grant call to mes-signature-verify client, username from authenticated JWT, 401→false, discard token) in .../workinstruction/service/
- [ ] T025 [US2] Implement SignatureService (verify + persist signature in same transaction as status change; failed-attempt audit log) in .../workinstruction/service/
- [ ] T026 [US2] Add submit/approve/reject/create-revision/cancel-draft to WorkInstructionService incl. zero-step submit guard and copy-on-revision (steps + customFields in this PR; media-row copy extends in T034, skill-req copy extends in T040 — each verified by that phase's ITs) in .../workinstruction/service/
- [ ] T027 [US2] Add workflow endpoints to controller per contract; publish `engineering.work-instruction.approved` Kafka event (JsonSerializer — ERR-MES-063) via .../kafka/
- [ ] T028 [US2] Run `./gradlew :services:engineering-service:check`; log/fix defects
- [ ] T029 [US2] Pre-PR retrospective spot-check (ERR-MES-001 categories: Envers, KC clients, JWT sub, privilege registration, IT inheritance) and fix violations

**Checkpoint**: Controlled-document lifecycle complete with Part 11-style signatures.

> **Raise PR 1 after this checkpoint** (T001–T029) | CI: `./gradlew :services:engineering-service:check` | Target: `Develop`

---

## Phase 4: User Story 3 — Media attachments (P2) [PR 2]

**Goal**: Upload images/PDF/video to steps, stream downloads with auth, size/type limits, copy-on-revision reference counting.

**Independent Test**: Upload image to step, fetch metadata, download binary; oversize/wrong-type rejected; media ops on APPROVED rejected.

### Tests (write first, confirm failing)

- [X] T030 [P] [US3] IT: multipart upload PNG/PDF 201 with metadata; oversize 422 naming limit; unsupported type 422; upload to non-DRAFT 409 in WorkInstructionMediaIT.java
- [X] T031 [P] [US3] IT: download streams with correct content type + auth required (401 without token); caption/order PATCH; delete removes row, binary retained while other revision references it (same class)
- [X] T032 [P] [US3] Unit: MediaStorageService path layout {orgId}/{instructionId}/{attachmentId}, orphan-cleanup guard, reference counting in MediaStorageServiceTest.java

### Implementation

- [X] T033 [P] [US3] Create MediaAttachment entity + repository in .../workinstruction/domain/ and .../repository/
- [X] T034 [US3] Implement MediaStorageService (MinIO SDK behind interface; bucket auto-create on startup; streaming put/get, no full buffering; Testcontainers MinIO in ITs) and extend copy-on-revision to duplicate attachment metadata rows pointing at the same object key (refcount semantics) in .../workinstruction/service/
- [X] T035 [US3] Implement media endpoints (multipart upload, StreamingResponseBody download, caption/order patch, delete with refcount guard) in .../workinstruction/api/
- [X] T036 [US3] Run `./gradlew :services:engineering-service:check`; log/fix defects

**Checkpoint**: Media complete.

> **Raise PR 2 after this checkpoint** (T030–T036) | CI: `./gradlew :services:engineering-service:check` | Target: `Develop`

---

## Phase 5: User Story 4 — Skill requirements + qualification evaluation (P2) [PR 3]

**Goal**: Skill requirement refs on revisions; evaluation endpoint calling labour-service bulk API; fail-closed.

**Independent Test**: Add 2 skill requirements; evaluate operator holding 1 → not qualified listing missing skill; labour-service down → VERIFICATION_UNAVAILABLE not-qualified.

### Tests (write first, confirm failing)

- [X] T037 [P] [US4] IT: add/remove skill requirement on DRAFT (denormalised code/name fetched from labour API — WireMock); duplicate skillId 409; ops on non-DRAFT 409 in WorkInstructionSkillIT.java
- [X] T038 [P] [US4] IT (WireMock): evaluation maps labour bulk response → qualified/missing list; EXPIRING_SOON qualifies; EXPIRED/REVOKED/NOT_HELD do not; labour 5xx/timeout → fail-closed VERIFICATION_UNAVAILABLE in QualificationEvaluationIT.java
- [X] T039 [P] [US4] Unit: LabourServiceClient timeout config (2 s) + error mapping in LabourServiceClientTest.java

### Implementation

- [X] T040 [P] [US4] Create SkillRequirement entity + repository and extend copy-on-revision to duplicate skill-requirement rows in .../workinstruction/domain/ and .../repository/
- [X] T041 [US4] Implement LabourServiceClient (RestClient, forwarded JWT, POST /api/v1/labour/qualifications/evaluate per MES-11 contract; GET /skills/{id} for denorm) in .../workinstruction/client/
- [X] T042 [US4] Implement QualificationService (fail-closed aggregation) + skill-requirement + qualification endpoints per contract in .../workinstruction/
- [X] T043 [US4] Run `./gradlew :services:engineering-service:check`; log/fix defects
- [ ] T044 [US4] Post-MES-11 live verification: run evaluation against real labour-service via gateway, confirm 200 paths (execute after MES-11 PR 2 merges)

**Checkpoint**: Gating definitions + evaluation contract complete.

> **Raise PR 3 after this checkpoint** (T037–T044) | CI: `./gradlew :services:engineering-service:check` | Target: `Develop`

---

## Phase 6: User Story 5 — Frontend (P3) [PR 4]

**Goal**: Engineering > Work Instructions list + detail/authoring with step editor, media upload, signature dialog, revision history.

**Independent Test**: Author 3-step instruction with image in UI, submit, approve via password dialog, view historical revision.

### Tests

- [ ] T045 [P] [US5] Vitest: work-instruction-list (load/search/UDF columns) in frontend/angular/src/app/features/work-instructions/pages/work-instruction-list/work-instruction-list.component.spec.ts
- [ ] T046 [P] [US5] Vitest: signature-dialog (password required, error display on 422, success emits) in .../components/signature-dialog/signature-dialog.component.spec.ts

### Implementation

- [ ] T047 [P] [US5] Create WorkInstructionApiService + DTO interfaces in frontend/angular/src/app/features/work-instructions/services/work-instruction-api.service.ts
- [ ] T048 [US5] Create work-instruction-list page (GridPreferenceService + UDF per ERR-MES-078; cdr per ERR-MES-059; Lucide icons) in .../pages/work-instruction-list/
- [ ] T049 [US5] Create work-instruction-detail page (header, revision selector + history with working View, workflow buttons, reload-after-action) in .../pages/work-instruction-detail/
- [ ] T050 [US5] Create step-editor component (step CRUD, reorder, PrimeNG Editor rich text, media upload with progress) in .../components/step-editor/
- [ ] T051 [US5] Create signature-dialog component (password re-entry, meaning display, 422 handling) in .../components/signature-dialog/
- [ ] T052 [US5] Add skill-requirements panel (add/remove, qualification check preview) to detail page in .../pages/work-instruction-detail/
- [ ] T053 [US5] Add route + sidebar entry Engineering > Work Instructions in frontend/angular/src/app/app.routes.ts and sidebar component
- [ ] T054 [US5] `npm run lint && npm run test` green; full-stack gateway smoke (author→approve with signature in browser); log/fix defects

**Checkpoint**: Module usable end-to-end.

---

## Phase 7: Compliance Verification & Defect Closure [PR 4 continued]

- [ ] T055 Verify Constitution Check gates in plan.md all ✅ (human review gate III recorded)
- [ ] T056 [P] Confirm signature immutability: no update/delete path exists (code review + IT attempting mutation)
- [ ] T057 [P] Confirm Envers audit rows for all mutations; failed signature attempts logged; org_id scoping ITs green
- [ ] T058 [P] Confirm media download auth enforced (no unauthenticated access) and export-control note in docs
- [ ] T059 Confirm all logged defects closed; run quickstart.md end-to-end; pre-PR retrospective vs MES-ERR-001 index

> **Raise PR 4 after this checkpoint** (T045–T059) | CI: `npm run lint && npm run test` + `./gradlew :services:engineering-service:check` | Target: `Develop`

---

## Dependencies & Execution Order

- Phase 1 blocks all. US1 blocks US2 (workflow operates on authored content).
- US3 (media) depends on US1 steps; independent of US2 except copy-on-revision wiring (T026 handles copy; T033 adds media rows to the copy path — verify in T031).
- US4 depends on US1 (revisions) and externally on MES-11 contract (WireMock until merged).
- US5 depends on all backend stories.
- Parallel: T002–T005, T007; test tasks per story; T012/T013; T023; T033; T040.

## Implementation Strategy

MVP = PR 1 (authoring + controlled approval). PR 3's live verification (T044) is the only cross-epic timing dependency — schedule after MES-11 PR 2.
