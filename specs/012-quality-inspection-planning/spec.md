# Feature Specification: Quality Inspection Planning (Control Plans)

**Feature Branch**: `012-quality-inspection-planning`

**Created**: 2026-06-12

**Status**: Draft

**Input**: Jira Epic MES-12 — "P2 · Quality Inspection Planning (Control Plans)": Define inspection plans (control plans) per part: characteristics to measure, inspection methods, gauge and instrument type requirements, sample sizes, and accept/reject criteria. Control plans must exist before Work Orders referencing that route can be released to the shop floor. Microservice: quality-service (planning domain). New module under Quality > Inspection Plans in the sidebar. Inspection plans are revision controlled like a BOM or route and require formal approval before use in route creation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create an Inspection Plan for a Part (Priority: P1)

A quality engineer creates an inspection plan for a part number: the plan header identifies the part (item master reference), plan name, description, and reason for revision. The plan starts at revision 0 in DRAFT status and follows the established revision lifecycle (DRAFT → PENDING_APPROVAL → APPROVED, reject back to DRAFT), identical in semantics to BOM revision management.

**Why this priority**: The plan container with its revision lifecycle is the foundation; characteristics (US2) live inside it, and routing (MES-9) references an approved plan revision.

**Independent Test**: Create a plan for an item, verify revision 0 DRAFT persists; submit and approve it; create a new draft revision and verify the previous revision's content is copied.

**Acceptance Scenarios**:

1. **Given** a user with `quality:inspection-plan:create` privilege and an existing item master, **When** they create an inspection plan referencing that item, **Then** the plan persists org-scoped at revision 0 DRAFT.
2. **Given** a part that already has an inspection plan, **When** a second plan is created for the same item, **Then** the system rejects it with a conflict error (one plan per item; revisions provide change control).
3. **Given** a DRAFT plan revision, **When** the engineer submits it for approval and an authorised approver approves it, **Then** the revision becomes APPROVED with submit/approve metadata recorded, and the plan is eligible for reference by route creation.
4. **Given** an APPROVED plan revision, **When** the engineer edits the plan header, **Then** a new DRAFT revision N+1 is auto-created carrying a full copy of all characteristics, leaving revision N intact (mirrors BOM patchHeader behaviour).
5. **Given** a PENDING_APPROVAL revision, **When** an approver rejects it with a reason, **Then** it returns to DRAFT and the reason is recorded and visible.

---

### User Story 2 - Define Inspection Characteristics (Priority: P1)

Within a DRAFT plan revision, the quality engineer defines the characteristics to be verified. Each characteristic has an identifying number, name/description, source (design characteristic from the 3D model/drawing, or in-process characteristic defined by manufacturing engineering), a characteristic type — **Specific**, **Common**, or **Calculated** — an inspection method, gauge/instrument type requirement, sample size rule, unit of measure, and accept/reject criteria.

- **Specific criteria**: apply to a specific part number, recorded against each serial number or piece within a lot. Typically numeric with nominal value and lower/upper tolerance limits.
- **Common criteria**: recorded once against all serial numbers/pieces in the lot (e.g. "Certificate of Conformity present and covers the lot") — boolean pass/fail style answers.
- **Calculated criteria**: derived from values captured by other characteristics or from machine historian tag data, using a simple mathematical expression (e.g. `(C1 + C2) / 2`); the expression references other characteristics in the same plan.

**Why this priority**: Characteristics are the substance of a control plan — without them the plan proves nothing about conformity.

**Independent Test**: In a draft plan, create one Specific characteristic with nominal/tolerances, one Common boolean characteristic, and one Calculated characteristic referencing the Specific one; verify all persist with their type-specific fields and the calculated expression validates.

**Acceptance Scenarios**:

1. **Given** a DRAFT plan revision, **When** the engineer adds a Specific characteristic with characteristic number, description, inspection method, gauge type, UoM, nominal, lower and upper limits, and sample size, **Then** it persists and is listed in characteristic-number order.
2. **Given** a DRAFT plan revision, **When** the engineer adds a Common characteristic with a yes/no (conforms/does-not-conform) expected answer, **Then** it persists flagged as per-lot rather than per-piece.
3. **Given** a DRAFT plan revision with characteristics C1 and C2, **When** the engineer adds a Calculated characteristic with expression referencing C1 and C2, **Then** the system validates the references exist in the plan and stores the expression; an expression referencing a non-existent characteristic is rejected with a validation error.
4. **Given** characteristics in a draft, **When** the engineer edits, reorders, or deletes them, **Then** changes persist only while the revision is DRAFT; the same operations on PENDING_APPROVAL/APPROVED revisions are rejected.
5. **Given** a characteristic marked per-piece (Specific), **When** the plan is viewed, **Then** the recording basis (per piece / per lot) is unambiguous for downstream execution.
6. **Given** a Calculated characteristic that other characteristics depend on is deleted, **When** the deletion is attempted, **Then** the system rejects it naming the dependent characteristics (no dangling references).

---

### User Story 3 - Formal Approval Gate for Downstream Use (Priority: P1)

Only APPROVED inspection plan revisions may be referenced by downstream consumers (route creation in MES-9; work order release later). The service exposes a read API for consumers that resolves "the approved plan revision for item X" and answers whether a plan exists and is approved — the data MES-9 needs at route creation and the Work Order epic needs at release ("Control plans must exist before Work Orders referencing that route can be released").

**Why this priority**: The approval gate is the epic's stated business rule and its cross-service contract.

**Independent Test**: Query the approved-plan endpoint for an item with only a DRAFT plan and verify "not available"; approve the plan and verify the endpoint returns the approved revision with characteristics.

**Acceptance Scenarios**:

1. **Given** an item with a DRAFT-only plan, **When** a consumer queries for its approved plan, **Then** the API reports no approved plan available.
2. **Given** an item with APPROVED revision 0 and DRAFT revision 1, **When** a consumer queries, **Then** the API returns revision 0 content (latest approved governs).
3. **Given** an approved plan revision, **When** a consumer retrieves it, **Then** the full characteristic list with type-specific fields is returned in a stable contract suitable for allocation to route operations in MES-9.

---

### User Story 4 - Browse and Manage Inspection Plans in the UI (Priority: P2)

Quality engineers access a new sidebar module **Quality > Inspection Plans**: a list screen (search, filters, column picker, UDF columns) and a detail/authoring screen showing the plan header, revision selector with history, and the characteristics grid with type-specific editing. The screens mirror the BOM browser/authoring UX so users transfer knowledge directly.

**Why this priority**: The UI makes the module usable, but API-first delivery lets MES-9 proceed even if UI polish lands later.

**Acceptance Scenarios**:

1. **Given** the Angular app, **When** a user with read privilege opens Quality > Inspection Plans, **Then** they see a paged, searchable list with part number, plan name, current revision, status, and modified date.
2. **Given** a plan with multiple revisions, **When** the user opens its detail page, **Then** the display revision (latest approved, else pending, else draft) is shown with a revision dropdown and history table, and selecting an older revision loads that revision's characteristics.
3. **Given** a DRAFT revision in the detail page, **When** the engineer adds/edits characteristics, **Then** the grid provides type-appropriate forms (numeric tolerances for Specific, boolean expectation for Common, expression editor for Calculated).
4. **Given** the standard workflow buttons (Submit, Approve, Reject, Create Revision), **When** used, **Then** they behave exactly as the BOM authoring screen including confirmation toasts and reload-after-action (ERR-MES-059 change-detection rules applied).

---

### Edge Cases

