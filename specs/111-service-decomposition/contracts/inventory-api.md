# inventory-service REST Contract

Base URL: `http://inventory-service:8096` (internal) / `http://gateway:8082` (external via `/api/v1/item-master/**` and `/api/v1/boms/**`)

All endpoints require `Authorization: Bearer <JWT>` with a valid Keycloak-issued token. Privilege checks via `@RequiresPrivilege`.

## Item Master

Identical to current `work-order-service` Item Master API — zero URL or contract changes.

| Method | Path | Privilege | Description |
|---|---|---|---|
| GET | `/api/v1/item-master` | `item-master:records:view` | Paginated list with search/filter |
| POST | `/api/v1/item-master` | `item-master:records:manage` | Create item |
| GET | `/api/v1/item-master/{id}` | `item-master:records:view` | Get by ID |
| PATCH | `/api/v1/item-master/{id}` | `item-master:records:manage` | Patch item fields |
| POST | `/api/v1/item-master/{id}/obsolete` | `item-master:records:manage` | Mark obsolete |
| POST | `/api/v1/item-master/{id}/clone` | `item-master:records:manage` | Clone item |

## BOM

Identical to current `work-order-service` BOM API — zero URL or contract changes.

| Method | Path | Privilege | Description |
|---|---|---|---|
| GET | `/api/v1/boms` | `item-master:bom:manage` | List BOMs for item (`?parentItemId=`) |
| POST | `/api/v1/boms` | `item-master:bom:manage` | Create BOM |
| GET | `/api/v1/boms/{bomId}` | `item-master:bom:manage` | Get BOM header |
| PATCH | `/api/v1/boms/{bomId}` | `item-master:bom:manage` | Patch BOM header |
| POST | `/api/v1/boms/{bomId}/release` | `item-master:bom:manage` | Release BOM (publishes `bom.released` event) |
| GET | `/api/v1/boms/{bomId}/lines` | `item-master:bom:manage` | List BOM lines |
| POST | `/api/v1/boms/{bomId}/lines` | `item-master:bom:manage` | Add BOM line |
| PATCH | `/api/v1/boms/{bomId}/lines/{lineId}` | `item-master:bom:manage` | Update BOM line |
| DELETE | `/api/v1/boms/{bomId}/lines/{lineId}` | `item-master:bom:manage` | Delete BOM line |
| GET | `/api/v1/boms/{bomId}/explosion` | `item-master:bom:manage` | BOM explosion |
| GET | `/api/v1/boms/{bomId}/explosion/download` | `item-master:records:view` | Download CSV/PDF |

## UDF

| Method | Path | Privilege | Description |
|---|---|---|---|
| GET | `/api/v1/udf/fields` | `item-master:udf:manage` | List UDF field definitions (`?module=`) |
| POST | `/api/v1/udf/fields` | `item-master:udf:manage` | Create UDF field |
| DELETE | `/api/v1/udf/fields/{id}` | `item-master:udf:manage` | Delete UDF field |
