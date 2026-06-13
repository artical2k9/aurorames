# API Contract: Quality Inspection Planning (MES-12)

Base path (gateway): `/api/v1/inspection-plans/**` → quality-service:8099. Bearer JWT; org scoping from `org_id` claim. Privileges `quality:inspection-plan:<action>` via manifest (auto-grant SYSTEM_ADMIN — ERR-MES-075).

## Plans & revisions

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/inspection-plans` | create | Body: itemId, name, description?, customFields? → validates item via inventory-service; 409 if plan exists for item; 201 revision 0 DRAFT |
| GET | `/api/v1/inspection-plans` | read | Paged list; search by part number/name; status filter; display-revision rows |
| GET | `/api/v1/inspection-plans/{id}` | read | `?revisionNumber=N` / `?revisionStatus=…` optional; default display revision (APPROVED > PENDING > DRAFT, rev DESC tiebreak) |
| PATCH | `/api/v1/inspection-plans/{id}` | update | Header fields; on APPROVED auto-creates draft N+1 with full characteristic copy |
| DELETE | `/api/v1/inspection-plans/{id}` | delete | 409 if any revision ever APPROVED |
| GET | `/api/v1/inspection-plans/{id}/revisions` | read | Revision history |
| POST | `/api/v1/inspection-plans/{id}/submit` | update | 422 if zero characteristics or invalid expressions |
| POST | `/api/v1/inspection-plans/{id}/approve` | approve | Standard audited approval (no Part 11 e-sign — see spec compliance table) |
| POST | `/api/v1/inspection-plans/{id}/reject` | approve | Body: `{ "reason": "…" }` |
| POST | `/api/v1/inspection-plans/{id}/revisions` | update | Explicit Create Revision; 409 if draft exists |
| DELETE | `/api/v1/inspection-plans/{id}/draft` | update | Cancel draft |

## Characteristics (DRAFT revision only; 409 otherwise)

| Method | Path | Privilege | Notes |
|---|---|---|---|
| GET | `/api/v1/inspection-plans/{id}/characteristics?revisionNumber=N` | read | Ordered by characteristic_number |
| POST | `/api/v1/inspection-plans/{id}/characteristics` | update | Type-specific validation (field matrix in data-model.md); expression validated on save |
| PATCH | `/api/v1/inspection-plans/{id}/characteristics/{charId}` | update | Re-validates expressions incl. dependents |
| DELETE | `/api/v1/inspection-plans/{id}/characteristics/{charId}` | update | 409 naming dependents if referenced by CALCULATED expressions |

Characteristic payload (union):
```json
{
  "characteristicNumber": 10,
  "name": "Bore diameter",
  "source": "DESIGN",
  "characteristicType": "SPECIFIC",
  "inspectionMethod": "CMM",
  "gaugeType": "CMM-PROBE-2MM",
  "unitOfMeasure": "mm",
  "sampleSizeRule": "ALL",
  "recordingBasis": "PER_PIECE",
  "nominalValue": 25.4, "lowerLimit": 25.38, "upperLimit": 25.42,
  "expectedBoolean": null,
  "expression": null,
  "customFields": {}
}
```
CALCULATED example expression: `"(C10 + C20) / 2"`; historian tag: `"#{furnace1.temp} - C10"`.

## Consumer contract (MES-9 / Work Order release)

| Method | Path | Privilege | Notes |
|---|---|---|---|
| GET | `/api/v1/inspection-plans/by-item/{itemId}/approved` | read | 200 latest approved revision + full characteristics; 404 `NO_APPROVED_PLAN` |
| GET | `/api/v1/inspection-plans/by-item/{itemId}/status` | read | `{ "exists": bool, "approved": bool, "latestApprovedRevision": int\|null }` — cheap release gate |

## Events (Kafka)

`quality.inspection-plan.approved` — `{ orgId, planId, itemId, partNumber, revision, approvedBy, approvedAt }`.

## Error model

GlobalExceptionHandler standard shapes (404/409/422/400); no inline controller catches (ERR-MES-073). Expression validation errors return 422 with `details` listing each bad reference/cycle.
