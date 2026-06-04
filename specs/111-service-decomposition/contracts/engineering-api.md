# engineering-service REST Contract

Base URL: `http://engineering-service:8097` (internal) / `http://gateway:8082` (external via `/api/v1/ecos/**`)

All endpoints require `Authorization: Bearer <JWT>`.

## ECO

Identical to current `work-order-service` ECO API — zero URL or contract changes.

| Method | Path | Privilege | Description |
|---|---|---|---|
| GET | `/api/v1/ecos` | `item-master:eco:manage` | Paginated ECO list (filter by `?status=`, `?orgId=`) |
| POST | `/api/v1/ecos` | `item-master:eco:manage` | Create ECO |
| GET | `/api/v1/ecos/{id}` | `item-master:eco:manage` | Get ECO detail |
| POST | `/api/v1/ecos/{id}/approve` | `item-master:eco:manage` | Approve ECO |

## Kafka Consumer (inbound)

| Topic | Group ID | Handler | Description |
|---|---|---|---|
| `bom.released` | `engineering-service` | `BomReleasedEventHandler` | When `ecoId` is present, adds `bomId` to ECO's `outputBomIds` |

## Kafka Producer (outbound)

| Topic | Trigger | Payload |
|---|---|---|
| `eco.approved` | `POST /api/v1/ecos/{id}/approve` | `{ ecoId, orgId, ecoNumber, approvedBy, approvedAt }` |
