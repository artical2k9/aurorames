# Research: Work Instructions (MES-10)

## R1 — Electronic signature: verifying the approver's password

**Decision**: Verify the re-entered password server-side using Keycloak's Direct Access Grant (Resource Owner Password Credentials) token endpoint for the `mes` realm, scoped to a dedicated confidential client (`mes-signature-verify`) with direct access grants enabled. The engineering-service calls `POST /realms/mes/protocol/openid-connect/token` with `grant_type=password`, the approver's `preferred_username` (taken from their authenticated JWT, never from request input) and the password supplied in the signature dialog. HTTP 200 = credential valid; the returned token is discarded immediately. On success the service writes the `ElectronicSignature` row in the same transaction as the status change (Part 11 §11.70 signature/record linking).

**Rationale**: No bespoke credential handling (Constitution §VII) — Keycloak remains the only credential verifier. The username is bound to the already-authenticated session, satisfying §11.200(a) (signature applied by the genuine owner). Failed attempts are detected via 401 from KC and logged.

**Alternatives considered**: (a) Keycloak step-up authentication (ACR/AMR) — correct long-term but requires browser redirect flows that break the modal UX and need KC 24+ step-up config; deferred. (b) Local password hash comparison — violates Keycloak-only auth, rejected outright. (c) Re-login popup via OIDC prompt=login — full redirect loses unsaved page state; rejected for UX.

**New client requirements**: `mes-signature-verify` must be added to `keycloak/mes-realm.json` with `directAccessGrantsEnabled: true`, confidential, no roles, plus the mandatory `sub` protocol mapper (ERR-MES-060). Secret supplied via env var, listed in `.env.example` (ERR-MES-016).

## R2 — Revision lifecycle implementation pattern

**Decision**: Reuse the MES-114 BOM pattern verbatim: root entity (`WorkInstruction`) + revision entity (`WorkInstructionRevision`) with `RevisionStatus` enum (DRAFT/PENDING_APPROVAL/APPROVED), one-draft-at-a-time invariant, auto-draft creation on edit of an approved revision (copying steps, media refs, skill requirements), display-revision resolution APPROVED > PENDING_APPROVAL > DRAFT with `revision DESC` tiebreaker (ERR-MES-082).

**Rationale**: Identical semantics already proven and tested in inventory-service; users get consistent behaviour across Item Master, BOM, and Work Instructions.

**Alternatives considered**: Envers-only versioning (no explicit revision rows) — rejected: Envers is an audit trail, not a user-facing revision model; cannot represent concurrent APPROVED+DRAFT.

## R3 — Media binary storage

**Decision**: Store binaries on a dedicated Docker volume (`wi-media`) mounted at `/data/wi-media`, organised as `{orgId}/{instructionId}/{attachmentId}.{ext}`. A `MediaStorageService` interface isolates the storage mechanism; the DB row stores metadata + relative path. Downloads stream via `StreamingResponseBody` with privilege check. Max sizes via `application.yml` (`mes.wi.media.max-image-bytes`, `max-video-bytes`); Spring multipart limits raised accordingly.

**Rationale**: No object store exists in the stack; a volume keeps v1 simple while the interface keeps DEF-001 (S3/MinIO) a drop-in replacement. Path includes orgId for isolation audits.

**Alternatives considered**: (a) PostgreSQL bytea/LO — bloats backups, poor for 100 MB videos; rejected. (b) MinIO container now — new infra + credentials + healthchecks not justified by v1 scale; deferred (DEF-001).

## R4 — Rich text step content

**Decision**: Store sanitised HTML (string column, `TEXT`). Frontend uses the PrimeNG Editor (Quill) already available in the PrimeNG dependency; backend sanitises with OWASP java-html-sanitizer (allowlist: basic formatting, lists, tables, links) before persist.

**Rationale**: Operators need formatting (warnings, bold cautions, tables). Sanitising server-side blocks stored-XSS regardless of client.

**Alternatives considered**: Markdown — loses table/alignment fidelity authors expect; plain text — insufficient for shop-floor documents.

## R5 — Skill requirement references and qualification evaluation

**Decision**: `SkillRequirement` stores `skillId` (UUID from labour-service) + denormalised `skillCode`/`skillName` for display. Evaluation endpoint `GET /work-instructions/{id}/revisions/{rev}/qualification?employeeId=…` calls labour-service's bulk qualification API (MES-11 FR-007) via a `LabourServiceClient` (RestClient with 2 s timeout). Any client error/timeout → respond `qualified=false, reason=VERIFICATION_UNAVAILABLE` (fail closed). Contract tests with WireMock pin the labour-service contract until MES-11 merges.

**Rationale**: Cross-service access via owning service's REST API only (Constitution §XI); fail-closed is the only safe behaviour for a compliance gate.

**Alternatives considered**: Kafka-cached local copy of certifications — eventual consistency could let an expired cert pass a gate; rejected for the gating path (acceptable later for dashboards).

## R6 — Identifier generation

**Decision**: Work instruction identifier is user-supplied with uniqueness per org, auto-suggested as `WI-{seq}` (max existing numeric suffix + 1) — same UX as item part numbers. No DB sequence; suggestion endpoint computes from existing rows.

**Rationale**: Aerospace customers usually carry their own document numbering; suggestion-not-enforcement matches Item Master behaviour.
