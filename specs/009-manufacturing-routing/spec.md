# Feature Specification: Manufacturing Routing

**Feature Branch**: `009-manufacturing-routing`

**Created**: 2026-06-15

**Status**: Draft

**Input**: Jira Epic MES-9 — "P2 · Manufacturing Routing". Define and manage manufacturing routes (operations, sequences, work centres, time standards, labour skill requirements per operation). Routes are the prerequisite for Work Order creation (MES-14) and Shop Floor Tracking (MES-15). New microservice: `shopfloor-service` (routing domain).

## Clarifications

### Session 2026-06-15

- Q: How are the additional significant-process (SME) approvers determined during route approval? → A: Per-process-type mapping — each significant-process type maps (in Settings) to a required approver role; approval gathers the distinct significant-process types on the route and requires an e-signature from a holder of each mapped approver role.
- Q: When an approved route's pinned BOM/inspection-plan revision is superseded upstream, what should happen? → A: Flag/warn only — the route keeps its approved revision; the system surfaces a "newer upstream revision available" indicator (via a supersede-event consumer) and the engineer decides whether to start a route revision. No automatic block.
- Q: How should an OSP operation capture its outside source? → A: An OSP operation carries an **OSP resource code** (the OSP labour resource, which triggers the ERP requisition) **plus an optional supplier** selected from an interim Settings-maintained Supplier list — decoupling supplier from resource code so fewer resource codes are needed. The supplier is confirmed/selected at OSP-operation **start** during execution (MES-15), which emits a Kafka event (resource code + supplier) that a middleware tool ties to the requisition to raise the PO. Routing persists the resource code + optional supplier; PO creation/sourcing itself is out of scope (DEF-002).
- Q: Canonical names for the two labour-type concepts (to remove the "Labour Planning Type" vs "Labour Plan Type" collision)? → A: The fixed set Setup/Run/Inspection/Transport is the **Labour Activity Type**; the extensible set Machine/People/OSP is the **Labour Plan Type**. (Terminology normalised across the spec.)
- Q: Canonical name for the basic/default operation, group and step flow-control type, given "Standard" already names the default Route Type? → A: Use **Normal** for the operation/group/step basic type; reserve **Standard** exclusively for the Route Type. The type indicator shows Normal / Optional / Parallel / Mutually Exclusive. (Terminology normalised across the spec.)

### Session 2026-06-16

- Q: What does the route "lock" control? → A: A route is edited under an exclusive **edit lock**. To edit a draft the user must hold the lock; while held, only the lock-holder can edit and all other users see it read-only. The lock is released when the holder unlocks, or when the route is approved/published. A user with a dedicated **force-unlock privilege** can release a lock held by another user (e.g. the holder is on leave). (New FR-031/032/033.)
- Q: What does the operation "Labour Type" (Direct/Indirect) on the Attributes tab mean? → A: It classifies how the operation's labour is **charged** — **Direct** labour is allocated to the labour code defined on the operation's Resources tab; **Indirect** labour is charged to a central cost centre rather than the labour code. (New FR-013a.)
- Q: Are the Properties-tab "custom properties" the same as UDFs? → A: No. A **UDF** is global — added once, it appears on every screen. **Custom properties** are **scoped** — defined against a specific route type, a specific part type, or against variables within a specific inspection plan. This scoped custom-property engine is a **wider-product capability** beyond MES-9 and is tracked as its own epic; routing's Properties tab is one consumer of it. (New FR-034; separate epic.)

## User Scenarios & Testing *(mandatory)*

A **route** describes how a part is made: a **route header** (what it applies to), an ordered set of **route operations** (the logical manufacturing steps), and optional **operation steps** (the breakdown of a complex operation). A process engineer authors a route, attaches resources and standards to each operation, takes it through approval, and revises it over time. Approved routes are consumed downstream by Work Orders and Shop Floor execution.

### User Story 1 - Author a route header linked to part, BOM and inspection plan (Priority: P1)

A process engineer creates a new route and records what it applies to: the part number/revision, the **route type** (defaulting to Standard), the linked BOM and its revision, the owning organisation, the linked inspection plan revision, and the reason for the revision (including the initial revision). A given part number/revision can have multiple routes of different types, but **at most one route of type Standard** (the route the majority of work orders are made to); alternate-type routes (e.g. NPI, FAI, Process Improvement) may also exist. The route starts in a draft state.

**Why this priority**: The header is the anchor for everything else — operations, resources and approvals all hang off it, and downstream Work Orders resolve a route by part identity and route type. Nothing in the epic is testable without it.

**Independent Test**: Create a draft route header against an approved part/BOM/inspection-plan with a route type, confirm it persists with all linkages and a recorded reason for revision, is retrievable by part number, and that a second Standard route for the same part/revision is rejected.

**Acceptance Scenarios**:

1. **Given** an approved item, BOM revision and inspection-plan revision exist, **When** the engineer creates a route header referencing them with route type Standard and an initial reason for revision, **Then** the route is saved in DRAFT, scoped to the caller's organisation, with revision 1.
2. **Given** a Standard route already exists for a part/revision, **When** the engineer creates a second route for the same part/revision with type Standard, **Then** the system rejects it (only one Standard route per part/revision).
3. **Given** a Standard route exists for a part/revision, **When** the engineer creates an additional route for the same part/revision with an alternate type (e.g. NPI), **Then** it is accepted as a separate route.
4. **Given** a route header, **When** another user in a different organisation lists routes, **Then** the route is not visible to them (org isolation).
5. **Given** a route is created without a reason for revision, **When** the engineer attempts to save, **Then** the system rejects it with a validation error.

---

