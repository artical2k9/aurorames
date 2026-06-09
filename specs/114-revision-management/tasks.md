# Tasks: Automated Revision Numbering — Item Master & BOM (MES-114)

**Input**: Design documents from `specs/114-revision-management/`

**Branch**: `114-revision-management` | **Epic**: MES-114 | **Service**: `inventory-service`

---

## PR Strategy

| PR | Phases | Task Range | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Phase 2 + Phase 3 | T002–T030 | `./gradlew :services:inventory-service:check` | Item schema migrations (V014–V016) + Item revision workflow. Includes Envers tables per ERR-MES-057. Drop of `item_master` happens in V022 (PR 2) — item_master stays alive in PR 1 for backward compat. |
| PR 2 | Phase 4 | T031–T059 | `./gradlew :services:inventory-service:check` | BOM schema migrations (V017–V022) + BOM revision workflow. V022 drops both `item_master` and `bill_of_materials`. Depends on PR 1 merged. |
| PR 3 | Phase 5 | T060–T067 | `ng build --configuration=production` | Item Master frontend revision UI. Depends on PR 1 merged. |
| PR 4 | Phase 6 | T068–T074 | `ng build --configuration=production` | BOM frontend revision UI. Depends on PR 2 and PR 3 merged. |
| PR 5 | Phase 7 | T075–T080 | `./gradlew :services:inventory-service:check` | Revision history API (P2). Depends on PR 1 + PR 2 merged. Can be deferred. |

**Sequencing note**: PR 1 introduces `Item`/`ItemRevision` entities and keeps `item_master` alive (renamed to legacy). PR 2's V022 performs the final drop of both `item_master` and `bill_of_materials` — the two migrations form a single logical data replacement split across two PRs for reviewability.

---

## Phase 1: Pre-flight [PR 1 start]

**Purpose**: Verify baseline before any schema changes.

- [ ] T001 Confirm `./gradlew :services:inventory-service:check` passes zero failures on `Develop` — this is the baseline IT suite that must remain green after every migration

**Checkpoint**: Baseline confirmed — schema migration work can begin.

---

## Phase 2: Foundational — Item Schema Migrations & Entities [PR 1]

**Purpose**: New `item` and `item_revision` tables + Java entities. All US1/US2/US5 tasks depend on this phase.

**⚠️ CRITICAL**: Do not begin Phase 3 tasks until all Phase 2 migrations and entities compile successfully.

### Migrations

- [ ] T002 Write `V014__create_item_revision_tables.sql` — CREATE `inventory.item` (id, org_id, part_number, created_by, created_at) + `inventory.item_revision` (all current item_master data fields + revision INTEGER, revision_status VARCHAR(20), submitted_by, submitted_at, approved_by, approved_at) with UNIQUE(item_id, revision) and partial unique index WHERE revision_status='DRAFT' per data-model.md DDL at `services/inventory-service/src/main/resources/db/migration/V014__create_item_revision_tables.sql`
- [ ] T003 Write `V015__migrate_item_master_to_revisions.sql` — INSERT into `inventory.item` (one row per distinct org_id+part_number from item_master); INSERT into `inventory.item_revision` using `item_revision.id = item_master.id` (UUID preserved), `revision = ROW_NUMBER() OVER (PARTITION BY org_id, part_number ORDER BY created_at ASC) - 1`, `revision_status = 'APPROVED'`; include `NOT NULL` audit columns (`created_by`, `modified_by`) per ERR-MES-077 at `services/inventory-service/src/main/resources/db/migration/V015__migrate_item_master_to_revisions.sql`
- [ ] T004 Write `V016__create_item_revision_envers.sql` — RENAME `inventory.item_master_aud` TO `inventory.item_master_aud_legacy`; CREATE `inventory.item_aud` + `inventory.item_revision_aud` tables (all columns mirroring entity fields + rev INTEGER FK → revinfo, revtype SMALLINT) per ERR-MES-057; CREATE `inventory.item_revision_aud` must include all columns from `item_revision` at `services/inventory-service/src/main/resources/db/migration/V016__create_item_revision_envers.sql`

### Java Entities

