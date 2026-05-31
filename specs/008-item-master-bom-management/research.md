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

Authoritative values from Penpot token set `aurora/dark` (verified against dark frame fills 2026-05-29).

| Token | Hex | Usage |
|---|---|---|
| `color.bg.base` | `#0F1923` | App background, content area |
| `color.bg.surface` | `#172030` | Cards, table rows |
| `color.bg.elevated` | `#1E2D42` | Drawers, overlays, elevated panels |
| `color.bg.overlay` | `#243350` | Modal backdrops |
| `color.bg.subtle` | `#0D1520` | Rail, top bar, sidebar |
| `brand.primary` | `#1A5FD4` | Buttons, selected nav, primary actions |
| `brand.primary-bright` | `#2E8BF5` | Hover state, focus border |
| `brand.ice` | `#6BB8FF` | Text accent, focus ring, UDF badge text |
| `brand.navy` | `#0D1B2E` | Deepest backgrounds |
| `brand.slate` | `#8A9BB0` | Secondary text, muted icons |
| `color.border.default` | `#243350` | Dividers, input borders, table borders |
| `color.border.strong` | `#2E4A6E` | Strong borders, separators |
| `color.border.focus` | `#2E8BF5` | Focus rings on inputs |
| `color.text.primary` | `#E8EDF5` | Body text, headings |
| `color.text.secondary` | `#8A9BB0` | Labels, metadata, placeholders |
| `color.text.disabled` | `#3D5270` | Disabled text |
| `color.text.accent` | `#6BB8FF` | Links, accent labels |
| `color.interactive.default` | `#1A5FD4` | Button fill, active nav |
| `color.interactive.hover` | `#2E8BF5` | Button hover |
| `color.interactive.active` | `#0D4AAF` | Button press |
| `color.status.success` | `#22C55E` | Success text/icon |
| `color.status.success-subtle` | `#14291E` | Success badge background |
| `color.status.warning` | `#F59E0B` | Warning text/icon |
| `color.status.warning-subtle` | `#2A2010` | Warning badge background |
| `color.status.error` | `#EF4444` | Error text/icon |
| `color.status.error-subtle` | `#2A1212` | Error badge background |
| `color.status.info` | `#2E8BF5` | Info text/icon |
| `color.status.info-subtle` | `#0F1F35` | Info badge background |

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
- Dark: Shell (collapsed rail), Shell (flyout open), Item Master List, Item Master Column Picker, Item Master Create, BOM / Explosion Tree, BOM / Authoring final, BOM / Header Edit *(current)*
- Light: Shell (collapsed rail — light), Item Master List (light), Item Master Column Picker (light), Item Master Edit, BOM / Explosion Tree (light), BOM / Authoring final (light), BOM / Header Edit (light) *(current)*

> **Naming note**: The dark side has "Item Master / Create (dark)" and the light side has "Item Master / Edit (light)" — these are intentionally different scenarios, not paired equivalents of the same screen.

> **Duplicate frames** (audit 2026-05-31): Two frame-name duplicates exist in the Penpot file. The second copy in each pair is the current authoritative version (higher ID, created later):
> - "Item Master / List & Search" dark — IDs `137a1557caff` (earlier) and `137a50e2afcd` (current)
> - "BOM / Explosion Tree (light)" — IDs `139798a5fc2b` (earlier) and `1397d2d3a99b` (current)
> The earlier copies should be deleted from Penpot when convenient; they are not referenced by any task.