### User Story 2 - Add and sequence Normal route operations (Priority: P1)

The engineer adds route operations to a draft route. Each operation has an operation number (e.g. 10, 20, 30), a description, and a sequence number that controls execution order. A **Normal** operation has matching operation and sequence numbers and is the basic building block.

**Why this priority**: A route with no operations is not a route. Normal operations plus the header form the minimum viable, executable route.

**Independent Test**: Add several Normal operations to a draft route, confirm they persist with operation/sequence numbers and render in sequence order.

**Acceptance Scenarios**:

1. **Given** a draft route, **When** the engineer adds operations 10, 20, 30 as Normal, **Then** they are stored and returned ordered by sequence number.
2. **Given** an operation number that already exists on the route, **When** the engineer adds another with the same operation number, **Then** the system rejects the duplicate.
3. **Given** a draft route with operations, **When** the engineer reorders, renumbers or deletes an operation, **Then** the route reflects the change while still in draft.

---

### User Story 3 - Define operation resources, standards and consumption (Priority: P1)

For each route operation the engineer defines what is needed to execute it: the eligible machine(s)/workstation(s); whether the operation is clocking or non-clocking; the labour code per labour plan type (People, Machine, OSP); the standard time breakdown per labour activity type (Setup, Run, Inspection, Transport) with a per-item or per-lot basis; which BOM material is consumed at the operation and whether consumption is mandatory; the quality variables collected (from the linked inspection plan); the gage/tooling consumed; an optional machine STEP-file reference; the skills required to execute; the work instruction to follow; and whether the operation is a significant (special) process.

**Why this priority**: These attributes are what make an operation executable and schedulable; without them a route cannot drive Work Order execution, labour planning or skill gating. This is the substance of a route.

**Independent Test**: Attach a full resource/standard profile to an operation and confirm each attribute persists and is retrievable; confirm material lines reference valid BOM lines and skill requirements reference valid labour skills.

**Acceptance Scenarios**:

1. **Given** an operation, **When** the engineer sets a Setup time (per-lot) and a Run time (per-item) — both Labour Activity Types — against the People Labour Plan Type, **Then** both activity-type times persist with their basis.
2. **Given** an operation, **When** the engineer marks a BOM material line as consumed-and-mandatory, **Then** the consumption is recorded against that operation referencing the BOM line.
3. **Given** an operation, **When** the engineer adds a required skill, **Then** the requirement references an existing labour skill and is returned with the operation.
4. **Given** an operation, **When** the engineer flags it as a significant process by selecting a significant-process type, **Then** the type is stored and its mapped approver role later drives the additional approvers (US7).

---

### User Story 4 - Advanced operation types and flow control (Priority: P2)

Beyond Normal, an operation's flow-control type is set in two ways — some types are **derived dynamically from the sequence number**, others are **explicit toggles**:

- **Normal (derived)**: an operation whose sequence number is unique on the route.
- **Parallel (derived)**: two or more operations that share one sequence number — derived automatically, executable in any order, but all must complete before the next sequence.
- **Optional (toggle)**: an explicit toggle on the operation; when on, the operation may be skipped during execution when the work instruction directs.
- **Mutually Exclusive (toggle)**: an explicit toggle, only available within a parallel sequence. When toggled on, the system asks **which operations in that parallel sequence are mutually exclusive**, and the user selects the subset. Operations in the selected mutually-exclusive set behave such that the first one clocked-on becomes active and excludes the others *in the set* from needing completion; operations in the parallel sequence that are **not** selected remain parallel to whichever member ends up active.
- **Outside Processing / OSP**: the step is subcontracted outside the work order. Indicated by assigning an OSP-type labour resource (the OSP resource code) to the operation, with an optional supplier selected from the Settings Supplier list. The resource code triggers the ERP requisition; the supplier is confirmed at OSP-operation start (MES-15), emitting the event a middleware tool ties to the req to raise the PO.

Worked example: operations 20, 30 and 40 share a sequence number, so all three are derived Parallel. The user toggles Mutually Exclusive and selects only 30 and 40. Now 30 and 40 are mutually exclusive (only one need complete), while 20 stays parallel to whichever of 30/40 becomes active.

**Why this priority**: Real aerospace routes need conditional, concurrent and subcontract flows; these are essential for production realism but build on the Normal-operation foundation (P1).

**Independent Test**: Create a parallel sequence, toggle Optional on one operation, toggle Mutually Exclusive and select a subset of the parallel members, and toggle OSP on another; confirm derived vs toggled types, the mutually-exclusive subset membership, and the sequencing constraints are validated and persisted.

**Acceptance Scenarios**:

1. **Given** two operations sharing one sequence number, **When** the route is saved, **Then** both are reported as Parallel automatically (derived), with no explicit "parallel" toggle required.
2. **Given** an operation with a unique sequence number, **When** the engineer toggles Mutually Exclusive, **Then** the system rejects it because mutual exclusivity is only available within a parallel sequence.
3. **Given** operations 20, 30 and 40 sharing a sequence number, **When** the engineer toggles Mutually Exclusive and selects 30 and 40, **Then** 30/40 are stored as a mutually-exclusive set and 20 remains parallel to the active member of that set.
4. **Given** an operation, **When** the engineer toggles Optional, **Then** the operation is recorded as skippable during execution.
5. **Given** an operation, **When** the engineer assigns it an OSP-type labour resource (and optionally a supplier from the Settings Supplier list), **Then** it is classified OSP (subcontracted); the OSP resource code triggers the ERP requisition and the optional supplier is persisted for confirmation/event emission at execution start (MES-15).

---

### User Story 5 - Group operations and apply type/flow control at the group level (Priority: P2)

