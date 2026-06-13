# Research: Quality Inspection Planning (MES-12)

## R1 — Calculated-characteristic expression engine

**Decision**: Hand-rolled recursive-descent parser over a restricted grammar:

```
expr    := term (('+'|'-') term)*
term    := factor (('*'|'/') factor)*
factor  := NUMBER | REF | TAG | '(' expr ')'
REF     := 'C' characteristicNumber     e.g. C10, C20
TAG     := '#{' tagName '}'             e.g. #{furnace1.temp}   (format-validated only, v1)
```

Validation at save and submit: every REF resolves to a characteristic in the same revision; dependency graph is acyclic (DFS cycle detection); REFs may only target SPECIFIC or CALCULATED characteristics (a boolean COMMON value has no numeric meaning). Implemented as a `final` utility (`ExpressionValidator`) with exhaustive unit tests (ERR-MES-070).

**Rationale**: Tiny, auditable, zero injection surface. SpEL/MVEL/JS engines allow arbitrary code execution — unacceptable on a quality-record path (Constitution §VII). Evaluation (computing values) is an execution-epic concern; this epic only validates structure.

**Alternatives considered**: SpEL with restricted context — still a large attack/behaviour surface, rejected; exp4j library — fine for evaluation but we still need custom reference/cycle validation, and a 30-line parser avoids a dependency.

## R2 — Revision lifecycle implementation pattern

**Decision**: Copy the MES-114 BOM pattern: `InspectionPlan` root + `InspectionPlanRevision` + child `InspectionCharacteristic` rows; one-draft invariant (partial unique index); auto-draft on PATCH of approved header with full characteristic copy (including customFields — MES-114 BOM-line lesson); display revision APPROVED > PENDING_APPROVAL > DRAFT with `revision DESC` tiebreaker (ERR-MES-082).

**Rationale**: Third reuse of the proven pattern; uniform UX and test shapes across Item Master, BOM, and Inspection Plans.

**Alternatives considered**: none seriously — divergence here would be gratuitous inconsistency.

## R3 — Item master reference across service boundary

**Decision**: Store `item_id` (UUID) + denormalised `part_number` (display). On plan creation, validate the item exists via inventory-service REST (`GET /api/v1/item-master/{id}`) with the caller's forwarded JWT; reject creation when missing. No FK across schemas, no cross-schema queries (§XI). The consumer API includes the item's current state flag fetched live when requested with `?includeItemState=true`.

**Rationale**: Inventory-service is the item source of truth; REST validation at write keeps referential integrity practical without shared schema access.

**Alternatives considered**: Kafka-cached item replica in quality schema — more moving parts than needed for a write-time existence check; deferred until volume demands it.

## R4 — Consumer contract for MES-9 / work-order release

**Decision**: Two read endpoints: `GET /api/v1/inspection-plans/by-item/{itemId}/approved` → 200 with latest approved revision + characteristics, or 404 `NO_APPROVED_PLAN`; and `GET /api/v1/inspection-plans/by-item/{itemId}/status` → `{ "exists": bool, "approved": bool, "latestApprovedRevision": int|null }` for cheap release gating. Plus Kafka `quality.inspection-plan.approved` event (orgId, planId, itemId, revision, approvedBy/At) for reactive consumers.

**Rationale**: MES-9 route creation needs full characteristics (allocation to operations); work-order release only needs a boolean — separating them keeps the gating call sub-10 ms.

**Alternatives considered**: single endpoint with projection params — two trivially simple endpoints beat one parameterised one for contract stability.

## R5 — Sample size representation

**Decision**: `sample_size_rule` enum: `ALL` or `FIXED_COUNT` + nullable `sample_size_count` (required and ≥1 when FIXED_COUNT). Statistical schemes (ANSI Z1.4) deferred (spec DEF-001) — enum extension point reserved.

**Rationale**: Matches spec scope; enum + count covers the two v1 cases without modelling sampling tables.

## R6 — Port and naming

**Decision**: quality-service on port 8099 (next free after labour-service 8098 from MES-11). Gateway predicate `/api/v1/inspection-plans/**`. If MES-11 and MES-12 merge in either order, ports do not collide; both PR 1s touch compose/gateway/sonar files — expect a trivial merge conflict to resolve in whichever merges second (noted for implementation).
