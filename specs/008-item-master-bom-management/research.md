# Research: Item Master & BOM Management (MES-8)

## Decision 1 — Service Location

**Decision:** Scaffold `services/work-order-service/` as a brand-new Spring Boot service following the platform-service pattern.

**Why:** work-order-service does not yet exist in the monorepo. The spec (FR-019) explicitly places item master and BOM in this service. Although the constitution's service table lists "Inventory & Materials / BOM" under `inventory-service`, FR-019 is an owner-approved deviation justified by the fact that item master data is the primary configuration input for work order materialisation. The `inventory-service` will consume item master events via Kafka, not duplicate the master data.

**Complexity note:** This deviation is logged in plan.md § Complexity Tracking.

---

## Decision 2 — Database Schema

**Decision:** Schema name `work_order` (PostgreSQL, shared `mes` database). Convention: service name, snake_case, matching existing: `iam`, `platform`, `audit`.

**Rationale:** All existing services follow this convention.

---

## Decision 3 — BOM Explosion Algorithm

**Decision:** PostgreSQL recursive CTE executed via `@Query(nativeQuery = true)`, with `CYCLE` clause for circular reference detection at query time. Pre-INSERT validation via a lightweight CTE check prevents circular references from being persisted.

**Rationale:** A recursive CTE at 10 levels / 50 nodes returns in ~50–300 ms on indexed data — well within the 2-second SLA. Application-level iterative traversal would require N round-trips. The `CYCLE` clause (PostgreSQL 14+, our version is 16) provides native cycle detection.

**CTE skeleton:**
```sql
WITH RECURSIVE bom_tree AS (
  SELECT bl.id, bl.bom_id, bl.component_item_id, bl.quantity, bl.find_number,
         1 AS depth, ARRAY[bl.component_item_id] AS path,
         bl.quantity AS rolled_qty
  FROM work_order.bom_line bl
  WHERE bl.bom_id = :bomId AND (:asOfDate IS NULL OR (
        (bl.effective_from_date IS NULL OR bl.effective_from_date <= :asOfDate)
    AND (bl.effective_to_date   IS NULL OR bl.effective_to_date   >= :asOfDate)))
  UNION ALL
  SELECT bl.id, bl.bom_id, bl.component_item_id, bl.quantity, bl.find_number,
         bt.depth + 1, bt.path || bl.component_item_id,
         bt.rolled_qty * bl.quantity
  FROM bom_tree bt
  JOIN work_order.bom_line bl ON bl.bom_id = (
       SELECT b.id FROM work_order.bill_of_materials b
       WHERE b.parent_item_id = bt.component_item_id LIMIT 1)
  WHERE bt.depth < :maxDepth
) CYCLE component_item_id SET is_cycle USING cycle_path
SELECT * FROM bom_tree WHERE NOT is_cycle ORDER BY depth, find_number;
```

**Pre-INSERT circular check:** Before persisting a new BOM line, run a CTE that walks from the proposed `componentItemId` upward; if it reaches the BOM's `parentItemId`, reject with HTTP 422.

**Index requirements:**
```sql
CREATE INDEX idx_bom_line_bom_id       ON work_order.bom_line (bom_id);
CREATE INDEX idx_bom_line_component_id ON work_order.bom_line (component_item_id);
CREATE INDEX idx_bom_parent_item       ON work_order.bill_of_materials (parent_item_id);
```

---

## Decision 4 — MATERIALS_ADMIN Role

**Decision:** Do NOT add a new seeded system role. Instead, register item master privileges at startup and grant them to `ENGINEER` and `SYSTEM_ADMIN` by default. The `MATERIALS_ADMIN` role in the spec represents a logical role that an org admin creates and configures via IAM.

**Privileges to register (module: `item-master`):**
- `item-master:records:view`
- `item-master:records:manage`
- `item-master:bom:manage`
- `item-master:eco:manage`
- `item-master:udf:manage`

**Seeding:** A Flyway migration seeds these privileges into `iam.privilege` and grants them to `SYSTEM_ADMIN` and `ENGINEER`.

---

## Decision 5 — mes-udf-lib Library