The engineer can group related operations under a manually-assigned group sequence number (following the same 10/20/30 convention), so that blocks of operations can be managed and resequenced together. A group itself can be classified with the **same types as an operation — Normal, Optional, Parallel and Mutually Exclusive — applied at the group sequence level**, giving the same flow-control behaviour but to whole groups rather than single operations:

- **Normal group**: a basic group with a unique group sequence number.
- **Optional group**: the whole group may be skipped during execution when the work instruction directs.
- **Parallel group**: two or more groups sharing one group sequence number, executable in any order, but all groups in that group sequence must complete before the next group sequence.
- **Mutually Exclusive group**: an extension of parallel groups (the groups must form a parallel group set to be eligible) — the first group clocked-on becomes active and excludes the other groups in the set from needing completion.

**Why this priority**: Grouping plus group-level flow control lets large routes express conditional/concurrent/alternative *blocks* of operations (not just single operations); it aids authoring and revision but is not required for a minimal executable route.

**Independent Test**: Assign a group sequence number to several operations, classify the group, and confirm the group type and its sequencing constraints are validated and persisted, and that the group resequences as a unit during a route revision.

**Acceptance Scenarios**:

1. **Given** several operations, **When** the engineer assigns them a common group sequence number, **Then** they are returned as a group ordered by group sequence then operation sequence.
2. **Given** a grouped route under revision, **When** the engineer resequences a group, **Then** the contained operations move together.
3. **Given** a group, **When** the engineer toggles Optional, **Then** the whole group is recorded as skippable during execution.
4. **Given** two groups sharing one group sequence number, **When** the route is saved, **Then** they are reported as a Parallel group set automatically (derived from the shared group sequence number).
5. **Given** groups that do not share a group sequence number, **When** the engineer toggles Mutually Exclusive on one, **Then** the system rejects it because the group mutually-exclusive toggle is only available within a parallel group set.
6. **Given** three groups sharing one group sequence number, **When** the engineer toggles Mutually Exclusive and selects two of them, **Then** those two form a mutually-exclusive group set and the third remains parallel to the active member.

---

### User Story 6 - Break operations into operation steps (Priority: P2)

For complex operations the engineer defines operation steps — an ordered breakdown the operator follows. Each step has an operation step number and may itself be Normal, Optional, Parallel or Mutually Exclusive, governed by an operation step sequence number, using the same mechanics as route operations: Normal/Parallel derived from the step sequence number, Optional/Mutually Exclusive as explicit toggles (with the mutually-exclusive subset selected from the parallel step set), and a visible type indicator on each step.

**Why this priority**: Steps add execution granularity valued on the shop floor but are optional refinement of an operation.

**Independent Test**: Add steps to an operation with mixed step types and confirm they persist with step numbers and the same sequencing semantics as operations.

**Acceptance Scenarios**:

1. **Given** an operation, **When** the engineer adds steps 1, 2, 3, **Then** they persist ordered by step sequence number.
2. **Given** two steps sharing a step sequence number, **When** marked Parallel, **Then** they form a parallel step set under the operation.

---

### User Story 7 - Route approval with significant-process approvers (Priority: P2)

A draft route is submitted for approval and approved via e-signature before it can be consumed by Work Orders. When the route contains one or more significant (special) processes, the approval workflow requires additional subject-matter-expert approvers beyond the standard approver(s).

**Why this priority**: Approval is the gate that makes a route usable downstream and is an AS9100D control-of-production requirement; significant-process gating is a key aerospace safeguard. It depends on the route content (P1) being in place.

**Independent Test**: Submit and approve a route with no significant processes (standard approver path); submit a route containing a significant process and confirm the additional SME approver(s) are required before it can be approved.

**Acceptance Scenarios**:

1. **Given** a draft route with no significant processes, **When** it is submitted and the standard approver e-signs, **Then** the route becomes APPROVED and is consumable downstream.
2. **Given** a draft route containing a significant-process operation, **When** it is submitted, **Then** approval cannot complete until the required additional SME approver(s) have e-signed.
3. **Given** an approved route, **When** an attempt is made to edit its operations directly, **Then** the edit is rejected until a new revision is created (US8).

---

### User Story 8 - Two-tier revision strategy (route + operation) with audit history (Priority: P2)

Routes are revised in two ways. A **route revision** is the main revision: it controls the quantity, sequence and grouping of operations — during a route revision the engineer may add, duplicate, resequence, group and delete operations or groups. An **operation revision** tracks changes to the content of a single operation (not its sequence or grouping) and is approved only at the operation level without re-approving the whole route. For audit, a user can view a main route approval together with the subsequent operation revisions made against it, until the next main route approval — after which subsequent operation revisions are tied to the new route revision.

**Why this priority**: Controlled revision and audit history are mandatory for production control and traceability, but they layer onto an already-approvable route.

**Independent Test**: Create a route revision and confirm structural edits are allowed and require route-level approval; create an operation revision and confirm only its content changes, it is approved at operation level, and the audit view groups operation revisions under their governing route revision.

**Acceptance Scenarios**:

1. **Given** an approved route, **When** the engineer starts a route revision, **Then** a new draft route revision is created allowing add/duplicate/resequence/group/delete of operations, and requires route-level approval.
2. **Given** an approved route, **When** the engineer starts an operation revision on one operation, **Then** only that operation's content may change, sequence/grouping are locked, and approval is required only at the operation level.
3. **Given** a route with a main approval followed by operation revisions, **When** a user opens the audit history, **Then** the operation revisions are shown grouped under the governing route revision, and re-grouped under the next route revision once it is approved.

---

