<!--
SYNC IMPACT REPORT
==================
Version change: N/A (template) → 1.0.0 (initial ratification)

Principles added (all new):
  I.   Spec-First Development
  II.  Test-First Implementation (TDD + Defect Tracking)
  III. AI-Generated, Human-Approved
  IV.  Compliance by Design
  V.   Full System Auditability
  VI.  ISA-95 / ISA-88 Architecture Conformance
  VII. Security-First (NIST SP 800-171 / CMMC)
  VIII.Integration Integrity
  IX.  Multi-Organisation Data Isolation
  X.   Manufacturing Data Accuracy & Real-Time Fidelity

Sections added:
  - Functional Domain Coverage
  - Compliance Register
  - Technology Stack Constraints (deferred — see TODO below)

Templates requiring updates:
  ✅ .specify/memory/constitution.md — this file (completed)
  ⚠  .specify/templates/plan-template.md — Constitution Check gates should
     reference aerospace compliance and ISA-95 principles (pending manual update)
  ⚠  .specify/templates/spec-template.md — add Compliance References section
     to every feature spec (pending manual update)
  ⚠  .specify/templates/tasks-template.md — add compliance verification and
     defect-registration task types (pending manual update)

Deferred TODOs:
  - TODO(TECH_STACK): Primary language/framework not yet specified — will be
    established during first /speckit-plan run.
  - TODO(RATIFICATION_DATE): Treated as 2026-05-19 (today, initial commit).
-->

# MikeMES Constitution

## Core Principles

### I. Spec-First Development

Every feature, module, and integration MUST begin with a written specification
approved by the project owner before any implementation work starts. The
specification is the authoritative source of truth; code is its expression.

- All work items MUST be traceable to an approved spec in `/specs/`.
- Specs MUST use the `/speckit-specify` workflow and the standard spec template.
- No pull request will be merged unless it references a spec.
- Retroactive specs (written after code) are not acceptable.

### II. Test-First Implementation (TDD + Defect Tracking)

Implementation MUST follow a strict Red-Green-Refactor cycle:

1. Write tests that precisely capture spec acceptance scenarios.
2. Confirm tests **fail** before writing any implementation code.
3. Implement only enough code to make the tests pass.
4. Refactor, keeping all tests green.

Additional mandatory rules:

- Test failures encountered during a feature's development MUST be logged as
  tracked defects before the feature branch is closed.
- A feature MUST NOT be marked complete while any defect it introduced remains
  open.
- Every functional requirement (FR-XXX) in a spec MUST have at least one
  corresponding automated test.
- All manufacturing-data-path code requires integration tests against a real
  database; mocking the persistence layer is not permitted for these paths.

### III. AI-Generated, Human-Approved

Claude Code (or any AI assistant) may generate, refactor, or modify code.
However:

- Every AI-generated change MUST be reviewed and explicitly approved by a human
  before merging to `main`.
- The human reviewer is responsible for correctness, compliance, and security —
  not the AI.
- AI-generated specs, plans, and constitutions MUST be reviewed and ratified by
  the project owner before they govern any implementation work.
- AI tools MUST NOT be used to approve their own output (no auto-merge of
  AI-authored PRs).

### IV. Compliance by Design

MikeMES operates in aerospace, defence, and regulated manufacturing
environments. Compliance is a first-class architectural concern, not a
post-hoc audit activity.

- All features touching quality, traceability, electronic records, or material
  disposition MUST be designed against the applicable standards in the
  Compliance Register (Section below) from day one.
- Every spec MUST include a "Compliance References" section citing the
  applicable standards.
- Changes that affect compliance scope require explicit owner sign-off separate
  from routine code review.
- 21 CFR Part 11 / Annex 11 requirements (electronic records and electronic
  signatures) apply to **all** data that serves as an official quality record.

### V. Full System Auditability

Every user action, system event, data mutation, and integration message MUST
be logged in a tamper-evident audit trail.

- Audit logs MUST capture: timestamp (UTC), actor identity, organisation,
  action type, affected entity ID, before/after values (for mutations), and
  outcome.
- Audit logs are **immutable**; update or delete operations on audit records
  are prohibited.
- System-level activity logging (principle explicitly required by owner) covers
  login events, permission changes, and all administrative actions.
- Audit data MUST be retained for a minimum period defined per applicable
  standard (default: 10 years for AS9100D quality records).
- Log access MUST itself be audited.

### VI. ISA-95 / ISA-88 Architecture Conformance

The system architecture MUST align with IEC 62264 (ISA-95) enterprise-to-
control integration levels and ISA-88 batch process control models.

