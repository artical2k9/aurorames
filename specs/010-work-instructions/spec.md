# Feature Specification: Work Instructions

**Feature Branch**: `010-work-instructions`

**Created**: 2026-06-12

**Status**: Draft

**Input**: Jira Epic MES-10 — "P2 · Work Instructions": Author, version-control, and publish step-by-step work instructions linked to route operations. Includes revision approval workflow with 21 CFR Part 11 electronic signatures, media attachments (images, drawings, videos), and skill/qualification gating (operator must hold required skill before being presented an instruction). Separate microservice from Manufacturing Routing. Microservice: engineering-service.

## Clarifications

### Session 2026-06-12 (project owner via /speckit-clarify)

- Q: E-signature mechanism? → A: **Keycloak password re-authentication** via a dedicated confidential client (mes-signature-verify, Direct Access Grants); signer identity bound to the authenticated session; immutable signature record (research.md R1 confirmed).
- Q: Media binary storage? → A: **MinIO now** — add a MinIO container to the compose stack and store media via the S3 API from day one (supersedes the Docker-volume v1 in the original research R3; DEF-001 is closed, not deferred).
- Build order: MES-11 → MES-12 → MES-10 — this epic implements last, so the labour qualification API is live for integration verification (no dangling WireMock-only merges).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Author a Work Instruction (Priority: P1)

A manufacturing engineer creates a new work instruction for an assembly task. The instruction has a header (identifier, title, description, linked part number context) and an ordered list of steps. Each step has a step number, a title, and rich-text body content describing exactly what the operator must do. The instruction starts in DRAFT status and can be edited freely until submitted for approval.

**Why this priority**: Authoring is the foundational capability — nothing else in the epic (approval, media, gating) has value without an instruction to act on.

**Independent Test**: Create a work instruction with three steps via the API/UI, reload it, and verify header and steps persist in correct order. Delivers a usable draft-authoring tool on its own.

**Acceptance Scenarios**:

1. **Given** an authenticated user with `engineering:work-instruction:create` privilege, **When** they create a work instruction with title and description, **Then** the system persists it with revision 0 in DRAFT status and returns its identifier.
2. **Given** a DRAFT work instruction, **When** the author adds steps 10, 20, 30 with rich-text content, **Then** the steps are stored in order and returned in step-number order on read.
3. **Given** a DRAFT work instruction, **When** the author reorders, edits, or deletes a step, **Then** the change persists and step ordering remains consistent.
4. **Given** an APPROVED work instruction revision, **When** any user attempts to edit its content directly, **Then** the system rejects the edit with a conflict error (a new draft revision must be created instead).

---

### User Story 2 - Revision Approval Workflow with Electronic Signature (Priority: P1)

A work instruction follows the same controlled revision lifecycle as Item Masters and BOMs: DRAFT → PENDING_APPROVAL → APPROVED, with reject-back-to-draft. Because work instructions are quality records presented to operators, approval requires a 21 CFR Part 11-compliant electronic signature: the approver must re-authenticate (enter their password) at the moment of signing, and the system records the signer identity, timestamp, and meaning of the signature (e.g. "Approved").

**Why this priority**: Uncontrolled instructions cannot legally be presented on an aerospace/regulated shop floor. Approval with e-signature is what turns a draft into a usable document.

**Independent Test**: Submit a draft, approve it with password re-authentication, and verify the revision is APPROVED with a complete signature record (who, when, meaning). Reject path returns revision to DRAFT with reason.

**Acceptance Scenarios**:

1. **Given** a DRAFT work instruction revision, **When** the author submits it for approval, **Then** status becomes PENDING_APPROVAL and the submitter/timestamp are recorded.
2. **Given** a PENDING_APPROVAL revision, **When** a user with approval privilege signs approval providing their password, **Then** the system verifies the credential against the identity provider, sets status APPROVED, and stores an electronic signature record (full printed name, date/time, meaning of signature) that cannot be modified or deleted.
3. **Given** a PENDING_APPROVAL revision, **When** the approver enters an incorrect password, **Then** the approval is rejected, status remains PENDING_APPROVAL, and the failed attempt is logged.
4. **Given** a PENDING_APPROVAL revision, **When** an approver rejects it with a reason, **Then** status returns to DRAFT and the rejection reason is visible to the author.
5. **Given** an APPROVED revision, **When** an editor creates a new revision, **Then** a new DRAFT revision N+1 is created carrying a full copy of the previous revision's steps and media references, leaving revision N untouched and still APPROVED.

