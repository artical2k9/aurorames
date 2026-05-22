# Data Model: Platform & System Administration (MES-6)

**Branch**: `006-platform-system-administration`  
**Date**: 2026-05-23

---

## admin-service

**No persistent entities.** Spring Boot Admin Server stores registered instances in-memory. Instance registry is lost on restart — clients re-register automatically on their next heartbeat. This is acceptable for observability infrastructure.

No PostgreSQL schema, no Flyway migrations, no JPA entities in admin-service.

---

## platform-service

### Schema: `platform`

Flyway migration: `services/platform-service/src/main/resources/db/migration/V001__create_platform_schema.sql`

```sql
CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.system_configuration (
    id             BIGSERIAL       PRIMARY KEY,
    org_id         UUID            NOT NULL,
    config_key     VARCHAR(255)    NOT NULL,
    config_value   TEXT,
    description    VARCHAR(1000),
    active         BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by     VARCHAR(255),
    CONSTRAINT uq_syscfg_org_key UNIQUE (org_id, config_key)
);

CREATE INDEX idx_syscfg_org_id  ON platform.system_configuration (org_id);
CREATE INDEX idx_syscfg_org_key ON platform.system_configuration (org_id, config_key);
```

### Entity: `SystemConfiguration`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | Auto-generated surrogate key |
| `org_id` | `UUID` | NOT NULL | Extracted from JWT `org_id` claim; logical FK to `iam.organisations` (no DB FK — cross-schema) |
| `config_key` | `VARCHAR(255)` | NOT NULL | Dot-notation recommended: `e.g. quality.tolerance.default` |
| `config_value` | `TEXT` | nullable | JSON, plain string, or numeric — caller interprets |
| `description` | `VARCHAR(1000)` | nullable | Human-readable purpose |
| `active` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | Soft-delete flag |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Immutable after creation |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT NOW() | Updated on every PUT |
| `created_by` | `VARCHAR(255)` | nullable | Keycloak `sub` claim at creation time |

**Unique constraint**: `UNIQUE(org_id, config_key)` — one value per key per org.

**Soft delete**: DELETE endpoint sets `active = false`, does not remove the row.

### ISA-95 Part 2 Mapping

`SystemConfiguration` maps to **Resource Management** configuration objects at ISA-95 Level 3. The closest standardised concept is `OperationalLocation` property or `ProcessSegmentParameter` used as a free-form operational parameter store. The key/value structure intentionally avoids premature schema specificity — domain services declare their own config keys as string constants and read them via the platform-service internal API.

---

## iam-service — Privilege Seeds (new Flyway migration)

Flyway migration: `services/iam-service/src/main/resources/db/migration/V005__seed_platform_module_privileges.sql`

Two new rows in `iam.privilege`:

| `name` | `module` | `description` |
|---|---|---|
| `platform:config:manage` | `platform` | Create, update, and delete platform configuration entries |
| `platform:config:read` | `platform` | Read platform configuration entries |

Both assigned to the `ADMIN` role via `iam.role_privilege_assignment`.

---

## No entity changes in gateway-service

Gateway is stateless — routing config only. No DB schema changes.
