# Research: Automated Revision Numbering — MES-114

## Decision 1 — Schema approach

**Decision**: Option B — parent/child tables (`item` + `item_revision`; `bom` + `bom_revision`).

**Rationale**: Chosen by project owner. Required for the planned Revision History screen (US4/DEF-004). A single-table approach with a `revision` column and partial-unique constraint could enforce uniqueness, but it cannot provide a clean revision history list without coupling to Envers. The parent/child model stores each revision as a first-class row, making `GET /item-master/{id}/revisions` a simple query rather than an Envers audit log traversal.

**Alternatives considered**:
- Option A (single table, `revision INTEGER` + `revisionStatus` column): simpler migration, but no clean history without Envers. Rejected because owner confirmed revision history UI is coming (DEF-004).
- Option C (event sourcing): rejected as architectural over-engineering for this phase.

---

## Decision 2 — Migration strategy for existing data

**Decision**: Flyway data migrations (V015, V018). Integer revision assigned as `ROW_NUMBER() OVER (PARTITION BY org_id, part_number ORDER BY created_at ASC) - 1`. All migrated rows receive `revision_status = 'APPROVED'`.

**Rationale**: All data currently in `item_master` and `bill_of_materials` represents production-live records. Setting them to APPROVED ensures no existing business workflows are disrupted — downstream processes (BOM lines, work orders) continue working on the day the migration runs.

The old free-form revision string is not preserved; it is not stored anywhere. If needed for audit purposes, it exists in `item_master_aud_legacy` (the renamed Envers table).

**SQL pattern for integer assignment** (item master):
```sql
INSERT INTO inventory.item (id, org_id, part_number, created_by, created_at)
SELECT DISTINCT ON (org_id, part_number) 
    gen_random_uuid(), org_id, part_number, created_by, MIN(created_at)
FROM inventory.item_master
GROUP BY org_id, part_number;

INSERT INTO inventory.item_revision (
    id, item_id, revision, revision_status, description, ...all other fields...,
    approved_by, approved_at, created_by, created_at, modified_by, modified_at
)
SELECT 
    im.id,  -- preserve UUID so any FK reference from other tables still resolves during migration
    i.id,
    (ROW_NUMBER() OVER (PARTITION BY im.org_id, im.part_number ORDER BY im.created_at ASC) - 1)::INTEGER,
    'APPROVED',
    im.description,
    ... all other fields ...,
    im.modified_by,  -- use last modifier as approvedBy for migrated rows
    im.modified_at,
    im.created_by,
    im.created_at,
    im.modified_by,
    im.modified_at
FROM inventory.item_master im
JOIN inventory.item i ON i.org_id = im.org_id AND i.part_number = im.part_number;
```

Note: `item_revision.id` is set to `item_master.id` during migration so that `bom_line.component_item_revision_id` can be populated from the existing `bom_line.component_item_id` FK directly. This avoids a separate mapping table.

---

## Decision 3 — Envers audit table strategy

**Decision**: Rename existing `item_master_aud` → `item_master_aud_legacy` and `bill_of_materials_aud` → `bill_of_materials_aud_legacy`. Create new `item_aud`, `item_revision_aud`, `bom_aud`, `bom_revision_aud` tables in V016 and V019 respectively.

**Rationale**: ERR-MES-057 mandates that every `@Audited` entity has a matching `_aud` table in the same migration. Dropping the old Envers tables would lose pre-migration audit history. Renaming preserves the history while allowing the new entities to get clean Envers tables.

---

## Decision 4 — One-draft constraint mechanism

**Decision**: PostgreSQL partial unique index:
```sql
CREATE UNIQUE INDEX uq_item_revision_one_draft
    ON inventory.item_revision (item_id)
    WHERE revision_status = 'DRAFT';

CREATE UNIQUE INDEX uq_bom_revision_one_draft
    ON inventory.bom_revision (bom_id)
    WHERE revision_status = 'DRAFT';
```

**Rationale**: Database-enforced constraint is safer than service-layer checks under concurrent writes. The partial index only applies to DRAFT rows, so multiple APPROVED revisions per item are unrestricted. A concurrent second-draft attempt will receive a constraint violation (caught by Spring Data and translated to HTTP 409).

---

## Decision 5 — API URL preservation

**Decision**: Existing URL paths (`/api/v1/item-master/**`, `/api/v1/boms/**`) are preserved. New workflow endpoints added as sub-resources.

**New endpoints**:
```
POST /api/v1/item-master/{id}/submit        — DRAFT → PENDING_APPROVAL
POST /api/v1/item-master/{id}/approve       — PENDING_APPROVAL → APPROVED
DELETE /api/v1/item-master/{id}/draft       — hard-delete DRAFT
GET  /api/v1/item-master/{id}/revisions     — list all revisions (P2)

POST /api/v1/boms/{id}/submit
POST /api/v1/boms/{id}/approve
DELETE /api/v1/boms/{id}/draft
GET  /api/v1/boms/{id}/revisions            — list all revisions (P2)
```

`GET /api/v1/item-master` (list) returns one entry per item identity showing:
- current APPROVED revision fields (or DRAFT if no APPROVED exists)
- `revisionStatus` badge
- `hasDraft: true` if a DRAFT also exists alongside an APPROVED revision

**Rationale**: Breaking URL changes require frontend updates to all consumers. Preserving paths limits the PR blast radius.

---

## Decision 6 — BOM auto-copy on first edit after approval

**Decision**: When `BomService.patch(bomId, ...)` is called on a BOM with no current DRAFT, the service creates a new `BomRevision` at `max_approved + 1` and copies all `BomLine` rows from the last APPROVED revision into the new draft. The patch is then applied to the new draft.

**Rationale**: A user clicking "Edit BOM" expects to see the current approved structure as their starting point. Without auto-copy, the new draft would be empty and they'd have to re-add every line from scratch — unusable. The auto-copy mirrors how PLM tools like Windchill and Agile handle ECO-driven revisions.

---

## Decision 7 — RevisionStatus vs existing BomStatus and ItemStatus

**Decision**: Introduce new enum `RevisionStatus { DRAFT, PENDING_APPROVAL, APPROVED }` applied to both `item_revision` and `bom_revision`. Retire `ItemStatus` and `BomStatus` enums (delete classes, update all references).

**Old→New mapping for migration**:

| Old ItemStatus | New RevisionStatus |
|---|---|
| ACTIVE | APPROVED |
| OBSOLETE | APPROVED (OBSOLETE status deferred to DEF-003) |

| Old BomStatus | New RevisionStatus |
|---|---|
| DRAFT | APPROVED (all existing data treated as production-live) |
| RELEASED | APPROVED |
| OBSOLETE | APPROVED |

**Rationale**: All existing data in the production DB represents real configurations. Setting them to APPROVED is the only safe migration path — DRAFT on existing data would block downstream processes.

---

## Decision 8 — Cascade delete for BOM draft cancellation

**Decision**: `BomRevision` entity has `cascade = CascadeType.ALL` + `orphanRemoval = true` on its `bomLines` collection. Deleting a `BomRevision` (draft cancel) automatically deletes all attached `BomLine` rows.

**Rationale**: Prevents orphaned BOM lines in the database. Alternative (manual delete before revision delete) is error-prone and creates a window where FK constraints could fire.
