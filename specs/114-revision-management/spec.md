# Feature Specification: Automated Revision Numbering — Item Master & BOM

**Feature Branch**: `114-revision-management`

**Created**: 2026-06-09

**Status**: Draft

**Input**: Jira Epic MES-114 — P3 · Automated Revision Numbering — Item Master & BOM

---

## Background

The current `item_master` and `bill_of_materials` tables use a free-form `VARCHAR(20)` `revision` field. This causes two problems:

1. **Duplicate risk** — users can enter the same revision string on different records, or accidentally reuse a cancelled draft's label.
2. **No traceability** — there is no system-managed audit trail linking each change to an ordered version number.

This feature replaces free-form revision text with system-managed integer sequences and introduces a formal approval lifecycle (Draft → Pending Approval → Approved) so that downstream processes (work orders, receiving, inspection) can only consume approved revision data.

---

## Data Model — Option B (Parent / Child)

### Current schema

```
inventory.item_master      (id, org_id, part_number, revision VARCHAR, status VARCHAR, …)
inventory.bill_of_materials (id, org_id, parent_item_id, bom_revision VARCHAR, status VARCHAR, …)
inventory.bom_line         (id, bom_id → bill_of_materials, …)
```

### Target schema

```
inventory.item             (id, org_id, part_number)              — identity only
inventory.item_revision    (id, item_id → item, revision INTEGER,
                             revision_status, all current item fields)

inventory.bom              (id, org_id, parent_item_id → item)    — identity only
inventory.bom_revision     (id, bom_id → bom, revision INTEGER,
                             revision_status, all current BOM header fields)
inventory.bom_line         (id, bom_revision_id → bom_revision,   — FK changed
                             component_item_revision_id → item_revision, …)
```

### Revision status lifecycle

```
DRAFT  ──submit──►  PENDING_APPROVAL  ──approve──►  APPROVED
  │                                                      │
  └──cancel (hard delete)                            (immutable)
```

- Only one DRAFT revision may exist per item/BOM at any time.
- An APPROVED revision is immutable; any subsequent edit creates a new DRAFT at `revision = max(approved_revision) + 1`.
- Cancelled drafts are hard-deleted; the gap in the integer sequence is acceptable.
- Only APPROVED item revisions may be referenced in BOM lines (`component_item_revision_id`).

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Create Item and Submit First Revision for Approval (Priority: P1)

A materials administrator creates a new item. The system automatically assigns `revision = 0` in DRAFT status. The administrator fills in all required fields, then submits the draft for approval. An approver reviews and approves it. The item is now visible and usable in downstream processes (BOM, Work Orders).

**Why this priority**: This is the foundation workflow. No other story delivers value until items can be approved.

**Independent Test**: A `MATERIALS_ADMIN` user creates a new item (part number `TEST-001`, description `Test bracket`). The API returns HTTP 201 with `revision = 0`, `revisionStatus = DRAFT`. The user POSTs to `/items/{id}/submit` — status changes to `PENDING_APPROVAL`. A `SYSTEM_ADMIN` POSTs to `/items/{id}/approve` — status changes to `APPROVED`. The item is returned in `GET /items` with `revisionStatus = APPROVED`.

**Acceptance Scenarios**:

1. **Given** no existing item for `(orgId, partNumber = "TEST-001")`, **When** a `MATERIALS_ADMIN` POSTs a new item record, **Then** the system creates an `item` identity row and an `item_revision` row with `revision = 0`, `revisionStatus = DRAFT`, and returns HTTP 201.
2. **Given** an item revision in `DRAFT` status, **When** the user POSTs `/items/{id}/submit`, **Then** the revision status changes to `PENDING_APPROVAL` and HTTP 200 is returned.
3. **Given** an item revision in `PENDING_APPROVAL`, **When** a user with `item-master:revisions:approve` POSTs `/items/{id}/approve`, **Then** the revision status changes to `APPROVED` and HTTP 200 is returned.
4. **Given** an item revision in `PENDING_APPROVAL`, **When** a user without `item-master:revisions:approve` POSTs `/items/{id}/approve`, **Then** HTTP 403 is returned.
7. **Given** an item revision in `PENDING_APPROVAL`, **When** an approver POSTs `/items/{id}/reject` with a `rejectionReason`, **Then** the revision status returns to `DRAFT`, `rejectedBy`, `rejectedAt`, and `rejectionReason` are persisted on the revision, and HTTP 200 is returned with the updated revision data.
8. **Given** an item revision in `PENDING_APPROVAL`, **When** an approver POSTs `/items/{id}/reject` with an empty or missing `rejectionReason`, **Then** HTTP 422 is returned ("Rejection reason is required").
9. **Given** an item revision NOT in `PENDING_APPROVAL`, **When** `/items/{id}/reject` is called, **Then** HTTP 409 is returned.
5. **Given** no DRAFT revision exists for an item, **When** `/items/{id}/submit` is called, **Then** HTTP 409 is returned with message "No draft revision exists".
6. **Given** an unauthenticated request, **When** any item revision endpoint is called, **Then** HTTP 401 is returned.

---

### User Story 2 — Edit an Approved Item (Create Next Draft) (Priority: P1)

A materials administrator edits an item that already has an APPROVED revision. The system automatically creates a new DRAFT revision at `revision = N+1`. Only one DRAFT may exist at a time. The administrator may save incremental edits, cancel the draft (hard delete), or submit for approval.

**Why this priority**: The entire revision increment mechanism depends on this story being reliable.

**Independent Test**: An item with `revision = 0, status = APPROVED` exists. The user PATCHes the item description. The API creates `revision = 1, status = DRAFT` and returns the new draft. The user calls DELETE `/items/{id}/draft` — the draft is hard-deleted and the item reverts to showing `revision = 0, APPROVED`. The user edits again — a new `revision = 1, DRAFT` is created (not revision = 2).

**Acceptance Scenarios**:

1. **Given** an item with only an APPROVED revision, **When** the user PATCHes any field, **Then** a new `item_revision` with `revision = currentApproved + 1`, `revisionStatus = DRAFT` is created and returned; the APPROVED revision is unchanged.
2. **Given** an item already has a DRAFT revision, **When** the user attempts to create a second DRAFT (via another PATCH or POST), **Then** HTTP 409 is returned with "A draft revision already exists".
3. **Given** an item has a DRAFT revision at `revision = 1`, **When** the user DELETEs `/items/{id}/draft`, **Then** the DRAFT row is hard-deleted, HTTP 204 is returned, and the item's current revision reverts to the last APPROVED revision.
4. **Given** a DRAFT at `revision = 1` was hard-deleted, **When** the user creates a new DRAFT, **Then** the new draft is assigned `revision = 1` again (gap from the previous cancellation is overwritten, restarting from `max_approved + 1`).
5. **Given** an item revision is in `APPROVED` status, **When** the user attempts to PATCH that specific revision directly, **Then** HTTP 409 is returned ("Approved revisions are immutable").

---

### User Story 3 — BOM Revision Workflow (Priority: P1)

A product engineer creates a new BOM for an approved item. The BOM starts at `revision = 0, DRAFT`. After adding all BOM lines the engineer submits for approval. An approver approves it. Only approved BOM lines referencing approved item revisions are available for production planning.

**Why this priority**: BOM is the primary configuration document; work order materialisation cannot proceed until BOMs can be approved.

**Independent Test**: An approved item `PART-001` at `revision = 0` exists. An engineer creates a new BOM for `PART-001` — the system creates `bom` identity + `bom_revision` at `revision = 0, DRAFT`. The engineer adds a BOM line referencing approved item `PART-002 rev 0`. The engineer submits. An approver approves. The BOM is returned in the BOM browser with `revisionStatus = APPROVED`.

**Acceptance Scenarios**:

1. **Given** an approved item identity `PART-001`, **When** an engineer POSTs a new BOM for that item, **Then** a `bom` identity row and a `bom_revision` at `revision = 0, revisionStatus = DRAFT` are created; HTTP 201 is returned.
2. **Given** a BOM line is being added to a DRAFT BOM revision, **When** the `componentItemRevisionId` references an item revision with `revisionStatus != APPROVED`, **Then** HTTP 422 is returned with "Component must be an approved item revision".
3. **Given** a BOM revision in DRAFT, **When** the engineer POSTs `/boms/{id}/submit`, **Then** status changes to `PENDING_APPROVAL`.
4. **Given** a BOM revision in PENDING_APPROVAL, **When** an approver POSTs `/boms/{id}/approve`, **Then** status changes to `APPROVED`.
5. **Given** a DRAFT BOM revision, **When** the engineer DELETEs `/boms/{id}/draft`, **Then** the draft and all its BOM lines are hard-deleted; HTTP 204 is returned.
10. **Given** a BOM revision in `PENDING_APPROVAL`, **When** an approver POSTs `/boms/{id}/reject` with a `rejectionReason`, **Then** the BOM revision returns to `DRAFT`, `rejectedBy`, `rejectedAt`, and `rejectionReason` are persisted, and HTTP 200 is returned.
6. **Given** a BOM in APPROVED status, **When** the engineer edits the BOM header, **Then** a new DRAFT at `revision = N+1` is created with all existing BOM lines copied from the last APPROVED revision.
7. **Given** a DRAFT BOM revision with one or more lines, **When** the engineer updates a line's quantity or find-number, **Then** the change is persisted and HTTP 200 is returned; the BOM revision remains in DRAFT.
8. **Given** a DRAFT BOM revision with one or more lines, **When** the engineer deletes a specific line, **Then** that `bom_line` row is hard-deleted and HTTP 204 is returned.
9. **Given** a BOM revision in `PENDING_APPROVAL` or `APPROVED` status, **When** any line write operation (add, update, delete) is attempted, **Then** HTTP 409 is returned ("BOM revision is not in DRAFT status").

---

### User Story 4 — Revision History Screen (Priority: P2)

A quality engineer needs to view the full revision history of an item or BOM — who created each revision, what changed, and when it was approved — for AS9100D traceability.

**Why this priority**: Audit trail is required for aerospace compliance but can follow the approval workflow as a second phase.

**Independent Test**: An item has `revision = 0 (APPROVED)` and `revision = 1 (APPROVED)`. Calling `GET /items/{id}/revisions` returns both revisions ordered by revision number, each with `createdBy`, `createdAt`, `approvedBy` (user who called `/approve`), and `approvedAt`.

**Acceptance Scenarios**:

1. **Given** an item with three revisions (0=APPROVED, 1=APPROVED, 2=DRAFT), **When** `GET /items/{id}/revisions` is called, **Then** all three revisions are returned ordered by `revision ASC`, each with status and audit fields.
2. **Given** an item with an APPROVED revision, **When** `GET /items/{id}/revisions/{revisionNumber}` is called with a valid revision number, **Then** the full field snapshot for that revision is returned.
3. **Given** the same API is called for a BOM (`GET /boms/{id}/revisions`), **Then** all BOM revisions are returned with the same structure.

---

### User Story 5 — Data Migration — Existing Free-Form Revisions to Integers (Priority: P1)

All existing `item_master` and `bill_of_materials` rows are migrated to the new parent/child schema. Existing free-form revision strings are replaced with integers assigned by creation order. All migrated rows are assigned `revisionStatus = APPROVED` so no existing data is blocked from downstream use.

**Why this priority**: Without migration, the new schema cannot co-exist with existing data and the service will not start.

**Independent Test**: After running Flyway migrations against a populated database, the `item` table contains one row per unique `(org_id, part_number)` combination from `item_master`. The `item_revision` table contains one row per original `item_master` row, with `revision` integers assigned sequentially (0, 1, 2…) ordered by `created_at` within each `(org_id, part_number)` group, and `revision_status = APPROVED`.

**Acceptance Scenarios**:

1. **Given** two rows in `item_master` with `part_number = "P001"` and different free-form revisions `"A"` and `"B"` (created in that order), **When** the migration runs, **Then** one `item` identity row exists for `P001`, and two `item_revision` rows exist with `revision = 0` (for "A") and `revision = 1` (for "B"), both `APPROVED`.
2. **Given** rows in `item_master` with unique `part_number` values (no siblings), **When** the migration runs, **Then** each becomes `revision = 0, APPROVED`.
3. **Given** the `bill_of_materials` table has rows with `bom_revision = "Rev A"`, **When** the migration runs, **Then** each unique `(org_id, parent_item_id)` becomes a `bom` identity, and each original BOM row becomes a `bom_revision` with `revision = 0, APPROVED`.
4. **Given** existing `bom_line` rows reference `bom_id → bill_of_materials`, **When** the migration runs, **Then** `bom_line.bom_revision_id` is updated to reference the corresponding new `bom_revision` row; `bom_line.component_item_revision_id` is updated to reference the migrated `item_revision` row for the component.

---

### Edge Cases

- What happens when a part number has many free-form revisions in the old schema that are not in alphabetical/temporal order? Migration uses `created_at` ordering only; the mapping from old text revision to integer is logged but the old text value is not stored.
- What happens when a user tries to approve their own submission? No constraint in this phase (workflow MES-112 will add a 4-eyes rule later).
- What happens when a BOM line's component item revision is approved, then that approval is somehow revoked? Not in scope — approved items are immutable. Revocation is a future compliance requirement.
- What happens when two users simultaneously try to create a draft for the same item? A unique constraint on `(item_id, revision_status = DRAFT)` (enforced via partial index) prevents this; the second request gets HTTP 409.
- What happens when an engineer submits a BOM DRAFT that has zero BOM lines? The system returns HTTP 422 ("BOM must have at least one line before submission"). This validation fires in `BomService.submitDraft()` before any status transition.
- What happens when a user tries to clone an item that has never been approved (DRAFT-only)? The system returns HTTP 422 ("Source item has no approved revision — approve it before cloning"). Only items with at least one APPROVED revision may be the source of a clone operation.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST manage `item_revision.revision` as a system-assigned INTEGER; users MUST NOT be able to set or override it.
- **FR-002**: System MUST enforce that only one DRAFT revision exists per item or BOM at any time.
- **FR-003**: System MUST enforce the lifecycle: DRAFT → PENDING_APPROVAL → APPROVED; no other transitions are valid in this phase.
- **FR-004**: System MUST hard-delete cancelled DRAFT revisions (no soft-delete, no orphan cleanup jobs).
- **FR-005**: When a DRAFT is cancelled, the next draft MUST restart from `max(approved_revision) + 1` (not `max_all_revisions + 1`).
- **FR-006**: APPROVED revisions MUST be immutable — PATCH on an approved revision MUST return HTTP 409.
- **FR-007**: BOM lines MUST reference a specific `item_revision` (not the item identity), and that revision MUST have `revisionStatus = APPROVED`.
- **FR-008**: When editing an APPROVED BOM, the system MUST auto-copy all existing BOM lines from the last APPROVED revision into the new DRAFT.
- **FR-009**: System MUST migrate all existing `item_master` rows to the new parent/child schema via a Flyway migration with no data loss; all migrated rows receive `revisionStatus = APPROVED`.
- **FR-010**: System MUST migrate all existing `bill_of_materials` and `bom_line` rows to the new schema via a Flyway migration; FK references must be updated.
- **FR-011**: `GET /items` and `GET /boms` MUST return one entry per item/BOM identity showing the most stable revision available, using this display priority: (1) APPROVED preferred if one exists; (2) PENDING_APPROVAL if no APPROVED exists; (3) DRAFT if neither APPROVED nor PENDING_APPROVAL exists. All three revision statuses are visible to any authenticated user with `item-master:records:view`.
- **FR-012**: List endpoints MUST include `hasDraft: true` whenever any in-progress revision (DRAFT or PENDING_APPROVAL) exists for the item/BOM. `hasDraft` is set on draft initiation and cleared only when the revision is APPROVED or hard-deleted. The in-progress revision is accessible via `GET /items/{id}?revisionStatus=DRAFT` or `?revisionStatus=PENDING_APPROVAL`.
- **FR-013**: `approvedBy` and `approvedAt` MUST be recorded on `item_revision` and `bom_revision` when approved.
- **FR-014**: Deleting a DRAFT BOM also MUST hard-delete all `bom_line` rows attached to that DRAFT revision.
- **FR-015**: `POST /api/v1/item-master/{id}/clone` MUST clone from the source item's current APPROVED `item_revision`.
- **FR-016**: `POST /boms/{id}/submit` MUST return HTTP 422 with "BOM must have at least one line before submission" if the DRAFT `bom_revision` has zero `bom_line` rows. An empty BOM may not be submitted for approval.
- **FR-017**: BOM line write operations (add, update quantity/find-number, delete individual line) MUST be permitted only when the parent `bom_revision` is in `DRAFT` status. Any write attempt against a `PENDING_APPROVAL` or `APPROVED` BOM revision MUST return HTTP 409 ("BOM revision is not in DRAFT status").
- **FR-018**: An approver with `item-master:revisions:approve` (or `bom:revisions:approve`) MUST be able to reject a `PENDING_APPROVAL` revision via `POST /item-master/{id}/reject` (or `POST /boms/{id}/reject`). The request MUST include a non-empty `rejectionReason`. On rejection: `revisionStatus` transitions back to `DRAFT`; `rejectedBy` (actor), `rejectedAt` (timestamp), and `rejectionReason` are stored on the revision record. The draft is NOT deleted — it reverts to editable state so the submitter can address the feedback and re-submit. If the source item has no APPROVED revision (DRAFT-only), the system MUST return HTTP 422 with message "Source item has no approved revision — approve it before cloning". The cloned item receives a new `item` identity and a new `item_revision` at `revision = 0, revisionStatus = DRAFT`.