- [ ] T005 [P] Create `RevisionStatus.java` enum with values `DRAFT`, `PENDING_APPROVAL`, `APPROVED` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/domain/RevisionStatus.java`
- [ ] T006 [P] Create `Item.java` — `@Entity @Audited`, table `inventory.item`, fields: id, orgId, partNumber, createdBy (@CreatedBy), createdAt (@CreatedDate), `@OneToMany(mappedBy = "item", fetch = LAZY) List<ItemRevision> revisions` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/domain/Item.java`
- [ ] T007 Create `ItemRevision.java` — `@Entity @Audited`, table `inventory.item_revision`, `@ManyToOne(fetch=LAZY) @JoinColumn("item_id") Item item`, `revision INTEGER`, `@Enumerated(EnumType.STRING) RevisionStatus revisionStatus`, all data fields from current `ItemMaster` (description, unitOfMeasure, cageCode, classification, makeBuyCode, traceabilityMethod, shelfLifeControlled, shelfLifeDays, stepPartRef, counterfeitRiskLevel, approvedSuppliers JSONB, verificationRequired, customFields JSONB), submittedBy, submittedAt, approvedBy, approvedAt, @CreatedBy/@CreatedDate/@LastModifiedBy/@LastModifiedDate audit fields at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/domain/ItemRevision.java`

### Repositories

- [ ] T008 [P] Create `ItemRepository.java` extending `JpaRepository<Item, UUID>` with `Optional<Item> findByOrgIdAndPartNumber(UUID orgId, String partNumber)` and `Page<Item> findAllByOrgId(UUID orgId, Pageable pageable)` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/repository/ItemRepository.java`
- [ ] T009 [P] Create `ItemRevisionRepository.java` extending `JpaRepository<ItemRevision, UUID>` with `Optional<ItemRevision> findByItemIdAndRevisionStatus(UUID itemId, RevisionStatus status)`, `List<ItemRevision> findByItemIdOrderByRevisionAsc(UUID itemId)`, `Optional<ItemRevision> findTopByItemIdAndRevisionStatusOrderByRevisionDesc(UUID itemId, RevisionStatus status)` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/repository/ItemRevisionRepository.java`

**Checkpoint**: Phase 2 complete — schema migrations and entities ready for service layer.

---

## Phase 3: US1 + US2 + US5(item) — Item Revision Workflow Backend [PR 1]

**Goal**: Replace `ItemMaster`-based CRUD with `Item`+`ItemRevision` model. Add submit/approve/cancel-draft lifecycle. Migrate and delete old entity.

**Independent Test**: `./gradlew :services:inventory-service:check` — all existing IT tests pass + new ItemRevisionIT scenarios green.

### Tests (write first — confirm FAILING before implementation)

- [ ] T010 [P] [US1] Write `ItemRevisionIT.java` — IT scenarios: (1) POST /item-master creates Item + ItemRevision rev=0 DRAFT; (2) POST /item-master/{id}/submit → PENDING_APPROVAL; (3) POST /item-master/{id}/approve (SYSTEM_ADMIN JWT) → APPROVED at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemRevisionIT.java`
- [ ] T011 [P] [US2] Add US2 IT scenarios to `ItemRevisionIT.java`: (4) PATCH on APPROVED auto-creates DRAFT rev=1; (5) second PATCH while DRAFT exists → 409; (6) DELETE /item-master/{id}/draft → 204, reverts to APPROVED rev=0; (7) new PATCH after cancel → creates DRAFT rev=1 again at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemRevisionIT.java`
- [ ] T012 [P] [US1] Write `ItemRevisionServiceTest.java` — unit tests: submitDraft() happy path; submitDraft() on APPROVED → exception; approveDraft() on PENDING_APPROVAL happy path; cancelDraft() hard-deletes DRAFT; one-draft uniqueness (mock repo throws DataIntegrityViolationException on second draft) at `services/inventory-service/src/test/java/com/mes/inventory/unit/itemmaster/ItemRevisionServiceTest.java`
- [ ] T013 [US5] Write `ItemRevisionMigrationIT.java` — verify: COUNT(item_revision) = COUNT(item_master original), COUNT(item) = COUNT(DISTINCT org_id+part_number), all item_revision rows have revision_status='APPROVED', revision integers are 0-based within each (org_id,part_number) group, no NULL modified_by/created_by at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemRevisionMigrationIT.java`

### DTOs

- [ ] T014 [P] [US1] Create `ItemRevisionDto.java` — add `revisionId`, `revision: Integer`, `revisionStatus: String`, `hasDraft: boolean` alongside all existing ItemMasterDto fields; keep `id` as the item identity UUID for backward compatibility at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/dto/ItemRevisionDto.java`
- [ ] T015 [P] [US1] Update `ItemMasterDto.java` — add `revisionId`, `revision`, `revisionStatus`, `hasDraft` fields; keep `id` as item identity UUID; drop `status` field (replaced by `revisionStatus`) at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/dto/ItemMasterDto.java`
- [ ] T016 [US1] Update `ItemMasterMapper.java` — map from `Item` + `ItemRevision` to `ItemMasterDto`; set `id = item.getId()`, `revisionId = revision.getId()`, `revision = revision.getRevision()`, `revisionStatus = revision.getRevisionStatus().name()`, `hasDraft` computed by checking for co-existing DRAFT at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/dto/ItemMasterMapper.java`

### Service Layer

- [ ] T017 [US1] Update `ItemMasterService.java` — rewrite `create(orgId, partNumber, req)` to: (1) check uniqueness on `item` table; (2) create `Item` identity; (3) create `ItemRevision(rev=0, DRAFT)` with all req fields; validate shelf life + UDF; return mapped DTO at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/service/ItemMasterService.java`
- [ ] T018 [US1] Add `submitDraft(UUID itemId, UUID orgId)` to `ItemMasterService.java` — find DRAFT revision for item (404 if none); set `revisionStatus=PENDING_APPROVAL`, `submittedBy`, `submittedAt`; save; return DTO at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/service/ItemMasterService.java`
- [ ] T019 [US1] Add `approveDraft(UUID itemId, UUID orgId, String actor)` to `ItemMasterService.java` — find PENDING_APPROVAL revision (409 if status is not PENDING_APPROVAL); set `revisionStatus=APPROVED`, `approvedBy`, `approvedAt`; save; publish `ITEM_REVISION_APPROVED` Kafka event; return DTO at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/service/ItemMasterService.java`
- [ ] T020 [US2] Update `ItemMasterService.patchItemMaster(UUID itemId, UUID orgId, PatchItemMasterRequest req)` — if current revision is APPROVED: create new DRAFT at `maxApproved+1`; if PENDING_APPROVAL: return 409; if DRAFT: update in place; apply all optional patch fields; validate UDF; return DTO at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/service/ItemMasterService.java`
- [ ] T021 [US2] Add `cancelDraft(UUID itemId, UUID orgId)` to `ItemMasterService.java` — find DRAFT revision (409 if none); hard-delete (`itemRevisionRepository.delete(draft)`); return 204 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/service/ItemMasterService.java`