- Approving a plan revision with zero characteristics is rejected — an empty control plan proves nothing.
- Circular references between Calculated characteristics (C3 = f(C4), C4 = f(C3)) must be detected and rejected at save time.
- A Calculated expression referencing a characteristic deleted in the same draft session: validation at save and at submit must both catch it.
- Item master is soft-deleted/obsoleted after a plan exists: plan remains, but the approved-plan query flags the item state so MES-9 can warn.
- Lower limit greater than upper limit, or nominal outside the limits, is rejected at characteristic save.
- Sample size semantics: v1 supports "all" (100%) or a fixed count n per lot; statistical sampling schemes (ANSI Z1.4) are deferred.
- Historian tag references in Calculated characteristics are stored as free-text tag identifiers in v1 (no live historian integration exists yet).
- Only one DRAFT revision per plan at a time, consistent with Item/BOM behaviour.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide inspection plan CRUD in `quality-service` (new service, planning domain) with plan header: item master reference (one plan per item per org), name, description, reason for revision, custom fields.
- **FR-002**: System MUST manage plans under the established revision lifecycle: revisions from 0, DRAFT → PENDING_APPROVAL → APPROVED, reject to DRAFT with reason, at most one DRAFT at a time, full copy of characteristics into a new draft created from an approved revision (auto-draft on edit of approved, mirroring BOM behaviour).
- **FR-003**: System MUST support characteristics within a revision with shared fields: characteristic number (unique within revision), name/description, source (DESIGN or IN_PROCESS), inspection method (free text v1), gauge/instrument type requirement (free text v1), unit of measure, sample size rule (ALL or fixed count), recording basis (PER_PIECE or PER_LOT), and custom fields.
- **FR-004**: System MUST support characteristic type SPECIFIC with numeric accept criteria: nominal value, lower limit, upper limit (validated lower ≤ nominal ≤ upper); recorded per piece/serial by default.
- **FR-005**: System MUST support characteristic type COMMON with boolean accept criteria (expected answer conforms/pass), recorded per lot by default.
- **FR-006**: System MUST support characteristic type CALCULATED with a mathematical expression referencing other characteristics in the same plan revision by characteristic number and/or named historian tag placeholders; the system validates referenced characteristics exist and rejects circular dependencies.
- **FR-007**: System MUST prevent any modification of characteristics or header content on PENDING_APPROVAL and APPROVED revisions.
- **FR-008**: System MUST reject submission for approval of a revision containing zero characteristics or invalid calculated references.
- **FR-009**: System MUST expose a consumer read API: latest approved plan revision (with full characteristics) by item id, and an existence/approval status check — the contract used by MES-9 route creation and work order release gating.
- **FR-010**: System MUST expose revision history per plan (revision, status, actor/timestamp metadata) and retrieval of any historical revision's full content.
- **FR-011**: All entities MUST be org-scoped, privilege-protected (`quality:inspection-plan:*` keys registered in the privilege manifest), and Envers-audited with `_aud` tables.
- **FR-012**: Plan headers and characteristics MUST support UDFs via the established module-key mechanism (new module keys INSPECTION_PLAN, INSPECTION_CHARACTERISTIC).
- **FR-013**: Frontend MUST add Quality > Inspection Plans to the sidebar with list + detail/authoring screens following established patterns (column picker + UDF, ERR-MES-059 change detection, Lucide icon directives).
- **FR-014**: Approval workflow events MUST be published (Kafka) on plan approval, consistent with BOM approval events, so downstream services can react.

### Key Entities