- Data models for production, scheduling, and resource management MUST map to
  ISA-95 Part 2 object models (Personnel, Equipment, Material, Process Segment,
  Production Schedule, Production Performance).
- Batch and recipe management MUST follow ISA-88 procedural control hierarchy
  (Procedure → Unit Procedure → Operation → Phase).
- No cross-level data access that violates ISA-95 level boundaries is permitted
  without explicit architectural justification.
- MTConnect and IPC-2591 (CFX) integration adapters MUST use the standard data
  models defined in their respective specifications.

### VII. Security-First (NIST SP 800-171 / CMMC)

Security is non-negotiable for all controlled unclassified information (CUI)
handled in aerospace and defence supply-chain contexts.

- Authentication MUST be provided exclusively via Keycloak (OpenID Connect /
  OAuth 2.0). No bespoke authentication implementations are permitted.
- Authorisation MUST follow Role-Based Access Control (RBAC) with least-
  privilege assignment per organisation and department.
- Multi-organisation tenancy MUST enforce strict data isolation at the
  application and database layer simultaneously (not just UI).
- All NIST SP 800-171 control families apply. The system MUST maintain a
  current System Security Plan (SSP) aligned with CMMC Level 2 requirements.
- Secrets, credentials, and keys MUST NOT appear in source code, commit
  history, or log output.
- All inter-service and external communications MUST use TLS 1.2+.

### VIII. Integration Integrity

All inbound and outbound integrations (ERP, PLM, suppliers, machines, quality
systems) MUST be designed for reliability and auditability.

- Every integration endpoint MUST be idempotent: replaying the same message
  MUST NOT produce duplicate records or corrupted state.
- Integration messages MUST be validated against a published schema before
  processing.
- All integration events (received, processed, rejected, retried) MUST be
  written to the audit log (Principle V).
- Supported integration standards: OAGIS, ATA Spec 2000, ISO 10303 (STEP),
  QIF (ISO 23952), MTConnect, IPC-2591 (CFX). Custom formats require owner
  approval.
- Outbound integrations MUST implement circuit-breaker and retry patterns;
  silent data loss is not acceptable.

### IX. Multi-Organisation Data Isolation

MikeMES MUST support multiple independent organisations within a single
deployment. Data isolation is a safety and compliance requirement, not just
a feature.

- Every data entity MUST carry an `organisation_id` foreign key; queries
  without explicit organisation scope are prohibited in application code.
- Department and labour resource assignments are organisation-scoped.
- Cross-organisation data access MUST require explicit, audited super-admin
  elevation — standard roles cannot access other organisations' data.
- Organisation provisioning and deprovisioning MUST be atomic operations with
  full audit records.

### X. Manufacturing Data Accuracy & Real-Time Fidelity

Shop floor data (machine status, job progress, material consumption, labour
bookings) MUST reflect reality as closely as system constraints allow.

- Real-time machine data collected via MTConnect or IPC-2591 MUST be
  time-stamped at source; synthetic or interpolated timestamps are prohibited.
- Any tolerance or approximation applied to measurement data MUST be recorded
  alongside the value (instrument ID, calibration record, gauge tolerance).
- Gauge and tool calibration status MUST block their use in production
  operations when expired or out-of-tolerance.
- Work instruction versions used at point of manufacture MUST be recorded
  against each operation record; retroactive version changes are not permitted.

---

## Functional Domain Coverage

MikeMES MUST cover the following functional domains. Each domain MUST have a
dedicated spec before implementation begins.

| Domain | AS Standards | Notes |
|---|---|---|
| Work Orders & Scheduling | AS9100D §8.1 | ISA-95 Production Schedule |
| Shop Floor Tracking | AS9100D §8.5 | MTConnect / IPC-2591 |
| Quality & Inspection | AS9100D, AS9102, AS9103, QIF | First Article, FAI |
| Material Receiving & Inbound Inspection | AS9100D §8.4, AS6174, AS5553 | Counterfeit prevention |
| Inventory & Materials / BOM | AS9100D §8.5.4, ISO 10303 | STEP integration |
| Manufacturing Engineering | AS9100D §8.5, AS9145 | Work instructions, APQP |
| User Skills Management | AS9100D §7.2 | Competence records |
| Gauge & Tool Management | AS9100D §7.1.5 | Calibration traceability |
| Document Management | AS9100D §7.5 | Controlled documents |
| Outside Processing (OSP) | AS9100D §8.4, AS9117 | Supplier escape prevention |
| Nonconformance Management (NCM) | AS9100D §10.2, AS9131 | Disposition workflow |
| Inbound / Outbound Integrations | ISA-95, OAGIS, ATA Spec 2000 | See Principle VIII |
| User Security & IAM | NIST SP 800-171, CMMC | Keycloak, RBAC |
| System Activity Logging | 21 CFR Part 11, CMMC | See Principle V |
| Multi-Organisation / Multi-Department | ISA-95 | See Principle IX |
| Labour Resource Tracking | AS9100D §7.1.2 | ISA-95 Personnel |