**Decision:** New Gradle subproject at `libs/mes-udf-lib/` following the lib-common-audit pattern: produces a plain Java library (no Spring Boot plugin), published to `mavenLocal`. It provides:
- `UdfFieldDefinition` JPA entity (mapped to `udf_field_definition` table owned by the consuming service's schema)
- `UdfValidator` service bean (validates JSONB custom field values against definitions)
- Flyway migration SQL fragment (V-prefix migrations handled by each consuming service)
- REST controller for `/udf/fields` endpoint (included via Spring Boot autoconfigure)

**Alternatives rejected:** Embedding UDF directly in work-order-service was rejected because FR-026 explicitly requires a reusable shared library.

---

## Decision 6 — Kafka Topics

New topics (following `<service>.<domain>.<entity>.events` convention):

| Topic | Publisher | Consumers |
|---|---|---|
| `work-order.item-master.events` | work-order-service | audit-service, inventory-service (future) |
| `work-order.bom.events` | work-order-service | audit-service, work-order-service (scheduling, future) |
| `work-order.eco.events` | work-order-service | audit-service |

---

## Decision 7 — Organisation Scoping

**Decision:** `item_master`, `bill_of_materials`, `engineering_change_order` all carry `org_id UUID NOT NULL` (FK → iam.organisation). All service layer queries and REST controllers scope by `org_id` extracted from the JWT `org_id` claim via `OrganisationContextHolder`. UDF field definitions are also org-scoped.

---

## Decision 8 — Envers Audit

**Decision:** Apply `@Audited` to `ItemMaster`, `BillOfMaterials`, `BomLine`, and `EngineeringChangeOrder`. Use the same `MesRevisionEntity` and `MesRevisionListener` pattern from `lib-common-audit`. The `revinfo` table is shared in the `work_order` schema.

---

## Decision 9 — BOM Line Columns & Effectivity Model

### BOM Line Sequence Number vs Find Number

- **Seq** (`sequence_number INT NOT NULL`): BOM line attribute — user-assigned ordering number (convention: 10, 20, 30… incrementing by 10 to allow future insertions). Defines display order on the BOM. Editable on the Authoring screen.
- **Find #** (`find_number VARCHAR`): Item master attribute — the drawing balloon/callout number that maps the part to a location on the engineering drawing. Read-only on BOM lines; sourced from `item_master.find_number`. Displayed in the Explosion Tree view only.

### Default BOM Authoring Column Order

`Seq | Find # | Part Number | Description | Revision | Qty | Unit | Eff From | Eff To | [UDF columns…] | Actions`

Find # is included in the Authoring table as a read-only column (sourced from `item_master.find_number`). It is also shown in the Explosion Tree.

### Inline Column Chooser

The column chooser is rendered as a `⊕ Columns` button at the far-right end of the table header row (not a separate panel). Clicking it opens a PrimeNG `p-overlayPanel` anchored to the button. The picker lists all standard columns and all org-configured UDF columns in a single unified list — UDF columns are not special; they are just another set of columns the user can show or hide.

### BOM Line UDFs

BOM lines participate in the `mes-udf-lib` UDF system (Decision 5). UDF field definitions are scoped per org and per module (`module_key = 'bom-line'`). UDF values are stored as JSONB in `bom_line.custom_fields`. In the UI:

- **UDF columns render as normal table column headers** alongside the standard columns — consistent with the shared `GridPreferenceService` / `ColumnPickerComponent` pattern used across all grid screens.
- The column picker lists UDF columns under a `USER DEFINED FIELDS` section header so they are visually grouped, but they behave identically to standard columns (toggle on/off, drag to reorder, width saved to `user_grid_preferences`).
- There is no "n UDFs" chip. If a UDF column is hidden and has a value, it is simply not shown (same behaviour as any hidden column).

### Effectivity Mode — Date vs Unit

`BomLine` supports two effectivity modes controlled by `item_master.effectivity_type`:

| Mode | `effectivity_type` | Eff From field | Eff To field |
|---|---|---|---|
| Date effective | `DATE` | ISO date picker | ISO date picker (nullable = open-ended) |
| Unit effective | `UNIT` | Unit/serial/lot number text input | Unit/serial/lot number text input (nullable = open-ended) |

The UI detects `effectivity_type` from the selected part's item master record and swaps the input widget accordingly. The column header label remains "Eff From / Eff To" but a `(Unit)` badge appears on affected cells to distinguish from date values.

---

## Theme Tokens — Aurora MES Dark & Light Mode

Authoritative source: Penpot file "New File 1" → page "Token reference board" (fileId: `e7a86fff-661d-81c1-8008-131bc45d179c`).

These values are used in `frontend/angular/src/styles.scss` for PrimeNG overrides and Aurora MES CSS custom properties.

### Dark Mode (`.aurora-dark` class on `<html>`)

| Token | Hex | Usage |
|---|---|---|
| `bg.base` | `#0A1628` | App background, content area |
| `bg.subtle` | `#0D1F3C` | Rail, top bar, panel backgrounds |
| `brand.primary` | `#2563EB` | Buttons, links, selected nav, active badges |
| `text.primary` | `#F1F5F9` | Body text, headings |
| `text.secondary` | `#94A3B8` | Labels, metadata, placeholders |
| `border.subtle` | `#1E3A5F` | Dividers, input borders, table borders |
| `icon.default` | `#94A3B8` | Unselected nav icons, action icons |
| `rail.selected.bg` | `#1E3A5F` | Selected nav item background |
| `badge.required.bg` | `#7F1D1D` | Required badge background |
| `badge.required.text` | `#FCA5A5` | Required badge text |
| `badge.udf.bg` | `#1E3A5F` | UDF badge background |
| `badge.udf.text` | `#93C5FD` | UDF badge text (ice blue) |
| `status.active.bg` | `#14532D` | Active status badge bg |
| `status.active.text` | `#86EFAC` | Active status badge text |
| `status.eco.bg` | `#1E3A5F` | Under ECO status badge bg |
| `status.eco.text` | `#93C5FD` | Under ECO status badge text |
| `status.draft.bg` | `#451A03` | Draft status badge bg |
| `status.draft.text` | `#FCD34D` | Draft status badge text |

### Light Mode (`:root:not(.aurora-dark)`)

| Token | Hex | Usage |
|---|---|---|
| `bg.base` | `#F8FAFC` | App background, content area |
| `bg.subtle` | `#EFF6FF` | Rail, panel backgrounds, toggle button bg |
| `bg.surface` | `#FFFFFF` | Top bar, cards, table rows (even), popover |
| `brand.primary` | `#2563EB` | Unchanged — blue works on both themes |
| `text.primary` | `#0F172A` | Body text, headings |
| `text.secondary` | `#64748B` | Labels, metadata, placeholders |
| `border.subtle` | `#CBD5E1` | Dividers, input borders, table borders |
| `icon.default` | `#64748B` | Unselected nav icons, action icons |
| `rail.selected.bg` | `#DBEAFE` | Selected nav item background |
| `table.header.bg` | `#F1F5F9` | Table `<th>` row background |
| `table.row.alt` | `#F8FAFC` | Alternating table row tint |
| `badge.required.bg` | `#FEE2E2` | Required badge background |
| `badge.required.text` | `#B91C1C` | Required badge text |
| `badge.udf.bg` | `#DBEAFE` | UDF badge background |
| `badge.udf.text` | `#1D4ED8` | UDF badge text |
| `status.active.bg` | `#DCFCE7` | Active status badge bg |
| `status.active.text` | `#15803D` | Active status badge text |
| `status.eco.bg` | `#DBEAFE` | Under ECO status badge bg |
| `status.eco.text` | `#1D4ED8` | Under ECO status badge text |
| `status.draft.bg` | `#FEF3C7` | Draft status badge bg |
| `status.draft.text` | `#92400E` | Draft status badge text |

### Theme Toggle Behaviour

| Context | Icon | aria-label |
|---|---|---|
| Dark mode active | ☀ (sun) — `brand.primary` colour | "Switch to light mode" |
| Light mode active | ☾ (moon) — `icon.default` colour | "Switch to dark mode" |

**PrimeNG config**: `darkModeSelector: '.aurora-dark'` in `providePrimeNG()`. Class toggled on `document.documentElement` by `ThemeService`. Persisted to `localStorage` key `aurora-mes-theme`. Falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches` on first visit.

**Penpot frames** (all on page "Aurora MES / Shell", fileId `e7a86fff-661d-81c1-8008-131bc45d179c`):
- Dark: Shell (collapsed rail), Shell (flyout open), Item Master List, Item Master Column Picker, Item Master Create, BOM / Explosion Tree, BOM / Authoring v2 *(current)*
- Light: Shell (collapsed rail — light), Item Master List (light), Item Master Column Picker (light), Item Master Edit, BOM / Explosion Tree (light), BOM / Authoring v2 *(current)*

| Frame | Mode | Penpot ID | Notes |
|---|---|---|---|
| Shell (collapsed rail) | Dark | — | |
| Item Master / List & Search | Dark | — | |
| Item Master / Column Picker | Dark | — | |
| Item Master / Create | Dark | — | |
| BOM / Explosion Tree | Dark | `d1e9cefe-fcab-80d7-8008-1394bcf67efc` | |
| BOM / Authoring v1 | Dark | `d1e9cefe-fcab-80d7-8008-1398d303c5ec` | Superseded — Find# column, no Seq |
| BOM / Authoring v2 | Dark | `d1e9cefe-fcab-80d7-8008-139d269ff559` | Superseded — no Find #, UDF as chip |
| **BOM / Authoring final** | **Dark** | **`86f35c31-9e0e-809d-8008-139f969d722f`** | **Current** — Seq, Find #, UDF cols, unit eff, col picker open |
| Shell (collapsed rail) | Light | `d1e9cefe-fcab-80d7-8008-1385436e319f` | |
| Item Master / List & Search | Light | `d1e9cefe-fcab-80d7-8008-13856f83fb22` | |
| Item Master / Column Picker | Light | `d1e9cefe-fcab-80d7-8008-1385d5f1262e` | |
| Item Master / Edit | Light | `d1e9cefe-fcab-80d7-8008-138bde15a086` | |
| BOM / Explosion Tree | Light | `d1e9cefe-fcab-80d7-8008-1397d2d3a99b` | |
| BOM / Authoring v1 | Light | `d1e9cefe-fcab-80d7-8008-13997f6538b8` | Superseded |
| BOM / Authoring v2 | Light | `d1e9cefe-fcab-80d7-8008-139dbd1988d3` | Superseded |
| **BOM / Authoring final** | **Light** | **`86f35c31-9e0e-809d-8008-13a00d8698d5`** | **Current** — Seq, Find #, UDF cols as headers, unit eff |