### User Story 9 - Labour plan export for external scheduling (Priority: P3)

Labour plans (Setup/Run/Inspection/Transport times by labour plan type and basis) defined on operations can be exported/uploaded to external schedulers or ERPs to inform execution planning and scheduling. Execution does not consume labour plans directly; they are a planning input, and execution labour records may later be compared against plan.

**Why this priority**: Export is an integration convenience that depends on labour plans (P1) already existing and is not required for routing itself.

**Independent Test**: Export the labour plan for an approved route and confirm the output contains each operation's activity-type times (Setup/Run/Inspection/Transport) with basis and Labour Plan Type in a consumable format.

**Acceptance Scenarios**:

1. **Given** an approved route with labour plans, **When** the user exports the labour plan, **Then** the export lists per-operation Setup/Run/Inspection/Transport times with per-item/per-lot basis and labour plan type.

---

### User Story 10 - Manage routing reference data in a Settings submodule (Priority: P2)

An administrator manages routing reference data through a new **Settings submodule**, covering: **work centres / machines** (the resources operations are executed against), **labour codes**, **labour plan types** (seeded Machine / People / OSP, user-extensible), and **route types** (seeded with Standard as a protected default; alternates such as NPI, FAI, Process Improvement are user-added). Operations and route headers reference these maintained entities rather than free text. This Settings submodule is the interim master for this data until the full equipment-master epic (DEF-004) is delivered.

**Why this priority**: Route authoring (US1–US3) needs work centres, labour codes and at least the seeded Standard route type to exist; the seeds make the MVP workable out of the box, while the maintenance UI is the enhancement that lets administrators extend the reference data without code changes.

**Independent Test**: In the Settings submodule, create a work centre, a labour code and an alternate route type, confirm each becomes selectable when authoring an operation/route header, and confirm seeded protected entries (Standard route type) cannot be deleted and in-use entries cannot be deleted.

**Acceptance Scenarios**:

1. **Given** the routing Settings submodule, **When** an administrator creates a work centre/machine, **Then** it becomes selectable as an operation's eligible resource (US3).
2. **Given** the routing Settings submodule, **When** an administrator adds a labour code or a labour plan type, **Then** it becomes selectable on an operation's labour plan.
3. **Given** the Route Types list, **When** an administrator views it, **Then** Standard is present as the default entry and cannot be deleted; adding an alternate type (e.g. NPI) makes it selectable on a route header.
4. **Given** any reference entity in use by at least one route/operation, **When** an administrator attempts to delete it, **Then** the system prevents deletion (or deactivates it) so existing routes remain valid.
5. **Given** all reference data, **When** accessed, **Then** it is organisation-scoped with no cross-organisation visibility.

---

### User Story 11 - Author operations and steps in both a tabular and a visual graphical view (Priority: P2)

The route creation/edit screen for adding operations, steps and grouping offers two interchangeable views over the same route: a **tabular/grid view** (the default) and a **visual graphical, Visio-style view**. Both views support the full set of authoring actions — add, edit, delete and group operations and steps — and both reflect operation/group/step types and flow control (Normal/Optional/Parallel/Mutually-Exclusive, OSP, significant process). The graphical view renders sequence, parallelism, mutual exclusivity, grouping and OSP as a connected diagram; the grid view presents the same as sortable rows. A change made in one view is reflected in the other (they operate on the same underlying route model).

**Why this priority**: The tabular/grid view is the default and is sufficient to author a route (it underpins US2/US3/US5/US6), so it is effectively part of the P1 authoring slice; the visual graphical view is a higher-effort alternative representation that improves comprehension of complex flows but is not required for a route to be authored. Capturing the dual-view requirement now ensures the design keeps both editors over a single, consistent model.

**Independent Test**: Author a route with grouped, parallel and mutually-exclusive operations in the grid view, switch to the graphical view and confirm the same structure renders correctly; then add/edit/delete/group operations and steps in the graphical view and confirm the grid view and persisted model reflect the changes (and vice versa).

**Acceptance Scenarios**:

1. **Given** the route edit screen, **When** it opens, **Then** the tabular/grid view is shown by default with a control to switch to the visual graphical view.
2. **Given** a route with parallel, mutually-exclusive, grouped and OSP operations, **When** the user switches to the graphical view, **Then** the diagram renders sequence, parallel branches, mutually-exclusive sets, groups and OSP steps consistent with the grid.
3. **Given** the graphical view, **When** the user adds, edits, deletes or groups an operation or step, **Then** the change is persisted and reflected in the tabular view (and vice versa) over the same underlying model.
4. **Given** unsaved edits in one view, **When** the user switches views, **Then** the edits are carried over consistently (no divergence between the two representations).

---

### Edge Cases

