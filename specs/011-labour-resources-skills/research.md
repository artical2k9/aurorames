# Research: Labour Resources & Skills (MES-11)

## R1 — Certification state: derived vs stored

**Decision**: Derive state at read time from `(revoked, expiryDate, warningWindowDays, today)` in a single pure function `CertificationStateCalculator.stateOf(cert, today)` returning ACTIVE / EXPIRING_SOON / EXPIRED / REVOKED. No state column. The warning window is a service config property `mes.labour.expiry-warning-days` (default 30).

**Rationale**: A stored state needs a scheduler to flip records at midnight and invites stale-state gating bugs (the worst failure mode for a compliance gate — SC-005 zero false positives). A pure function is trivially unit-testable across boundary dates (spec edge cases) and immune to clock-skew drift. Query filters needing state (expiry dashboard) translate to date predicates (`expiry_date BETWEEN today AND today+N`), which use an index.

**Alternatives considered**: stored state + nightly job — rejected (staleness risk, extra infra); DB view computing state — rejected (logic split across SQL and Java; warning window config lives in the service layer).

## R2 — Bulk qualification API shape

**Decision**: `POST /api/v1/labour/qualifications/evaluate` with body `{ "employeeId": uuid | null, "iamUserId": string | null, "skillIds": [uuid] }` → `{ "employeeId": uuid, "employeeActive": bool, "results": [ { "skillId", "status": HELD_ACTIVE|EXPIRING_SOON|EXPIRED|REVOKED|NOT_HELD|SKILL_INACTIVE } ] }`. POST (not GET) because the skill-id list can exceed practical URL length; the operation is still read-only and idempotent.

**Rationale**: One round-trip for MES-10's evaluation (FR-013 there) and future MES-9 operation gating; lookup by either employee id or IAM user id covers both back-office and shop-floor token-derived callers. Implementation: one query fetching latest-governing certification per requested skill (`DISTINCT ON (skill_id) ... ORDER BY skill_id, expiry_date DESC` — tiebreaker per ERR-MES-082 analogue), then state computed in Java.

**Alternatives considered**: GET with comma-separated ids — URL limits and cache pollution; per-skill endpoint — N round-trips, violates SC-002 latency.

## R3 — Service-to-service authentication for consumers

**Decision**: Consumers (engineering-service) call labour-service through the internal Docker network using the caller's forwarded user JWT (relay the `Authorization` header). The qualification endpoint requires a read privilege (`labour:qualification:read`) granted to roles that can view work instructions.

**Rationale**: Matches how existing cross-service calls work in the stack (no client-credentials infrastructure yet); preserves actor identity end-to-end for audit (§V).

**Alternatives considered**: Keycloak client-credentials service account — cleaner for machine-to-machine but introduces new realm clients and secret management; deferred until a call path exists with no user context (none in this epic).

## R4 — Employee ↔ IAM user linkage

**Decision**: `iam_user_id` (varchar, KC subject) nullable column on employee, unique per org where not null (partial unique index). Link is set by admins choosing from IAM users (existing iam-service user list API). No automatic sync; an unlinked employee is valid (agency staff).

**Rationale**: The shop floor will identify operators by JWT subject; the link makes `evaluate(iamUserId=…)` possible. Uniqueness prevents ambiguous gating identities (spec edge case).

**Alternatives considered**: Kafka sync of KC users into labour — premature; manual linkage suffices and keeps IAM the single identity source.

## R5 — Training records: one row per attendee vs event + attendance

**Decision**: Two tables: `training_event` (title, date, duration, trainer, outcome-default) and `training_attendance` (event FK, employee FK, individual outcome). The spec's "one queryable record per attendee" (FR-008) is satisfied by querying attendance joined to event.

**Rationale**: Avoids duplicating event metadata per attendee; lets one event cover many employees (US4 acceptance scenario records one event for two employees); individual outcomes still possible (one attendee can fail).

**Alternatives considered**: flat row-per-attendee — simpler but duplicates trainer/title/date and complicates editing an event's shared fields under audit.

## R6 — Skill catalogue contract stability for cross-service reference

**Decision**: Expose `GET /api/v1/labour/skills/{id}` and `GET /api/v1/labour/skills?ids=…&active=…` returning a minimal stable DTO (id, code, name, active, certificationRequired). MES-10 denormalises code/name at write time and revalidates on read of the evaluation endpoint only.

**Rationale**: Stable minimal contract decouples consumers from catalogue field churn; denormalised display data survives labour-service outages for read paths while gating still fails closed.
