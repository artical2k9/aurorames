# Feature Specification: Labour Resources & Skills

**Feature Branch**: `011-labour-resources-skills`

**Created**: 2026-06-12

**Status**: Draft

**Input**: Jira Epic MES-11 — "P2 · Labour Resources & Skills": Define workforce resources: employees, competency profiles, skill certifications, skill expiry tracking, and training record management. Labour resources are assigned to route operations and work orders; shop floor execution gates on active skill certificates. Microservice: labour-service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage Employees (Labour Resources) (Priority: P1)

An HR/production administrator maintains the register of workforce resources: employees with an employee number, name, contact details, employment status (active/inactive), assigned organisation, and an optional link to an IAM user account (so a shop-floor login can be matched to an employee record). Employees are the anchor for all skills, certifications and training records.

**Why this priority**: Every other capability in this epic (skills, certifications, training) hangs off the employee record; nothing works without it.

**Independent Test**: Create an employee, search for them in the list, edit their details, deactivate them — all persisted and audited.

**Acceptance Scenarios**:

1. **Given** a user with `labour:employee:create` privilege, **When** they create an employee with employee number and name, **Then** the record persists org-scoped with ACTIVE status and an audit row is written.
2. **Given** an existing employee number within the organisation, **When** a second employee is created with the same number, **Then** the system rejects it with a conflict error.
3. **Given** an employee linked to an IAM user, **When** the employee record is fetched by IAM user id, **Then** the matching employee is returned (used by qualification checks at execution time).
4. **Given** an ACTIVE employee, **When** an administrator deactivates them, **Then** status becomes INACTIVE, they no longer appear in default assignment lists, and historical records remain intact.

---

### User Story 2 - Skill Catalogue (Priority: P1)

A training/quality administrator defines the catalogue of skills and qualifications used across the organisation: skill code, name, description, category (e.g. welding, inspection, soldering), whether certification is required, and the default validity period (e.g. 24 months) after which a certification expires and must be renewed.

**Why this priority**: The skill catalogue is referenced by Work Instructions (MES-10), route operations (MES-9), and certifications (this epic). It must exist before certifications can be issued.

**Independent Test**: Create a skill with a 12-month validity period, list and search the catalogue, deactivate a skill and verify it cannot be newly assigned.

**Acceptance Scenarios**:

1. **Given** a user with `labour:skill:create` privilege, **When** they create a skill with code, name, category and validity period, **Then** it persists org-scoped and appears in the catalogue.
2. **Given** a duplicate skill code within the organisation, **When** a skill is created with the same code, **Then** the system rejects it with a conflict error.
3. **Given** an active skill, **When** an administrator deactivates it, **Then** no new certifications can be issued against it, while existing certifications remain visible and continue to expire on schedule.
4. **Given** external consumers (engineering-service, shopfloor-service), **When** they query the skill catalogue API, **Then** they receive id, code, name, active flag — a stable contract for cross-service reference.

---

### User Story 3 - Certifications with Expiry Tracking (Priority: P1)