- **Stale downstream links**: the linked BOM revision or inspection-plan revision is superseded after the route is approved — the route MUST continue to reference the revision it was approved against (never silently float), and the system MUST surface a non-blocking "newer upstream revision available" indicator (driven by an upstream supersede-event consumer) so the engineer can choose to start a route revision. No automatic block or re-approval is forced.
- **Second Standard route**: creating, or re-typing an alternate route to, Standard when a Standard route already exists for that part/revision must be rejected — but creating a new route *revision* of the existing Standard route (same logical route) must be allowed.
- **Route type in use deleted**: deleting a Route Type from Settings while routes reference it must be prevented or replaced by deactivation; the seeded Standard type can never be deleted.
- **Operation type misuse**: marking a single operation Parallel (no shared sequence) or marking a non-parallel operation Mutually Exclusive must be rejected. The same constraints apply at the group level — a Mutually Exclusive group requires a parallel group set.
- **Nested flow control**: an Optional/Parallel/Mutually-Exclusive operation inside a group that itself carries a type — the interaction of operation-level and group-level flow control must be defined (e.g. a mutually-exclusive group containing parallel operations) so execution (MES-15) has unambiguous rules.
- **Skill/quality references**: a required skill or quality variable references a labour skill / inspection characteristic that is later deactivated — the route must surface the broken reference rather than fail silently.
- **Significant process added during operation revision**: flagging an operation as a significant process during an operation-level revision must escalate the approval requirements appropriately.
- **Empty parallel/mutually-exclusive set on execution**: a mutually-exclusive set where no operation is clocked-on must not allow the sequence to be considered complete (a constraint surfaced to MES-15 execution).
- **OSP without an OSP labour resource**: an operation classified OSP that has no OSP-type labour resource cannot trigger the downstream ERP requisition — the system should warn/block, since sourcing has nothing to act on. The supplier is optional at routing (it may be left for confirmation at execution start).
- **Deleting an operation referenced by an in-progress work order**: routing must prevent structural deletion that would orphan downstream execution data (coordination with MES-14/15).
- **View divergence**: an edit made in the graphical view that is not reflected in the grid view (or vice versa), or unsaved edits lost when switching views, must not occur — both views are bound to one model and must stay consistent.
- **Unrepresentable layout in graphical view**: a complex flow (deeply nested groups, large parallel sets) that is awkward to render visually must still be fully editable in the grid view and must not block authoring.

## Requirements *(mandatory)*

### Functional Requirements

**Route header**

- **FR-001**: System MUST allow creating a route header linked to a part number and revision, a BOM and its revision, an owning organisation, a linked inspection-plan revision, and a reason for revision (including the initial revision).
- **FR-002**: System MUST scope every route to the caller's organisation and prevent cross-organisation visibility or modification.
- **FR-003**: System MUST require a reason for revision on every route revision, including revision 1.
- **FR-004**: System MUST preserve the specific BOM revision and inspection-plan revision a route was approved against, independent of later revisions of those records.
- **FR-004f**: System MUST detect when a route's pinned BOM/inspection-plan revision has been superseded upstream (via a Kafka supersede-event consumer, idempotent per §VIII) and surface a non-blocking "newer upstream revision available" indicator on the route; it MUST NOT auto-update the pinned revision or block consumption.
- **FR-004a**: System MUST assign every route a route type, defaulting to Standard, and MUST allow a given part number/revision to have multiple routes of differing types.
- **FR-004b**: System MUST enforce at most one route of type Standard per part number/revision, while allowing any number of alternate-type routes for the same part/revision.
- **FR-004c**: System MUST provide an administrator-configurable Route Types list in Settings, seeded with Standard as a protected default that cannot be deleted, to which administrators can add alternate types (e.g. NPI, FAI, Process Improvement) that become selectable when authoring a route header.
- **FR-004d**: System MUST prevent deletion (or require deactivation instead) of a route type that is referenced by an existing route, so existing routes remain valid.
- **FR-004e**: System MUST provide a routing Settings submodule to create and maintain routing reference data — work centres/machines, labour codes, labour plan types (seeded Machine/People/OSP, user-extensible), route types, **significant-process types (each with a required approver role, FR-024)**, and **suppliers (interim list for OSP supplier selection, FR-009a)** — all organisation-scoped, with seeded protected entries and in-use entries protected from deletion (deactivate instead). This is the interim master pending the equipment-master (DEF-004) and supplier/OSP-procurement (DEF-002) epics.

**Route operations**

- **FR-005**: Users MUST be able to add, edit, duplicate, resequence and delete route operations while the route (or route revision) is in draft.
- **FR-006**: System MUST assign each operation an operation number and a sequence number and MUST reject duplicate operation numbers within a route.
- **FR-007**: System MUST support operation types Normal, Optional, Parallel, Mutually Exclusive and Outside Processing (OSP), where Normal and Parallel are **derived dynamically from the sequence number** and Optional, Mutually Exclusive and OSP are **explicit toggles** on the operation.
- **FR-008**: System MUST derive Parallel automatically for operations that share one sequence number (no explicit parallel toggle), derive Normal for a unique sequence number, and MUST require all operations in a sequence to be eligible-complete before the next sequence (semantics consumed by execution, MES-15).
- **FR-009**: System MUST only permit the Mutually Exclusive toggle within a parallel sequence (shared sequence number). When toggled on, the system MUST prompt the user to select which operations in that parallel sequence are mutually exclusive, storing the selected subset as a mutually-exclusive set; operations in the sequence that are not selected MUST remain parallel to the active member of that set.
- **FR-009a**: System MUST treat Optional as an independent per-operation toggle that does not depend on the sequence number. An operation is classified **OSP** by assigning it an OSP-type labour resource (OSP labour plan type, FR-013) — the OSP resource code that triggers the downstream ERP requisition. The OSP operation MAY also carry an **optional supplier** (from the Settings-maintained Supplier list, FR-004e); supplier and resource code are decoupled so a single OSP resource code can serve many suppliers. Routing persists the resource code and optional supplier; the supplier is confirmed/selected at OSP-operation start during execution (MES-15), which emits the event used to raise the PO (FR-009c).
- **FR-009c**: System MUST persist enough on an OSP operation (OSP resource code + optional supplier) for execution (MES-15) to emit a Kafka `osp.operation.started` event (resource code + selected supplier) that a middleware tool ties to the ERP requisition to create the PO. The PO creation, supplier sourcing and the execution-time event itself are out of scope for routing (DEF-002).
- **FR-009b**: System MUST display a clear type indicator for every group, operation and step showing Normal / Optional / Parallel / Mutually Exclusive; Normal and Parallel are shown as derived from the sequence number, while Optional and Mutually Exclusive reflect the explicit toggle state (and, for Mutually Exclusive, membership of the selected subset).
- **FR-010**: System MUST allow an operation to be flagged as a significant (special) process by assigning it a significant-process **type** (from the Settings-maintained list, FR-004e); the type carries the required approver role used at approval (FR-024).
- **FR-011**: System MUST allow grouping operations under a manually-assigned group sequence number and resequencing groups as a unit during a route revision.
- **FR-011a**: System MUST support group types Normal, Optional, Parallel and Mutually Exclusive at the group sequence level, applying the same derived-vs-toggle mechanics as operations: Normal/Parallel derived from the group sequence number, Optional/Mutually Exclusive as explicit toggles.
- **FR-011b**: System MUST derive a Parallel group set from groups sharing one group sequence number, MUST require all groups in a group sequence to be eligible-complete before the next group sequence (semantics consumed by execution, MES-15), and MUST only permit the Mutually Exclusive group toggle within a parallel group set — prompting selection of which groups in that set are mutually exclusive, with non-selected groups remaining parallel to the active member.

