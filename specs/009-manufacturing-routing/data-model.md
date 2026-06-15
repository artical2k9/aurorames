# Phase 1 Data Model: Manufacturing Routing

Schema: `routing`. All entities carry `id` (UUID), `organisation_id` (UUID, NOT NULL, org isolation §IX), and audit columns `created_by`/`created_at`/`modified_by`/`modified_at` (NOT NULL — seed with `'migration'`/`NOW()` in Flyway INSERTs, ERR-MES-077; Spring Data Auditing + `AuditorAware` at runtime, ERR-MES-062). All mutable entities are `@Audited` (Hibernate Envers, §V). `custom_fields` JSONB where the platform UDF pattern applies.

## Reference data (Settings submodule — interim master, R2/DEF-004)

### WorkCentre
Resource an operation is executed against.
- `code` (string, unique per org), `name`, `description?`, `active` (bool)
- Validation: code required, unique within org; cannot delete if referenced (deactivate).

### LabourCode
- `code` (unique per org), `name`, `labourPlanTypeId` (FK → LabourPlanType, optional), `active`

### LabourPlanType
User-extensible category (seeded Machine, People, OSP).
- `code`, `name`, `seeded` (bool, protected), `active`

### RouteType
Seeded protected `Standard`; alternates user-added (NPI, FAI, Process Improvement…).
- `code`, `name`, `isStandard` (bool — only the Standard row), `seeded` (bool, protected), `active`
- Validation: exactly one `isStandard=true` seeded row; cannot delete seeded/in-use (deactivate).

### SignificantProcessType
A special-process classification (e.g. brazing, fusion welding, EB welding) with the approver role required to approve a route containing it.
- `code`, `name`, `requiredApproverRole` (string — Keycloak role/privilege), `active`
- Used by `RouteOperation.significantProcessTypeId` and the approval resolution (FR-024).

### Supplier
Interim OSP supplier list (pending the supplier/OSP-procurement epic, DEF-002).
- `code` (unique per org), `name`, `active`
- Referenced by `RouteOperation.supplierId` (optional) for OSP operations (FR-009a).

## Route aggregate

### Route (header)
The routing record for a part identity, of a given type.
- `partId` (UUID, ref item-master), `partRevision` (string, informational), `routeTypeId` (FK → RouteType)
- `bomId` (UUID), `bomRevisionId` (UUID — pinned), `inspectionPlanRevisionId` (UUID — pinned)
- `revision` (int), `status` (DRAFT | PENDING_APPROVAL | APPROVED | REJECTED)
- `reasonForRevision` (string, NOT NULL — incl. initial)
- `bomRevisionSuperseded` (bool, default false), `inspectionPlanRevisionSuperseded` (bool, default false) — set by the upstream supersede-event consumer; drive the non-blocking "newer upstream revision available" indicator (FR-004f). Never auto-update the pinned revision.
- `hasDraft` (derived), `customFields` (JSONB)
- **Constraints**: at most one Route with `routeType.isStandard=true` per (`organisation_id`,`partId`,`partRevision`) in non-superseded state (FR-004b). Multiple alternate-type routes allowed. Pinned BOM/inspection revisions immutable after approval (FR-004).
- ISA-95: Operations Definition / Process Segment for a product.

### RouteOperation
A logical manufacturing step.
- `routeId` (FK → Route), `operationNumber` (int, unique within route), `sequenceNumber` (int), `description`
- Type model (R6): `optional` (bool toggle), `osp` (bool toggle), `significantProcessTypeId?` (FK → SignificantProcessType — set when significant)
- OSP fields (when `osp=true`): OSP resource code comes from the operation's OSP-type `LabourPlanLine` (FR-009a); `supplierId?` (FK → Supplier, optional intended supplier). No supplier/PO stored beyond this (FR-009c; DEF-002).
- `groupId?` (FK → OperationGroup), `clocking` (bool)
- `operationRevision` (int), `operationStatus` (DRAFT | PENDING_APPROVAL | APPROVED), `governingRouteRevision` (int — R7)
- **Derived (not stored as enum)**: Normal if its `sequenceNumber` is unique in the route; Parallel if shared with ≥1 sibling.
- **Constraints**: unique `operationNumber` per route (FR-006); an OSP operation must have an OSP-type labour resource (edge case); `significantProcessTypeId` required when flagged significant.

### OperationGroup
A manually-sequenced grouping of operations, itself typed.
- `routeId` (FK), `groupSequenceNumber` (int), `name?`
- Type model: `optional` (bool), with Normal/Parallel derived from `groupSequenceNumber` uniqueness (FR-011a/b)
- **Constraints**: groups sharing a `groupSequenceNumber` form a parallel group set.