A training administrator awards a skill certification to an employee: skill, award date, expiry date (defaulted from the skill's validity period, overridable), certifying authority/assessor, and optional evidence reference. The system computes certification state (ACTIVE, EXPIRING_SOON, EXPIRED, REVOKED) and provides queries used for gating: "does employee X currently hold active certification for skill Y?"

**Why this priority**: Certification state is what MES-10's qualification evaluation and future shop-floor gating consume — it is the integration contract of this epic.

**Independent Test**: Award a certification with a past expiry date and verify state EXPIRED; award one expiring in 10 days and verify EXPIRING_SOON; revoke one and verify gating answers false.

**Acceptance Scenarios**:

1. **Given** an active employee and active skill, **When** an administrator awards a certification, **Then** the record persists with expiry defaulted to award date + skill validity period, state ACTIVE.
2. **Given** a certification expiring within the configured warning window (default 30 days), **When** its state is computed, **Then** it reports EXPIRING_SOON while still satisfying gating checks.
3. **Given** a certification past its expiry date, **When** gating is evaluated, **Then** the employee is reported as not qualified for that skill.
4. **Given** an ACTIVE certification, **When** an administrator revokes it with a reason, **Then** state becomes REVOKED immediately, gating fails, and the revocation (who/when/why) is audited.
5. **Given** an employee with an expired certification, **When** a new certification for the same skill is awarded (renewal), **Then** both records remain in history and the newest governs gating.
6. **Given** a qualification query for employee X and a set of skill ids, **When** the API is called, **Then** it returns per-skill held/missing/expired status in a single response (bulk contract for MES-10/MES-9 consumers).

---

### User Story 4 - Training Records (Priority: P2)

A training administrator records training events: employee(s) trained, course/title, date, duration, trainer, outcome (completed/failed), and optional link to the skill(s) the training supports. Training records provide the AS9146/AS9100D §7.2 evidence trail behind certifications.

**Why this priority**: Compliance evidence — required for audits but not on the runtime gating path.

**Independent Test**: Record a training event for two employees against a skill, open an employee's training history, verify both the event and its link to the skill.

**Acceptance Scenarios**:

1. **Given** a user with `labour:training:create` privilege, **When** they record a training event with title, date, and attendee employees, **Then** a training record is persisted for each attendee and is visible in each employee's training history.
2. **Given** a training record linked to a skill, **When** viewing a certification for that employee+skill, **Then** the supporting training records are listed as evidence.
3. **Given** a training record, **When** anyone attempts to modify outcome after creation, **Then** the change is audited with who/when/before/after values.

---

### User Story 5 - Employee Competency Profile & Expiry Dashboard (Priority: P2)

Supervisors view an employee's competency profile — all skills with certification state and expiry dates — and an organisation-level expiry dashboard listing certifications expiring within a chosen window, so renewals can be scheduled before operators are locked out on the shop floor.

**Why this priority**: Operational visibility that prevents production stoppages; consumes data from stories 1–3.

**Acceptance Scenarios**:

1. **Given** an employee with several certifications, **When** a supervisor opens the employee's profile, **Then** all skills are listed with state badges (ACTIVE / EXPIRING_SOON / EXPIRED / REVOKED) and expiry dates.
2. **Given** certifications across the organisation, **When** the expiry report is run for the next 60 days, **Then** all certifications expiring in that window are listed with employee, skill, and expiry date, sortable and exportable to the standard list UI.
3. **Given** the Angular UI, **When** the user opens Labour > Employees / Skills / Certifications screens, **Then** they follow the established list/detail patterns (column picker, UDF support, search, pagination).

---

### Edge Cases

- Certification awarded for a skill that requires no certification (`certificationRequired=false`): allowed but flagged informational — gating for such skills always passes.
- Employee deactivated while holding active certifications: certifications remain but gating fails because the employee is inactive.
- Skill validity period changed after certifications were issued: existing expiry dates are not retroactively recalculated; only new awards use the new period.
- Two certifications for the same employee+skill overlapping in time: the one with the latest expiry governs; duplicates with identical award dates are rejected.
- Timezone handling: expiry is evaluated against the organisation's local date, stored as a date (not timestamp) to avoid off-by-one at midnight.
- An IAM user linked to two employee records must be rejected — the link is unique per organisation.
- Bulk qualification query with an empty skill list returns an empty result, not an error (caller convenience).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a new `labour-service` microservice following the established service scaffold (Spring Boot, Flyway schema `labour`, Keycloak resource-server security, privilege manifest registration, Envers audit with `_aud` tables, AuditorAware bean).
- **FR-002**: System MUST support employee CRUD with fields: employee number (unique per org), first/last name, email, employment status (ACTIVE/INACTIVE), hire date, optional IAM user id link (unique per org), custom fields (UDF).
- **FR-003**: System MUST support skill catalogue CRUD with fields: skill code (unique per org), name, description, category, certificationRequired flag, validity period in months (nullable = never expires), active flag, custom fields (UDF).
- **FR-004**: System MUST support awarding certifications: employee + skill + award date + expiry date (defaulted from validity period, overridable) + assessor/authority + evidence reference; duplicate (employee, skill, award date) rejected.
- **FR-005**: System MUST compute certification state as ACTIVE, EXPIRING_SOON (within configurable warning window, default 30 days), EXPIRED (past expiry), or REVOKED; state is derived at read time, never stored stale.
- **FR-006**: System MUST support revoking a certification with mandatory reason, recording who/when, immediately failing gating.
- **FR-007**: System MUST provide a bulk qualification evaluation API: given an employee (by employee id or IAM user id) and a list of skill ids, return per-skill qualification status (HELD_ACTIVE, EXPIRING_SOON, EXPIRED, REVOKED, NOT_HELD, SKILL_INACTIVE); employees who are INACTIVE fail all checks.
- **FR-008**: System MUST support training records: title, date, duration, trainer, outcome, attendees (many employees), optional linked skills; one queryable record per attendee.
- **FR-009**: System MUST provide employee competency profile retrieval (all certifications with computed state) and an org-wide expiry query (certifications expiring within N days).
- **FR-010**: All list endpoints MUST be org-scoped, paginated, and searchable consistent with existing services.
- **FR-011**: All privilege keys MUST follow `labour:<entity>:<action>` and be registered in the service's privilege manifest (auto-granted to SYSTEM_ADMIN per ERR-MES-075).
- **FR-012**: All entities MUST be audited (Envers) including revocation and training-outcome changes.
- **FR-013**: The skill catalogue read API MUST be stable for cross-service consumption by engineering-service (MES-10 skill requirements) and future shopfloor-service (MES-9 operation skill requirements).
- **FR-014**: Frontend MUST add a Labour navigation area with Employees, Skills, Certifications (and training visible from employee detail) following the established Angular list/detail patterns including column picker + UDF integration (ERR-MES-078) and change-detection rules (ERR-MES-059).
- **FR-015**: New module UDF keys (EMPLOYEE, SKILL, CERTIFICATION) MUST be registered in the UDF module-key enumeration.

### Key Entities

- **Employee**: Org-scoped workforce resource; employee number, names, status, optional IAM user link; parent of certifications and training attendance.
- **Skill**: Org-scoped catalogue entry; code, name, category, certificationRequired, validityMonths, active flag.
- **Certification**: Employee × Skill award; award/expiry dates, assessor, evidence ref, revocation fields; state derived (ACTIVE/EXPIRING_SOON/EXPIRED/REVOKED).
- **TrainingRecord**: Training event attendance; title, date, duration, trainer, outcome, linked skills, one row per attendee.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A training administrator can register an employee, define a skill, and award a certification in under 5 minutes end-to-end.
- **SC-002**: The bulk qualification API answers a 20-skill query in under 500 ms (it sits on the MES-10/MES-9 hot path).
- **SC-003**: The expiry report surfaces 100% of certifications expiring in the selected window — verified by integration test fixtures spanning the boundary dates.
- **SC-004**: An auditor can trace any certification to its supporting training records and to the award/revocation actors and timestamps without leaving the UI.
- **SC-005**: Zero gating false-positives: no EXPIRED/REVOKED certification or INACTIVE employee ever evaluates as qualified (covered by integration tests).

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §7.2 competence: determine necessary competence, ensure persons are competent, retain documented evidence |
| AS9102 (FAI) | No | FAI does not consume labour records directly |
| AS9131 (NCM) | No | NCM references operators via execution records, not this module |
| NIST SP 800-171 / CMMC | Yes | PS (Personnel Security) domain: workforce records access-controlled and audited; least-privilege on PII fields |
| 21 CFR Part 11 / Annex 11 | No | No electronic signatures executed in this module (certification award is an audited admin action, not a Part 11 signature) — aerospace context |
| ISA-95 | Yes | Part 2 Personnel model: personnel class (skill), person, qualification test specification/result mapping |
| AS9146 (FOD/training) | Partial | Training record retention supports AS9146 training evidence requirements |

---

## Assumptions

- A brand-new `labour-service` is created (the Epic names it); it follows the service scaffold conventions of `platform-service`/`engineering-service`, registered in the gateway, compose files, and `sonar-project.properties`.
- Employee PII is limited to name/email/employee number in v1; payroll, address, and HR data are out of scope.
- Certification evidence is a free-text/URL reference in v1; document upload re-uses no media store (deferred).
- The IAM user link is optional because not all employees have system logins (e.g. agency staff tracked for certification only).
- Assignment of labour resources to route operations and work orders (mentioned in the Epic) is consumed by MES-9 and Work Order epics; this epic only exposes the APIs they need.
- Expiry warning window (30 days) is a service-level configuration, not per-skill, for v1.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Document upload for certification evidence | Needs shared media storage strategy (same concern as MES-10 DEF-001) | Evidence remains external links; audit trail weaker | P3 | |
| DEF-002 | Automated expiry notifications (email/queue) | Notification infrastructure not yet established platform-wide | Supervisors must poll the expiry dashboard | P3 | |
| DEF-003 | Per-skill expiry warning windows | Config complexity; single org-level window suffices for v1 | Skills with long lead-time renewals get late warnings | P3 | |
| DEF-004 | Shift/calendar & labour-rate management | Scheduling/costing domain, not competence; separate epic | Labour costing absent from routing exports | Post-GA | |
| DEF-005 | Skill matrix bulk import (CSV) | Manual entry acceptable at current org size | Slow onboarding for large workforces | P3 | |
