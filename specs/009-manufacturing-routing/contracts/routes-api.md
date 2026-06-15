# Contract: Routes API (`routing-service`)

Base: `/api/v1/routes` (gateway predicate `Path=/api/v1/routes/**` → `routing-service:8100`). All requests require a Keycloak bearer token; all responses org-scoped via `org_id` claim. JSON. Errors via the shared `GlobalExceptionHandler` shape. Privileges shown per endpoint.

## Routes (header)

| Method | Path | Privilege | Purpose |
|---|---|---|---|
| POST | `/api/v1/routes` | `routing:route:manage` | Create a draft route header (partId, partRevision, routeTypeId, bomId, bomRevisionId, inspectionPlanRevisionId, reasonForRevision). 409 if a Standard route already exists for the part/revision (FR-004b). |
| GET | `/api/v1/routes` | `routing:route:view` | List/search routes (filter by partNumber, routeTypeId, status; paged). |
| GET | `/api/v1/routes/{id}` | `routing:route:view` | Get a route (optional `?revision=`). |
| PATCH | `/api/v1/routes/{id}` | `routing:route:manage` | Edit draft header fields (rejected if APPROVED — start a revision instead, FR-025). |
| DELETE | `/api/v1/routes/{id}/draft` | `routing:route:manage` | Cancel a draft route/revision. |
| GET | `/api/v1/routes/{id}/revisions` | `routing:route:view` | Route revision history. |
| POST | `/api/v1/routes/{id}/revisions` | `routing:route:manage` | Start a new route revision (structural: add/duplicate/resequence/group/delete). |

## Operations / groups / steps

| Method | Path | Privilege | Purpose |
|---|---|---|---|
| GET | `/api/v1/routes/{id}/operations` | `routing:route:view` | List operations (ordered by sequence; includes derived Standard/Parallel type, ME-set membership, group). |
| POST | `/api/v1/routes/{id}/operations` | `routing:route:manage` | Add operation (operationNumber unique; optional/osp/significantProcess toggles). |
| PATCH | `/api/v1/routes/{id}/operations/{opId}` | `routing:route:manage` | Edit operation; resequence/renumber only within a route revision (FR-026). |
| DELETE | `/api/v1/routes/{id}/operations/{opId}` | `routing:route:manage` | Delete operation (draft/route-revision only; blocked if referenced by in-progress WO — edge case). |
| POST | `/api/v1/routes/{id}/operations/{opId}/revision` | `routing:route:manage` | Start an operation-level content revision (FR-027). |
| PUT | `/api/v1/routes/{id}/groups` | `routing:route:manage` | Create/update operation group (groupSequenceNumber, optional toggle). |
| GET/POST/PATCH/DELETE | `/api/v1/routes/{id}/operations/{opId}/steps` | `routing:route:view`/`manage` | CRUD operation steps. |
| PUT | `/api/v1/routes/{id}/mutually-exclusive-sets` | `routing:route:manage` | Define/replace a mutually-exclusive subset for a parallel sequence (level=OPERATION/GROUP/STEP, sequenceNumber, memberIds). 422 if not a parallel set or members ⊄ parallel set (FR-009). |

## Operation detail (US3)

`POST`/`PATCH`/`DELETE` under `/api/v1/routes/{id}/operations/{opId}/...`, privilege `routing:route:manage`:
`resources` (workCentreId), `labour-plan` (labourActivityType ∈ {Setup,Run,Inspection,Transport}, labourPlanTypeId → Machine/People/OSP, labourCodeId, timeValue, basis), `materials` (bomLineId, mandatory), `quality-variables` (inspectionCharacteristicId), `tooling` (gageOrToolRef), `skills` (skillId), `work-instruction` (workInstructionId), `step-file` (reference). All referenced IDs validated against the owning service / Settings master.

Operation-level fields set via `PATCH .../operations/{opId}`: `optional` toggle; `significantProcessTypeId` (→ SignificantProcessType, required when flagged significant); OSP is set by adding an OSP-type `labour-plan` line (the OSP resource code) plus an optional `supplierId` (→ Supplier). The operations list/get DTO returns the **derived type** (Normal/Parallel) and ME-set membership for the type indicator (FR-009b). An OSP operation with no OSP labour resource is rejected (422).

## Approval (US7) & export (US9)

| Method | Path | Privilege | Purpose |
|---|---|---|---|
| POST | `/api/v1/routes/{id}/submit` | `routing:route:manage` | DRAFT → PENDING_APPROVAL. |
| POST | `/api/v1/routes/{id}/approve` | `routing:route:approve` | Approve (e-signature). Resolution: gather the distinct SignificantProcessTypes on the route; each type's `requiredApproverRole` must be satisfied once by an e-signed approval from a holder of that role, in addition to the standard approval. Blocked until all are satisfied (FR-024). → APPROVED; emits `routing.route.approved`. |
| POST | `/api/v1/routes/{id}/reject` | `routing:route:approve` | Reject with reason → REJECTED. |
| POST | `/api/v1/routes/{id}/operations/{opId}/approve` | `routing:operation:approve` | Approve an operation revision (operation-level only, FR-027). |
| GET | `/api/v1/routes/{id}/labour-plan/export` | `routing:route:view` | Export labour plan (per-operation Setup/Run/Inspection/Transport × basis × labour plan type) — schema-defined JSON/CSV (FR-030). |

**Events**: emits `routing.route.approved` (routeId, partId, routeTypeId, revision, org). Consumes upstream `*.revision.superseded` events to set `bomRevisionSuperseded`/`inspectionPlanRevisionSuperseded` on affected routes — a non-blocking "newer upstream revision available" indicator; never auto-updates the pinned revision (FR-004f, idempotent §VIII). The OSP `osp.operation.started` event (resource code + supplier → middleware → PO) is emitted at execution **start by MES-15**, not by routing (FR-009c, DEF-002).

**Route header GET** returns `bomRevisionSuperseded`/`inspectionPlanRevisionSuperseded` flags so the UI can show the stale-reference indicator.