---

## Compliance Register

All features MUST be assessed against applicable standards from this register.
The spec "Compliance References" section MUST cite each applicable standard.

### Quality Management
- **AS9100D** — Quality Management Systems: Aviation, Space, Defense
- **AS13100** — Quality Systems: Aerospace (supplemental requirements)

### First Article & Process Control
- **AS9102** — First Article Inspection (FAI)
- **AS9103** — Variation Management of Key Characteristics
- **AS9145** — APQP & PPAP for Aviation, Space, Defense

### Supply Chain & Counterfeit Prevention
- **AS6174** — Counterfeit Materiel: Assurance Countermeasures
- **AS5553** — Counterfeit Electronic Parts: Avoidance, Detection, Mitigation
- **AS9134** — Supply Chain Management
- **AS9117** — Supplier Escape: Containment and Corrective Action

### Nonconformance & Traceability
- **AS9131** — Nonconformance Data Classifications

### FOD Prevention
- **AS9146** — Foreign Object Damage/Debris (FOD) Prevention

### Industrial Automation & Integration
- **ISA-95 / IEC 62264** — Enterprise-Control System Integration
- **ISA-88 / IEC 61512** — Batch Control
- **MTConnect** — Machine Tool Connectivity Standard
- **IPC-2591 (CFX)** — Connected Factory Exchange

### Cybersecurity
- **NIST SP 800-171** — Protecting CUI in Nonfederal Systems
- **CMMC Level 2** — Cybersecurity Maturity Model Certification

### Electronic Records
- **21 CFR Part 11** — Electronic Records; Electronic Signatures (FDA)
- **EU Annex 11** — Computerised Systems (EMA)

### Aviation & Logistics
- **FAA Regulations** — as applicable to aviation-certified parts
- **ATA Spec 2000** — Aviation Parts Management & Logistics

### Reliability & Maintenance
- **ISO 14224** — Reliability and Maintenance Data for Equipment

### Data Exchange
- **ISO 10303 (STEP)** — Product Data Representation and Exchange
- **QIF / ISO 23952** — Quality Information Framework
- **OAGIS** — Open Applications Group Integration Specification

---

## Technology Stack Constraints

TODO(TECH_STACK): Primary language, framework, and database not yet specified.
To be established during the first `/speckit-plan` run. The following
constraints are already known:

- **IAM**: Keycloak (OpenID Connect / OAuth 2.0) — non-negotiable.
- **Multi-tenancy**: Organisation-level data isolation enforced at DB layer.
- **Script type**: PowerShell (Windows primary development environment).
- **AI Agent**: Claude Code (spec-kit claude integration).

---

## Governance

### Amendment Procedure

1. Any team member may propose a constitutional amendment via a spec or PR
   description.
2. The amendment MUST document: the changed principle, the rationale, and the
   impact on existing specs, plans, and tasks.
3. The project owner MUST formally approve all amendments (written approval in
   PR review or meeting record).
4. After approval, version MUST be incremented per the versioning policy below,
   and `LAST_AMENDED_DATE` updated.
5. All open feature branches MUST be assessed for constitution compliance after
   each amendment.

### Versioning Policy

- **MAJOR** (X.0.0): Removal or redefinition of an existing principle; removal
  of a mandatory compliance standard.
- **MINOR** (0.Y.0): Addition of a new principle, section, or compliance
  standard; material expansion of existing guidance.
- **PATCH** (0.0.Z): Clarifications, wording corrections, typo fixes, or
  non-semantic refinements that do not change intent.

### Compliance Review

- The constitution MUST be reviewed against the active Compliance Register at
  least once per quarter, or immediately following any new regulatory guidance
  that affects the functional domains above.
- Each review MUST be documented with a PATCH or higher version bump.

### Enforcement

- All pull requests MUST include a Constitution Check section in the plan or PR
  description.
- Reviewers MUST reject PRs that violate any principle without documented
  justification in the Complexity Tracking section of the plan.
- This constitution supersedes all other informal conventions and practices.

**Version**: 1.0.0 | **Ratified**: 2026-05-19 | **Last Amended**: 2026-05-19
