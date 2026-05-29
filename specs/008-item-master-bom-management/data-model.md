# Data Model: Item Master & BOM Management (MES-8)

**Schema:** `work_order` (PostgreSQL 16, shared `mes` database)

---

## Entity Map

```
organisation (iam schema)
    │
    ├── item_master ─────────────────────────┐
    │       │                                │
    │       ├── bill_of_materials            │ (parent assembly)
    │       │       │                        │
    │       │       └── bom_line ────────────┘ (component item)
    │       │
    │       └── eco_affected_item ┐
    │                             │
    └── engineering_change_order ─┘
            │
            └── bill_of_materials (ecoId FK)

    udf_field_definition (module-scoped, ITEM_MASTER)
    item_master.custom_fields JSONB  ← validated against udf_field_definition
```

---

## Tables

### `work_order.item_master`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | |
| `org_id` | UUID | NOT NULL | From JWT; FK-style (no FK across schema) |
| `part_number` | VARCHAR(100) | NOT NULL | |
| `revision` | VARCHAR(20) | NOT NULL | |
| `description` | VARCHAR(500) | NOT NULL | |
| `unit_of_measure` | VARCHAR(20) | NOT NULL | e.g. EA, KG, M |
| `cage_code` | VARCHAR(10) | | Defense contractor code |
| `classification` | VARCHAR(30) | NOT NULL | RAW_MATERIAL / PURCHASED_PART / FABRICATED / ASSEMBLY / COTS / SERVICE |
| `make_buy_code` | VARCHAR(10) | NOT NULL | MAKE / BUY / EITHER |
| `traceability_method` | VARCHAR(15) | NOT NULL | SERIAL / LOT / HEAT_CODE / DATE_CODE / NONE |
| `shelf_life_controlled` | BOOLEAN | NOT NULL DEFAULT false | |
| `shelf_life_days` | INTEGER | | NOT NULL when shelf_life_controlled=true (check constraint) |
| `step_part_ref` | VARCHAR(255) | | ISO 10303 part reference |
| `counterfeit_risk_level` | VARCHAR(10) | | LOW / MEDIUM / HIGH / CRITICAL |
| `approved_suppliers` | JSONB | | Array of supplier name strings |
| `verification_required` | BOOLEAN | DEFAULT false | AS5553 |
| `custom_fields` | JSONB | | UDF values; validated at application layer |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE / OBSOLETE |
| `created_by` | VARCHAR(255) | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `modified_by` | VARCHAR(255) | NOT NULL | |
| `modified_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**Constraints:**
- `UNIQUE (org_id, part_number, revision)` — FR-002
- `CHECK (shelf_life_controlled = false OR shelf_life_days IS NOT NULL)` — FR-003

**Indexes:**
- `idx_item_master_org_part_rev ON item_master (org_id, part_number, revision)`
- `idx_item_master_org_status   ON item_master (org_id, status)`
- `idx_item_master_org_class    ON item_master (org_id, classification)`

---

### `work_order.bill_of_materials`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `org_id` | UUID | NOT NULL | |
| `parent_item_id` | UUID | NOT NULL | FK → item_master.id |
| `bom_revision` | VARCHAR(20) | NOT NULL | e.g. "A", "Rev-2" |
| `status` | VARCHAR(10) | NOT NULL DEFAULT 'DRAFT' | DRAFT / RELEASED / OBSOLETE |
| `description` | VARCHAR(500) | | |
| `eco_id` | UUID | | FK → engineering_change_order.id (nullable) |
| `created_by` | VARCHAR(255) | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `modified_by` | VARCHAR(255) | NOT NULL | |
| `modified_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**Constraints:**
- `UNIQUE (org_id, parent_item_id, bom_revision)` — one revision label per assembly per org

**Indexes:**
- `idx_bom_parent_item ON bill_of_materials (parent_item_id)`
- `idx_bom_org_status  ON bill_of_materials (org_id, status)`

---

### `work_order.bom_line`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `bom_id` | UUID | NOT NULL | FK → bill_of_materials.id |
| `component_item_id` | UUID | NOT NULL | FK → item_master.id |
| `quantity` | NUMERIC(18,6) | NOT NULL | |
| `unit_of_measure` | VARCHAR(20) | NOT NULL | |
| `find_number` | VARCHAR(20) | NOT NULL | |
| `reference_designators` | VARCHAR(500) | | Comma-separated (e.g. C1,C2,R5) |
| `effectivity_method` | VARCHAR(10) | | DATE / UNIT / null (perpetual) |
| `effective_from_date` | DATE | | Required when effectivity_method='DATE' |
| `effective_to_date` | DATE | | Null = open-ended (no end boundary) |
| `effective_from_unit` | VARCHAR(50) | | Required when effectivity_method='UNIT' |
| `effective_to_unit` | VARCHAR(50) | | Null = open-ended (no end boundary) |
| `created_by` | VARCHAR(255) | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `modified_by` | VARCHAR(255) | NOT NULL | |
| `modified_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**Constraints:**
- `CHECK (effectivity_method IS NULL OR effectivity_method IN ('DATE','UNIT'))` 
- Date overlap validation enforced at service layer (FR-009)

**Indexes:**
- `idx_bom_line_bom_id       ON bom_line (bom_id)`
- `idx_bom_line_component_id ON bom_line (component_item_id)`
- `idx_bom_line_bom_find     ON bom_line (bom_id, find_number)`

---

### `work_order.engineering_change_order`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `org_id` | UUID | NOT NULL | |
| `eco_number` | VARCHAR(30) | UNIQUE | System-generated sequence |
| `title` | VARCHAR(255) | NOT NULL | |
| `description` | TEXT | | |
| `status` | VARCHAR(15) | NOT NULL DEFAULT 'DRAFT' | DRAFT / APPROVED / IMPLEMENTED |
| `initiated_by` | VARCHAR(255) | NOT NULL | JWT sub |
| `approved_by` | VARCHAR(255) | | |
| `approved_at` | TIMESTAMPTZ | | |
| `implemented_at` | TIMESTAMPTZ | | |
| `created_by` | VARCHAR(255) | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `modified_by` | VARCHAR(255) | NOT NULL | |
| `modified_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**Indexes:**
- `idx_eco_org_status ON engineering_change_order (org_id, status)`

