# Feature Specification: Item Master & BOM Management

**Feature Branch**: `008-item-master-bom-management`

**Created**: 2026-05-25

**Status**: In Implementation

**Input**: Jira Epic MES-8 — P2 · Item Master & BOM Management

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Item Master Record Management (Priority: P1)

A materials administrator or engineer creates, updates, and retrieves item master records that define every part used across the MES. Each record carries all identity, traceability, and compliance attributes required by AS9100D, AS9102, and AS5553. Without item master data, no downstream domain (BOM, Routing, Work Orders, Receiving, Inventory) can function.

**Why this priority**: Every other domain in the MES depends on item master records existing. This is the foundation layer — nothing ships without it.

**Independent Test**: An engineer logs in with the `ENGINEER` or `MATERIALS_ADMIN` role, creates a new item master record for a fabricated aluminium bracket, retrieves it by part number + revision, and confirms all fields are persisted correctly. The REST API returns HTTP 201 on create and HTTP 200 with the full record on retrieval.

**Acceptance Scenarios**:

1. **Given** a logged-in `MATERIALS_ADMIN` user, **When** they POST a new item master record with all mandatory fields (partNumber, revision, description, unitOfMeasure, cageCode, classification, makeBuyCode, traceabilityMethod), **Then** the system persists the record, returns HTTP 201 with the generated UUID and a `Location` header, and emits a `item-master.created` Kafka event.
2. **Given** a duplicate part number + revision combination already exists, **When** a second POST is attempted with the same partNumber + revision, **Then** the system returns HTTP 409 Conflict with a descriptive error body.
3. **Given** an existing item master record, **When** a `MATERIALS_ADMIN` PATCHes the description field, **Then** the system records the change with the modifier identity and timestamp, returns HTTP 200, and emits a `item-master.updated` Kafka event.
4. **Given** an item marked as `shelfLifeControlled = true` with `shelfLifeDays = 180`, **When** the record is retrieved, **Then** the `shelfLifeControlled` flag and `shelfLifeDays` are present in the response.
5. **Given** an unauthenticated request, **When** any item master endpoint is called, **Then** the system returns HTTP 401.

---

### User Story 2 — Multi-Level BOM Authoring (Priority: P1)

A product engineer creates and manages multi-level Bills of Materials linking assemblies to sub-assemblies and purchased components. BOMs are versioned with explicit revision labels. The BOM structure drives work order materialisation, routing, and receiving in downstream services.

**Why this priority**: The BOM is the primary configuration document for production. Routing and work order services cannot be implemented until BOM data is queryable.

**Independent Test**: An engineer creates a two-level BOM (Assembly → Sub-Assembly → Component) with three BOM lines, then explodes the BOM (flat and indented) via the REST API and confirms the full component tree is returned with correct quantities and parent-child relationships.

**Acceptance Scenarios**:

1. **Given** a released item master record for an assembly, **When** an `ENGINEER` POSTs a new BOM header for that assembly with a revision label of "A", **Then** the system creates a BOM in `Draft` status and returns HTTP 201.
2. **Given** a BOM in `Draft` status, **When** the engineer adds BOM lines referencing valid component item masters with quantities and find numbers, **Then** each line is persisted and retrievable via GET `/boms/{bomId}/lines`.
3. **Given** a multi-level BOM (≥3 levels deep), **When** a BOM explosion is requested via GET `/boms/{bomId}/explosion?format=indented`, **Then** the response contains all levels recursively with correct nesting, quantities rolled up correctly, and the operation completes in under 2 seconds.
4. **Given** a BOM line references an item master UUID that does not exist, **When** the line is POSTed, **Then** the system returns HTTP 422 Unprocessable Entity with a body identifying the missing item.
5. **Given** a BOM in `Draft` status, **When** an `ENGINEER` transitions it to `Released`, **Then** the BOM status changes, a `bom.released` Kafka event is emitted, and further structural changes to that revision are rejected (HTTP 409).

---

### User Story 3 — User-Defined Fields on Item Master (Priority: P2)

A system administrator defines custom fields that apply to all item master records in the module. Once defined, any user with read access sees the custom field values alongside the standard item master fields. The ability to create, modify, or delete UDF definitions is a privileged operation assigned to the `SYSTEM_ADMIN` role by default.

User-defined fields are a cross-cutting platform capability — the same framework will be reused by Work Orders, Routing, Receiving, and Inventory modules. The design must therefore be module-agnostic from the outset.

**Why this priority**: UDFs are required early because engineering teams invariably need organisation-specific attributes (drawing references, material grades, customer part numbers) that the standard schema cannot anticipate. Making the framework reusable prevents each module from reimplementing ad-hoc JSONB columns independently.

**Independent Test**: A `SYSTEM_ADMIN` user defines a required TEXT field `drawing_ref` on the `ITEM_MASTER` module. An `ENGINEER` then attempts to create an item master record without the field — the system returns HTTP 422. They retry with the field populated — the record is created and the custom field value is returned in the GET response.

**Acceptance Scenarios**:

1. **Given** a `SYSTEM_ADMIN` user, **When** they POST a new UDF field definition with `moduleKey = ITEM_MASTER`, `fieldKey = drawing_ref`, `label = "Drawing Reference"`, `type = TEXT`, `required = true`, **Then** the definition is persisted and returned on subsequent GET `/udf/fields?module=ITEM_MASTER` with HTTP 200.
2. **Given** a required UDF field `drawing_ref` is defined on `ITEM_MASTER`, **When** an `ENGINEER` POSTs a new item master without including `drawing_ref` in `customFields`, **Then** the system returns HTTP 422 with a body identifying the missing required field.
3. **Given** a LIST-type UDF field `material_standard` with options `["AMS2750", "AMS4000", "ASTM-B209"]`, **When** an `ENGINEER` sets `material_standard = "ISO-9001"` on an item master, **Then** the system returns HTTP 422 with a "value not in allowed list" error.
4. **Given** a UDF field definition `drawing_ref` exists with values on 50 item master records, **When** a `SYSTEM_ADMIN` attempts to DELETE the field definition without `force=true`, **Then** the system returns HTTP 409 with a message indicating how many records have values for that field.
5. **Given** a user with only the `ENGINEER` role, **When** they attempt to POST a new UDF field definition, **Then** the system returns HTTP 403.
6. **Given** a `SYSTEM_ADMIN` defines a NUMBER-type UDF field `weight_kg` with a validation rule `min=0, max=10000`, **When** a value of `-5` is submitted on an item master, **Then** the system returns HTTP 422 with a range validation error.