---

### User Story 3 - Media Attachments on Steps (Priority: P2)

Authors attach images, drawings (e.g. PDF), and videos to a work instruction step so operators can see exactly what to do. Media is uploaded once, stored by the service, and referenced from steps. Each attachment has a caption and display order within the step.

**Why this priority**: Visual content is what makes instructions usable on a real shop floor, but a text-only instruction is still functional, so this follows authoring and approval.

**Independent Test**: Upload an image to a step, retrieve the step, verify the attachment metadata and that the binary can be downloaded.

**Acceptance Scenarios**:

1. **Given** a DRAFT revision step, **When** the author uploads an image (PNG/JPG), drawing (PDF), or video (MP4) within configured size limits, **Then** the file is stored and an attachment record (filename, content type, size, caption, order) is linked to the step.
2. **Given** a step with attachments, **When** a client requests the step, **Then** attachment metadata is returned with download URLs that enforce the same authorisation as the instruction itself.
3. **Given** an upload exceeding the configured max size or an unsupported content type, **When** the author uploads it, **Then** the system rejects it with a validation error naming the limit.
4. **Given** an APPROVED revision, **When** anyone attempts to add or remove media on its steps, **Then** the system rejects the change (media is part of the controlled content).

---

### User Story 4 - Skill / Qualification Gating Definition (Priority: P2)

An author declares which skills/qualifications an operator must hold to be presented this work instruction (e.g. "IPC-A-610 soldering certificate"). The work instruction stores skill requirements as references to skills defined in the Labour Resources module (MES-11). Enforcement at execution time (blocking an unqualified operator on the shop floor) belongs to Shop Floor Execution; this epic delivers the definition and an evaluation endpoint that answers "may operator X be presented instruction Y?".

**Why this priority**: Gating metadata must exist on the instruction before routing (MES-9) and execution can consume it, but it depends on the skill catalogue from MES-11.

**Independent Test**: Add two skill requirements to an instruction, call the evaluation endpoint for an operator who holds one of the two skills, verify the response is "not qualified" listing the missing skill.

**Acceptance Scenarios**:

1. **Given** a DRAFT revision, **When** the author adds a required skill (reference to a skill in the labour service), **Then** the requirement is stored on the revision.
2. **Given** an instruction with skill requirements, **When** the evaluation endpoint is called for an operator, **Then** the system returns qualified/not-qualified with the list of missing or expired certifications, based on current data from the labour service.
3. **Given** the labour service is unavailable, **When** the evaluation endpoint is called, **Then** the system fails closed (returns "not qualified — unable to verify") rather than allowing unverified access.

---

### User Story 5 - Browse, Search and View Published Instructions (Priority: P3)

Engineers and supervisors browse a list of work instructions with search and filters (status, title, identifier), open one, and view the released (latest APPROVED) revision with full revision history. The UI lives in the Angular frontend under the Engineering area, following the established list/detail patterns (column picker, UDF support, revision history table).

**Why this priority**: Discovery and read-only consumption complete the authoring loop but depend on everything above existing first.

**Acceptance Scenarios**:

1. **Given** several work instructions exist, **When** a user opens the Work Instructions list, **Then** they see a paged, searchable list showing identifier, title, current revision, status and modified date.
2. **Given** an instruction with revisions 0 (APPROVED) and 1 (DRAFT), **When** a user opens its detail page, **Then** the APPROVED revision content is shown by default with a revision selector and full revision history, mirroring the Item Master detail page behaviour.
3. **Given** a revision history entry, **When** the user clicks View, **Then** the content of that historical revision loads on screen.

---

### Edge Cases

