# Data Model: Automated Revision Numbering — MES-114

## Entity Diagram

```
inventory.item (identity)
  id UUID PK
  org_id UUID NOT NULL
  part_number VARCHAR(100) NOT NULL
  UNIQUE (org_id, part_number)
      │
      │ 1:N
      ▼
inventory.item_revision (versioned)
  id UUID PK
  item_id UUID FK → item(id)
  revision INTEGER NOT NULL
  revision_status VARCHAR(20) NOT NULL  -- DRAFT | PENDING_APPROVAL | APPROVED
  ... all item data fields ...
  submitted_by VARCHAR(255)
  submitted_at TIMESTAMPTZ
  approved_by VARCHAR(255)
  approved_at TIMESTAMPTZ
  UNIQUE (item_id, revision)
  PARTIAL UNIQUE INDEX (item_id) WHERE revision_status = 'DRAFT'


inventory.bom (identity)
  id UUID PK
  org_id UUID NOT NULL
  parent_item_id UUID FK → inventory.item(id)
  UNIQUE (org_id, parent_item_id)
      │
      │ 1:N
      ▼
inventory.bom_revision (versioned)
  id UUID PK
  bom_id UUID FK → bom(id)
  revision INTEGER NOT NULL
  revision_status VARCHAR(20) NOT NULL
  ... all bom header fields ...
  submitted_by, submitted_at, approved_by, approved_at
  UNIQUE (bom_id, revision)
  PARTIAL UNIQUE INDEX (bom_id) WHERE revision_status = 'DRAFT'
      │
      │ 1:N
      ▼
inventory.bom_line
  id UUID PK
  bom_revision_id UUID FK → bom_revision(id)       ← was: bom_id → bill_of_materials
  component_item_revision_id UUID FK → item_revision(id) ← was: component_item_id → item_master
  quantity NUMERIC(18,6) NOT NULL
  ... effectivity fields unchanged ...
```

---

## DDL: V014 — Create `item` and `item_revision` Tables

```sql
CREATE TABLE inventory.item (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id      UUID         NOT NULL,
    part_number VARCHAR(100) NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_item PRIMARY KEY (id),
    CONSTRAINT uq_item_org_part UNIQUE (org_id, part_number)
);

CREATE INDEX idx_item_org ON inventory.item (org_id);

CREATE TABLE inventory.item_revision (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    item_id                UUID         NOT NULL,
    revision               INTEGER      NOT NULL,
    revision_status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    description            VARCHAR(500) NOT NULL,
    unit_of_measure        VARCHAR(20)  NOT NULL,
    cage_code              VARCHAR(10),
    classification         VARCHAR(30)  NOT NULL,
    make_buy_code          VARCHAR(10)  NOT NULL,
    traceability_method    VARCHAR(15)  NOT NULL,
    shelf_life_controlled  BOOLEAN      NOT NULL DEFAULT false,
    shelf_life_days        INTEGER,
    step_part_ref          VARCHAR(255),
    counterfeit_risk_level VARCHAR(10),
    approved_suppliers     JSONB,
    verification_required  BOOLEAN      NOT NULL DEFAULT false,
    custom_fields          JSONB,

    submitted_by  VARCHAR(255),
    submitted_at  TIMESTAMPTZ,
    approved_by   VARCHAR(255),
    approved_at   TIMESTAMPTZ,
    rejected_by   VARCHAR(255),
    rejected_at   TIMESTAMPTZ,
    rejection_reason VARCHAR(500),

    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by   VARCHAR(255) NOT NULL,
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_item_revision              PRIMARY KEY (id),
    CONSTRAINT fk_item_revision_item         FOREIGN KEY (item_id) REFERENCES inventory.item (id),
    CONSTRAINT uq_item_revision_item_rev     UNIQUE (item_id, revision),
    CONSTRAINT chk_item_shelf_life           CHECK (shelf_life_controlled = false OR shelf_life_days IS NOT NULL),
    CONSTRAINT chk_item_revision_status      CHECK (revision_status IN ('DRAFT','PENDING_APPROVAL','APPROVED'))
);

CREATE UNIQUE INDEX uq_item_revision_one_draft
    ON inventory.item_revision (item_id)
    WHERE revision_status = 'DRAFT';

CREATE INDEX idx_item_revision_item_id ON inventory.item_revision (item_id);
CREATE INDEX idx_item_revision_status  ON inventory.item_revision (item_id, revision_status);
```

---

## DDL: V016 — Envers tables for `item` and `item_revision`