### OperationStep
Breakdown of a complex operation.
- `operationId` (FK → RouteOperation), `stepNumber` (int), `stepSequenceNumber` (int), `description`
- Type model: `optional` (bool); Normal/Parallel derived from `stepSequenceNumber` (FR-022)

### MutuallyExclusiveSet
A named subset of members flagged mutually exclusive within a parallel sequence — at operation, group, or step level (R6, FR-009/FR-011b).
- `routeId` (FK), `level` (OPERATION | GROUP | STEP), `sequenceNumber` (the shared parallel sequence)
- `memberIds` (set of FK to the level's rows — the selected subset)
- **Semantics**: active (first clocked-on) member excludes other *members* from needing completion; parallel peers not in `memberIds` are unaffected. Membership is a strict subset of the parallel set.
- **Constraints**: only valid when the referenced sequence has ≥2 members (parallel); `memberIds` ⊆ that parallel set.

## Operation detail (resources / standards / consumption — US3)

### OperationResource
Eligible work centre(s)/machine(s) for an operation.
- `operationId` (FK), `workCentreId` (FK → WorkCentre)

### LabourPlanLine
A standard time on an operation.
- `operationId` (FK), `labourActivityType` (Setup | Run | Inspection | Transport — fixed, FR-014), `labourPlanTypeId?` (FK → LabourPlanType for Machine/People/OSP — an OSP plan type here classifies the operation as OSP, FR-009a), `labourCodeId?` (FK → LabourCode)
- `timeValue` (decimal), `basis` (PER_ITEM | PER_LOT)
- ISA-95: resource/standard for the process segment.

### MaterialConsumption
- `operationId` (FK), `bomLineId` (UUID, ref BOM line), `mandatory` (bool)

### QualityVariableRequirement
- `operationId` (FK), `inspectionCharacteristicId` (UUID, ref inspection-plan characteristic)

### ToolingRequirement
- `operationId` (FK), `gageOrToolRef` (string/ref), `description?`

### SkillRequirement
- `operationId` (FK), `skillId` (UUID, ref labour skill)

### WorkInstructionLink
- `operationId` (FK), `workInstructionId` (UUID, ref engineering work-instruction)

### StepFileReference
- `operationId` (FK), `reference` (string — machine program reference only; transmission deferred DEF-001)

## Approval & revision (US7/US8)

### ApprovalRecord
E-signature approval entry (reuses engineering-service KC e-sign verify pattern).
- `subjectType` (ROUTE_REVISION | OPERATION_REVISION), `subjectId` (FK), `revision` (int)
- `actor` (KC subject), `actorRole`, `meaning` (string), `timestamp` (UTC, server-recorded)
- `isSignificantProcessApprover` (bool — distinguishes the additional SME approvals, FR-024), `significantProcessTypeId?` (the type whose required approver role this approval satisfies)
- **Significant-process resolution (FR-024)**: gather the distinct `SignificantProcessType` of the route's operations → each type's `requiredApproverRole` must be satisfied exactly once by an `ApprovalRecord` from a holder of that role, in addition to the standard route approval.
- Immutable (§V).

### Revision governance (R7)
- Route revision: increments `Route.revision`; route-level `ApprovalRecord`.
- Operation revision: increments `RouteOperation.operationRevision`; operation-level `ApprovalRecord`; `governingRouteRevision` pins the operation revision to the route revision in force, re-anchored when the next route revision is approved.
- Field-level history for all `@Audited` entities via Envers `_aud` tables (own Flyway migrations, ERR-MES-061).

## Relationships (summary)

```
RouteType 1───* Route *───1 (partId, bomRevisionId, inspectionPlanRevisionId pinned)
Route 1───* OperationGroup 1───* RouteOperation 1───* OperationStep
RouteOperation 1───* {OperationResource, LabourPlanLine, MaterialConsumption,
                      QualityVariableRequirement, ToolingRequirement,
                      SkillRequirement, WorkInstructionLink, StepFileReference}
Route 1───* MutuallyExclusiveSet (level=OPERATION|GROUP|STEP)
Route/Operation 1───* ApprovalRecord
WorkCentre, LabourCode, LabourPlanType  ←referenced by  OperationResource / LabourPlanLine
SignificantProcessType  ←referenced by  RouteOperation (+ ApprovalRecord resolution)
Supplier  ←referenced by  RouteOperation (optional, OSP)
```

## State transitions

Route / operation revision: `DRAFT → PENDING_APPROVAL → APPROVED` (or `→ REJECTED → DRAFT`). Approved routes are immutable; further change requires a new route revision (structural) or operation revision (content) per R7. A significant-process route cannot reach `APPROVED` until both standard and the additional SME `ApprovalRecord`(s) exist (FR-024).