- Creating a new draft revision when a DRAFT already exists must be rejected (only one open draft per instruction, consistent with Item/BOM behaviour).
- Approving an instruction with zero steps must be rejected with a validation error — an empty instruction is not a usable quality document.
- An operator's skill certificate expires between page load and evaluation: evaluation must always re-check live data, never cache qualification results.
- Media file storage failure mid-upload must not leave orphan attachment records.
- Deleting a work instruction that has ever been APPROVED is prohibited; only never-approved instructions (all revisions DRAFT) may be deleted, and deletion is soft (audit trail preserved).
- A skill referenced by an instruction is deactivated in the labour service: instruction remains valid but evaluation reports the requirement as unsatisfiable; UI flags the stale reference.
- Concurrent edits to the same draft step: last-write-wins is acceptable for v1 but every write is audited.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow users with the appropriate privilege to create work instructions with header fields: identifier (unique per organisation, auto-suggested), title, description, and optional linkage context (part number free-text reference until MES-9 routing exists).
- **FR-002**: System MUST manage work instruction content under revision control: revisions numbered from 0, lifecycle DRAFT → PENDING_APPROVAL → APPROVED, with reject returning to DRAFT, identical in semantics to the Item Master/BOM revision model.
- **FR-003**: System MUST permit at most one DRAFT revision per work instruction at any time.
- **FR-004**: System MUST support ordered steps within a revision; each step has a step number, title, and rich-text body; steps can be added, edited, reordered, and deleted only while the revision is DRAFT.
- **FR-005**: System MUST copy all steps, media references, and skill requirements from the source revision when a new draft revision is created from an APPROVED revision.
- **FR-006**: System MUST require an electronic signature for approval: the approver re-enters their password, which is verified against the identity provider (Keycloak) at signing time; on success the system records signer's full name, user id, timestamp (UTC), and signature meaning.
- **FR-007**: Electronic signature records MUST be immutable: no API may update or delete them; they are retained for the life of the record and included in the audit trail (21 CFR Part 11 §11.10(e), §11.50, §11.70).
- **FR-008**: System MUST log failed signature attempts (wrong password) with user, timestamp and instruction reference, and MUST NOT change revision status on failure.
- **FR-009**: System MUST prevent any content modification (header fields, steps, media, skill requirements) on PENDING_APPROVAL and APPROVED revisions.
- **FR-010**: System MUST support media attachments (PNG, JPG, PDF, MP4 at minimum) on steps with configurable max file size; attachments carry filename, content type, size, caption and display order.
- **FR-011**: Media binaries MUST be downloadable only by authenticated users with read privilege on work instructions; download URLs must not be publicly accessible.
- **FR-012**: System MUST allow skill requirements (references to labour-service skill definitions) to be attached to a DRAFT revision.
- **FR-013**: System MUST provide a qualification evaluation operation answering whether a given operator currently satisfies all skill requirements of a given instruction revision, returning the list of missing/expired certifications; the check uses live labour-service data and fails closed when that data is unavailable.
- **FR-014**: System MUST provide list/search over work instructions (identifier, title, status, modified date) scoped to the caller's organisation, with pagination.
- **FR-015**: System MUST expose full revision history per instruction (revision number, status, submitted/approved/rejected by+at, signature info) and allow retrieval of any historical revision's full content.
- **FR-016**: All entities MUST be org-scoped (multi-tenant) and protected by the established privilege model (`engineering:work-instruction:*` privilege keys registered via the privilege manifest).
- **FR-017**: All create/update/delete operations MUST be audited via the established Envers audit mechanism, including `_aud` tables for every audited entity.
- **FR-018**: Work instruction headers and steps MUST support admin-defined custom fields (UDFs) via the established UDF module pattern (module keys registered, `customFields` JSONB storage).
- **FR-019**: Deletion of a work instruction MUST be allowed only if no revision has ever reached APPROVED status, and must be implemented as a soft delete.

### Key Entities