### Controller

- [ ] T022 [US1] Update `ItemMasterController.java` `POST /api/v1/item-master` — remove `revision` from request (use `CreateItemMasterRequest` without revision); delegate to `itemMasterService.create(orgId, partNumber, req)`; return 201 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T023 [US1] Update `ItemMasterController.java` `GET /api/v1/item-master` — query `ItemRepository.findAllByOrgId()`, then for each item fetch current revision (APPROVED preferred, fallback DRAFT); compute `hasDraft`; return page of `ItemMasterDto` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T024 [US1] Update `ItemMasterController.java` `GET /api/v1/item-master/{id}` — accept optional `?revisionStatus=` query param; default returns APPROVED or DRAFT if no APPROVED; use `ItemRevisionRepository` queries at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T025 [US1] Update `ItemMasterController.java` `PATCH /api/v1/item-master/{id}` — delegate to `itemMasterService.patchItemMaster()` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T026 [US1] Add `POST /api/v1/item-master/{id}/submit` to `ItemMasterController.java` — `@RequiresPrivilege("item-master:records:manage")`; delegate to `submitDraft()`; return 200 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T027 [US1] Add `POST /api/v1/item-master/{id}/approve` to `ItemMasterController.java` — `@RequiresPrivilege("item-master:revisions:approve")`; delegate to `approveDraft()`; return 200 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T028 [US2] Add `DELETE /api/v1/item-master/{id}/draft` to `ItemMasterController.java` — `@RequiresPrivilege("item-master:records:manage")`; delegate to `cancelDraft()`; return 204 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`

### Privilege + Kafka + Cleanup

- [ ] T029 [US1] Add `item-master:revisions:approve` to the privilege manifest in `InventoryServiceApplication.java` `onApplicationEvent()` registration list at `services/inventory-service/src/main/java/com/mes/inventory/InventoryServiceApplication.java`
- [ ] T030 [US1] Add `ITEM_REVISION_APPROVED` event type to `ItemMasterEventPublisher.java`; publish from `ItemMasterService.approveDraft()` with payload `{itemId, revision, approvedBy, approvedAt}` at `services/inventory-service/src/main/java/com/mes/inventory/kafka/ItemMasterEventPublisher.java`
- [ ] T031 [US5] Update `CreateItemMasterRequest.java` — remove `revision` field; system assigns rev=0 at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/dto/CreateItemMasterRequest.java`
- [ ] T032 [US5] Delete `ItemMaster.java` (domain class replaced by `Item` + `ItemRevision`) at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/domain/ItemMaster.java`
- [ ] T033 [US5] Delete `ItemStatus.java` (replaced by `RevisionStatus`) at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/domain/ItemStatus.java`
- [ ] T034 [US5] Update `ItemMasterRepository.java` — replace `JpaRepository<ItemMaster, UUID>` with `JpaRepository<Item, UUID>`; update all callers at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/repository/ItemMasterRepository.java`
- [ ] T035 [US5] Update `ItemMasterControllerIT.java` — update POST body (no revision field), update assertion checks for `revision: 0` and `revisionStatus: "DRAFT"`, update PATCH tests at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemMasterControllerIT.java`
- [ ] T036 [US5] Update `AS5553IT.java` — AS5553 fields are now on `ItemRevision`; update test to check fields on revision object at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/AS5553IT.java`

**Checkpoint**: Phase 3 complete — all item IT scenarios passing, `./gradlew :services:inventory-service:check` green.

> **Raise PR 1 after this checkpoint** (T002–T036) | CI: `./gradlew :services:inventory-service:check` | Target: `Develop`

---

## Phase 4: US3 + US5(BOM) — BOM Revision Workflow Backend [PR 2]

**Goal**: Replace `BillOfMaterials`-based CRUD with `Bom`+`BomRevision` model. Update `BomLine` FKs. Add BOM submit/approve/cancel-draft lifecycle. Drop old tables.

**Independent Test**: `./gradlew :services:inventory-service:check` — all BOM IT tests pass (explosion, authoring, browser) + new BomRevisionIT scenarios green.

### Migrations

- [ ] T037 [P] Write `V017__create_bom_revision_tables.sql` — CREATE `inventory.bom` (id, org_id, parent_item_id FK → inventory.item, created_by, created_at) + `inventory.bom_revision` (all current BOM header fields + id, bom_id FK → bom, revision INTEGER, revision_status, submitted_by, submitted_at, approved_by, approved_at, audit columns); add UNIQUE(bom_id, revision) + partial unique index WHERE revision_status='DRAFT' at `services/inventory-service/src/main/resources/db/migration/V017__create_bom_revision_tables.sql`
- [ ] T038 Write `V018__migrate_bom_to_revisions.sql` — INSERT into `inventory.bom` (one row per distinct org_id+parent_item_id, FK now references inventory.item not inventory.item_master); INSERT into `inventory.bom_revision` using `id = bill_of_materials.id` (UUID preserved), revision = ROW_NUMBER()-1 per group ordered by created_at, `revision_status = 'APPROVED'`; include NOT NULL audit columns per ERR-MES-077 at `services/inventory-service/src/main/resources/db/migration/V018__migrate_bom_to_revisions.sql`
- [ ] T039 Write `V019__create_bom_revision_envers.sql` — RENAME `inventory.bill_of_materials_aud` TO `inventory.bill_of_materials_aud_legacy`; CREATE `inventory.bom_aud` + `inventory.bom_revision_aud` per ERR-MES-057 at `services/inventory-service/src/main/resources/db/migration/V019__create_bom_revision_envers.sql`
- [ ] T040 Write `V020__migrate_bom_line_fks.sql` — ALTER TABLE `inventory.bom_line`: ADD `bom_revision_id UUID NULL`, ADD `component_item_revision_id UUID NULL`; UPDATE both from existing FK values (bom_revision.id = bill_of_materials.id preserved; item_revision.id = item_master.id preserved from V015); ALTER NOT NULL; ADD FK constraints to bom_revision and item_revision; DROP CONSTRAINT fk_bom_line_bom; DROP CONSTRAINT fk_bom_line_component; DROP COLUMN bom_id; DROP COLUMN component_item_id; update indexes per data-model.md at `services/inventory-service/src/main/resources/db/migration/V020__migrate_bom_line_fks.sql`
- [ ] T041 Write `V021__update_bom_line_envers.sql` — ADD `bom_revision_id UUID` + `component_item_revision_id UUID` to `inventory.bom_line_aud`; DROP `bom_id` + `component_item_id` columns from `inventory.bom_line_aud` at `services/inventory-service/src/main/resources/db/migration/V021__update_bom_line_envers.sql`
- [ ] T042 Write `V022__drop_legacy_tables_and_register_bom_privileges.sql` — DROP TABLE `inventory.bill_of_materials`; DROP TABLE `inventory.item_master`; INSERT INTO `iam.privilege` for `bom:revisions:approve`; INSERT INTO `iam.role_privilege` granting `bom:revisions:approve` to SYSTEM_ADMIN; include `created_by = 'migration'` per ERR-MES-077 at `services/inventory-service/src/main/resources/db/migration/V022__drop_legacy_tables_and_register_bom_privileges.sql`

### Java Entities

- [ ] T043 [P] [US3] Create `Bom.java` — `@Entity @Audited`, table `inventory.bom`, fields: id, orgId, parentItemId (UUID FK → `inventory.item`), createdBy (@CreatedBy), createdAt (@CreatedDate), `@OneToMany(mappedBy="bom", cascade=ALL, orphanRemoval=false) List<BomRevision> revisions` at `services/inventory-service/src/main/java/com/mes/inventory/bom/domain/Bom.java`
- [ ] T044 [P] [US3] Create `BomRevision.java` — `@Entity @Audited`, table `inventory.bom_revision`, `@ManyToOne(fetch=LAZY) @JoinColumn("bom_id") Bom bom`, revision INTEGER, revisionStatus (RevisionStatus), all current BOM header fields (description, ecoId, reasonForRevision, productionLine, bomType, effectivityType, customFields JSONB), submittedBy, submittedAt, approvedBy, approvedAt, audit fields, `@OneToMany(mappedBy="bomRevision", cascade=ALL, orphanRemoval=true) List<BomLine> bomLines` at `services/inventory-service/src/main/java/com/mes/inventory/bom/domain/BomRevision.java`
- [ ] T045 [US3] Update `BomLine.java` — replace `@Column("bom_id") UUID bomId` with `@ManyToOne(fetch=LAZY) @JoinColumn("bom_revision_id") BomRevision bomRevision`; replace `@Column("component_item_id") UUID componentItemId` with `@ManyToOne(fetch=LAZY) @JoinColumn("component_item_revision_id") ItemRevision componentItemRevision` at `services/inventory-service/src/main/java/com/mes/inventory/bom/domain/BomLine.java`

### Tests (write first — confirm FAILING)

- [ ] T046 [P] [US3] Write `BomRevisionIT.java` — IT scenarios: (1) POST /boms creates Bom+BomRevision rev=0 DRAFT; (2) add BOM line with APPROVED component item revision → 201; (3) add line with DRAFT item revision → 422; (4) POST /boms/{id}/submit → PENDING_APPROVAL; (5) POST /boms/{id}/approve (SYSTEM_ADMIN) → APPROVED; (6) DELETE /boms/{id}/draft → 204 (cascades BOM lines); (7) PATCH header on APPROVED → auto-creates DRAFT rev=1 with copied lines at `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomRevisionIT.java`
- [ ] T047 [P] [US5] Write `BomRevisionMigrationIT.java` — verify: COUNT(bom_revision) = COUNT(bill_of_materials), COUNT(bom) = COUNT(DISTINCT org_id+parent_item_id), all bom_revision rows APPROVED, no NULL bom_revision_id or component_item_revision_id in bom_line at `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomRevisionMigrationIT.java`

### Repositories

- [ ] T048 [P] [US3] Create `BomRevisionRepository.java` extending `JpaRepository<BomRevision, UUID>` with `Optional<BomRevision> findByBomIdAndRevisionStatus(UUID bomId, RevisionStatus status)`, `Optional<BomRevision> findTopByBomIdAndRevisionStatusOrderByRevisionDesc(UUID bomId, RevisionStatus status)`, `List<BomRevision> findByBomIdOrderByRevisionAsc(UUID bomId)` at `services/inventory-service/src/main/java/com/mes/inventory/bom/repository/BomRevisionRepository.java`
- [ ] T049 [US3] Update `BomRepository.java` — change to `JpaRepository<Bom, UUID>`; add `Optional<Bom> findByOrgIdAndParentItemId(UUID orgId, UUID parentItemId)`, `Page<Bom> findAllByOrgId(UUID orgId, Pageable page)` at `services/inventory-service/src/main/java/com/mes/inventory/bom/repository/BomRepository.java`
- [ ] T050 [US3] Update `BomLineRepository.java` — replace `findByBomId(UUID bomId)` with `findByBomRevisionId(UUID bomRevisionId)` at `services/inventory-service/src/main/java/com/mes/inventory/bom/repository/BomLineRepository.java`

### DTOs

- [ ] T051 [P] [US3] Update `BomDto.java` — add `bomRevisionId: UUID`, `revision: Integer`, `revisionStatus: String`, `hasDraft: boolean`; keep `id` as Bom identity UUID for backward compat at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/BomDto.java`
- [ ] T052 [P] [US3] Update `BomSummaryDto.java` — add `revision`, `revisionStatus`, `hasDraft` fields; update mapper from BomRevision at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/BomSummaryDto.java`
- [ ] T053 [US3] Update `BomMapper.java` — map from `Bom` + `BomRevision` → `BomDto`; compute hasDraft at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/BomMapper.java`
- [ ] T054 [US3] Update `BomLineDto.java` — replace `componentItemId` with `componentItemRevisionId` and add `componentRevision: Integer` for display at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/BomLineDto.java`
- [ ] T055 [US3] Update `CreateBomRequest.java` — remove `bomRevision` field (system-managed) at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/CreateBomRequest.java`
- [ ] T056 [US3] Update `CreateBomLineRequest.java` — rename `componentItemId` → `componentItemRevisionId` to reference `item_revision.id` at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/CreateBomLineRequest.java`

### Service Layer

- [ ] T057 [US3] Update `BomService.java` — rewrite `createBom()` to: create `Bom` identity + `BomRevision(rev=0, DRAFT)`; add `submitDraft(bomId, orgId)`, `approveDraft(bomId, orgId, actor)`, `cancelDraft(bomId, orgId)` (cascade deletes BomLines); add `createDraftFromApproved(bom, lastApprovedRevision)` (copies BomLine rows into new DRAFT); update `addLine()` to validate `componentItemRevision.revisionStatus == APPROVED` (422 if not) at `services/inventory-service/src/main/java/com/mes/inventory/bom/service/BomService.java`
- [ ] T058 [US3] Update `BomExplosionService.java` — replace `bom_id` with `bom_revision_id` in recursive CTE native query; find current APPROVED BomRevision before exploding at `services/inventory-service/src/main/java/com/mes/inventory/bom/service/BomExplosionService.java`
- [ ] T059 [US3] Update `BomExportService.java` — use `BomRevision` + `bomRevisionId` in queries; preserve all existing CSV/PDF export logic at `services/inventory-service/src/main/java/com/mes/inventory/bom/service/BomExportService.java`

### Controller + Cleanup

- [ ] T060 [US3] Update `BomController.java` — `POST /boms` uses new `createBom()` (no revision in request body); `GET /boms` uses `BomRepository` + current revision; `GET /boms/{id}` returns current revision; `PATCH /boms/{id}/header` operates on current DRAFT or creates new DRAFT from APPROVED; add `POST /boms/{id}/submit` (`@RequiresPrivilege("item-master:bom:manage")`), `POST /boms/{id}/approve` (`@RequiresPrivilege("bom:revisions:approve")`), `DELETE /boms/{id}/draft` at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/BomController.java`
- [ ] T061 [US3] Add `bom:revisions:approve` to `InventoryServiceApplication.java` privilege manifest at `services/inventory-service/src/main/java/com/mes/inventory/InventoryServiceApplication.java`
- [ ] T062 [US3] Add `BOM_REVISION_APPROVED` event to `BomEventPublisher.java`; publish from `BomService.approveDraft()` at `services/inventory-service/src/main/java/com/mes/inventory/kafka/BomEventPublisher.java`
- [ ] T063 [US5] Delete `BillOfMaterials.java` (replaced by `Bom` + `BomRevision`) at `services/inventory-service/src/main/java/com/mes/inventory/bom/domain/BillOfMaterials.java`
- [ ] T064 [US5] Delete `BomStatus.java` (replaced by `RevisionStatus`) at `services/inventory-service/src/main/java/com/mes/inventory/bom/domain/BomStatus.java`
- [ ] T065 [US5] Update `BomControllerIT.java` — update POST body (no `bomRevision` field); update assertions for `revision: 0`, `revisionStatus: "DRAFT"`; update line-add tests to use `componentItemRevisionId` at `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomControllerIT.java`

