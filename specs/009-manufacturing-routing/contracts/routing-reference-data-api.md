# Contract: Routing Reference-Data API (Settings submodule)

Base: `/api/v1/routing` (gateway predicate `Path=/api/v1/routing/**` → `routing-service:8100`). Keycloak bearer; org-scoped. Privilege `routing:settings:manage` for writes, `routing:settings:view` for reads (route authoring reads also accept `routing:route:view`). All lists are org-scoped; seeded/in-use entries cannot be deleted (deactivate instead) — FR-004c/d/e.

## Work centres / machines

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/routing/work-centres` | List (filter `active`, search by code/name). |
| POST | `/api/v1/routing/work-centres` | Create (code unique per org). |
| PATCH | `/api/v1/routing/work-centres/{id}` | Update / activate-deactivate. |
| DELETE | `/api/v1/routing/work-centres/{id}` | Delete — 409 if referenced by any operation (deactivate instead). |

## Labour codes

| Method | Path | Purpose |
|---|---|---|
| GET / POST / PATCH / DELETE | `/api/v1/routing/labour-codes[/{id}]` | CRUD; optional `labourPlanTypeId`; delete blocked if in use. |

## Labour plan types (seeded Machine/People/OSP, extensible)

| Method | Path | Purpose |
|---|---|---|
| GET / POST / PATCH / DELETE | `/api/v1/routing/labour-plan-types[/{id}]` | CRUD; seeded rows protected; delete blocked if in use. |

## Route types (seeded protected Standard)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/routing/route-types` | List selectable types for route authoring. |
| POST | `/api/v1/routing/route-types` | Add alternate (e.g. NPI, FAI, Process Improvement). |
| PATCH | `/api/v1/routing/route-types/{id}` | Update / deactivate. `isStandard` row is protected. |
| DELETE | `/api/v1/routing/route-types/{id}` | 409 for seeded Standard or any in-use type (FR-004c/d). |

## Significant-process types (each with a required approver role — FR-024)

| Method | Path | Purpose |
|---|---|---|
| GET / POST / PATCH / DELETE | `/api/v1/routing/significant-process-types[/{id}]` | CRUD (code, name, `requiredApproverRole`); delete blocked if in use. Referenced by an operation's significant-process flag; drives the additional approver resolution. |

## Suppliers (interim OSP supplier list — FR-009a, DEF-002)

| Method | Path | Purpose |
|---|---|---|
| GET / POST / PATCH / DELETE | `/api/v1/routing/suppliers[/{id}]` | CRUD (code, name); delete blocked if referenced by an OSP operation. Interim list pending the supplier/OSP-procurement epic. |

**Privileges registered on startup** (`routing:route:view|manage|approve`, `routing:operation:approve`, `routing:settings:view|manage`) and auto-granted to `SYSTEM_ADMIN` via `registerManifest()` (ERR-MES-075). The frontend Settings submodule consumes these endpoints; the GridPreference/UDF + column-picker pattern applies to the routing list screens (ERR-MES-078).