**Operation resources, standards & consumption**

- **FR-012**: System MUST allow assigning one or more eligible machines/workstations (from the Settings-maintained work-centre list, FR-004e) to an operation and marking the operation clocking or non-clocking.
- **FR-013**: System MUST allow a labour code (from the Settings-maintained labour-code list) per labour plan type (default plan types Machine, People, OSP; plan types are user-extensible via FR-004e).
- **FR-013a**: System MUST allow classifying an operation's **Labour Type** as **Direct** or **Indirect** (an operation attribute). Direct labour MUST be charged to the operation's assigned labour code (FR-013); Indirect labour MUST be charged to a central cost centre rather than the labour code. The classification is carried as routing metadata; the actual cost posting is performed by downstream costing/execution.
- **FR-014**: System MUST allow standard times per labour activity type — Setup, Run, Inspection, Transport (fixed, not user-extensible) — each with a per-item or per-lot basis. (These four are the *Labour Activity Types*, distinct from the extensible *Labour Plan Types* Machine/People/OSP in FR-013.)
- **FR-015**: System MUST allow specifying which BOM material lines are consumed at an operation and whether consumption is mandatory.
- **FR-016**: System MUST allow associating quality variables (from the linked inspection-plan revision) to be collected at an operation.
- **FR-017**: System MUST allow specifying gage/tooling consumed at an operation.
- **FR-018**: System MUST allow attaching a machine STEP-file reference to an operation (reference only; transmission to machines is out of scope — see Deferred).
- **FR-019**: System MUST allow specifying the skills required to execute an operation, referencing existing labour skills (MES-11).
- **FR-020**: System MUST allow linking the work instruction to be followed at an operation (MES-10).

**Operation steps**

- **FR-021**: Users MUST be able to break an operation into ordered operation steps, each with an operation step number.
- **FR-022**: System MUST support step types Normal, Optional, Parallel and Mutually Exclusive governed by an operation step sequence number, applying the same derived-vs-toggle mechanics and sequencing semantics as route operations (Normal/Parallel derived from the step sequence number; Optional/Mutually Exclusive as explicit toggles, with the mutually-exclusive subset selected from the parallel step set).

**Authoring views**

- **FR-022a**: System MUST provide two interchangeable views of the route operations/steps/grouping editor — a tabular/grid view (default) and a visual graphical (Visio-style) view — with a control to switch between them.
- **FR-022b**: Both views MUST support the full authoring action set: add, edit, delete and group operations and steps.
- **FR-022c**: Both views MUST operate on the same underlying route model so that a change in one view is consistently reflected in the other and in the persisted route (no divergence between representations).
- **FR-022d**: The graphical view MUST render sequence, parallel branches, mutually-exclusive sets, groups, OSP and significant-process operations consistently with their type indicators (FR-009b).

**Route locking & concurrency**

- **FR-031**: System MUST require a user to hold an exclusive **edit lock** on a route before making any edit to it or its draft. Acquiring the lock records the lock-holder (user + timestamp); a route can be locked by at most one user at a time.
- **FR-032**: While a route is locked, the system MUST permit edits only by the lock-holder and MUST present the route as **read-only** to all other users. The lock MUST be released when the holder explicitly unlocks, or automatically when the route is approved/published. The route header MUST surface the lock state and the lock-holder.
- **FR-033**: System MUST allow a user with a dedicated **force-unlock privilege** (`routing:route:unlock`) to release a lock held by another user (e.g. the holder is unavailable), recording who force-unlocked and when in the audit trail (§V). Users without this privilege MUST NOT be able to take or break another user's lock.

**Approval**

- **FR-023**: System MUST require a draft route to be submitted and approved via e-signature before it can be consumed by Work Orders.
- **FR-024**: System MUST require additional subject-matter-expert approver(s) when a route contains one or more significant-process operations. The required approver(s) are resolved by mapping: the system gathers the **distinct significant-process types** present on the route and requires an e-signature from a holder of each type's mapped approver role (in addition to the standard route approval). Each distinct mapped approver role must be satisfied exactly once regardless of how many operations share that type.
- **FR-025**: System MUST prevent direct edits to an approved route; structural or content changes MUST go through a route revision or operation revision.

**Revision & audit**