---

### User Story 4 — BOM Effectivity Management (Priority: P2)

A design engineer assigns effectivity rules to individual BOM lines to indicate when a component is valid for production. Two methods are supported: date-based (from-date / to-date) and unit-serial-based (from-unit / to-unit). This supports AS9100D product configuration control for mixed-configuration production runs.

**Why this priority**: Required for any product with engineering changes mid-production run. P2 because P1 stories can deliver a release-level BOM without effectivity; effectivity adds precision for change management.

**Independent Test**: Create a BOM with two alternate BOM lines for the same find number — one date-effective (covering units before a given date) and one date-effective (covering units after). Request the BOM explosion for a specific date and confirm only the correct line is returned.

**Acceptance Scenarios**:

1. **Given** a BOM line, **When** the effectivity method is set to `DATE` with `effectiveFromDate = 2025-01-01` and `effectiveToDate = 2025-12-31`, **Then** a BOM explosion for `asOfDate = 2025-06-01` includes that line, and an explosion for `asOfDate = 2026-01-01` excludes it.
2. **Given** a BOM line, **When** the effectivity method is set to `UNIT` with `effectiveFromUnit = "SN-001"` and `effectiveToUnit = "SN-050"`, **Then** a BOM explosion for `asOfUnit = "SN-025"` includes that line and for `asOfUnit = "SN-051"` excludes it.
3. **Given** a BOM line with effectivity method `DATE`, **When** a second line for the same find number is added with overlapping date ranges, **Then** the system returns HTTP 422 with an error message identifying the specific conflicting line by its find number and position (e.g., "date range overlap for BOM line find number 003 — conflicts with existing line ID {uuid}").
4. **Given** a date range with a gap (no BOM line covers a specific date), **When** a BOM explosion is requested for that date, **Then** the system returns HTTP 422 with a "BOM effectivity gap detected" error identifying the find number and gap period.
5. **Given** a BOM line with effectivity method `DATE` or `UNIT`, `effectiveFromDate` (or `effectiveFromUnit`) set to a valid value, and `effectiveToDate` (or `effectiveToUnit`) left blank, **When** the BOM is exploded, **Then** the line is treated as effective from its start value with no end boundary (perpetually effective from that point). A BOM line with neither `effectiveFromDate` nor `effectiveFromUnit` set is treated as perpetually effective in both directions (always included).

---

### User Story 5 — Engineering Change Orders (Priority: P2)

An engineering manager initiates an Engineering Change Order (ECO) to document a change to one or more item master records, link the affected items, and trigger a new BOM revision. The ECO provides an auditable change control record satisfying AS9100D §8.1.

**Why this priority**: ECO workflow is essential for AS9100D compliance on any released product. P2 because the BOM authoring (P1) must exist first, and ECOs are the formal mechanism for changing released BOMs.

**Independent Test**: Create an ECO referencing two item masters, approve it, then create a new BOM revision referencing the ECO. Retrieve the ECO and confirm it lists the new BOM revision as an output.

**Acceptance Scenarios**:

1. **Given** a logged-in `ENGINEER`, **When** they POST a new ECO with a title, description, and a list of affected item master IDs, **Then** the system creates the ECO in `Draft` status and returns HTTP 201 with the ECO ID.
2. **Given** an ECO in `Draft` status, **When** an `ENGINEER` transitions it to `Approved`, **Then** the status updates, an `eco.approved` Kafka event is emitted, and the `approvedBy` / `approvedAt` fields are set.
3. **Given** an approved ECO, **When** a new BOM revision is created referencing that ECO ID, **Then** the BOM revision record stores the `ecoId` and the ECO record lists the new BOM revision in its outputs.
4. **Given** two open ECOs referencing the same item master, **When** the second ECO is created, **Then** the system persists it but includes a warning flag (`concurrentEcoWarning: true`) in the HTTP 201 response.
5. **Given** an ECO in `Approved` status, **When** a user attempts to edit the ECO description, **Then** the system returns HTTP 409 (state machine violation — approved ECOs are immutable).

---

### User Story 6 — AS5553 Counterfeit-Part Risk Fields (Priority: P3)

A supplier quality engineer records counterfeit-part risk attributes on item master records for aerospace supply chain compliance per AS5553. Risk level and approved supplier information are stored and queryable.

**Role**: `QUALITY_ENGINEER` is a **formal Keycloak role** (not an alias for `ENGINEER`). It is seeded via Flyway migration alongside `ENGINEER` and `SYSTEM_ADMIN`, and is granted the privilege `item-master:as5553:manage` by default. The `ENGINEER` role does NOT hold `item-master:as5553:manage` unless explicitly granted via Keycloak role mapping. `SYSTEM_ADMIN` holds all privileges including `item-master:as5553:manage`.

**Why this priority**: P3 because this is a compliance enrichment of the item master; the core item master (P1) must exist first. AS5553 fields are mandatory for aerospace but can be populated after initial item creation.

**Independent Test**: Log in as `QUALITY_ENGINEER`. Create an item master (as ENGINEER first), then PATCH it to add AS5553 attributes (riskLevel: HIGH, approvedSuppliers: ["Supplier A", "Supplier B"], verificationRequired: true). Retrieve the record and confirm the AS5553 fields are present. Confirm that an ENGINEER token attempting the same PATCH receives HTTP 403.

**Acceptance Scenarios**:

1. **Given** an existing item master, **When** a `QUALITY_ENGINEER` PATCHes it with AS5553 fields (`counterfeitRiskLevel`, `approvedSuppliers`, `verificationRequired`), **Then** the fields are persisted and returned in subsequent GETs.
2. **Given** a user holding only the `ENGINEER` role (no `item-master:as5553:manage`), **When** they PATCH an item master with AS5553 fields, **Then** the system returns HTTP 403.
3. **Given** an item with `counterfeitRiskLevel = HIGH`, **When** it is added as a BOM component, **Then** the BOM line response includes a `counterfeitRiskAlert: true` flag.
4. **Given** a search for all items with `counterfeitRiskLevel = HIGH`, **When** the query is submitted, **Then** the system returns a paginated list of matching items with all AS5553 fields populated.

---

### Edge Cases