- **WorkInstruction**: Org-scoped root; unique identifier + lifecycle container; one-to-many revisions.
- **WorkInstructionRevision**: Numbered revision with status (DRAFT/PENDING_APPROVAL/APPROVED), header content snapshot (title, description, context), submit/approve/reject metadata, custom fields.
- **WorkInstructionStep**: Ordered child of a revision; step number, title, rich-text body, custom fields; owns media attachments.
- **MediaAttachment**: File metadata (filename, content type, size, caption, order) + binary storage reference, linked to a step.
- **SkillRequirement**: Reference (skill id + denormalised skill name) from a revision to a labour-service skill definition.
- **ElectronicSignature**: Immutable record — signer user id, full name, UTC timestamp, meaning ("Approved"), linked revision; created only after successful re-authentication.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An engineer can author and submit a 10-step work instruction with images in under 15 minutes without training beyond the existing MES UI conventions.
- **SC-002**: 100% of APPROVED revisions have a complete, immutable electronic signature record retrievable via API and visible in the UI.
- **SC-003**: Qualification evaluation responds in under 2 seconds including the live labour-service check.
- **SC-004**: Zero content changes possible on APPROVED revisions, verified by integration tests covering every mutating endpoint.
- **SC-005**: Revision history for any instruction reconstructs the exact content an operator would have seen for any past APPROVED revision.

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §7.5 documented information: controlled creation, review/approval, revision identification, availability at point of use; §8.5.1 documented production process control |
| AS9102 (FAI) | No | Work instructions are referenced by FAI but no FAI records are produced in this module |
| AS9131 (NCM) | Partial | Inspection record context: instructions feeding inspection steps must be revision-traceable so NCM reports can cite the instruction revision in force |
| NIST SP 800-171 / CMMC | Yes | Access control on technical data (drawings/media may be export-controlled); org-scoped isolation; audit of access to controlled documents |
| 21 CFR Part 11 / Annex 11 | Yes | §11.10(b) system access controls; §11.50 signature manifestation (name, date/time, meaning); §11.70 signature/record linking; §11.200(a) re-authentication at signing |
| ISA-95 | Partial | Work instructions are part of Operations Definition (Part 2) information exchanged with routing |

---

## Assumptions

- The existing `engineering-service` (currently hosting ECO) is the home for this module, per the Epic ("Microservice: engineering-service"); no new service is scaffolded.
- Media binaries are stored in MinIO (S3-compatible object store, new compose container) from v1, per owner clarification 2026-06-12.
- Rich text is stored as sanitised HTML produced by the frontend editor; no server-side rendering is required.
- Linkage to route operations (the "linked to route operations" phrase in the Epic) is delivered by MES-9 Manufacturing Routing, which will reference work instruction revisions — this epic only needs stable, versioned identifiers for MES-9 to point at.
- Skill gating enforcement on the shop floor is delivered by Shop Floor Execution; this epic delivers definitions plus the evaluation endpoint.
- The single-approver workflow used by Item Master/BOM is sufficient for v1; multi-stage approval routing is the domain of MES-112 (Workflow Approval Engine) and can replace the inline workflow later.
- Keycloak password grant (or token exchange) is available to verify the approver's password server-side for the e-signature.
- MES-11 (Labour) must be implemented before the qualification evaluation endpoint can integrate; until then the endpoint is built against the labour-service API contract.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| ~~DEF-001~~ | ~~Object storage (S3/MinIO) for media binaries~~ | **Closed 2026-06-12 — owner chose MinIO in v1 scope (see Clarifications)** | — | — | |
| DEF-002 | Multi-stage / role-routed approval chains | MES-112 Workflow Approval Engine owns this | Single approver only; significant-process instructions cannot demand SME co-signature | P3 (MES-112) | |
| DEF-003 | Operator-facing execution view (step-by-step runner with clock-on) | Belongs to Shop Floor Execution epic | Instructions viewable but not interactively executed | P3 | |
| DEF-004 | Where-used report (which routes reference this instruction) | Requires MES-9 routing data | Authors cannot see impact of revising an instruction | P2 (MES-9 follow-up) | |
| DEF-005 | Offline/print-formatted PDF export of an instruction | Nice-to-have; PDF infra exists (BOM export) but not required for approval flow | Shop floor needs network access to view instructions | P3 | |
