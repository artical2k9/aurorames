# Phase 0 Research: Manufacturing Routing

All decisions below resolve the NEEDS CLARIFICATION / design choices for the plan.

## R1 — Service boundary (where routing lives)

- **Decision**: New dedicated `routing-service` (ISA-95 Operations Definition domain), port 8100, schema `routing`.
- **Rationale**: Owner decision during planning. Routing is the *definition* of how a part is made; Shop Floor Tracking (`shopfloor-service`, MES-15) is *execution*; Work Instructions (`engineering-service`) is a different engineering artefact. §XI mandates one ISA-95 domain per service; bundling routing into either would recreate the catch-all coupling that motivated the MES-111 decomposition.
- **Alternatives considered**: `shopfloor-service` (epic's original wording — rejected: conflates definition with execution); `engineering-service` (rejected: epic explicitly requires separation from Work Instructions).
- **Impact**: Requires a constitution amendment adding `routing-service` as the 19th domain service (MINOR bump). Drafted for owner ratification with this plan.

## R2 — Work-centre / reference-data master

- **Decision**: A routing-owned **Settings submodule** provides CRUD for work centres/machines, labour codes, labour plan types (seeded Machine/People/OSP, extensible) and route types (seeded protected Standard). All org-scoped, in-use/seeded entries protected from deletion (deactivate instead).
- **Rationale**: No equipment master exists; operations need validated resource references (not free text) for downstream scheduling integrity. Owner directed an interim Settings submodule until the full equipment-master epic (DEF-004).
- **Alternatives considered**: free-text codes (rejected — no integrity); build full equipment master now (rejected — separate domain, out of scope).

## R3 — E-signature approval

- **Decision**: Reuse the Keycloak e-signature approval mechanism already implemented in `engineering-service` for Work Instructions (MES-10) — same `mes-signature-verify` KC client and client pattern.
- **Rationale**: Constitution §IV/§VII forbid bespoke auth; MES-10 already delivered a vetted e-sign verify client. Significant-process additional approvers extend the same flow with extra required approver roles.
- **Alternatives considered**: new bespoke approval module (rejected — duplicate, violates §VII).

## R4 — Graphical-view library (Angular-native)

- **Decision**: Use a **native Angular** flow/diagram library; leading candidate **`@foblex/flow`** (MIT, Angular-native, supports custom nodes/edges, connections, drag, grouping). React Flow is a **research reference only** (React — not used directly, per owner).
- **Rationale**: Clean integration with Angular 21 / Signals / PrimeNG and the change-detection rules (ERR-MES-059); avoids React-in-Angular interop overhead. `@foblex/flow` mirrors React Flow's node/edge model that informed the UX.
- **Validation needed at PR 7**: confirm licence (MIT), maintenance/activity, bundle-size budget impact, and OnPush/`detectChanges` compatibility. Fallback: `ngx-graph` (D3-based) or, last resort, React-in-Angular interop.
- **Alternatives considered**: React Flow direct (rejected — React); hand-rolled SVG/canvas editor (rejected — high effort, reinvents pan/zoom/connect).

## R5 — Upstream references (Item/BOM/Inspection/Skill/Work-Instruction)

- **Decision**: Reference upstream entities by their stable IDs/revision IDs via the owning services' read APIs (inventory `item-master`/`boms`, quality `inspection-plans`, labour `skills`, engineering `work-instructions`). Persist the specific revision a route is approved against. No cross-schema queries (§XI, Database Isolation).
- **Rationale**: §XI prohibits shared-schema access; routes must pin the approved revision (spec FR-004).
- **Kafka**: consume supersede/approval events where available to flag stale references; emit `routing.route.approved` for MES-14/15. Consumers idempotent (§VIII).
- **Alternatives considered**: direct DB joins (rejected — §XI); copying upstream data (rejected — drift).

## R6 — Operation/group/step classification model

- **Decision**: Standard and Parallel are **derived** from the (operation/group/step) sequence number; Optional, Mutually Exclusive and OSP are **explicit boolean/structured toggles**. Mutual exclusivity is modelled as a named subset (`mutually_exclusive_set`) within a parallel sequence; non-member parallel peers remain parallel to the active member.
- **Rationale**: Matches the owner's stated mechanics; keeps "type" computable from data rather than a redundant stored enum that can drift from the sequence number.
- **Alternatives considered**: a single stored `type` enum on every row (rejected — can diverge from sequence membership; harder to keep consistent across the two editors).

## R7 — Two-tier revisioning

- **Decision**: A **route revision** governs operation quantity/sequence/grouping and is approved at route level. An **operation revision** changes only one operation's content, approved at operation level, without re-approving the route. Audit history groups operation revisions under the governing route revision (re-anchored when the next route revision is approved). Implement via Hibernate Envers for field-level history plus explicit revision-pointer columns to model the two-tier governance relationship.
- **Rationale**: Envers gives immutable audit (§V) but not the route-vs-operation governance semantics — those are explicit columns/relationships.
- **Alternatives considered**: Envers alone (rejected — can't express the two-tier approval grouping); fully custom versioning (rejected — duplicates Envers).

## R8 — Frontend dual-view consistency

- **Decision**: Both editors bind to one in-memory route model (Angular Signals); the graph editor is a presentation/edit surface over the same signal state the grid uses. Saving persists the single model; switching views never serialises to a second representation.
- **Rationale**: Spec FR-022c / SC-008 require no divergence. Single source of truth avoids sync bugs.
- **Alternatives considered**: separate models per view with a sync layer (rejected — divergence risk, the exact failure mode the spec calls out).