- **InspectionPlan**: Org-scoped root, one per item master; container for revisions.
- **InspectionPlanRevision**: Numbered revision with status, header snapshot, submit/approve/reject metadata, custom fields.
- **InspectionCharacteristic**: Child of a revision; characteristic number, name, source, type (SPECIFIC/COMMON/CALCULATED), inspection method, gauge type, UoM, sample size rule, recording basis, type-specific fields (nominal/limits, expected boolean, expression), custom fields.
- **CharacteristicReference** (within Calculated expression): validated link by characteristic number to peer characteristics; historian tag placeholders stored as text.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A quality engineer can author and submit a 15-characteristic control plan in under 30 minutes using the UI.
- **SC-002**: 100% of route-creation queries (MES-9 contract) resolve the correct latest-approved revision — verified by integration tests covering draft-only, approved+draft, and multi-approved histories.
- **SC-003**: Zero invalid calculated expressions reach APPROVED state (reference and cycle validation enforced at save and submit).
- **SC-004**: Any historical approved revision's full characteristic set is reconstructable for audit (supports AS9103 variation-management evidence).
- **SC-005**: The approved-plan consumer API responds in under 500 ms for a 50-characteristic plan.

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §8.6 release of products: planned arrangements (control plan) must exist and be approved before release; §8.1 operational planning |
| AS9102 (FAI) | Partial | FAI forms consume design characteristics; characteristic numbering and source (design vs in-process) must support future FAI reporting |
| AS9131 (NCM) | Partial | Nonconformance records cite the characteristic and plan revision in force at inspection time |
| AS9103 | Yes | Variation management of key characteristics: identification, measurement method, and control documented per characteristic |
| AS9145 (APQP/PPAP) | Yes | Control plan methodology: characteristics, methods, sample size, reaction documented per APQP phase 4 expectations |
| NIST SP 800-171 / CMMC | Yes | Plans may embody export-controlled design data; org isolation + privilege-gated access + audit |
| 21 CFR Part 11 / Annex 11 | No | Aerospace context; approval uses the standard audited workflow without Part 11 e-signature (unlike MES-10 which presents documents to operators under FDA-style control) |
| ISA-95 | Partial | Part 2 test specifications map to characteristics within Operations Definition |
| QIF (ISO 23952) | Partial | Characteristic model aligned to QIF concepts (nominal, tolerances, method) to enable future QIF import/export |

---

## Assumptions

- A new `quality-service` is scaffolded for the planning domain (the Epic names it); execution-time inspection recording (results capture) is a later epic in the same service.
- One inspection plan per item master per organisation; plan-to-route characteristic allocation happens in MES-9 (the Epic states characteristics are allocated to operations during route creation).
- Inspection methods and gauge/instrument types are free-text fields in v1; managed gauge catalogues (calibration management) are a future epic.
- Calculated expressions use a restricted arithmetic grammar (+, −, ×, ÷, parentheses, numeric literals, characteristic references, historian tag placeholders); no arbitrary code execution.
- Historian integration does not exist yet; tag references are stored for future use and validated only for format.
- The single-approver workflow is sufficient for v1; multi-approver chains arrive with MES-112 (Workflow Approval Engine).
- Item master data is referenced cross-service by id (inventory-service is the source of truth); part number is denormalised for display.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Statistical sampling schemes (ANSI/ASQ Z1.4, C=0) | v1 supports ALL or fixed-n; sampling tables add significant rule complexity | Sample sizes managed manually by QE judgement | P3 | |
| DEF-002 | Managed gauge/instrument catalogue with calibration tracking | Separate calibration-management domain | Gauge requirements remain free text; no calibration-due gating at inspection | P3 | |
| DEF-003 | Live historian tag browsing/validation for Calculated characteristics | No historian integration exists in the platform yet | Tag references unvalidated until execution epic | P3 | |
| DEF-004 | QIF (ISO 23952) import/export of characteristics | Standards alignment captured in data model only | Manual characteristic entry from CAD/CMM data | Post-GA | |
| DEF-005 | Reaction plans / OCAP per characteristic (AIAG control plan column) | Execution-domain behaviour; no execution module yet | Control plan lacks documented reaction steps until execution epic | P3 | |
| DEF-006 | FMEA linkage (severity/occurrence/detection per characteristic) | AIAG methodology referenced by Epic but FMEA module out of scope | Risk linkage tracked outside MES | Post-GA | |