**Checkpoint**: Phase 4 complete — all BOM IT tests pass, explosion and export work, `./gradlew :services:inventory-service:check` green.

> **Raise PR 2 after this checkpoint** (T037–T065) | CI: `./gradlew :services:inventory-service:check` | Target: `Develop`

---

## Phase 5: US1 + US2 Frontend — Item Master Revision UI [PR 3]

**Goal**: Update Angular Item Master screens to show revision integers, revision status badges, and workflow action buttons (Submit / Approve / Cancel Draft).

**Independent Test**: `ng build --configuration=production` passes; Item Master list shows revision number + status badge; Submit/Approve/Cancel buttons visible based on status.

### Tests (write first — confirm FAILING)

- [ ] T066 [P] [US1] Write Vitest tests for new `item-master-api.service.ts` methods: `submit(id)` calls `POST /api/v1/item-master/:id/submit`; `approve(id)` calls `POST /api/v1/item-master/:id/approve`; `cancelDraft(id)` calls `DELETE /api/v1/item-master/:id/draft`; assert response mapped to `ItemMasterDto` at `frontend/angular/src/app/features/item-master/services/item-master-api.service.spec.ts`

### Implementation

- [ ] T067 [P] [US1] Update `item-master.model.ts` — add `revisionId: string`, `revision: number`, `revisionStatus: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED'`, `hasDraft: boolean` to `ItemMasterDto`; add `RevisionStatus` type export; remove `status` field (replaced by `revisionStatus`) at `frontend/angular/src/app/features/item-master/models/item-master.model.ts`
- [ ] T068 [US1] Update `item-master-api.service.ts` — add `submit(id: string): Observable<ItemMasterDto>`, `approve(id: string): Observable<ItemMasterDto>`, `cancelDraft(id: string): Observable<void>` methods at `frontend/angular/src/app/features/item-master/services/item-master-api.service.ts`
- [ ] T069 [US1] Update `item-master-list.component.ts` — add `revision` column (shows integer `0`, `1`…) and `revisionStatus` badge column to the grid; show `hasDraft` chip ("Draft pending" in amber) alongside status; call `this.cdr.detectChanges()` in all `.subscribe()` callbacks per ERR-MES-059 at `frontend/angular/src/app/features/item-master/pages/item-master-list/item-master-list.component.ts`
- [ ] T070 [P] [US1] Update `default-columns.ts` — replace `{ key: 'status', label: 'Status' }` with `{ key: 'revisionStatus', label: 'Rev Status', visible: true, order: 6 }`; add `{ key: 'revision', label: 'Rev', visible: true, order: 1, locked: true }` at `frontend/angular/src/app/features/item-master/constants/default-columns.ts`
- [ ] T071 [US2] Update `item-master-edit.component.ts` — add "Submit for Approval" button (`[disabled]="saving"`, visible when `item.revisionStatus === 'DRAFT'`); add "Cancel Draft" button (visible when `item.revisionStatus === 'DRAFT'`); disable all form inputs when `revisionStatus === 'PENDING_APPROVAL'`; call `this.cdr.detectChanges()` in submit/cancel subscribe callbacks per ERR-MES-059 at `frontend/angular/src/app/features/item-master/pages/item-master-edit/item-master-edit.component.ts`
- [ ] T072 [US1] Update `item-master-detail.component.ts` — add "Approve" button (visible when `revisionStatus === 'PENDING_APPROVAL'`); show revision number `Rev 0`, `Rev 1` alongside part number in the header; call `this.cdr.detectChanges()` per ERR-MES-059 at `frontend/angular/src/app/features/item-master/pages/item-master-detail/item-master-detail.component.ts`
- [ ] T073 [US1] Update `item-master-create.component.ts` — remove `revision` input field from the create form (system assigns rev=0); update `CreateItemMasterRequest` payload to omit revision at `frontend/angular/src/app/features/item-master/pages/item-master-create/item-master-create.component.ts`

