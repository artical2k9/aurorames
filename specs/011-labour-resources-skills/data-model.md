# Data Model: Labour Resources & Skills (MES-11)

Schema: `labour` (new, owned by labour-service). Every table has a matching `_aud` table with `revend`/`revend_tstmp` (ValidityAuditStrategy). Audit columns NOT NULL; AuditorAware bean mandatory (ERR-MES-062). All INSERTs in seed migrations include `created_by='migration'` etc. (ERR-MES-077).

## employee

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| org_id | uuid | NOT NULL |
| employee_number | varchar(40) | NOT NULL, UNIQUE (org_id, employee_number) |
| first_name / last_name | varchar(100) | NOT NULL |
| email | varchar(255) | |
| employment_status | varchar(20) | NOT NULL — ACTIVE / INACTIVE |
| hire_date | date | |
| iam_user_id | varchar(255) | nullable; partial UNIQUE (org_id, iam_user_id) WHERE iam_user_id IS NOT NULL |
| custom_fields | jsonb | UDF (@Type(JsonBinaryType)) |
| created_by/at, modified_by/at | audit | NOT NULL |

## skill

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| org_id | uuid | NOT NULL |
| skill_code | varchar(50) | NOT NULL, UNIQUE (org_id, skill_code) |
| name | varchar(200) | NOT NULL |
| description | text | |
| category | varchar(100) | |
| certification_required | boolean | NOT NULL default true |
| validity_months | integer | nullable = never expires |
| active | boolean | NOT NULL default true |
| custom_fields | jsonb | |
| created_by/at, modified_by/at | audit | NOT NULL |

## certification

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| org_id | uuid | NOT NULL |
| employee_id | uuid | FK → employee, NOT NULL |
| skill_id | uuid | FK → skill, NOT NULL |
| award_date | date | NOT NULL; UNIQUE (employee_id, skill_id, award_date) |
| expiry_date | date | nullable = never expires (skill with null validity) |
| assessor | varchar(200) | |
| evidence_ref | varchar(500) | free text / URL |
| revoked | boolean | NOT NULL default false |
| revoked_by / revoked_at / revocation_reason | varchar/timestamptz/varchar(500) | reason mandatory when revoked |
| custom_fields | jsonb | |
| created_by/at, modified_by/at | audit | NOT NULL |

State (derived, never stored): REVOKED if revoked; else EXPIRED if expiry_date < today; else EXPIRING_SOON if expiry_date ≤ today + warningWindow; else ACTIVE. Governing certification per (employee, skill) = max expiry_date among non-revoked, nulls (never-expires) ranked highest; tiebreak award_date DESC.

Index: `(employee_id, skill_id, expiry_date DESC)`, `(org_id, expiry_date)` for the expiry dashboard window query.

## training_event

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| org_id | uuid | NOT NULL |
| title | varchar(255) | NOT NULL |
| training_date | date | NOT NULL |
| duration_minutes | integer | |
| trainer | varchar(200) | |
| notes | text | |
| custom_fields | jsonb | |
| created_by/at, modified_by/at | audit | NOT NULL |

## training_attendance

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| training_event_id | uuid | FK → training_event, NOT NULL |
| employee_id | uuid | FK → employee, NOT NULL; UNIQUE (training_event_id, employee_id) |
| outcome | varchar(20) | NOT NULL — COMPLETED / FAILED |
| created_by/at, modified_by/at | audit | NOT NULL |

## training_event_skill  (join: event → supported skills)

| Column | Type | Constraints |
|---|---|---|
| training_event_id | uuid | FK, NOT NULL |
| skill_id | uuid | FK, NOT NULL |
| PK | | (training_event_id, skill_id) |

## Qualification status enum (API-level, not persisted)

`HELD_ACTIVE`, `EXPIRING_SOON` (qualifies), `EXPIRED`, `REVOKED`, `NOT_HELD`, `SKILL_INACTIVE`. Employee INACTIVE ⇒ all results reported with `employeeActive=false` and gating consumers must treat as not qualified.

## ModuleKey additions (mes-udf-lib)

`EMPLOYEE`, `SKILL`, `CERTIFICATION`.
