# Feature Specification: System Activity & Audit Logging

**Feature Branch**: `007-system-activity-audit-logging`

**Created**: 2026-05-24

**Status**: Draft

**Jira Epic**: MES-7

**Input**: Jira Epic MES-7 — "P1 · System Activity & Audit Logging"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Domain Entity Change Capture (Priority: P1)

A compliance officer or quality engineer can view the complete change history of any audited domain entity (work order, operation, material, non-conformance, etc.) — who changed it, what changed, and when — to satisfy regulatory audit trail requirements.

**Why this priority**: The tamper-evident entity audit trail is the core deliverable of this Epic and the direct 21 CFR Part 11 §11.10(e) and AS9100D §7.5 requirement. All other capabilities depend on or extend this foundation.

**Independent Test**: Deploy audit-service with Hibernate Envers enabled. Create and update a WorkOrder entity via the work-order-service REST API. Call `GET /audit/entities/WorkOrder/{id}/history` and confirm all changes appear with correct userId, timestamp, before/after values.

**Acceptance Scenarios**:

1. **Given** a WorkOrder entity exists, **When** a user with OPERATOR role updates the `status` field, **Then** an AuditRecord is written containing the userId, previous status value, new status value, and UTC timestamp.
2. **Given** a domain entity is deleted, **When** the DELETE is committed, **Then** an AuditRecord of type ENTITY_CHANGE with action DELETE is created and the deleted entity's final state is preserved in `previousState`.
3. **Given** multiple concurrent updates to the same entity, **When** all commits succeed, **Then** revision numbers are monotonically increasing with no gaps.
4. **Given** the audit schema table, **When** a direct SQL UPDATE is attempted by the application DB user, **Then** the database rejects it (immutability enforced at DB layer).

---

### User Story 2 - Cross-Service Audit Event Streaming (Priority: P1)

A system administrator can verify that business events emitted by any domain service (e.g., a work order being released to the shop floor, a material being quarantined) are captured in a centralised audit store, even if the originating service is temporarily unavailable.

**Why this priority**: Kafka-based streaming decouples audit capture from domain services and ensures audit records survive service restarts. Required for tamper-evidence across a microservice topology.

**Independent Test**: Publish a test audit event to `mes.audit.events` Kafka topic. Confirm audit-service consumes and persists it within 5 seconds. Restart audit-service, confirm no events are lost on replay.

**Acceptance Scenarios**:

1. **Given** a domain service publishes an `AuditEventMessage` to `mes.audit.events`, **When** audit-service is running, **Then** the event is persisted as an AuditRecord within 5 seconds.
2. **Given** audit-service is stopped for 60 seconds, **When** domain services continue publishing events, **Then** on audit-service restart all queued events are consumed and persisted with zero loss.
3. **Given** an `AuditEventMessage` with no `userId` (system-initiated action), **When** audit-service persists it, **Then** the `userId` field is recorded as the publishing service name.
4. **Given** a duplicate event is delivered by Kafka (at-least-once delivery), **When** audit-service processes it, **Then** exactly one record is written (idempotent consumer).

---

### User Story 3 - Authentication & Session Audit (Priority: P1)

A security officer can query a log of all authentication events — user logins, logouts, failed login attempts, password changes, and permission grants/revocations — to meet CMMC AU domain controls and support incident investigation.

**Why this priority**: Authentication audit is a CMMC Level 2 and 21 CFR Part 11 hard requirement. Keycloak already emits these events; consuming them completes the compliance picture without additional instrumentation.

**Independent Test**: Log in and log out via Keycloak. Call `GET /audit/auth-events?userId={id}` and confirm both events appear with IP address, timestamp, and event type.

**Acceptance Scenarios**:

1. **Given** a user logs in successfully, **When** Keycloak emits a LOGIN event, **Then** an AuthAuditRecord is created with userId, clientId, ipAddress, sessionId, and UTC timestamp.
2. **Given** a user fails to log in three times, **When** Keycloak emits LOGIN_ERROR events, **Then** three AuthAuditRecord entries are created with `details.error = invalid_user_credentials`.
3. **Given** an administrator changes a user's role assignment, **When** the Keycloak admin event is emitted, **Then** an AuthAuditRecord of type ROLE_MAPPING is persisted with the before/after role set.

---

### User Story 4 - Audit Trail Query API (Priority: P2)

A compliance auditor can query the centralised audit trail via a REST API, filtering by entity type, entity ID, user, action, and date range, and receive paginated results suitable for regulatory review or export.

**Why this priority**: The audit trail is only useful if it can be queried efficiently. This enables both automated compliance checks and human-driven investigations.

**Independent Test**: Seed 1,000 audit records spanning 30 days. Query with a 7-day date range filter and confirm response time under 2 seconds and correct pagination.

**Acceptance Scenarios**:

1. **Given** audit records exist for multiple entities, **When** a user calls `GET /audit/entities/{type}/{id}/history`, **Then** only records for that entity are returned, sorted descending by timestamp, with pagination metadata.
2. **Given** a date range filter is applied, **When** the query executes, **Then** only records within the range are returned and total-count reflects the filtered dataset.
3. **Given** the requestor does not have AUDIT_READ role, **When** they call any audit query endpoint, **Then** a 403 Forbidden response is returned with no data disclosed.
4. **Given** a 30-day query over a high-volume audit table, **When** executed, **Then** response time is under 2 seconds (covered by SC-003).

---

### User Story 5 - Tamper-Evidence Verification (Priority: P2)

A compliance officer can run a tamper-evidence check against any time window of audit records and receive a pass/fail result, ensuring that no audit record has been modified or deleted since it was written.

**Why this priority**: Tamper-evidence is an explicit requirement of 21 CFR Part 11 and EU Annex 11 §9. Without a verifiable integrity check, the audit trail cannot satisfy electronic records regulations.

**Independent Test**: Write 100 audit records, run a verification check (pass expected). Manually corrupt one record in the database, re-run check (fail expected with the affected record ID reported).

**Acceptance Scenarios**:

1. **Given** an unmodified sequence of audit records, **When** `POST /audit/verify?from={}&to={}` is called, **Then** the response is `{ "status": "PASS", "recordsChecked": N, "violations": [] }`.
2. **Given** one audit record has been modified externally, **When** verification runs, **Then** the response is `{ "status": "FAIL", "violations": [{ "recordId": X, "reason": "CHECKSUM_MISMATCH" }] }`.
3. **Given** a tamper-evidence failure is detected, **When** the audit-service logs the failure, **Then** a WARN-level log entry with structured fields (recordId, expectedChecksum, actualChecksum) is emitted.

---

### Edge Cases