**Checkpoint**: Phase 5 complete — `ng build --configuration=production` green; item master list shows revision integers + status badges; Submit/Approve/Cancel buttons functional.

> **Raise PR 3 after this checkpoint** (T066–T073) | CI: `ng build --configuration=production` | Target: `Develop`

---

## Phase 6: US3 Frontend — BOM Revision UI [PR 4]

**Goal**: Update Angular BOM screens to show revision integers, status badges, and workflow buttons. Update BOM line add form to select APPROVED item revisions.

**Independent Test**: `ng build --configuration=production` passes; BOM browser shows revision + status; BOM authoring has Submit/Approve/Cancel buttons; BOM line component dropdown shows APPROVED item revisions only.

### Tests (write first — confirm FAILING)

- [ ] T074 [P] [US3] Write Vitest tests for new `bom-api.service.ts` methods: `submitBom(id)`, `approveBom(id)`, `cancelBomDraft(id)` — assert correct HTTP method + path at `frontend/angular/src/app/features/bom/services/bom-api.service.spec.ts`

### Implementation

- [ ] T075 [P] [US3] Update `bom.model.ts` — add `bomRevisionId: string`, `revision: number`, `revisionStatus: RevisionStatus`, `hasDraft: boolean` to `BomDto` and `BomSummaryDto`; import `RevisionStatus` from shared model at `frontend/angular/src/app/features/bom/models/bom.model.ts`
- [ ] T076 [US3] Update `bom-api.service.ts` — add `submitBom(id: string)`, `approveBom(id: string)`, `cancelBomDraft(id: string)` methods at `frontend/angular/src/app/features/bom/services/bom-api.service.ts`
- [ ] T077 [US3] Update `bom-browser.component.ts` — add `revision` integer column + `revisionStatus` badge; show `hasDraft` chip; `getCellValue()` already in place (ERR-MES-078 fix from MES-113); call `this.cdr.detectChanges()` per ERR-MES-059 at `frontend/angular/src/app/features/bom/pages/bom-browser/bom-browser.component.ts`
- [ ] T078 [US3] Update `bom-authoring.component.ts` — add "Submit for Approval" button (visible when DRAFT); add "Cancel Draft" button (visible when DRAFT); disable "Add Line" and line edit actions when `revisionStatus !== 'DRAFT'`; call `this.cdr.detectChanges()` per ERR-MES-059 at `frontend/angular/src/app/features/bom/pages/bom-authoring/bom-authoring.component.ts`
- [ ] T079 [US3] Update `bom-header-edit-dialog.component.ts` — show current revision number in dialog header `(Rev {revision})`; disable all form inputs when `revisionStatus === 'PENDING_APPROVAL' || revisionStatus === 'APPROVED'`; add "Submit" button in dialog footer when DRAFT at `frontend/angular/src/app/features/bom/components/bom-header-edit-dialog/bom-header-edit-dialog.component.ts`
- [ ] T080 [US3] Update `add-bom-line-form.component.ts` — change component item dropdown data source to `GET /api/v1/item-master?revisionStatus=APPROVED`; bind `componentItemRevisionId` (item_revision.id) as the line request field; display option label as `"{partNumber} Rev {revision} — {description}"` at `frontend/angular/src/app/features/bom/components/add-bom-line-form/add-bom-line-form.component.ts`