- What happens when a BOM line references a component that is subsequently obsoleted in item master? The system warns on BOM explosion (flag: `componentObsoleted: true`) but does not auto-remove the line — an ECO is required to make the structural change.
- What happens when unit-effective ranges overlap across two different BOM revisions for the same assembly? Each BOM revision is independent; overlapping effective ranges across revisions are permitted (they represent alternate configurations, not conflicts).
- What happens when a shelf-life-controlled item's `shelfLifeDays` is updated? The change is versioned in the audit log; downstream Receiving/Inventory services receive the `item-master.updated` Kafka event and must re-evaluate open lots.
- What happens when an AS5553 high-risk item is added to a BOM as a new component? The system emits a `compliance.as5553-risk-added` Kafka event alongside the standard `bom.line.created` event; the Supplier Quality module (future) handles approval workflow.
- What happens when a BOM explosion encounters a circular reference (Component A → Assembly B → Component A)? The system detects the cycle and returns HTTP 422 with a "circular BOM reference detected" error identifying the loop path.
- What happens when a BOM explosion is requested for a very deep BOM (>20 levels)? The system must handle this without stack overflow — use iterative traversal with depth limit configurable via application property (`mes.bom.max-depth`, default 50).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow creation of item master records with mandatory fields: `partNumber` (unique per revision), `revision`, `description`, `unitOfMeasure`, `cageCode`, `classification` (enum: RAW_MATERIAL, PURCHASED_PART, FABRICATED, ASSEMBLY, COTS, SERVICE), `makeBuyCode` (MAKE / BUY / EITHER), `traceabilityMethod` (SERIAL / LOT / HEAT_CODE / DATE_CODE / NONE), `shelfLifeControlled` (boolean).
- **FR-002**: System MUST enforce uniqueness of the composite key `(partNumber, revision)` across the item master table.
- **FR-003**: System MUST support shelf-life attributes: `shelfLifeControlled` flag and `shelfLifeDays` integer; `shelfLifeDays` is mandatory when `shelfLifeControlled = true`.
- **FR-004**: System MUST store an ISO 10303 (STEP) part identity reference field (`stepPartRef`) as a free-text field on the item master to support future PDM/CAD integration.
- **FR-005**: System MUST support multi-level (recursive) BOM structures with unlimited nesting depth up to a configurable maximum (default 50 levels).
- **FR-006**: System MUST version BOM structures as named revisions (e.g., "A", "B", "Rev-2") under a parent item master record.
- **FR-007**: System MUST enforce BOM revision lifecycle states: `Draft → Released → Obsolete`. Structural changes (add/remove/modify lines) are only permitted in `Draft` state.
- **FR-008**: System MUST support two effectivity methods per BOM line, mutually exclusive: `DATE` (effectiveFromDate required; effectiveToDate optional — blank means open-ended) and `UNIT` (effectiveFromUnit required; effectiveToUnit optional — blank means open-ended). Lines with no effectivity method set are treated as perpetually effective in all directions (always included). `effectiveFromDate` / `effectiveFromUnit` MUST be supplied when the corresponding effectivity method is set.
- **FR-009**: System MUST validate that date-effective BOM lines for the same find number do not have overlapping date ranges within a single BOM revision. The overlap error response MUST identify the conflicting line by its find number and UUID (e.g., "date range overlap for BOM line find number 003 — conflicts with existing line ID {uuid}").
- **FR-010**: System MUST return HTTP 422 when a BOM explosion detects an effectivity gap (no line covers the requested date or unit for a required find number).
- **FR-011**: System MUST detect and reject circular BOM references at line-create time, returning HTTP 422.
- **FR-012**: System MUST expose BOM explosion endpoints supporting both flat (list) and indented (tree) response formats, with optional `asOfDate` or `asOfUnit` filter parameters.
- **FR-013**: System MUST support Engineering Change Orders with lifecycle: `Draft → Approved → Implemented`. Approved and Implemented ECOs are immutable.
- **FR-014**: System MUST allow ECOs to reference one or more affected item master records and link to the resulting new BOM revisions.
- **FR-015**: System MUST warn (non-blocking) when a new ECO is created for an item master that already has an open ECO (`concurrentEcoWarning` flag in response).
- **FR-016**: System MUST record AS5553 fields on item master: `counterfeitRiskLevel` (enum: LOW / MEDIUM / HIGH / CRITICAL), `approvedSuppliers` (list of supplier names), `verificationRequired` (boolean). Writing these fields (via PATCH) MUST require the `item-master:as5553:manage` privilege; reading them requires only `item-master:records:view`. A user with `item-master:records:manage` but NOT `item-master:as5553:manage` MUST receive HTTP 403 when attempting to set AS5553 fields.
- **FR-016a**: System MUST seed a `QUALITY_ENGINEER` Keycloak role via Flyway migration. The role MUST be granted: `item-master:records:view`, `item-master:as5553:manage`. It MUST NOT be granted `item-master:records:manage`, `item-master:bom:manage`, or `item-master:eco:manage` by default (those require ENGINEER or SYSTEM_ADMIN). Integration tests for AS5553 functionality MUST use a `qualityEngineerToken()` fixture that issues a JWT with the `QUALITY_ENGINEER` role.
- **FR-017**: System MUST emit Kafka domain events on: item master create/update, BOM revision create/release/obsolete, ECO approve/implement. Events must include the aggregate UUID, event type, timestamp, and actor identity.
- **FR-018**: System MUST store full audit fields on all entities: `createdBy`, `createdAt`, `modifiedBy`, `modifiedAt`.
- **FR-019**: System MUST be implemented within the `work-order-service` microservice under the `item-master` sub-package.
- **FR-020**: All endpoints MUST be secured via Keycloak JWT; unauthenticated requests return HTTP 401; insufficiently-authorised requests return HTTP 403.

#### User-Defined Fields (UDF) Framework

