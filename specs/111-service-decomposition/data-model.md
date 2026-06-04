# Data Model: MES-111 Service Decomposition

## Entity → Service Mapping

| Entity | Current Service | New Service | New Schema | Notes |
|---|---|---|---|---|
| `ItemMaster` | `work-order-service` | `inventory-service` | `inventory` | Unchanged structure; package `com.mes.inventory.itemmaster` |
| `BillOfMaterials` | `work-order-service` | `inventory-service` | `inventory` | Package `com.mes.inventory.bom` |
| `BomLine` | `work-order-service` | `inventory-service` | `inventory` | Package `com.mes.inventory.bom` |
| `UdfFieldDefinition` (ITEM_MASTER/BOM keys) | `work-order-service` | `inventory-service` | `inventory` | Same table structure; split by module key |
| `EngineeringChangeOrder` | `work-order-service` | `engineering-service` | `engineering` | Package `com.mes.engineering.eco` |
| `EcoAffectedItem` | `work-order-service` | `engineering-service` | `engineering` | Embedded in ECO aggregate |
| `EcoOutputBom` | `work-order-service` | `engineering-service` | `engineering` | Added via `addOutputBom()` — currently `eco_output_bom` table |
| `UdfFieldDefinition` (ECO key) | `work-order-service` | `engineering-service` | `engineering` | Same structure; split by module key |
| `UserGridPreference` | `work-order-service` | `platform-service` | `platform` | Package `com.mes.platform.preferences` |

---

## inventory-service Schema (`inventory`)

### Flyway Migration Files

| File | Content |
|---|---|
| `V001__create_inventory_schema.sql` | `CREATE SCHEMA IF NOT EXISTS inventory;` |
| `V002__create_item_master.sql` | Identical to `work-order-service/V002` but prefixed `inventory.` |
| `V003__create_bom_tables.sql` | Identical to `work-order-service/V003` but prefixed `inventory.` |
| `V004__create_udf_field_definition.sql` | Identical to `work-order-service/V005` but prefixed `inventory.` |
| `V005__add_envers_tables.sql` | `inventory.revinfo`, `inventory.item_master_aud`, `inventory.bill_of_materials_aud`, `inventory.bom_line_aud` (mirrors `work-order-service/V006`) |
| `V006__add_envers_revend_columns.sql` | Mirror of `work-order-service/V011` |
| `V007__seed_inventory_privileges.sql` | Move item-master privilege seeds from `work-order-service/V007` |
| `V008__add_bom_header_edit_fields.sql` | Mirror of `work-order-service/V013` |
| `V009__add_bom_header_edit_fields_aud.sql` | Mirror of `work-order-service/V014` |

### Key Tables (unchanged structure)

```sql
inventory.item_master (
  id UUID PK,
  org_id UUID NOT NULL,
  part_number VARCHAR NOT NULL,
  revision VARCHAR NOT NULL,
  description TEXT,
  unit_of_measure VARCHAR(20),
  cage_code VARCHAR(9) NOT NULL,
  classification VARCHAR(50),
  make_buy_code VARCHAR(20),
  traceability_method VARCHAR(20),
  shelf_life_controlled BOOLEAN NOT NULL DEFAULT false,
  shelf_life_days INTEGER,
  step_part_ref VARCHAR(200),
  counterfeit_risk_level VARCHAR(20),
  approved_suppliers JSONB,
  verification_required BOOLEAN,
  custom_fields JSONB,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  -- audit columns (Envers)
  UNIQUE(org_id, part_number, revision)
)

inventory.bill_of_materials (
  id UUID PK,
  org_id UUID NOT NULL,
  parent_item_id UUID FK→item_master.id,
  bom_revision VARCHAR NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  description TEXT,
  eco_id UUID, -- reference only (no FK to engineering schema)
  reason_for_revision TEXT,
  production_line VARCHAR(200),
  bom_type VARCHAR(50),
  effectivity_type VARCHAR(20),
  custom_fields JSONB,
  -- audit columns
  UNIQUE(org_id, parent_item_id, bom_revision)
)

inventory.bom_line (
  id UUID PK,
  bom_id UUID FK→bill_of_materials.id,
  component_item_id UUID FK→item_master.id,
  quantity NUMERIC(18,6),
  unit_of_measure VARCHAR(20),
  find_number VARCHAR(20),
  reference_designators TEXT,
  effectivity_method VARCHAR(10),
  effective_from_date DATE,
  effective_to_date DATE,
  effective_from_unit INTEGER,
  effective_to_unit INTEGER
  -- audit columns
)

inventory.udf_field_definition (
  -- same structure as work_order.udf_field_definition
  -- module_key IN ('ITEM_MASTER', 'BOM_LINE', 'BOM_HEADER')
)
```

### Cross-service Reference: `eco_id` in `bill_of_materials`

`bill_of_materials.eco_id` is a **reference UUID only** — no foreign key constraint across schemas. `inventory-service` stores the ECO ID value but does not JOIN to `engineering` schema. The ID is passed in the `bom.released` Kafka event payload so `engineering-service` can correlate.

---

## engineering-service Schema (`engineering`)