**Checkpoint**: Phase 6 complete — `ng build --configuration=production` green; BOM screens show revision workflow UI; BOM line add uses APPROVED item revisions.

> **Raise PR 4 after this checkpoint** (T074–T080) | CI: `ng build --configuration=production` | Target: `Develop`

---

## Phase 7: US4 — Revision History API (Priority: P2) [PR 5]

**Goal**: `GET /api/v1/item-master/{id}/revisions` and `GET /api/v1/boms/{id}/revisions` return full ordered revision history per entity.

**Independent Test**: `./gradlew :services:inventory-service:check` — revision history endpoints return all revisions ordered by revision ASC with audit fields populated.

### Tests (write first — confirm FAILING)

- [ ] T081 [P] [US4] Add revision history IT tests to `ItemRevisionIT.java` — given item with rev=0 (APPROVED) and rev=1 (DRAFT); `GET /item-master/{id}/revisions` returns both ordered ASC with `revision`, `revisionStatus`, `approvedBy`, `approvedAt`, `createdBy`, `createdAt` at `services/inventory-service/src/test/java/com/mes/inventory/integration/itemmaster/ItemRevisionIT.java`
- [ ] T082 [P] [US4] Add revision history IT tests to `BomRevisionIT.java` — given BOM with rev=0 (APPROVED) and rev=1 (DRAFT); `GET /boms/{id}/revisions` returns both ordered ASC at `services/inventory-service/src/test/java/com/mes/inventory/integration/bom/BomRevisionIT.java`