```sql
-- Rename legacy Envers table (preserves pre-migration audit history)
ALTER TABLE inventory.item_master_aud RENAME TO item_master_aud_legacy;

CREATE TABLE inventory.item_aud (
    id        UUID        NOT NULL,
    rev       INTEGER     NOT NULL,
    revtype   SMALLINT,
    org_id    UUID,
    part_number VARCHAR(100),
    created_by  VARCHAR(255),
    created_at  TIMESTAMPTZ,
    CONSTRAINT pk_item_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_item_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);

CREATE TABLE inventory.item_revision_aud (
    id                     UUID        NOT NULL,
    rev                    INTEGER     NOT NULL,
    revtype                SMALLINT,
    item_id                UUID,
    revision               INTEGER,
    revision_status        VARCHAR(20),
    description            VARCHAR(500),
    unit_of_measure        VARCHAR(20),
    cage_code              VARCHAR(10),
    classification         VARCHAR(30),
    make_buy_code          VARCHAR(10),
    traceability_method    VARCHAR(15),
    shelf_life_controlled  BOOLEAN,
    shelf_life_days        INTEGER,
    step_part_ref          VARCHAR(255),
    counterfeit_risk_level VARCHAR(10),
    approved_suppliers     JSONB,
    verification_required  BOOLEAN,
    custom_fields          JSONB,
    submitted_by           VARCHAR(255),
    submitted_at           TIMESTAMPTZ,
    approved_by            VARCHAR(255),
    approved_at            TIMESTAMPTZ,
    created_by             VARCHAR(255),
    created_at             TIMESTAMPTZ,
    modified_by            VARCHAR(255),
    modified_at            TIMESTAMPTZ,
    CONSTRAINT pk_item_revision_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_item_revision_aud_rev FOREIGN KEY (rev) REFERENCES inventory.revinfo (rev)
);
```

---

## DDL: V017 — Create `bom` and `bom_revision` Tables

```sql
CREATE TABLE inventory.bom (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    org_id          UUID         NOT NULL,
    parent_item_id  UUID         NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_bom PRIMARY KEY (id),
    CONSTRAINT uq_bom_org_item UNIQUE (org_id, parent_item_id),
    CONSTRAINT fk_bom_parent_item FOREIGN KEY (parent_item_id) REFERENCES inventory.item (id)
);

CREATE INDEX idx_bom_org      ON inventory.bom (org_id);
CREATE INDEX idx_bom_item     ON inventory.bom (parent_item_id);

CREATE TABLE inventory.bom_revision (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    bom_id           UUID         NOT NULL,
    revision         INTEGER      NOT NULL,
    revision_status  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    description      VARCHAR(500),
    eco_id           UUID,
    reason_for_revision VARCHAR(500),
    production_line  VARCHAR(200),
    bom_type         VARCHAR(30),
    effectivity_type VARCHAR(10),
    custom_fields    JSONB,

    submitted_by     VARCHAR(255),
    submitted_at     TIMESTAMPTZ,
    approved_by      VARCHAR(255),
    approved_at      TIMESTAMPTZ,
    rejected_by      VARCHAR(255),
    rejected_at      TIMESTAMPTZ,
    rejection_reason VARCHAR(500),

    created_by       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    modified_by      VARCHAR(255) NOT NULL,
    modified_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_bom_revision          PRIMARY KEY (id),
    CONSTRAINT fk_bom_revision_bom      FOREIGN KEY (bom_id) REFERENCES inventory.bom (id),
    CONSTRAINT uq_bom_revision_bom_rev  UNIQUE (bom_id, revision),
    CONSTRAINT chk_bom_revision_status  CHECK (revision_status IN ('DRAFT','PENDING_APPROVAL','APPROVED'))
);

CREATE UNIQUE INDEX uq_bom_revision_one_draft
    ON inventory.bom_revision (bom_id)
    WHERE revision_status = 'DRAFT';

CREATE INDEX idx_bom_revision_bom_id ON inventory.bom_revision (bom_id);
CREATE INDEX idx_bom_revision_status ON inventory.bom_revision (bom_id, revision_status);
```

---

## DDL: V020 — Migrate `bom_line` FKs