- **FR-026**: System MUST support a route revision that governs the quantity, sequence and grouping of operations and requires route-level approval.
- **FR-027**: System MUST support an operation revision that changes only the content of a single operation (not its sequence or grouping) and requires approval only at the operation level.
- **FR-028**: System MUST provide an audit history that shows each main route approval together with the operation revisions made against it, regrouping subsequent operation revisions under the next route revision once it is approved.
- **FR-029**: System MUST record full audit trail (who/when/what, with e-signature meaning) for every approval and revision action.

**Labour planning export**

- **FR-030**: System MUST be able to export an approved route's labour plan (per-operation Labour Activity Type times — Setup/Run/Inspection/Transport — with basis and Labour Plan Type) in a format consumable by external schedulers/ERPs. Execution MUST NOT depend on labour plans being exported.

**Custom properties**

- **FR-034**: System MUST surface, on the route/operation Properties tab, any **custom properties** scoped to the route's route type (and applicable part type), allowing the engineer to record their values. Custom properties are **distinct from global UDFs**: a UDF appears on every screen, whereas a custom property is defined against a specific scope (route type, part type, or variables within a specific inspection plan). The scoped custom-property **definition engine** (defining properties and their scopes, and surfacing them across route type / part type / inspection-plan-variable consumers) is a **wider-product capability tracked as a separate epic** (see "Custom Properties" epic); MES-9 consumes it on the routing Properties tab and MUST degrade gracefully (show none) when no custom properties are scoped to the route.

### Key Entities *(include if feature involves data)*

- **Route (header)**: the routing record for a part; references part/revision, route type, BOM/revision, inspection-plan revision, organisation; carries revision number, status (draft/pending/approved), reason for revision. Constrained to at most one Standard-type route per part/revision.
- **Route Type**: an administrator-configurable classification of a route (Settings list); Standard is the seeded, protected default; alternates (e.g. NPI, FAI, Process Improvement) are user-added. Only Standard is subject to the one-per-part/revision constraint.
- **Work Centre / Machine**: a Settings-maintained resource (code, name, org-scoped) operations are executed against; interim master pending the equipment-master epic (DEF-004).
- **Labour Code**: a Settings-maintained code referenced by an operation's labour plan.
- **Labour Plan Type**: a Settings-maintained, user-extensible category (seeded Machine/People/OSP) associated with labour codes and labour plan lines.
- **Significant Process Type**: a Settings-maintained type (e.g. brazing, fusion welding, EB welding) with a **required approver role**; referenced by an operation's significant-process flag and used to resolve the additional approvers at route approval (FR-024).
- **Supplier**: a Settings-maintained interim list (code, name, org-scoped) used to select the optional supplier on an OSP operation; interim master pending the supplier/OSP-procurement epic (DEF-002).
- **Route Operation**: a logical manufacturing step; operation number, sequence number, description, type indicator (Normal and Parallel derived from the sequence number; Optional, Mutually Exclusive and OSP as explicit toggles), mutually-exclusive-set membership, significant-process type (when significant), OSP optional supplier reference (when OSP), clocking flag, group sequence number, operation revision number/status.
- **Operation Group**: a manually-sequenced grouping of operations within a route; carries a group sequence number and a group type (Normal/Optional/Parallel/Mutually-Exclusive) governing flow control at the group level.
- **Operation Step**: an ordered breakdown of an operation; step number, step sequence number, step type (derived Normal/Parallel + Optional/Mutually-Exclusive toggles).
- **Mutually Exclusive Set**: a named subset of members within a parallel sequence (at operation, group or step level) flagged mutually exclusive; the active (first clocked-on) member excludes the other members of the set from needing completion, while parallel members outside the set are unaffected. Carries the Optional and Mutually-Exclusive toggle state and indicator that distinguish it from the derived Normal/Parallel type.
- **Operation Resource Assignment**: eligible machine(s)/workstation(s) for an operation.
- **Labour Plan Line**: a standard time on an operation; Labour Activity Type (Setup/Run/Inspection/Transport — fixed), Labour Plan Type (Machine/People/OSP/… — extensible), basis (per-item/per-lot), time value, optional labour code.
- **Material Consumption**: link from an operation to a BOM line consumed there, with a mandatory flag.
- **Quality Variable Requirement**: link from an operation to inspection-plan characteristic(s) collected there.
- **Tooling/Gage Requirement**: gage or tooling consumed at an operation.
- **Skill Requirement**: link from an operation to a required labour skill.
- **Work Instruction Link**: the work instruction followed at an operation.
- **STEP File Reference**: a machine program reference attached to an operation.
- **Route Revision / Operation Revision**: revision records governing structural vs content changes, each with their own approval state.
- **Approval Record**: e-signature approval entry (actor, role, timestamp, meaning) for a route or operation revision, including additional significant-process approvers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A process engineer can author a complete, approvable route (header, ≥3 operations, resources/standards on each) for a typical part in under 30 minutes.
- **SC-002**: 100% of approved routes preserve the exact BOM revision and inspection-plan revision they were approved against, verifiable in the audit history.
- **SC-003**: A route containing a significant process cannot reach APPROVED without the additional SME approver(s) — 0% bypass in test.
- **SC-004**: All five operation types (Normal, Optional, Parallel, Mutually Exclusive, OSP) and their sequencing constraints are enforced, with invalid combinations rejected 100% of the time.
- **SC-005**: The audit history correctly attributes every operation revision to its governing route revision across at least two successive route approvals.
- **SC-006**: An approved route is resolvable by part identity and route type, and ready for consumption by Work Orders (MES-14) — demonstrated by a downstream retrieval returning the approved Standard route and its operations.
- **SC-007**: A part/revision can hold exactly one Standard route plus one or more alternate-type routes; a second Standard route is rejected 100% of the time, and an administrator can add a new alternate route type in Settings and use it on a route without code changes.
- **SC-008**: A route authored in the tabular/grid view renders identically (same operations, groups, sequence, parallel/mutually-exclusive/OSP flow) in the visual graphical view, and edits made in either view round-trip to the same persisted model with no divergence.

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §8.5.1 Control of production — routes define controlled production operations, special/significant-process approval, required skills/qualification per operation, and approved work instructions. §8.5.2 Identification & traceability — route/operation revision audit history. |
| AS9102 (FAI) | Partial | Routing defines where inspection (quality variables from the inspection-plan revision) occurs in the process; FAI execution/reporting itself is in the Quality module (MES-12), not routing. |
| AS9131 (NCM) | No | Non-conformance management is handled in the quality/execution modules, not in route authoring. |
| NIST SP 800-171 / CMMC | Partial | Routing may reference controlled technical data (e.g. machine STEP-file references, process detail) — access control and org isolation apply; storage/transmission of CUI artefacts beyond references is out of scope here. |
| 21 CFR Part 11 / Annex 11 | No | Aerospace & defence MES; no FDA-regulated data in manufacturing routing. E-signature approval still follows the platform's audited e-signature mechanism. |
| ISA-95 | Yes | Part 2 Operations Definition — routes are operations definitions; integrates with product definition (Item/BOM) and links to scheduling (labour plan export to ERP/scheduler). |
| ISA-88 | Yes | Procedural model — operations and operation steps mirror the ISA-88 procedural hierarchy (procedure → operation → step) with sequencing/branching control. |