### Implementation

- [ ] T083 [P] [US4] Create `ItemRevisionSummaryDto.java` — fields: revisionId, revision, revisionStatus, description, approvedBy, approvedAt, submittedBy, submittedAt, createdBy, createdAt at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/dto/ItemRevisionSummaryDto.java`
- [ ] T084 [US4] Add `GET /api/v1/item-master/{id}/revisions` to `ItemMasterController.java` — `@RequiresPrivilege("item-master:records:view")`; call `itemRevisionRepository.findByItemIdOrderByRevisionAsc(itemId)`; map to `List<ItemRevisionSummaryDto>` at `services/inventory-service/src/main/java/com/mes/inventory/itemmaster/api/ItemMasterController.java`
- [ ] T085 [P] [US4] Create `BomRevisionSummaryDto.java` — fields: bomRevisionId, revision, revisionStatus, description, approvedBy, approvedAt, submittedBy, submittedAt, createdBy, createdAt at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/dto/BomRevisionSummaryDto.java`
- [ ] T086 [US4] Add `GET /api/v1/boms/{id}/revisions` to `BomController.java` — `@RequiresPrivilege("item-master:bom:manage")`; call `bomRevisionRepository.findByBomIdOrderByRevisionAsc(bomId)`; map to `List<BomRevisionSummaryDto>` at `services/inventory-service/src/main/java/com/mes/inventory/bom/api/BomController.java`

**Checkpoint**: Phase 7 complete — revision history endpoints return full ordered history with audit fields.

> **Raise PR 5 after this checkpoint** (T081–T086) | CI: `./gradlew :services:inventory-service:check` | Target: `Develop`

---

## Phase 8: Polish & Compliance Verification

**Purpose**: Pre-PR spot-checks, constitution gate verification, and retrospective gate.