- **FR-021**: System MUST support user-defined fields (UDFs) on item master records. UDFs are defined at module level (not per-record) and apply uniformly to all records in that module once defined.
- **FR-022**: The UDF framework MUST support field types: `TEXT` (configurable max length), `NUMBER` (decimal, with optional min/max), `DATE`, `BOOLEAN`, and `LIST` (pre-defined option set stored as a JSONB array on the definition).
- **FR-023**: UDF field definitions MUST include: `fieldKey` (snake_case, unique per `moduleKey`), `label`, `type`, `required` (boolean), `defaultValue` (optional), `listOptions` (required for LIST type), `validationRules` (JSONB — min/max for NUMBER, maxLength for TEXT), `displayOrder` (integer), audit fields.
- **FR-024**: The ability to create, update, or delete UDF field definitions MUST be restricted to users holding the `udf:manage` permission. This permission MUST be assigned to the `SYSTEM_ADMIN` Keycloak role by default and may be delegated to other roles via Keycloak role mapping.
- **FR-025**: UDF values on item master records MUST be stored in a `customFields JSONB` column on the `item_master` table and validated against the active field definitions at record create/update time.
- **FR-026**: The UDF framework MUST be implemented as a reusable shared library (`mes-udf-lib`) within the monorepo `libs/` directory. It MUST be scoped by `moduleKey` (enum, extensible) so that Work Orders, Routing, Receiving, Inventory, and any future module can adopt it without duplicating schema, validation, or REST patterns. The `item-master` sub-domain of `work-order-service` is the first consumer. The `ModuleKey` enum MUST contain: `ITEM_MASTER`, `BOM_LINE`, `BOM_HEADER`, `WORK_ORDER`, `ROUTING`, `RECEIVING`, `INVENTORY`. `BOM_LINE` supports UDF columns on BOM authoring lines (FR-036); `BOM_HEADER` supports UDF fields on the BOM header edit dialog (FR-036a).
- **FR-027**: UDF values MUST be included in item master Kafka domain events (`item-master.created`, `item-master.updated`) within the `customFields` property so downstream consumers receive the complete record.
- **FR-028**: Deleting a UDF field definition that has non-null values on existing records MUST be rejected with HTTP 409 unless `force=true` is supplied (requires `udf:manage` permission). With `force=true`, the system nulls out all values for that field across all records and records an audit entry per affected record.
- **FR-029**: All UDF field definition create/update/delete operations and all custom field value changes MUST be captured in the audit trail, recording actor identity, timestamp, old value, and new value.

### Frontend Requirements

These requirements cover the Angular SPA screens for this Epic. They supplement the REST API functional requirements above and carry the same constitutional weight (§I Spec-First). All frontend screens MUST be secured via Keycloak OIDC (FR-020); privilege-gated actions MUST be hidden/disabled when the authenticated user lacks the required privilege.