| Frame | Mode | Penpot ID | Notes |
|---|---|---|---|
| Shell (collapsed rail) | Dark | `d1e9cefe-fcab-80d7-8008-1377eccc2a11` | |
| Shell (flyout open — Materials) | Dark | `d1e9cefe-fcab-80d7-8008-13788ebad07c` | Flyout group: Materials > Item Master, BOM Management, Inventory (coming soon) |
| Item Master / List & Search | Dark | `d1e9cefe-fcab-80d7-8008-137a50e2afcd` | Current (duplicate of `137a1557caff` — earlier copy) |
| Item Master / Column Picker | Dark | `d1e9cefe-fcab-80d7-8008-137d0178c6b2` | |
| Item Master / Create | Dark | `d1e9cefe-fcab-80d7-8008-138b99f8d400` | |
| BOM / Explosion Tree | Dark | `d1e9cefe-fcab-80d7-8008-1394bcf67efc` | |
| BOM / Authoring v1 | Dark | `d1e9cefe-fcab-80d7-8008-1398d303c5ec` | Superseded — Find# column, no Seq |
| BOM / Authoring v2 | Dark | `d1e9cefe-fcab-80d7-8008-139d269ff559` | Superseded — no Find #, UDF as chip |
| **BOM / Authoring final** | **Dark** | **`86f35c31-9e0e-809d-8008-139f969d722f`** | **Current** — Seq, Find #, UDF cols, unit eff, col picker open |
| **BOM / Header Edit** | **Dark** | **`ae108caa-e755-807c-8008-140ccbadbeac`** | **Current** — modal: Part Number (RO), BOM Description, Reason for Revision, Production Line, BOM Header Properties (Type + Effectivity), BOM Header UDFs |
| Shell (collapsed rail) | Light | `d1e9cefe-fcab-80d7-8008-1385436e319f` | |
| Item Master / List & Search | Light | `d1e9cefe-fcab-80d7-8008-13856f83fb22` | |
| Item Master / Column Picker | Light | `d1e9cefe-fcab-80d7-8008-1385d5f1262e` | |
| Item Master / Edit | Light | `d1e9cefe-fcab-80d7-8008-138bde15a086` | Edit dialog in light mode — different scenario from Create (dark) |
| BOM / Explosion Tree | Light | `d1e9cefe-fcab-80d7-8008-1397d2d3a99b` | Current (duplicate of `139798a5fc2b` — earlier copy) |
| BOM / Authoring v1 | Light | `d1e9cefe-fcab-80d7-8008-13997f6538b8` | Superseded |
| BOM / Authoring v2 | Light | `d1e9cefe-fcab-80d7-8008-139dbd1988d3` | Superseded |
| **BOM / Authoring final** | **Light** | **`86f35c31-9e0e-809d-8008-13a00d8698d5`** | **Current** — Seq, Find #, UDF cols as headers, unit eff |
| **BOM / Header Edit** | **Light** | **`ae108caa-e755-807c-8008-140d20b38509`** | **Current** — modal: same fields as dark, light theme |

---

## Decision 10 — Shell Navigation: Flyout Group vs Flat Rail (Design Divergence)

**Penpot frame**: `🖥 Shell — flyout open (Materials)` (ID `d1e9cefe-fcab-80d7-8008-13788ebad07c`)

**What the design shows**: Clicking a rail item labelled "Materials" opens a horizontal flyout panel containing sub-items: "Item Master", "BOM Management", and "Inventory" (marked COMING SOON). The breadcrumb reads "Home / Materials / Item Master". The rail item is a group — not a direct link.

**What FR-031 and T131 specify**: Four flat top-level rail items — Dashboard, Item Master, BOM, ECO — each linking directly to their own route. No grouping flyout.

**What is implemented**: The `AppShellComponent` follows FR-031 exactly: four flat nav items. The flyout frame is an exploratory design iteration that was not selected for implementation.

**Resolution**: The flat rail (FR-031 / T131) is the accepted implementation. The flyout frame is retained in Penpot as a future-state reference for when Inventory, Receiving, and other material modules are added and grouping becomes necessary. No code change required. Task T131 specification is authoritative.

---

## Decision 11 — BOM Authoring: Final Column Specification and Action Buttons

**Penpot frame**: `📝 BOM / Authoring final (dark/light)` (IDs `86f35c31-9e0e-809d-8008-139f969d722f` / `86f35c31-9e0e-809d-8008-13a00d8698d5`)

**Column order** (authoritative from final Penpot frame):

`Seq | Find # | Part Number | Description | Rev | Qty | Unit | Eff From | Eff To | [UDF columns…] | Actions`