---

### `work_order.eco_affected_item`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `eco_id` | UUID | NOT NULL | FK → engineering_change_order.id |
| `item_id` | UUID | NOT NULL | FK → item_master.id |
| PK | (eco_id, item_id) | | |

---

### `work_order.udf_field_definition`

Owned by work-order-service; `moduleKey = ITEM_MASTER` in v1. Future services add rows for their own module keys.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `org_id` | UUID | NOT NULL | |
| `module_key` | VARCHAR(50) | NOT NULL | ITEM_MASTER / WORK_ORDER / ROUTING / … |
| `field_key` | VARCHAR(100) | NOT NULL | snake_case |
| `label` | VARCHAR(255) | NOT NULL | |
| `field_type` | VARCHAR(15) | NOT NULL | TEXT / NUMBER / DATE / BOOLEAN / LIST |
| `required` | BOOLEAN | NOT NULL DEFAULT false | |
| `default_value` | VARCHAR(500) | | |
| `list_options` | JSONB | | Required when field_type = LIST |
| `validation_rules` | JSONB | | e.g. {"min":0,"max":10000} for NUMBER |
| `display_order` | INTEGER | NOT NULL DEFAULT 0 | |
| `active` | BOOLEAN | NOT NULL DEFAULT true | Soft-delete |
| `created_by` | VARCHAR(255) | NOT NULL | |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| `modified_by` | VARCHAR(255) | NOT NULL | |
| `modified_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

**Constraints:**
- `UNIQUE (org_id, module_key, field_key)` — unique field key per module per org

---

## Envers Audit Tables

Managed by Hibernate Envers via `lib-common-audit` pattern:

| Table | Audits |
|---|---|
| `work_order.revinfo` | Revision metadata (id, timestamp, actor) |
| `work_order.item_master_aud` | All item_master mutations |
| `work_order.bill_of_materials_aud` | BOM header mutations |
| `work_order.bom_line_aud` | BOM line mutations |
| `work_order.engineering_change_order_aud` | ECO mutations |

---

## Java Package Structure

```
services/work-order-service/
└── src/main/java/com/mes/workorder/
    ├── WorkOrderServiceApplication.java
    ├── config/
    │   └── SecurityConfig.java
    ├── itemmaster/
    │   ├── domain/
    │   │   ├── ItemMaster.java           (@Entity, @Audited)
    │   │   ├── Classification.java       (enum)
    │   │   ├── MakeBuyCode.java          (enum)
    │   │   ├── TraceabilityMethod.java   (enum)
    │   │   └── CounterfeitRiskLevel.java (enum)
    │   ├── api/
    │   │   ├── ItemMasterController.java
    │   │   ├── dto/
    │   │   │   ├── ItemMasterDto.java
    │   │   │   ├── CreateItemMasterRequest.java
    │   │   │   └── PatchItemMasterRequest.java
    │   ├── service/
    │   │   └── ItemMasterService.java
    │   └── repository/
    │       └── ItemMasterRepository.java
    ├── bom/
    │   ├── domain/
    │   │   ├── BillOfMaterials.java      (@Entity, @Audited)
    │   │   ├── BomLine.java              (@Entity, @Audited)
    │   │   ├── BomStatus.java            (enum)
    │   │   └── EffectivityMethod.java    (enum)
    │   ├── api/
    │   │   ├── BomController.java
    │   │   └── dto/
    │   ├── service/
    │   │   ├── BomService.java
    │   │   └── BomExplosionService.java
    │   └── repository/
    │       ├── BomRepository.java
    │       └── BomLineRepository.java
    ├── eco/
    │   ├── domain/
    │   │   ├── EngineeringChangeOrder.java (@Entity, @Audited)
    │   │   ├── EcoAffectedItem.java
    │   │   └── EcoStatus.java             (enum)
    │   ├── api/
    │   │   ├── EcoController.java
    │   │   └── dto/
    │   ├── service/
    │   │   └── EcoService.java
    │   └── repository/
    │       └── EcoRepository.java
    └── kafka/
        ├── ItemMasterEventPublisher.java
        ├── BomEventPublisher.java
        └── EcoEventPublisher.java

libs/mes-udf-lib/
└── src/main/java/com/mes/udf/
    ├── domain/
    │   ├── UdfFieldDefinition.java (@Entity)
    │   └── UdfFieldType.java       (enum)
    ├── service/
    │   └── UdfValidator.java
    ├── api/
    │   └── UdfFieldDefinitionController.java
    └── repository/
        └── UdfFieldDefinitionRepository.java
```

---

## ISA-95 Mapping

| MES Entity | ISA-95 Part 2 Object |
|---|---|
| `ItemMaster` | Material Class / Material Definition |
| `traceabilityMethod` SERIAL/LOT | Material Lot / Sublot Control |
| `BillOfMaterials` | Process Segment (material specification) |
| `BomLine` | Process Segment Dependency → Material Class Property |
| `EngineeringChangeOrder` | Production Request Amendment (change control) |