- **FR-030**: System MUST persist per-user, per-module column preferences via a `UserGridPreference` entity (`org_id + user_id (JWT sub) + moduleKey`). `GET /api/v1/users/preferences/grid/{moduleKey}` returns saved preferences or the module's built-in defaults. `PUT` saves-or-replaces atomically. Any future screen gains persistent column customisation by providing a `moduleKey` and `DEFAULT_COLUMNS` constant — no additional backend work.
- **FR-031**: The Angular SPA MUST render an application shell (nav rail) wrapping all feature screens: left nav rail collapsible between 64 px (icon-only) and 240 px (expanded with labels); nav items for Dashboard, Item Master, BOM, and ECO; top bar with Aurora MES wordmark, theme toggle, and user avatar (first letter of `preferred_username`); active route highlighted; collapse state persisted in `localStorage`.
- **FR-032**: The Item Master list screen MUST display: page heading "Item Master" with live item count ("1,247 items" format); breadcrumb "Home / Materials / Item Master"; `+ New Item` primary button (disabled without `item-master:records:manage` privilege); coloured classification badge chips using display labels per FR-040; status dot indicators (● ACTIVE green `#22C55E`, ◎ OBSOLETE grey `#94A3B8`); Actions column per row (View text-link button, Edit text-link button, ··· overflow menu containing View Detail / Edit / Clone Item / Obsolete); row-selection checkboxes with a selection bar showing "N item(s) selected" and bulk-Obsolete action; search field (🔍 placeholder "Search part number or description…"); Classification dropdown filter, Make / Buy dropdown filter, Status dropdown filter (default "Status: Active"); "Clear filters" text link; column-picker icon button (Columns tooltip) backed by FR-030; pagination showing "Showing X–Y of Z items" text on the left and numeric page links (1, 2, 3, …, N, ← Prev, Next →) on the right.
- **FR-033**: The Angular SPA MUST provide an Item Master create form as a **full-page route** (`/item-master/new`) — not a modal dialog. Page title "New Item"; breadcrumb "Materials / Item Master / New Item"; action buttons "Cancel" (navigates back to list) and "Save Item" (creates record, navigates to detail on success); "* Required fields" subtitle. Layout is two-column: left column "Core Identification" section (Part Number *, Revision *, Description * textarea, Unit of Measure * dropdown, Classification * dropdown, Make / Buy Code * using a p-selectbutton with options Make | Buy); right column "Traceability & Compliance" section (Traceability Method * dropdown, Shelf Life Controlled toggle with conditional Shelf Life Days * number field showing "days" suffix, CAGE Code text input with placeholder "5-digit CAGE code", Counterfeit Risk Level dropdown defaulting to NONE, Verification Required toggle, STEP Part Reference text input with placeholder "e.g. S000-BRKT-001"). Below both columns: "User-Defined Fields" section showing subtitle "N fields configured for ITEM_MASTER module" and all active UDF fields loaded from `GET /api/v1/udf/fields?module=ITEM_MASTER`. Client-side validation MUST mirror server rules; server 422 violations MUST display inline.
- **FR-033a**: The Angular SPA MUST provide an Item Master **edit form** as a **full-page route** (`/item-master/:id/edit`). Page title is the item's "PARTNUM / Rev X"; breadcrumb "Materials / Item Master / PARTNUM Rev X"; status badge ("Active" / "Inactive" / "Obsolete") shown inline next to the title; "Obsolete this item" text-link button (requires `item-master:records:manage`, hidden when status already OBSOLETE) shown inline; action buttons "Cancel" and "Save Changes". All fields identical to FR-033 except Part Number, Revision, and Traceability Method render as read-only values (not inputs). UDF fields pre-populated from the item's `customFields`.
- **FR-034**: The Angular SPA MUST provide an Item Master detail read-only view showing all fields including UDF `customFields` in a card layout. An **Edit** button navigates to the full-page edit route `/item-master/:id/edit` (FR-033a) — it MUST NOT open a modal dialog from the detail page.
- **FR-035**: The Angular SPA MUST provide a BOM list screen (per item master) showing all BOM revisions with status chips (DRAFT/RELEASED/OBSOLETE) and Actions (Author → BOM authoring screen; Explode → BOM explosion view); a `+ New BOM Revision` button opens a create dialog (bomRevision label, description).
- **FR-036**: The Angular SPA MUST provide a BOM authoring screen at route `/boms/:bomId`. Header area shows: parent item part number (e.g. "ASSY-102") with ✏ icon that opens the BOM Header Edit dialog (FR-036a); BOM Rev dropdown ("Rev A ▾") to switch revisions; status chip (Draft/Released/Obsolete); BOM description; ECO reference ("No active ECO" or linked ECO ID); metadata line "Created DATE · Last modified DATE by USER". Action bar below header: "+ Add Line" button; "Save Draft" button (saves header/line changes without state transition, DRAFT only); **"Submit for Review"** button (calls `POST /boms/{id}/release`, requires `item-master:bom:manage`, shows confirm dialog, label is "Submit for Review" in the UI); "← Explosion View" text link (navigates to explosion route). Navigation breadcrumb: Item Master > {partNumber} > BOMs > {bomRevision}. Lines table columns (in order, per Penpot final frame): **Seq (locked), Find # (locked), Part Number, Description, Rev, Qty, Unit, Eff From, Eff To, [UDF columns], Actions**. Eff From / Eff To cells show "Open" when null; when effectivity_type is UNIT, cells display the lot/unit value with a "(Unit)" badge. Column picker button "⊕ Columns" at far right of table header opens an overlay; STANDARD section: Seq (locked), Find # (locked), Part Number (locked), Description (locked), Revision, Qty, Unit, Eff From, Eff To, Reference Designator, Make / Buy; USER DEFINED FIELDS section: all active UDF field definitions for module `bom-line`. Per-row actions: ✏ (inline edit — opens the row in place with editable fields and Save / Cancel buttons) and 🗑 (remove line — DRAFT only). Bottom status bar: "N components · Unsaved changes — click Save Draft to retain" (shown when dirty); "N components" (shown when clean). All structural controls (Add Line, Submit for Review, ✏/🗑 per-row) MUST be hidden when BOM status ≠ DRAFT.
- **FR-036a**: The BOM Header Edit dialog (triggered by ✏ icon in BOM Authoring header) MUST contain: Part Number (read-only, shows 🔒 icon); BOM Description (text input); Reason for Revision (text input, e.g. "ECO-2024-047: Updated fastener torque spec"); Production Line (**free-text input** — not a dropdown; stores the production line identifier as a plain string on `bill_of_materials.production_line VARCHAR(200)`; org-managed list support is deferred to a future enhancement); **BOM HEADER PROPERTIES** section — BOM Type (dropdown: Manufacturing BOM / Engineering BOM / Service BOM, maps to `bill_of_materials.bom_type` enum) and Effectivity Type (dropdown: DATE / UNIT / NONE, maps to `bill_of_materials.effectivity_type`); **BOM HEADER UDFs** section — active UDF fields for module `bom-header` loaded from `GET /api/v1/udf/fields?module=BOM_HEADER`; footer buttons "Cancel" and "Save Changes". Save calls `PATCH /api/v1/boms/{id}` with updated fields; on success refreshes the authoring screen header card without full page reload. **Note**: V013 migration adds `production_line VARCHAR(200)` to `bill_of_materials` alongside the other header edit fields.
- **FR-037**: The Angular SPA MUST provide a BOM explosion view at route `/boms/:bomId/explosion`. Header area shows: "PARENT ITEM" label with part number / revision value; "BOM REVISION" label with revision dropdown to switch between revisions; status chip (Released/Draft/Obsolete); "Released by USER · DATE" metadata; "N components · N levels deep" count badge; "Effective from: DATE" metadata line. Toolbar (below header): Format toggle p-selectbutton "Indented" | "Flat"; "As of date:" label with date-picker (optional); "Max depth: All ▾" dropdown (options: All / 2 / 3 / 5 / 10); "Collapse All" button; "Expand All" button; "⬇ CSV" export button; "⬇ PDF" export button. Table columns (in order): **Find # (locked), Part Number, Rev, Description, Qty, Make/Buy, Ctft Risk, Status**. Tree expand/collapse per row: ▼ for expanded parent, ▶ for collapsed parent, └─ for child row, – for leaf. Ctft Risk column shows "—" for no risk and "⚠ HIGH" warning text for HIGH or CRITICAL risk items. Status column shows item status (Active/Draft/Obsolete). Indented rows are visually offset by depth level. Bottom summary bar: "N components shown (M top-level, P children) · Effectivity: as of DATE · All items current" (or effectivity gap error message). Effectivity gap errors from the API MUST display as `p-message severity="error"` inline.
- **FR-038**: The Angular SPA MUST provide an ECO list screen with status filter (All/Draft/Approved/Implemented) and a `+ New ECO` button opening a create dialog (title, description, affected item masters multi-select autocomplete). `concurrentEcoWarning` returned on create MUST surface as a warning toast.
- **FR-039**: The Angular SPA MUST provide an ECO detail view showing all ECO fields, affected items (chips linked to item detail), and output BOMs (chips linked to BOM authoring). An Approve button (DRAFT only, requires `item-master:eco:manage`) with confirmation dialog transitions the ECO to Approved. `concurrentEcoWarning` MUST be displayed as an inline warning banner on the detail view when applicable.

> **FR-040 onwards**: These requirements were added after Penpot frame audit (2026-05-31) and represent design details not captured in FR-032 through FR-039. They are authoritative and must be implemented.

- **FR-040**: Classification values MUST be displayed using human-readable display labels — the backend enum value is never shown raw in the UI. Display label mapping: `ASSEMBLY` → "ASSEMBLY"; `COTS` → "COTS"; `FABRICATED` → "FABRICATED"; `PURCHASED_PART` → "PURCHASED"; `RAW_MATERIAL` → "RAW MATERIAL"; `SERVICE` → "SERVICE". This mapping MUST be applied consistently in: the list screen chips, the column picker, the create/edit form dropdown options, and all detail views.

- **FR-041**: The Item Master list screen overflow menu (··· per row) MUST contain four actions: **View Detail** (navigates to detail page), **Edit** (navigates to edit page), **Clone Item** (creates a new draft item pre-populated with all fields from the source record except Part Number and Revision which are cleared; navigates to the New Item form pre-filled), and **Obsolete** (requires `item-master:records:manage`; shows confirm dialog). The same four actions MUST appear in the bulk-action bar when one or more rows are selected: "View Detail", "Edit", "Clone Item", "Obsolete Selected".