### Flyway Migration Files

| File | Content |
|---|---|
| `V001__create_engineering_schema.sql` | `CREATE SCHEMA IF NOT EXISTS engineering;` |
| `V002__create_eco_tables.sql` | Mirrors `work-order-service/V004` + `V012` (eco_output_bom), prefixed `engineering.` |
| `V003__create_udf_field_definition.sql` | Same structure; `module_key = 'ECO'` |
| `V004__add_envers_tables.sql` | `engineering.revinfo`, `engineering.engineering_change_order_aud` |
| `V005__add_envers_revend_columns.sql` | Mirror of `work-order-service/V011` |
| `V006__seed_eco_privileges.sql` | ECO-related privilege seeds |

### Key Tables

```sql
engineering.engineering_change_order (
  id UUID PK,
  org_id UUID NOT NULL,
  eco_number VARCHAR(50) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  initiated_by VARCHAR(255),
  approved_by VARCHAR(255),
  approved_at TIMESTAMPTZ,
  implemented_at TIMESTAMPTZ,
  -- audit columns
  INDEX(org_id, status)
)

engineering.eco_affected_item (
  eco_id UUID FK→engineering_change_order.id,
  item_id UUID, -- reference only; no FK to inventory schema
  PRIMARY KEY(eco_id, item_id)
)

engineering.eco_output_bom (
  eco_id UUID FK→engineering_change_order.id,
  bom_id UUID, -- reference only; no FK to inventory schema
  PRIMARY KEY(eco_id, bom_id)
)

engineering.udf_field_definition (
  -- same structure; module_key = 'ECO'
)
```

### Kafka Consumer: `bom.released`

```java
// engineering-service KafkaListener
@KafkaListener(topics = "bom.released")
public void onBomReleased(BomReleasedEvent event) {
    if (event.getEcoId() == null) return;
    ecoRepository.findById(event.getEcoId()).ifPresent(eco -> {
        eco.addOutputBom(event.getBomId());
        ecoRepository.save(eco);
    });
}

// BomReleasedEvent payload
{
  "bomId": "UUID",
  "orgId": "UUID",
  "ecoId": "UUID | null",
  "parentItemId": "UUID",
  "bomRevision": "string"
}
```

---

## platform-service Schema (`platform`)

### New Flyway Migration

| File | Content |
|---|---|
| `V00X__create_user_grid_preferences.sql` | Add `platform.user_grid_preferences` (same structure as current `work_order.user_grid_preferences`) |

```sql
platform.user_grid_preferences (
  id UUID PK DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL,
  user_id VARCHAR(255) NOT NULL,
  module_key VARCHAR(50) NOT NULL,
  column_config JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(org_id, user_id, module_key)
)
```

---

## Kafka Events (Domain Events)

### `bom.released` (new — replaces direct EcoService call)

Published by: `inventory-service.BomEventPublisher`
Consumed by: `engineering-service.BomReleasedEventHandler`

```json
{
  "eventType": "bom.released",
  "bomId": "string (UUID)",
  "orgId": "string (UUID)",
  "ecoId": "string (UUID) | null",
  "parentItemId": "string (UUID)",
  "bomRevision": "string",
  "releasedAt": "ISO-8601 timestamp",
  "releasedBy": "string (JWT sub)"
}
```

### Existing events (publisher moves, topic names unchanged)

| Event | Publisher (new) |
|---|---|
| `item-master.created` | `inventory-service` |
| `item-master.obsoleted` | `inventory-service` |
| `eco.approved` | `engineering-service` |

---

## Gateway Routing (After Migration)

```yaml
spring.cloud.gateway.routes:
  - id: inventory-service-item-master
    uri: ${INVENTORY_SERVICE_URL:http://inventory-service:8096}
    predicates:
      - Path=/api/v1/item-master/**
    filters:
      - StripPrefix=0

  - id: inventory-service-boms
    uri: ${INVENTORY_SERVICE_URL:http://inventory-service:8096}
    predicates:
      - Path=/api/v1/boms/**
    filters:
      - StripPrefix=0

  - id: inventory-service-udf
    uri: ${INVENTORY_SERVICE_URL:http://inventory-service:8096}
    predicates:
      - Path=/api/v1/udf/**
    filters:
      - StripPrefix=0

  - id: engineering-service-ecos
    uri: ${ENGINEERING_SERVICE_URL:http://engineering-service:8097}
    predicates:
      - Path=/api/v1/ecos/**
    filters:
      - StripPrefix=0

  - id: platform-service-preferences
    uri: ${PLATFORM_SERVICE_URL:http://platform-service:8090}
    predicates:
      - Path=/api/v1/users/**
    filters:
      - StripPrefix=0

  # Residual route for future work-order endpoints
  - id: work-order-service
    uri: ${WORK_ORDER_SERVICE_URL:http://work-order-service:8095}
    predicates:
      - Path=/api/v1/work-orders/**
    filters:
      - StripPrefix=0
```

**Note**: The catch-all `Path=/api/v1/**` → `work-order-service` route is REMOVED. All currently served paths are covered by the domain-specific routes above.