- **Burst volume**: Bulk work order creation (e.g., 500 orders in one batch) must not cause audit event backpressure or dropped records; Kafka consumer group scaling handles bursts.
- **Kafka consumer lag on restart**: Consumer offsets are committed only after successful persistence; service restart must replay uncommitted offsets without duplication.
- **Missing userId**: System-scheduled jobs and background tasks have no JWT principal; service identity (e.g., `system:work-order-service`) must be substituted.
- **Large change payloads**: Document or BOM changes may produce multi-KB JSON diffs; payload fields must have a 64 KB limit with truncation flag set if exceeded.
- **Envers schema migration**: When an audited entity adds or removes a column, existing revision records must remain queryable without error (Envers handles this with ValidityAuditStrategy).
- **Database unavailable during audit write**: Kafka consumer must apply exponential backoff and not ACK the offset; alerting must fire after 3 consecutive failures.
- **Concurrent writes to same record**: Envers revision sequence must be guarded by optimistic locking; revision gaps must be reported as an anomaly.
- **Multi-org audit isolation**: With a single audit store, row-level filtering by `tenantId` must prevent cross-org data disclosure until full multi-org isolation is implemented.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST capture all INSERT, UPDATE, and DELETE lifecycle events for all `@Audited` JPA entities using Hibernate Envers.
- **FR-002**: Audit records MUST be persisted in an immutable PostgreSQL schema (`audit_trail`) where the application DB role has INSERT-only privileges — no UPDATE or DELETE.
- **FR-003**: All domain services MUST publish `AuditEventMessage` payloads to the Kafka topic `mes.audit.events` for business-significant events not captured by Envers.
- **FR-004**: The audit-service MUST consume `mes.audit.events` and persist records within 5 seconds under normal load (SLA: p95 ≤ 5 s).
- **FR-005**: Each audit record MUST include: `userId` (or service identity), `entityType`, `entityId`, `action` (CREATE/UPDATE/DELETE/PUBLISH/AUTH), `serviceSource`, `timestamp` (UTC, millisecond precision), `previousState` (JSON), `newState` (JSON), `checksum` (SHA-256 of canonical fields).
- **FR-006**: The audit-service MUST expose a REST API at `/audit/**` for querying by entity type, entity ID, user, action type, and date range, returning paginated results.
- **FR-007**: Audit records MUST be retained for a minimum of 7 years; no automated purge path may remove records within that window.
- **FR-008**: The audit-service MUST implement a tamper-evidence verification endpoint that validates per-record SHA-256 checksums against stored values.
- **FR-009**: The audit-service MUST consume Keycloak authentication events (via Keycloak Event Listener SPI or event stream) and persist them as `AuthAuditRecord` entries.
- **FR-010**: All audit query endpoints MUST enforce RBAC; callers must hold `AUDIT_READ` or `SYSTEM_ADMIN` role.
- **FR-011**: The Kafka consumer MUST be idempotent — duplicate event delivery must result in exactly one persisted record (deduplication by `eventId`).
- **FR-012**: The `AuditEventMessage` Kafka schema MUST be versioned; audit-service MUST handle schema version N and N-1 without error.
- **FR-013**: Audit query endpoints MUST return results within 2 seconds for a 30-day date range query against a table containing up to 10 million records (covered by database indexing on `entityType`, `entityId`, `timestamp`).
- **FR-014**: The audit-service MUST expose a `/actuator/health` endpoint that reports unhealthy when Kafka consumer lag exceeds a configurable threshold (default: 1,000 messages).

### Key Entities

- **AuditRecord**: Core immutable record. Fields: `id` (UUID), `eventType` (ENTITY_CHANGE | KAFKA_EVENT | AUTH_EVENT), `entityType` (String), `entityId` (String), `userId` (String), `serviceSource` (String), `action` (CREATE | UPDATE | DELETE | PUBLISH | AUTH), `timestamp` (OffsetDateTime), `previousState` (JSONB), `newState` (JSONB), `checksum` (String), `schemaVersion` (Integer).
- **EntityRevision**: Hibernate Envers custom `RevisionEntity`. Fields: `revisionId` (Long), `revisionTimestamp` (Long), `userId` (String), `serviceSource` (String). Links to all entity revision tables.
- **AuditEventMessage**: Kafka message DTO. Fields: `eventId` (UUID), `eventType` (String), `serviceSource` (String), `entityType` (String), `entityId` (String), `userId` (String), `timestamp` (OffsetDateTime), `action` (String), `payload` (Map<String,Object>), `schemaVersion` (Integer).
- **AuthAuditRecord**: Authentication and authorisation events from Keycloak. Fields: `id` (UUID), `eventType` (String), `userId` (String), `clientId` (String), `ipAddress` (String), `sessionId` (String), `timestamp` (OffsetDateTime), `details` (JSONB), `realmId` (String).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of INSERT, UPDATE, DELETE operations on all `@Audited` entities are captured in the audit trail with zero data loss under normal operation.
- **SC-002**: Kafka-published audit events are persisted with p95 latency ≤ 5 seconds under load of 500 events/minute.
- **SC-003**: Audit trail query API returns results in under 2 seconds for a 30-day window query against a 10-million-record table.
- **SC-004**: 100% of Keycloak authentication events (login, logout, login failure, role change) are recorded within 30 seconds of occurrence.
- **SC-005**: Tamper-evidence verification correctly identifies all modified records in a dataset of 10,000 records with zero false negatives and zero false positives.
- **SC-006**: Zero audit records are lost during a 60-second audit-service restart when Kafka consumer offset commit policy is `manual_ack`.
- **SC-007**: Audit records are retained indefinitely (minimum 7 years) with no automated purge path removing records within the retention window.
- **SC-008**: All audit API endpoints return HTTP 403 for callers without `AUDIT_READ` or `SYSTEM_ADMIN` role, verified by integration tests.