- **FR-042**: The Item Master list screen MUST display a pagination bar at the bottom with: left-aligned text "Showing X–Y of Z items" (e.g. "Showing 1–10 of 1,247 items"); right-aligned page navigation showing ← Prev, individual page number links (1, 2, 3), ellipsis (…), last page number, Next →. Active page number MUST be visually distinguished. Clicking a page number navigates to that page and updates the "Showing" text.

- **FR-043**: Every Item Master and BOM screen MUST render a breadcrumb below the top bar. Breadcrumb format per screen: List → "Home / Materials / Item Master"; Create → "Materials / Item Master / New Item"; Edit → "Materials / Item Master / {partNumber} Rev {revision}"; Detail → "Materials / Item Master / {partNumber} Rev {revision}"; BOM Authoring → "Materials / Item Master / {partNumber} Rev {revision} / BOM"; BOM Explosion → "Materials / Item Master / {partNumber} Rev {revision} / BOM". Each segment is a navigation link except the last (current page).

- **FR-044**: The Item Master create and edit screens MUST be full-page routes, not modal dialogs. Route `/item-master/new` renders the create form. Route `/item-master/:id/edit` renders the edit form. Both share the application shell (nav rail + top bar). The existing `ItemMasterFormComponent` (currently a `p-dialog`) MUST be replaced with standalone page components (`ItemMasterCreateComponent` and `ItemMasterEditComponent`) that use the same reactive form logic.

- **FR-045**: The Item Master create/edit form MUST use a **two-column page layout**: left column headed "Core Identification" containing Part Number, Revision, Description, Unit of Measure, Classification, Make / Buy Code; right column headed "Traceability & Compliance" containing Traceability Method, Shelf Life Controlled toggle + conditional Shelf Life Days field, CAGE Code, Counterfeit Risk Level, Verification Required, STEP Part Reference. UDF fields appear in a full-width "User-Defined Fields" section below both columns.

- **FR-046**: The Make / Buy Code field on the Item Master create/edit form MUST render as **two independent toggle buttons** ("Make" and "Buy") — not a mutually-exclusive `p-selectbutton` and not a dropdown. Each button has its own active/inactive state. The mapping to the backend `makeBuyCode` enum is: Make=true + Buy=false → `MAKE`; Make=false + Buy=true → `BUY`; Make=true + Buy=true → `EITHER`. The combination Make=false + Buy=false is invalid and MUST be prevented by client-side validation ("At least one of Make or Buy must be selected"). On load in edit mode, the toggles are initialised from the stored `makeBuyCode` value: `MAKE` activates only Make, `BUY` activates only Buy, `EITHER` activates both Make and Buy simultaneously.

- **FR-047**: The Item Master create/edit form MUST include a **STEP Part Reference** text input field in the "Traceability & Compliance" column with placeholder "e.g. S000-BRKT-001". This field maps to `item_master.step_part_ref` (FR-004). It is optional (no `Validators.required`). It MUST be included in both `CreateItemMasterRequest` payload and `PatchItemMasterRequest` payload.

- **FR-048**: The Item Master **edit** page MUST display an **"Obsolete this item"** text-link button inline in the page header (adjacent to the status badge). Clicking it shows a confirmation dialog; on confirm calls `POST /api/v1/item-master/{id}/obsolete`, refreshes the page to show the updated OBSOLETE status, and disables the Obsolete link. The button MUST be hidden when the item's current status is already OBSOLETE.

- **FR-049**: The Item Master column picker panel MUST be titled **"Customise Columns"** (with the "s" spelling). It MUST use two labelled sections: **"STANDARD COLUMNS"** (all built-in columns) and **"USER-DEFINED FIELDS"** (columns sourced from UDF definitions). Within STANDARD COLUMNS: Part Number, Revision, Description are locked (checkbox disabled, "required" badge shown, drag handle present but sorting has no effect on locked columns). **Counterfeit Risk Level** and **STEP Part Reference** MUST appear in the USER-DEFINED FIELDS section (not STANDARD COLUMNS) because they are domain-specific additions that most users will not need visible by default. All other standard columns (Classification, Make / Buy, Unit of Measure, Status, CAGE Code, Shelf Life Days) appear in STANDARD COLUMNS with drag-reorder enabled. Footer: "Cancel" and "Apply" buttons.

- **FR-050**: The BOM Explosion tree table MUST display the following columns in order: **Find # (locked first column), Part Number, Rev, Description, Qty, Make/Buy, Ctft Risk, Status**. Part Number and Rev are separate columns (not combined). Part Number MUST be a navigation link to the component's item detail page. "Ctft Risk" column displays "—" when no risk; displays "⚠ HIGH" warning badge (amber/red) when `counterfeitRiskAlert=true`. "Status" column displays the component item's current status (Active / Draft / Obsolete) using the `StatusBadgeComponent` dot indicator. UoM is not shown in the explosion tree.

- **FR-051**: The BOM Explosion toolbar MUST contain (left to right): Format toggle p-selectbutton ("Indented" | "Flat"); "As of date:" label with calendar datepicker (optional, clears to show current-date explosion); "Max depth: All ▾" dropdown with options All, 2, 3, 5, 10 (limits explosion depth shown in tree); "Collapse All" button (collapses all expanded nodes); "Expand All" button (expands all nodes to full depth); **"⬇ CSV" export button** (downloads explosion data as CSV); **"⬇ PDF" export button** (downloads explosion data as PDF). CSV and PDF exports MUST include all visible columns and respect current format, as-of-date, and depth filters. The backend MUST provide server-side download endpoints: `GET /api/v1/boms/{id}/explosion?download=csv` and `GET /api/v1/boms/{id}/explosion?download=pdf`, both accepting `format`, `asOfDate`, `asOfUnit`, and `maxDepth` query parameters. The frontend triggers a browser download by opening the URL; no client-side PDF/CSV generation library is required.

- **FR-052**: The BOM Explosion header card MUST display: "PARENT ITEM" label with part number / revision value (e.g. "ASSY-102 / Rev B") as a navigation link to the item detail page; "BOM REVISION" label with a revision dropdown (lists all BOM revisions for the parent item, switching revision reloads the explosion); status chip (Released/Draft/Obsolete); "Released by {user} · {date}" metadata when status is Released; "N components · N levels deep" summary badge; "Effective from: {date}" when a release date is known.

