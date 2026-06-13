# Data Model: Quality Inspection Planning (MES-12)

Schema: `quality` (new, owned by quality-service). All tables have `_aud` tables with `revend`/`revend_tstmp`. Audit columns NOT NULL; AuditorAware bean (ERR-MES-062). Seed INSERTs include audit columns (ERR-MES-077).

## inspection_plan

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| org_id | uuid | NOT NULL |
| item_id | uuid | NOT NULL — inventory-service item master id; UNIQUE (org_id, item_id) |
| part_number | varchar(100) | NOT NULL (denormalised display) |
| created_by/at, modified_by/at | audit | NOT NULL |

## inspection_plan_revision

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| inspection_plan_id | uuid | FK, NOT NULL |
| revision | integer | NOT NULL; UNIQUE (inspection_plan_id, revision) |
| revision_status | varchar(20) | NOT NULL — DRAFT / PENDING_APPROVAL / APPROVED |
| name | varchar(200) | NOT NULL |
| description | text | |
| reason_for_revision | varchar(500) | |
| custom_fields | jsonb | UDF (@Type(JsonBinaryType)) |
| submitted_by/at, approved_by/at, rejected_by/at, rejection_reason | | workflow metadata |
| created_by/at, modified_by/at | audit | NOT NULL |

Partial unique index: `(inspection_plan_id) WHERE revision_status = 'DRAFT'` — one open draft.

## inspection_characteristic

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| plan_revision_id | uuid | FK → inspection_plan_revision, NOT NULL |
| characteristic_number | integer | NOT NULL; UNIQUE (plan_revision_id, characteristic_number) |
| name | varchar(200) | NOT NULL |
| description | text | |
| source | varchar(20) | NOT NULL — DESIGN / IN_PROCESS |
| characteristic_type | varchar(20) | NOT NULL — SPECIFIC / COMMON / CALCULATED |
| inspection_method | varchar(255) | free text v1 |
| gauge_type | varchar(255) | free text v1 |
| unit_of_measure | varchar(20) | |
| sample_size_rule | varchar(20) | NOT NULL — ALL / FIXED_COUNT |
| sample_size_count | integer | NULL; required ≥1 when FIXED_COUNT (service-validated) |
| recording_basis | varchar(20) | NOT NULL — PER_PIECE / PER_LOT (defaults: SPECIFIC→PER_PIECE, COMMON→PER_LOT) |
| nominal_value / lower_limit / upper_limit | numeric(18,6) | SPECIFIC only; validated lower ≤ nominal ≤ upper |
| expected_boolean | boolean | COMMON only — expected answer (true = conforms) |
| expression | varchar(1000) | CALCULATED only — validated grammar (research R1) |
| custom_fields | jsonb | |
| created_by/at, modified_by/at | audit | NOT NULL |

Type-field matrix (service-enforced):

| Field | SPECIFIC | COMMON | CALCULATED |
|---|---|---|---|
| nominal/limits | required (≥1 of limits) | rejected | optional (result tolerance) |
| expected_boolean | rejected | required | rejected |
| expression | rejected | rejected | required |

Expression references: `C<number>` must resolve within the same revision to a SPECIFIC or CALCULATED characteristic; cycles rejected (DFS); deletion of a referenced characteristic rejected naming dependents (spec US2-6).

## State transitions (InspectionPlanRevision)

```
DRAFT --submit--> PENDING_APPROVAL --approve--> APPROVED   (submit blocked: 0 characteristics or invalid expressions)
DRAFT <--reject(reason)-- PENDING_APPROVAL
APPROVED --patch header / create revision--> new DRAFT rev N+1 (full characteristic copy incl. custom_fields)
DRAFT --cancel--> deleted (plan itself deleted only if never approved)
```

## Kafka event

Topic `quality.inspection-plan.approved`: `{ orgId, planId, itemId, partNumber, revision, approvedBy, approvedAt }`. JsonSerializer (ERR-MES-063). Idempotency key: (planId, revision).

## ModuleKey additions (mes-udf-lib)

`INSPECTION_PLAN`, `INSPECTION_CHARACTERISTIC`.