### Key Entities

- **`item`**: Identity anchor for a part. Carries only `id`, `org_id`, `part_number`. Has zero to many `item_revision` children.
- **`item_revision`**: A specific version of an item's attributes. Carries `revision INTEGER`, `revisionStatus`, `approvedBy`, `approvedAt`, and all current item data fields (description, unitOfMeasure, classification, etc.).
- **`bom`**: Identity anchor for a bill of materials. Carries `id`, `org_id`, `parent_item_id → item`.
- **`bom_revision`**: A specific version of a BOM header. Carries `revision INTEGER`, `revisionStatus`, `approvedBy`, `approvedAt`, and all current BOM header fields. Has zero to many `bom_line` children.
- **`bom_line`**: A line in a specific BOM revision. References `bom_revision_id` (not `bom_id`). References `component_item_revision_id` — must be an APPROVED item revision.
- **`RevisionStatus`**: Enum — `DRAFT`, `PENDING_APPROVAL`, `APPROVED`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After migration, `item_revision` row count equals the original `item_master` row count; `bom_revision` row count equals the original `bill_of_materials` row count.
- **SC-002**: `GET /items` returns all items within 500 ms for datasets up to 10,000 records.
- **SC-003**: Attempting to add a second DRAFT revision returns HTTP 409 in 100% of concurrent test scenarios.
- **SC-004**: All existing integration tests pass after the schema migration (no regression to BOM explosion, BOM browser, Item Master CRUD).
- **SC-005**: SonarCloud quality gate passes (≥ 80% line coverage on new/changed code, zero new vulnerabilities).