- **FR-053**: The `BomLine` entity and all related DTOs MUST include a **`referenceDesignators`** field (VARCHAR, nullable, e.g. "R1, R2, C4"). This field MUST appear in the BOM Authoring column picker under STANDARD COLUMNS (unchecked by default). It MUST be includable in the lines table and editable via the inline row-edit form. It is already partially specified in the `BomLine` Key Entities section — this FR formalises the UI column requirement and confirms the field must be exposed in `BomLineDto`, `CreateBomLineRequest`, and `UpdateBomLineRequest`.

- **FR-054**: The BOM Authoring screen MUST display a **bottom status bar** spanning the full table width. When the BOM has unsaved changes (lines added/edited but not yet saved): text "N components · Unsaved changes — click Save Draft to retain" in amber. When no unsaved changes: text "N components". The "N components" count updates in real-time as lines are added or removed without requiring a page refresh. When effectivity type is UNIT (lot/serial number effectivity), the **Eff From and Eff To** cells in the lines table MUST display a "(Unit)" sub-label badge beneath the field value to distinguish unit-effectivity values from date values.

### Key Entities

- **ItemMaster**: The canonical part record. Key fields: `id` (UUID PK), `partNumber`, `revision`, `description`, `unitOfMeasure`, `cageCode`, `classification`, `makeBuyCode`, `traceabilityMethod`, `shelfLifeControlled`, `shelfLifeDays`, `stepPartRef`, `counterfeitRiskLevel`, `approvedSuppliers` (JSONB), `verificationRequired`, audit fields. Unique constraint: `(partNumber, revision)`.
- **BillOfMaterials**: BOM header for one revision of one assembly. Key fields: `id` (UUID PK), `parentItemId` (FK → ItemMaster), `bomRevision`, `status` (DRAFT/RELEASED/OBSOLETE), `description`, `ecoId` (FK → EngineeringChangeOrder, nullable), `reasonForRevision` (VARCHAR 500, nullable), `productionLine` (VARCHAR 200, nullable — free-text, e.g. "Line 3 – Wing Final Assembly"), `bomType` (enum: MANUFACTURING / ENGINEERING / SERVICE, nullable), `effectivityType` (enum: DATE / UNIT / NONE, nullable — governs all BOM lines), `customFields` (JSONB, nullable — stores BOM-header-level UDF values for module `BOM_HEADER`), audit fields. Added by V013 migration (PR 2).
- **BomLine**: A component entry within a BOM revision. Key fields: `id` (UUID PK), `bomId` (FK → BillOfMaterials), `componentItemId` (FK → ItemMaster), `quantity` (decimal), `unitOfMeasure`, `findNumber`, `referenceDesignators`, `effectiveFromDate`, `effectiveToDate`, `effectiveFromUnit`, `effectiveToUnit`, audit fields.
- **EngineeringChangeOrder**: ECO header. Key fields: `id` (UUID PK), `ecoNumber` (unique sequence), `title`, `description`, `status` (DRAFT/APPROVED/IMPLEMENTED), `initiatedBy`, `approvedBy`, `approvedAt`, `implementedAt`. Relationships: many-to-many with `ItemMaster` (affected items); one-to-many with `BillOfMaterials` (output BOM revisions).
- **ItemMasterAuditEntry** (via Envers `@Audited`): Immutable audit trail. One row per change to `ItemMaster`, `BillOfMaterials`, or `BomLine`. Stores old/new values, actor, and timestamp.
- **UdfFieldDefinition** (`mes-udf-lib`): Module-scoped custom field schema. Key fields: `id` (UUID PK), `moduleKey` (enum: `ITEM_MASTER`, `BOM_LINE`, `BOM_HEADER`, `WORK_ORDER`, `ROUTING`, `RECEIVING`, `INVENTORY`), `fieldKey` (snake_case, unique per `moduleKey`), `label`, `type` (TEXT/NUMBER/DATE/BOOLEAN/LIST), `required`, `defaultValue`, `listOptions` (JSONB array), `validationRules` (JSONB), `displayOrder`, audit fields. Lives in a shared `udf_field_definition` table owned by whichever service hosts the module (for all current modules: `work-order-service` DB).
- **`customFields` JSONB column on `ItemMaster`**: Stores validated UDF values as a flat key-value map (e.g., `{"drawing_ref": "DRW-001", "weight_kg": 1.45}`). Keys must correspond to defined `UdfFieldDefinition.fieldKey` values for `moduleKey = ITEM_MASTER`.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An `ENGINEER` can create an item master record and retrieve it by `partNumber + revision` within 500 ms at p95 under a 100-concurrent-user load test.
- **SC-002**: A multi-level BOM with 10 levels and 50 total components can be exploded (indented format) and returned in under 2 seconds at p95.
- **SC-003**: BOM circular-reference detection correctly rejects a circular dependency at line-create time with HTTP 422 in 100% of test cases.
- **SC-004**: Effectivity filtering correctly returns only effective BOM lines for a given `asOfDate` in 100% of integration test cases covering boundary dates (first day, last day, day after expiry).
- **SC-005**: Kafka domain events are published and confirmed received by a test consumer within 5 seconds of the triggering operation for all event types (item-master.created, bom.released, eco.approved).
- **SC-006**: AS9100D audit trail records every create/update operation with user identity and timestamp, verifiable by querying the Envers audit table.
- **SC-007**: Zero SonarCloud blocker/critical issues on merge; overall quality gate green on the first PR.
- **SC-008**: A `SYSTEM_ADMIN` user can define a required TEXT UDF on `ITEM_MASTER`; subsequent item master create attempts without that field return HTTP 422 in 100% of integration test cases.
- **SC-009**: The `mes-udf-lib` library compiles independently and is importable by a stub second service (e.g., a test fixture for `routing-service`) with zero code changes to the library, confirming module-agnostic reusability.

---

## Clarifications

### Session 2026-05-31