---

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D | Yes | §7.5 — Documented Information: all quality record mutations must be captured in the audit trail with revision history |
| AS9102 (FAI) | Partial | FAI record changes (e.g., characteristic actuals, sign-off) must be in scope for entity audit capture |
| AS9131 (NCM) | Partial | Non-conformance record lifecycle events (creation, disposition, closure) must be audited |
| NIST SP 800-171 / CMMC | Yes | AU domain controls: AU.2.041 (generate audit logs), AU.2.042 (ensure user accountability), AU.3.045 (review/protect audit logs), AU.3.046 (reduce/report audit logs) |
| 21 CFR Part 11 / Annex 11 | Yes | §11.10(e) — Secure, computer-generated, time-stamped audit trails; Annex 11 §9 — Audit trail generation and review for GxP systems |
| ISA-95 | No | ISA-95 does not mandate audit trail implementation; no direct applicability |

---

## Assumptions

- The `audit-service` is a new standalone Spring Boot microservice within the MikeMES service mesh; it does not share a JVM or database with any existing service.
- Hibernate Envers is added as a shared library concern — each domain service adds `@Audited` to its entities and includes the Envers dependency; the `EntityRevision` entity and Kafka publisher are provided via a shared `audit-client` library.
- PostgreSQL is the only supported audit persistence store for v1; no NoSQL or object-store backend.
- Kafka is already operational in the service mesh (established by the platform infrastructure Epic MES-6).
- Keycloak 24+ is the authentication provider (established by IAM Epic MES-5); the Keycloak Event Listener SPI is available for authentication event streaming.
- The 7-year retention requirement maps to a Flyway-managed partitioned table or a retention policy enforced via a scheduled job that archives — but does not delete — records older than 7 years.
- Multi-tenancy / multi-org audit isolation is deferred (DEF-002); v1 uses a single audit store with `tenantId` column for future row-level filtering.
- The `audit-client` shared library publishes to Kafka synchronously from within the domain service transaction boundary; Kafka `acks=all` ensures durability before the domain transaction commits.

---

## Deferred Decisions *(mandatory — do not leave blank)*

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Real-time audit dashboard / UI | Requires dedicated frontend investment; compliance requires only a queryable API | Auditors must use raw API or export tools; no self-service UI | P3 | — |
| DEF-002 | Per-tenant audit store isolation | Depends on IAM multi-org completion (MES-5); shared store with row-level filtering is acceptable for single-org v1 | Cross-org data disclosure risk if row-level filtering has a bug | P2 | — |
| DEF-003 | Audit event enrichment (geolocation, device fingerprint) | Not required by 21 CFR Part 11 or AS9100D; adds significant complexity | Reduced forensic detail for security investigations | Post-GA | — |
| DEF-004 | Automated anomaly detection on audit stream | Requires SIEM integration or ML pipeline; beyond current scope | Manual review only; sophisticated insider threats may go undetected longer | Post-GA | — |
| DEF-005 | Audit record archival to cold storage (S3/GCS) after 2 years | PostgreSQL table grows large over 7-year retention window; archival reduces hot DB size | Query performance degrades as table grows beyond ~100 M rows | P3 | — |
| DEF-006 | GraphQL audit query interface | REST API is sufficient for v1; GraphQL would allow more flexible compliance tooling | External compliance tools may require custom REST adapters | Post-GA | — |