---

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §8.4.3 configuration control — each approved revision must be immutable and traceable; revision history must be retained |
| AS9102 (FAI) | Partial | FAI links to a specific item revision; `item_revision.id` becomes the FAI configuration reference |
| AS9131 (NCM) | No | Non-conformance management does not directly depend on revision numbering |
| NIST SP 800-171 / CMMC | No | Configuration data traceability is a quality requirement, not a cybersecurity one |
| 21 CFR Part 11 / Annex 11 | No | Aerospace-only feature; no FDA-regulated data |
| ISA-95 | Yes | Part 2 Material Class versioning — `item_revision` maps to a versioned MaterialClass definition |

---

## Assumptions

- Routes are excluded from this feature (deferred to MES-9 when the Route entity is built).
- The `ENGINEER` role retains the ability to edit items and BOMs in DRAFT status; approval requires a new privilege `item-master:revisions:approve` and `bom:revisions:approve`.
- The "Approve" action is a simple one-step operation in this phase; the MES-112 workflow engine integration is explicitly deferred.
- Existing Envers audit tables (`item_master_aud`, `bill_of_materials_aud`, `bom_line_aud`) will be renamed or replaced as part of the migration; the revision history is now stored in the `item_revision` / `bom_revision` tables rather than via Envers.
- The `@Audited` annotation on `ItemMaster` and `BillOfMaterials` will be moved to `ItemRevision` and `BomRevision` respectively.
- The `component_item_revision_id` FK on `bom_line` uses the approved item revision UUID as the stable reference; if an item later gets a new revision, existing BOM lines are unaffected.
- No UI for "revision history screen" is required in this phase — the API endpoint is sufficient for P1 delivery; the UI is P2.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Route revision management | Route entity does not exist yet | Routes cannot have version-controlled configurations | MES-9 | — |
| DEF-002 | Workflow engine integration (MES-112) for 4-eyes approval | MES-112 is a separate epic not yet started | Submitter can approve their own revision; no parallel approval chain. Note: basic reject-with-reason (PENDING_APPROVAL → DRAFT) is implemented in MES-114 (FR-018); full 4-eyes chain deferred | Post-MES-112 | — |
| DEF-003 | OBSOLETE revision status transition | Low priority; APPROVED revisions become superseded by newer ones implicitly | Old revisions remain queryable indefinitely with no OBSOLETE marker | P3 / Post-GA | — |
| DEF-004 | Revision history UI screen | P2 scope; API endpoint is sufficient for traceability at this phase | Engineers must use the API directly to view full revision history | P3 | — |
| DEF-005 | ECO (Engineering Change Order) linkage to revision | ECO module not yet implemented | Revisions cannot be linked to the ECO that drove the change | MES-9 | — |
| DEF-006 | "Compare revisions" diff view | Complex UI; no immediate compliance requirement | Users cannot easily see what changed between revisions in the UI | Post-GA | — |

---

## Clarifications

### Session 2026-06-09

- Q: What should happen when cloning an item that has no APPROVED revision (DRAFT-only)? → A: HTTP 422 — clone permitted only when source has at least one APPROVED revision.
- Q: Should submitting a BOM DRAFT with zero BOM lines be allowed? → A: No — HTTP 422 ("BOM must have at least one line before submission"); validation fires in BomService.submitDraft() before any status transition.
- Q: Can BOM lines be updated or deleted individually within an existing DRAFT BOM revision, or is cancel+recreate required? → A: Full CRUD — add, update quantity/find-number, and delete individual lines are all permitted while the BOM revision is in DRAFT; all line writes return HTTP 409 once PENDING_APPROVAL or APPROVED.
- Q: Who can see items/BOMs in PENDING_APPROVAL status in list and detail endpoints? → A: All authenticated users with `item-master:records:view` — same access as DRAFT and APPROVED; no privilege restriction beyond the existing records:view gate.
- Q: When an approver rejects a PENDING_APPROVAL revision, what transition occurs and should a reason be captured? → A: Option B — `POST /item-master/{id}/reject` (and `/boms/{id}/reject`) transitions PENDING_APPROVAL → DRAFT; approver MUST supply a non-empty `rejectionReason`; fields `rejectedBy`, `rejectedAt`, `rejectionReason` stored on the revision; revision survives (not deleted) for submitter to correct and re-submit.