---

## Assumptions

- Manufacturing routing is implemented as a **new dedicated microservice, `routing-service`** (ISA-95 Operations Definition domain), distinct from both the Work Instructions service (`engineering-service`) and Shop Floor Tracking (`shopfloor-service`, MES-15). This requires adding `routing-service` to the constitution's service table (owner-approved during planning).
- E-signature approval reuses the platform's existing Keycloak-backed e-signature/approval mechanism delivered for Work Instructions (MES-10) rather than introducing a new one.
- Item Master/BOM (MES-8), Work Instructions (MES-10), Labour Resources & Skills (MES-11) and Quality Inspection Planning (MES-12) are all available and approved upstream; routing references their approved revisions and does not duplicate their data.
- Routes are authored against **approved** part/BOM/inspection-plan revisions; behaviour when referencing non-approved upstream revisions defaults to rejection unless clarified otherwise.
- Org isolation, audit (created/modified by/at), and the column-picker/UDF conventions follow the established platform patterns used by the other modules.
- Execution semantics of operation types (how Parallel/Mutually-Exclusive/Optional behave at clock-on) are **defined** here as routing metadata; their runtime enforcement is delivered by Shop Floor Tracking (MES-15).
- No equipment/work-centre master service exists yet. A **new Settings submodule** (owned by routing) will provide create/maintain capability for routing reference data — work centres/machines, labour codes, labour plan types and route types — as the interim master until the full equipment-master epic (DEF-004) is implemented. Operations reference these maintained entities (not free text).
- Work Orders (MES-14) default to the Standard route for a part/revision and may explicitly select an alternate-type route (e.g. NPI, FAI) where applicable; the selection logic itself lives in the Work Orders module, while routing only provides the typed routes.
- The Route Types Settings list follows the established platform pattern for administrator-configurable reference lists (the same approach used for other configurable lists in Settings).
- The tabular/grid and visual graphical authoring views are two front-end representations of one route model (no separate data model per view); the graphical view's diagramming approach (library/component) is a design-phase decision.
- **Graphical-view library — preference**: a **native Angular** flow/diagram library is preferred for the graphical view, so it integrates cleanly with Angular 21, PrimeNG and the project's change-detection rules. React Flow (https://reactflow.dev/, MIT licence) is a **research reference only** for the node/edge interaction model and data shape — it is a React library and will not be used directly. The plan should evaluate Angular-native candidates that mirror React Flow's model (e.g. `@foblex/flow`, `ngx-graph`) on licence (MIT/permissive preferred), maintenance/activity, bundle size and feature fit; React-in-Angular interop is a fallback only if no suitable Angular-native option exists.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Transmission of machine STEP files to physical machines (DNC/machine push) | Routing stores the reference only; actual machine delivery is a shop-floor execution concern | Operators must fetch programs manually; no closed-loop program control | P3 (MES-15) | |
| DEF-002 | OSP procurement integration & supplier master: the execution-start `osp.operation.started` Kafka event (MES-15), the middleware that ties resource code + supplier to the ERP requisition to raise the PO, and a full supplier master (routing uses an interim Settings Supplier list) | Routing only classifies the operation as OSP (resource code + optional supplier) and persists the data; the event emission, PO creation and supplier master live in execution/procurement/OSP domains | OSP steps cannot be sourced/PO'd end-to-end from within the MES; supplier data stays minimal | P3 (osp-service / MES-15 / ERP middleware) | |
| DEF-003 | Bi-directional sync of labour plans with external schedulers/ERP (import of actuals, plan-vs-actual analytics) | This version provides one-way export only | No automated plan-vs-actual comparison inside the MES | Post-GA | |
| DEF-004 | Full equipment / work-centre master (capacity, calendars, rates, OEE) as its own ISA-95 Equipment domain service | Routing provides an interim Settings submodule (work centres/machines, labour codes, labour plan types) sufficient to author routes; the full equipment master is a separate domain | Limited scheduling fidelity; no capacity/calendar modelling until delivered | P3 | |
| DEF-005 | Route templating / copy-from-similar-part authoring accelerators | Focus first on first-principles authoring and revision control | Slower authoring for families of similar parts | Post-GA | |