```sql
-- Add new FK columns (nullable during migration)
ALTER TABLE inventory.bom_line
    ADD COLUMN bom_revision_id            UUID,
    ADD COLUMN component_item_revision_id UUID;

-- Populate bom_revision_id: each bom_line.bom_id maps to a bom_revision
-- whose bom.id was created from bill_of_materials with the same id
UPDATE inventory.bom_line bl
SET bom_revision_id = br.id
FROM inventory.bom_revision br
JOIN inventory.bom b ON br.bom_id = b.id
WHERE bl.bom_id = br.id  -- bill_of_materials.id was used as bom_revision.id in migration
;

-- Populate component_item_revision_id: item_revision.id = item_master.id (preserved in V015)
UPDATE inventory.bom_line bl
SET component_item_revision_id = bl.component_item_id;

-- Add constraints
ALTER TABLE inventory.bom_line
    ALTER COLUMN bom_revision_id            SET NOT NULL,
    ALTER COLUMN component_item_revision_id SET NOT NULL,
    ADD CONSTRAINT fk_bom_line_bom_revision      FOREIGN KEY (bom_revision_id) REFERENCES inventory.bom_revision (id),
    ADD CONSTRAINT fk_bom_line_item_revision      FOREIGN KEY (component_item_revision_id) REFERENCES inventory.item_revision (id);

-- Drop old FK columns
ALTER TABLE inventory.bom_line
    DROP CONSTRAINT fk_bom_line_bom,
    DROP CONSTRAINT fk_bom_line_component,
    DROP COLUMN bom_id,
    DROP COLUMN component_item_id;

-- Update indexes
DROP INDEX IF EXISTS inventory.idx_bom_line_bom_id;
DROP INDEX IF EXISTS inventory.idx_bom_line_component_id;
DROP INDEX IF EXISTS inventory.idx_bom_line_bom_find;

CREATE INDEX idx_bom_line_bom_revision_id     ON inventory.bom_line (bom_revision_id);
CREATE INDEX idx_bom_line_component_rev_id    ON inventory.bom_line (component_item_revision_id);
CREATE INDEX idx_bom_line_bom_rev_find        ON inventory.bom_line (bom_revision_id, find_number);
```

---

## Java Entity Definitions

### `Item` (identity)

```java
@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "item", schema = "inventory")
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "part_number", nullable = false, length = 100)
    private String partNumber;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "item", fetch = FetchType.LAZY)
    private List<ItemRevision> revisions = new ArrayList<>();
}
```

### `ItemRevision` (versioned)

```java
@Entity
@Audited
@EntityListeners(AuditingEntityListener.class)
@Table(name = "item_revision", schema = "inventory")
public class ItemRevision {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "revision", nullable = false)
    private Integer revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_status", nullable = false, length = 20)
    private RevisionStatus revisionStatus = RevisionStatus.DRAFT;

    // ... all data fields from ItemMaster (description, unitOfMeasure, etc.) ...

    @Column(name = "submitted_by", length = 255)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_by", length = 255)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedBy
    @Column(name = "modified_by", nullable = false, length = 255)
    private String modifiedBy;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;
}
```

### `RevisionStatus` enum

```java
public enum RevisionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED
}
```

### `Bom` (identity) and `BomRevision` (versioned) follow the same pattern as `Item`/`ItemRevision` above, with BOM-specific fields on `BomRevision`.

---

## API Response DTOs

### `ItemMasterDto` (updated)

Existing DTO extended with revision fields:

```typescript
// TypeScript model
export interface ItemMasterDto {
  // identity fields
  id: string;          // item identity UUID
  orgId: string;
  partNumber: string;

  // current revision fields
  revisionId: string;  // item_revision UUID
  revision: number;    // integer, e.g. 0, 1, 2
  revisionStatus: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED';
  hasDraft: boolean;   // true when a DRAFT revision exists for this item (set on draft initiation; cleared on approve or hard-delete)

  description: string;
  // ... all other existing fields ...

  approvedBy?: string;
  approvedAt?: string;
  submittedBy?: string;
  submittedAt?: string;
  rejectedBy?: string;
  rejectedAt?: string;
  rejectionReason?: string;
}
```

### `BomDto` (updated)

Same pattern — `revision: number`, `revisionStatus`, `hasDraft`.

---

## State Transition Table

| Current Status | Action | Required Privilege | New Status | HTTP |
|---|---|---|---|---|
| (none) | POST create item | `item-master:records:manage` | DRAFT (rev=0) | 201 |
| DRAFT | PATCH | `item-master:records:manage` | DRAFT | 200 |
| DRAFT | POST /submit | `item-master:records:manage` | PENDING_APPROVAL | 200 |
| DRAFT | DELETE /draft | `item-master:records:manage` | (deleted) | 204 |
| PENDING_APPROVAL | POST /approve | `item-master:revisions:approve` | APPROVED | 200 |
| PENDING_APPROVAL | POST /reject (with rejectionReason) | `item-master:revisions:approve` | DRAFT (revision survives; rejectedBy/rejectedAt/rejectionReason stored) | 200 |
| PENDING_APPROVAL | POST /reject (empty rejectionReason) | `item-master:revisions:approve` | 422 (reason required) | 422 |
| APPROVED | PATCH (auto-creates draft) | `item-master:records:manage` | new DRAFT at N+1 | 200 |
| APPROVED | POST /submit | — | 409 (no draft) | 409 |
| APPROVED | DELETE /draft | — | 409 (no draft) | 409 |
