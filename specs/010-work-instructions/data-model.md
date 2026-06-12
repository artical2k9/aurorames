# Data Model: Work Instructions (MES-10)

Schema: `engineering` (existing). All tables get matching `_aud` tables with `revend`/`revend_tstmp` columns (ValidityAuditStrategy — ERR-MES-057/V016 lesson). All audit columns (`created_by`, `created_at`, `modified_by`, `modified_at`) NOT NULL via AuditingEntityListener; engineering-service already has an AuditorAware bean.

## work_instruction

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK, generated |
| org_id | uuid | NOT NULL, part of unique constraints |
| identifier | varchar(40) | NOT NULL, UNIQUE (org_id, identifier) |
| deleted | boolean | NOT NULL default false (soft delete, FR-019) |
| created_by/at, modified_by/at | audit | NOT NULL |

## work_instruction_revision

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| work_instruction_id | uuid | FK → work_instruction, NOT NULL |
| revision | integer | NOT NULL, UNIQUE (work_instruction_id, revision) |
| revision_status | varchar(20) | NOT NULL — DRAFT / PENDING_APPROVAL / APPROVED |
| title | varchar(200) | NOT NULL |
| description | text | |
| part_context | varchar(100) | free-text part number reference (until MES-9) |
| reason_for_revision | varchar(500) | |
| custom_fields | jsonb | UDF storage (@Type(JsonBinaryType)) |
| submitted_by / submitted_at | varchar/timestamptz | |
| approved_by / approved_at | varchar/timestamptz | |
| rejected_by / rejected_at / rejection_reason | | |
| created_by/at, modified_by/at | audit | NOT NULL |

Invariant: at most one revision with status DRAFT per work_instruction (enforced in service; partial unique index `(work_instruction_id) WHERE revision_status='DRAFT'`).

## work_instruction_step

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| wi_revision_id | uuid | FK → work_instruction_revision, NOT NULL |
| step_number | integer | NOT NULL, UNIQUE (wi_revision_id, step_number) |
| title | varchar(200) | NOT NULL |
| body_html | text | sanitised HTML |
| custom_fields | jsonb | |
| created_by/at, modified_by/at | audit | NOT NULL |

## wi_media_attachment

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| step_id | uuid | FK → work_instruction_step, NOT NULL |
| file_name | varchar(255) | NOT NULL (original name) |
| content_type | varchar(100) | NOT NULL — allowlist image/png, image/jpeg, application/pdf, video/mp4 |
| size_bytes | bigint | NOT NULL |
| caption | varchar(500) | |
| display_order | integer | NOT NULL default 0 |
| storage_path | varchar(500) | NOT NULL — MinIO object key in bucket wi-media ({orgId}/{instructionId}/{attachmentId}.{ext}) |
| created_by/at, modified_by/at | audit | NOT NULL |

Copy-on-revision: new draft copies attachment ROWS (metadata) pointing at the SAME object key — binaries are content-addressed by attachment id of first upload and never deleted from MinIO while referenced; a reference count check guards object deletion (only when no revision references the key).

## wi_skill_requirement

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| wi_revision_id | uuid | FK → work_instruction_revision, NOT NULL |
| skill_id | uuid | NOT NULL — labour-service skill UUID |
| skill_code | varchar(50) | NOT NULL (denormalised) |
| skill_name | varchar(200) | NOT NULL (denormalised) |
| UNIQUE | | (wi_revision_id, skill_id) |
| created_by/at, modified_by/at | audit | NOT NULL |

## wi_electronic_signature  *(append-only — NOT Envers-audited-mutable; no update/delete repository methods)*

| Column | Type | Constraints |
|---|---|---|
| id | uuid | PK |
| wi_revision_id | uuid | FK, NOT NULL |
| signer_user_id | varchar(255) | NOT NULL (JWT sub, null-safe per ERR-MES-060) |
| signer_full_name | varchar(255) | NOT NULL (KC given+family name or preferred_username) |
| signed_at | timestamptz | NOT NULL (server UTC) |
| meaning | varchar(50) | NOT NULL — 'APPROVED' (extensible) |
| created_by/at | audit | NOT NULL |

Failed signature attempts are NOT rows here; they go to the application audit log (Envers revinfo + log statement) per FR-008.

## State transitions (WorkInstructionRevision)

```
DRAFT --submit--> PENDING_APPROVAL --approve(+e-sign)--> APPROVED
DRAFT <--reject(reason)-- PENDING_APPROVAL
APPROVED --editHeader/createRevision--> new DRAFT (rev N+1, full copy of steps/media/skills)
DRAFT --cancel--> deleted (only if instruction has another revision; rev-0-only instruction cancellation deletes the instruction if never approved)
```

## ModuleKey additions (mes-udf-lib)

`WORK_INSTRUCTION` (header UDFs), `WORK_INSTRUCTION_STEP` (step UDFs).
