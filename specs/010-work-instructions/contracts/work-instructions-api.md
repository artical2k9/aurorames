# API Contract: Work Instructions (MES-10)

Base path (gateway): `/api/v1/work-instructions` → engineering-service. All endpoints require Bearer JWT; org scoping from `org_id` claim. Privileges follow `engineering:work-instruction:<action>`.

## Instructions & revisions

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/work-instructions` | create | Body: identifier?, title, description?, partContext?, customFields? → 201, revision 0 DRAFT |
| GET | `/api/v1/work-instructions` | read | Paged list: search, status filter; returns display-revision summary rows |
| GET | `/api/v1/work-instructions/identifier-suggestion` | create | → `{ "identifier": "WI-0007" }` |
| GET | `/api/v1/work-instructions/{id}` | read | `?revisionNumber=N` or `?revisionStatus=DRAFT` optional; default display revision (APPROVED > PENDING > DRAFT, rev DESC tiebreak) |
| PATCH | `/api/v1/work-instructions/{id}` | update | Header fields; on APPROVED auto-creates draft rev N+1 with full copy (steps/media/skills) |
| DELETE | `/api/v1/work-instructions/{id}` | delete | 409 if any revision ever APPROVED; soft delete |
| GET | `/api/v1/work-instructions/{id}/revisions` | read | Revision history list (revision, status, actor/timestamps, signature summary) |
| POST | `/api/v1/work-instructions/{id}/submit` | update | DRAFT → PENDING_APPROVAL |
| POST | `/api/v1/work-instructions/{id}/approve` | approve | Body: `{ "password": "…", "meaning": "APPROVED" }` — verifies via KC direct grant; 200 + signature record, or 401-equivalent 422 `SIGNATURE_VERIFICATION_FAILED` (status unchanged) |
| POST | `/api/v1/work-instructions/{id}/reject` | approve | Body: `{ "reason": "…" }` → back to DRAFT |
| POST | `/api/v1/work-instructions/{id}/revisions` | update | Explicit Create Revision from latest APPROVED → new DRAFT; 409 if draft exists |
| DELETE | `/api/v1/work-instructions/{id}/draft` | update | Cancel draft |

## Steps (operate on the DRAFT revision only; 409 otherwise)

| Method | Path | Privilege |
|---|---|---|
| GET | `/api/v1/work-instructions/{id}/steps?revisionNumber=N` | read |
| POST | `/api/v1/work-instructions/{id}/steps` | update |
| PATCH | `/api/v1/work-instructions/{id}/steps/{stepId}` | update |
| DELETE | `/api/v1/work-instructions/{id}/steps/{stepId}` | update |
| POST | `/api/v1/work-instructions/{id}/steps/reorder` | update — body: ordered stepId list |

## Media

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/work-instructions/{id}/steps/{stepId}/media` | update | multipart/form-data (file, caption?); 422 on type/size violation |
| GET | `/api/v1/work-instructions/media/{attachmentId}` | read | Streams binary; Content-Disposition |
| PATCH | `/api/v1/work-instructions/{id}/steps/{stepId}/media/{attachmentId}` | update | caption/order |
| DELETE | `/api/v1/work-instructions/{id}/steps/{stepId}/media/{attachmentId}` | update | metadata row; binary GC'd when unreferenced |

## Skill requirements & qualification

| Method | Path | Privilege | Notes |
|---|---|---|---|
| GET | `/api/v1/work-instructions/{id}/skill-requirements?revisionNumber=N` | read | |
| POST | `/api/v1/work-instructions/{id}/skill-requirements` | update | Body: `{ "skillId": uuid }` — code/name denormalised from labour-service at write |
| DELETE | `/api/v1/work-instructions/{id}/skill-requirements/{reqId}` | update | |
| GET | `/api/v1/work-instructions/{id}/qualification?employeeId=…&revisionNumber=N` | read | → `{ "qualified": bool, "missing": [ {skillId, skillCode, state} ], "reason": "OK"\|"VERIFICATION_UNAVAILABLE" }`; fail-closed |

## Events (Kafka)

`engineering.work-instruction.approved` — payload: orgId, workInstructionId, identifier, revision, approvedBy, approvedAt, signatureId. JsonSerializer (ERR-MES-063).

## Error model

GlobalExceptionHandler shapes (consistent with eco/inventory): 404 NotFound, 409 Conflict, 422 Validation, 400 binding. No inline controller catches (ERR-MES-073).