The "⊕ Columns" button sits inline at the far-right of the table header row (Decision 9).

**Divergence from T153**: T153 lists "Find #, Component Part # / Rev (linked), Description, Quantity, UoM, Effectivity" — this is superseded by the final design above. Seq is a mandatory locked first column; Eff From / Eff To are two separate columns (not one combined "Effectivity" column); Rev is a separate column adjacent to Part Number.

**Action buttons in the BOM Authoring header bar** (extracted from Penpot frame, y ≈ 157):

| Button | Label | Behaviour |
|---|---|---|
| Primary-left | `+ Add Line` | Opens inline add-line form (T154) |
| Secondary | `Save Draft` | PATCHes BOM description/header fields without state change; available in DRAFT status only |
| Caution | `Submit for Review` | Maps to "Release BOM" in the backend state machine (DRAFT → RELEASED); label differs from T153 which says "Release BOM" — see note below |
| Text link | `← Explosion View` | Navigates to `/boms/{bomId}/explosion` |

**"Submit for Review" vs "Release BOM"**: The Penpot label is "Submit for Review" but the backend endpoint is `POST /boms/{bomId}/release` which transitions directly to RELEASED. The UI label "Submit for Review" is the accepted display label; the underlying operation remains `BomApiService.release()`. Task T153 should use this label when implemented.

**Per-row actions**: Each BOM line row shows ✏ (edit inline) and 🗑 (remove line). Both are only shown when BOM status is DRAFT. T153 currently specifies "Remove line" only — the edit (inline row edit) is an additional interaction that needs a task (see tasks.md T175).

---

## Decision 12 — BOM Header Edit Modal (Design Requirement, No Prior FR)

**Penpot frames**: `📝 BOM / Header Edit (dark/light)` (IDs `ae108caa-e755-807c-8008-140ccbadbeac` / `ae108caa-e755-807c-8008-140d20b38509`)

**What the design shows**: A modal dialog titled "Edit BOM Header" accessible from the BOM Authoring screen (via ✏ icon adjacent to the BOM part number in the header). Fields extracted verbatim from Penpot:

| Field | Type | Editable | Source |
|---|---|---|---|
| Part Number | Text (read-only) | No — shown with 🔒 icon | `item_master.part_number` |
| BOM Description | Text input | Yes | `bill_of_materials.description` |
| Reason for Revision | Text input | Yes | `bill_of_materials.reason_for_revision` (new column — see gap below) |
| Production Line | Dropdown | Yes | Org-configured lookup |
| BOM Type | Dropdown | Yes | e.g. "Manufacturing BOM" — stored as `bill_of_materials.bom_type` |
| Effectivity Type | Dropdown | Yes | DATE / UNIT / NONE — stored as `bill_of_materials.effectivity_type` |
| BOM Header UDFs | Dynamic UDF fields | Yes | `bill_of_materials.custom_fields` JSONB (module_key = 'bom-header') |
| Cancel | Button | — | Discards changes, closes modal |
| Save Changes | Button | — | PATCHes BOM header; calls `PATCH /boms/{bomId}` |

**Schema gaps this creates**: The BOM Header Edit modal introduces fields not currently in V003 migration:
- `reason_for_revision VARCHAR(500)` — not in `bill_of_materials` table
- `bom_type VARCHAR(50)` — not in `bill_of_materials` table
- `effectivity_type VARCHAR(10)` — not in `bill_of_materials` table (currently only `bom_line.effectivity_method` exists)
- `custom_fields JSONB` — not in `bill_of_materials` table (only `bom_line.custom_fields` exists)
- Production Line is likely a FK or a free-text field — scope to be determined during implementation

**Frontend API gap**: `BomApiService` needs a `patchHeader(bomId, req)` method. `BomController` needs a `PATCH /boms/{bomId}` endpoint. Neither exists. See tasks.md T175, T176.

**Scope decision**: BOM Header Edit is a Phase 12 deliverable (PR 7). It is not part of the current Phase 11 scope.