- [ ] T087 [P] Pre-PR retrospective check — grep for `getSubject()` without null-safe fallback (ERR-MES-060): `grep -rn "getSubject()" services/inventory-service/src/main/java --include="*.java"` — every match must use `JwtClaimsExtractor` or inline null-safe chain
- [ ] T088 [P] Pre-PR retrospective check — verify every new `@Audited` entity has an `_aud` migration table (ERR-MES-057): confirm V016 covers `item_aud`+`item_revision_aud`, V019 covers `bom_aud`+`bom_revision_aud`
- [ ] T089 [P] Pre-PR retrospective check — grep Angular new `.subscribe()` calls for missing `cdr.detectChanges()` (ERR-MES-059): `grep -rn "\.subscribe(" frontend/angular/src/app/features/item-master frontend/angular/src/app/features/bom --include="*.ts"` — all `next:` + `error:` callbacks that mutate `this.xxx` must end with `this.cdr.detectChanges()`
- [ ] T090 [P] Pre-PR retrospective check — verify no Flyway INSERT is missing NOT NULL audit columns (ERR-MES-077): read V015, V018, V022; confirm `created_by`, `modified_by` present in all INSERTs
- [ ] T091 [P] Pre-PR retrospective check — grep `libs/` for existing `getCellValue`/UDF helpers before adding new cell renderers (ERR-MES-078): confirm bom-browser + item-master-list use `getCellValue()` not `item[col.key]`
- [ ] T092 Verify Constitution Check gates in `plan.md` are all ✅ PASS — update `III — AI-Approved` gate status after owner review
- [ ] T093 Confirm `./gradlew :services:inventory-service:check` passes zero failures (all 5 PR phases)
- [ ] T094 Confirm `ng build --configuration=production` passes zero errors (PR 3 + PR 4 phases)
- [ ] T095 Retrospective gate — review session for new error patterns; add entries to `docs/governance/MES-ERR-001_Agent_Error_Log.md` if new lessons identified; promote to index if root cause is clear at `docs/governance/MES-ERR-001_Agent_Error_Log.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1** (T001): No dependencies — run immediately
- **Phase 2** (T002–T009): Depends on Phase 1 ✅ — blocks all backend phases
- **Phase 3** (T010–T036): Depends on Phase 2 ✅ — Item workflow; maps to PR 1
- **Phase 4** (T037–T065): Depends on Phase 3 merged to Develop ✅ — BOM workflow; maps to PR 2
- **Phase 5** (T066–T073): Depends on Phase 3 merged to Develop ✅ — can start in parallel with Phase 4
- **Phase 6** (T074–T080): Depends on Phase 4 AND Phase 5 both merged ✅ — maps to PR 4
- **Phase 7** (T081–T086): Depends on Phase 3 + Phase 4 merged ✅ — P2, can be deferred
- **Phase 8** (T087–T095): Runs as part of each PR pre-flight

### User Story Dependencies

- **US1** (P1): Depends on Phase 2 only — can start immediately after foundations
- **US2** (P1): Depends on US1 backend complete (same PR 1)
- **US3** (P1): Depends on US1/US2 backend (PR 1) merged — BOM uses `item_revision` FK
- **US4** (P2): Depends on US1 + US3 backend merged — pure query endpoints
- **US5** (P1): Data migration — runs as part of US1 (item) in PR 1 and US3 (BOM) in PR 2

### Parallel Opportunities Within Each PR

**PR 1 parallelisable**: T005, T006, T008, T009 (entities + repos); T010, T011, T012, T013, T014 (tests + DTOs)

**PR 2 parallelisable**: T037, T043, T044, T046, T047, T048 (migrations + entities + tests + repos)

**PR 3 parallelisable**: T066, T067, T070 (tests + models + columns)

**PR 4 parallelisable**: T074, T075 (tests + models)

---

## Implementation Strategy

### MVP: PR 1 Only

1. Complete Phase 1 (pre-flight)
2. Complete Phase 2 (foundations: V014–V016, entities)
3. Complete Phase 3 (US1+US2+US5 item backend)
4. Raise and merge PR 1 → Item Master revision workflow live in Develop

### Incremental Delivery

1. PR 1 merged → Item Master revision workflow complete (backend)
2. PR 2 merged → BOM revision workflow complete (backend)
3. PR 3 merged → Item Master revision workflow complete (frontend)
4. PR 4 merged → BOM revision workflow complete (frontend) — **full P1 scope shipped**
5. PR 5 (P2) merged → Revision history API complete

---

## Phase N+1: Compliance Verification *(bundled into each PR pre-flight via T087–T091)*

- [ ] T096 Verify Constitution Check gates: Spec-First ✅, TDD ✅, AI-Approved (⏳ pending owner sign-off), Compliance by Design ✅, Auditability ✅, ISA-95 ✅, Security-First ✅, Multi-Org ✅
- [ ] T097 [P] Confirm all data mutations produce audit log entries — `@Audited` on Item, ItemRevision, Bom, BomRevision, BomLine confirmed in entity source
- [ ] T098 [P] Confirm `org_id` scoping on all new entities — Item.orgId, Bom.orgId present; all service methods extract orgId from JWT via `JwtClaimsExtractor`
- [ ] T099 [P] Confirm all new controller endpoints are idempotent — submit/approve return 200 if already in target state (no duplicate action)
- [ ] T100 Confirm all test failures during this feature are logged as Jira defects and resolved before closing MES-114 as Done
