# API Contract: Labour Resources & Skills (MES-11)

Base path (gateway): `/api/v1/labour/**` → labour-service:8098. Bearer JWT; org from `org_id` claim. Privileges `labour:<entity>:<action>` registered via manifest (auto-grant SYSTEM_ADMIN, ERR-MES-075).

## Employees

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/labour/employees` | employee:create | 409 duplicate employee_number or iam_user_id |
| GET | `/api/v1/labour/employees` | employee:read | paged; search (number/name/email), status filter |
| GET | `/api/v1/labour/employees/{id}` | employee:read | |
| GET | `/api/v1/labour/employees/by-iam-user/{iamUserId}` | employee:read | 404 if unlinked |
| PATCH | `/api/v1/labour/employees/{id}` | employee:update | incl. status ACTIVE↔INACTIVE, iam link set/clear |
| GET | `/api/v1/labour/employees/{id}/profile` | employee:read | competency profile: certifications + derived state + training summary |
| GET | `/api/v1/labour/employees/{id}/training` | training:read | attendance history with event details |

## Skills

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/labour/skills` | skill:create | 409 duplicate skill_code |
| GET | `/api/v1/labour/skills` | skill:read | paged; `?ids=…` bulk fetch; `?active=true` filter — stable consumer contract |
| GET | `/api/v1/labour/skills/{id}` | skill:read | minimal stable DTO: id, code, name, active, certificationRequired |
| PATCH | `/api/v1/labour/skills/{id}` | skill:update | deactivate blocks NEW certifications only |

## Certifications

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/labour/certifications` | certification:create | body: employeeId, skillId, awardDate, expiryDate? (default award+validity), assessor?, evidenceRef?; 409 duplicate (emp, skill, awardDate); 422 inactive skill or inactive employee |
| GET | `/api/v1/labour/certifications` | certification:read | paged; filters: employeeId, skillId, state, `expiringWithinDays=N` (dashboard) |
| GET | `/api/v1/labour/certifications/{id}` | certification:read | includes derived state + supporting training records (via skill link) |
| POST | `/api/v1/labour/certifications/{id}/revoke` | certification:revoke | body: `{ "reason": "…" }` mandatory; immutable revocation metadata |

## Training

| Method | Path | Privilege | Notes |
|---|---|---|---|
| POST | `/api/v1/labour/training-events` | training:create | body: title, date, duration?, trainer?, attendees: [{employeeId, outcome}], skillIds? |
| GET | `/api/v1/labour/training-events` | training:read | paged |
| GET | `/api/v1/labour/training-events/{id}` | training:read | |
| PATCH | `/api/v1/labour/training-events/{id}` | training:update | event fields + attendance outcomes (audited) |

## Qualification evaluation (consumer contract — MES-10 / MES-9)

`POST /api/v1/labour/qualifications/evaluate` — privilege `labour:qualification:read`

Request:
```json
{ "employeeId": "uuid-or-null", "iamUserId": "kc-sub-or-null", "skillIds": ["uuid", "..."] }
```
Exactly one of employeeId/iamUserId required. Empty skillIds → empty results (200).

Response:
```json
{
  "employeeId": "uuid",
  "employeeActive": true,
  "results": [
    { "skillId": "uuid", "skillCode": "IPC-610", "status": "HELD_ACTIVE", "expiryDate": "2027-01-31" }
  ]
}
```
Status ∈ HELD_ACTIVE | EXPIRING_SOON | EXPIRED | REVOKED | NOT_HELD | SKILL_INACTIVE. 404 when employee not found/unlinked. Read-only, idempotent.

## Error model

GlobalExceptionHandler standard shapes; no inline catches (ERR-MES-073).