- Q: Should BOM explosion CSV and PDF export be generated server-side (backend endpoints) or client-side (Angular from in-memory tree data)? → A: Backend endpoints — `GET /api/v1/boms/{id}/explosion?download=csv` and `?download=pdf`; Spring generates streamed response; no client-side generation library required.
- Q: Does the `QUALITY_ENGINEER` role in US6 map to the existing ENGINEER role, or is it a new formal Keycloak role? → A: New formal Keycloak role — seeded via Flyway with `item-master:records:view` and `item-master:as5553:manage` privileges; ENGINEER does not hold `item-master:as5553:manage` by default; integration tests use a dedicated `qualityEngineerToken()` fixture.
- Q: Should `BOM_LINE` and `BOM_HEADER` be added to the `ModuleKey` enum in `mes-udf-lib`, or handled via strings or a separate mechanism? → A: Add to enum — `ModuleKey` MUST include `BOM_LINE` and `BOM_HEADER`; compile-time safety preserved; consistent with how `ITEM_MASTER` is handled; `UdfLibReusabilityIT` should be extended to cover these keys.
- Q: Should the Item Master detail page Edit button navigate to `/item-master/:id/edit` (full-page route) or open a modal dialog? → A: Navigate to full-page route `/item-master/:id/edit` — consistent with FR-044; Edit button is a `routerLink`; no dialog on the detail page.
- Q: How should the Make / Buy Code UI handle the EITHER enum value? → A: Two independent toggle buttons ("Make" and "Buy") where both active simultaneously = EITHER; Make=true+Buy=false=MAKE; Make=false+Buy=true=BUY; Make=true+Buy=true=EITHER; Make=false+Buy=false is invalid (client-side validation prevents it); EITHER is a first-class UI state, not API-only.

---

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §8.1 — product and service configuration control via BOM revisions and ECOs; §7.5 — documented information: item master and BOM are quality records subject to control, review, and retention |
| AS9102 (FAI) | Yes | Item master `partNumber + revision` and the released BOM revision are the configuration baseline referenced in the First Article Inspection report; traceability method field must be populated for all FAI-relevant parts |
| AS9131 (NCM) | Partial | Traceability method (serial/lot/heat-code) enables linking non-conforming material back to its item master record; the NCM disposition workflow (future Epic) consumes item master data |
| NIST SP 800-171 / CMMC | Partial | If item masters cover ITAR/EAR-controlled parts (e.g., classified assemblies), access to those records must be role-restricted; the `classification` field should support a future `ITAR_CONTROLLED` flag. For v1, standard Keycloak role-based access is sufficient. |
| 21 CFR Part 11 / Annex 11 | No | MikeMES targets aerospace/defence, not pharmaceutical/medical device manufacture; this standard does not apply |
| ISA-95 | Yes | Part 2 — Material Definition: `ItemMaster` maps to ISA-95 Material Class; `traceabilityMethod` and lot/serial attributes map to Material Lot/Sublot definitions |
| ISO 10303 (STEP) | Partial | Part identity reference field (`stepPartRef`) is in scope to support future PDM/CAD exchange; full STEP file import/export is deferred (see DEF-001) |
| AS5553 | Yes | Counterfeit-part risk fields on `ItemMaster` are mandatory for aerospace supply chain; `counterfeitRiskLevel`, `approvedSuppliers`, and `verificationRequired` must be recorded and queryable |

---

## Assumptions

- Part number schema is company-defined; the system enforces uniqueness and non-null constraints but does not validate format (e.g., no regex check on part number structure in v1).
- BOM revision labels are free-text strings (e.g., "A", "B", "Rev-2"); the system does not enforce a specific numbering scheme.
- CAGE code entry is free-text in v1; DLA database validation is deferred (see DEF-002).
- The `work-order-service` microservice exists or will be scaffolded as the implementation target for this Epic.
- Keycloak roles `ENGINEER`, `MATERIALS_ADMIN`, `QUALITY_ENGINEER`, and `SYSTEM_ADMIN` are provisioned by the IAM Epic (MES-5 / MES-6). The `udf:manage` permission is modelled as a fine-grained Keycloak resource permission and assigned to `SYSTEM_ADMIN` by default; it may be reassigned to other roles without a code change.
- The `mes-udf-lib` shared library will be declared in the monorepo `libs/` directory alongside existing shared libraries, consistent with the platform conventions established in prior Epics.
- Kafka topics follow the naming convention established by the Audit Logging Epic (MES-37); the `item-master` and `bom` topics will be registered in the platform broker config.
- The Hibernate Envers `@Audited` pattern already used in the platform (see MES-ERR memory) applies to all entities in this Epic.
- The `work-order-service` uses PostgreSQL as its database, consistent with all other MikeMES services.
- The programme Epic MES-4 provides the parent context for this Epic in the programme backlog.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | ISO 10303 (STEP) file import/export | High complexity; requires CAD/PDM system integration and STEP parser library; the `stepPartRef` field stores a text reference as a bridge | Engineers cannot import part geometry and identity directly from CAD tools; manual data entry remains | Post-GA | |
| DEF-002 | DLA CAGE code real-time validation | Requires DLA web service integration; CAGE data changes infrequently; free-text entry is acceptable for v1 | Invalid CAGE codes can be entered without system detection; may cause issues on government contract data submissions | P3 | |
| DEF-003 | Multi-site / multi-plant item master sharing | Single-tenant item master is sufficient for Phase 2; cross-site master data management adds significant complexity | Each plant would maintain independent item masters with no cross-site visibility or sharing | Post-GA | |
| DEF-004 | BOM comparison / redline view | UI feature; the data model supports comparing two BOM revision records but the diff rendering is non-trivial | Engineers must compare BOM revisions manually; change impact assessment is slower | P3 | |
| DEF-005 | Approved Supplier List (ASL) management UI and workflow | AS5553 ASL data stored as JSONB in v1 is sufficient for recording; a full ASL management workflow with supplier status lifecycle requires a dedicated UI and approval process | AS5553 compliance relies on manual discipline to keep the JSONB list accurate; no system-enforced supplier approval process | P3 | |
| DEF-006 | ITAR/EAR classification flag and access restriction | Requires classification tagging of individual item records and row-level security; not needed for initial deployment scope | ITAR-controlled parts accessible to any authenticated user with `ENGINEER` role; export control risk for classified programmes | P3 | |
| DEF-007 | Centralised UDF administration UI | The REST API for managing UDF definitions ships in this Epic; a unified admin UI panel showing all modules' UDF definitions in one place requires the Angular frontend Epic to be underway | System administrators must manage UDF definitions per-module via API or per-module admin screens; no cross-module overview | P3 | |
| DEF-008 | UDF import/export (CSV/JSON field definitions) | Allows bulk-loading UDF schemas from a template; useful for multi-site rollout but adds complexity beyond v1 scope | System administrators must define fields individually via API on each environment | Post-GA | |
