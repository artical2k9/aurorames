# Data Model: IAM & Multi-Org Security (Keycloak)

**Branch**: `001-iam-multi-org-security-keycloak` | **Date**: 2026-05-20

Schema: `iam` (owned by `iam-service`). Keycloak uses schema `keycloak` (separate credentials, managed by Keycloak itself).

---

## Entity Relationship Summary

```
organisation ──< role ──< role_privilege >── privilege
     │               │
     └──< user (Keycloak) ──< user_role (Keycloak realm role assignment)
```

All tables in schema `iam` carry `created_at`, `created_by`, `updated_at`, `updated_by` audit columns (populated by `lib-common-audit` base entity). Hibernate Envers auditing enabled on `role`, `role_privilege`, `organisation`.

---

## Tables

### `organisation`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, default gen_random_uuid() | |
| `name` | VARCHAR(200) | NOT NULL | Display name |
| `keycloak_group_id` | VARCHAR(100) | NOT NULL, UNIQUE | Keycloak group UUID |
| `keycloak_group_name` | VARCHAR(100) | NOT NULL | e.g., `org-{uuid}` |
| `is_active` | BOOLEAN | NOT NULL, default true | Soft-delete flag |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `created_by` | VARCHAR(200) | NOT NULL | Keycloak `sub` of creator |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_by` | VARCHAR(200) | NOT NULL | |

**Indexes**: `UNIQUE(keycloak_group_id)`

**ISA-95 mapping**: Partial — Organisation is not directly in ISA-95 Part 2 object model; closest is the Enterprise level of the ISA-95 hierarchy (Level 4). Treated as a tenancy container.

---

### `role`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `name` | VARCHAR(100) | NOT NULL | Unique per org; matches Keycloak realm role name |
| `description` | VARCHAR(500) | | |
| `org_id` | UUID | FK → organisation.id, NOT NULL | Scoped to organisation |
| `is_system_role` | BOOLEAN | NOT NULL, default false | True for the 6 default roles |
| `keycloak_role_id` | VARCHAR(100) | | Keycloak realm role UUID (populated after sync) |
| `created_at` | TIMESTAMPTZ | NOT NULL | |
| `created_by` | VARCHAR(200) | NOT NULL | |
| `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `updated_by` | VARCHAR(200) | NOT NULL | |

**Indexes**: `UNIQUE(name, org_id)`

**Constraints**:
- `is_system_role = true` records may not be deleted (enforced at service layer).
- `name` must match `[A-Z_]{1,100}` (uppercase snake-case, enforced by service layer).

**Envers audit**: Yes — captures role create, rename, delete with actor identity.

---

### `privilege`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `privilege_key` | VARCHAR(200) | NOT NULL, UNIQUE | Format: `{module}:{resource}:{action}` |
| `module_name` | VARCHAR(100) | NOT NULL | Owning module (e.g., `quality`) |
| `description` | VARCHAR(500) | | Human-readable |
| `registered_at` | TIMESTAMPTZ | NOT NULL | Last registration timestamp |
| `registered_by_service` | VARCHAR(100) | NOT NULL | Service name (e.g., `quality-service`) |

**Indexes**: `UNIQUE(privilege_key)`, `INDEX(module_name)`

**Notes**:
- Global table — privileges are not org-scoped. A privilege exists once in the registry; its assignment to a role is org-scoped via `role_privilege`.
- Populated by module self-registration via `POST /privileges/register`. Upsert on `privilege_key`.
- Not Envers-audited (registration is operational, not business action). Registration events logged to application log only.

---

### `role_privilege`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `role_id` | UUID | FK → role.id, NOT NULL | |
| `privilege_id` | UUID | FK → privilege.id, NOT NULL | |
| `org_id` | UUID | FK → organisation.id, NOT NULL | Denormalised for fast org-scoped queries |
| `granted_at` | TIMESTAMPTZ | NOT NULL | |
| `granted_by` | VARCHAR(200) | NOT NULL | Keycloak `sub` of ADMIN who granted |
| `revoked_at` | TIMESTAMPTZ | | Null if currently active |
| `revoked_by` | VARCHAR(200) | | |

**Indexes**: `UNIQUE(role_id, privilege_id)` (only one active assignment per pair), `INDEX(org_id)`, `INDEX(role_id)`

**Notes**:
- Soft-delete via `revoked_at` — retains history. Active assignments: `WHERE revoked_at IS NULL`.
- Every insert/update publishes a `PrivilegeChangeEvent` to Kafka `iam.privilege-changes`.

**Envers audit**: Yes — full history of who granted/revoked each role-privilege assignment.

---

## Domain Entities (Java)

### `Role` (JPA entity, schema `iam`)

```java
@Entity @Table(name = "role", schema = "iam")
@Audited  // Hibernate Envers
public class Role extends AuditableEntity {   // from lib-common-audit
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false) private String name;
    private String description;
    @Column(name = "org_id", nullable = false) private UUID orgId;
    @Column(name = "is_system_role") private boolean systemRole;
    @Column(name = "keycloak_role_id") private String keycloakRoleId;
    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    private List<RolePrivilegeAssignment> privilegeAssignments;
}
```

### `Privilege` (JPA entity)

```java
@Entity @Table(name = "privilege", schema = "iam")
public class Privilege {
    @Id @GeneratedValue private UUID id;
    @Column(name = "privilege_key", unique = true, nullable = false) private String privilegeKey;
    @Column(name = "module_name", nullable = false) private String moduleName;
    private String description;
    @Column(name = "registered_at") private Instant registeredAt;
    @Column(name = "registered_by_service") private String registeredByService;
}
```

### `RolePrivilegeAssignment` (JPA entity)

```java
@Entity @Table(name = "role_privilege", schema = "iam")
@Audited
public class RolePrivilegeAssignment extends AuditableEntity {
    @Id @GeneratedValue private UUID id;
    @ManyToOne @JoinColumn(name = "role_id") private Role role;
    @ManyToOne @JoinColumn(name = "privilege_id") private Privilege privilege;
    @Column(name = "org_id", nullable = false) private UUID orgId;
    @Column(name = "granted_at") private Instant grantedAt;
    @Column(name = "granted_by") private String grantedBy;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "revoked_by") private String revokedBy;

    public boolean isActive() { return revokedAt == null; }
}
```

---

## lib-common-security Privilege Model

```java
// Immutable value object — not persisted
public record PrivilegeManifest(String moduleName, List<PrivilegeDefinition> privileges) {
    public static PrivilegeManifest of(String module, List<PrivilegeDefinition> privs) { ... }
}

public record PrivilegeDefinition(String key, String description) {
    // key format enforced: "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$"
}

// GrantedAuthority implementation used by Spring Security
public final class PrivilegeGrantedAuthority implements GrantedAuthority {
    private final String privilege; // e.g., "quality:inspection:sign-off"
    @Override public String getAuthority() { return privilege; }
}
```

---

## Flyway Migrations (iam-service)

| Version | Description |
|---------|-------------|
| V001 | Create schema `iam`, tables `organisation`, `role`, `privilege`, `role_privilege` |
| V002 | Seed 6 default system roles (`ADMIN`, `OPERATOR`, `QUALITY_INSPECTOR`, `PLANNER`, `ENGINEER`, `VIEWER`) for all org_ids — uses a seed function |
| V003 | Seed default role-privilege baseline for IAM module privileges (`iam:users:create`, `iam:users:view`, `iam:roles:manage`, `iam:esig:sign`) |
| V004 | Create Envers audit schema tables (`iam_aud`, `revinfo`) |

**Note**: Domain-module privilege seeds (e.g., `quality:*`, `work-orders:*`) are NOT in iam-service migrations. Each domain service registers its own privileges at startup via the REST API.

---

## Keycloak Configuration (realm export — non-secret fields)

```json
{
  "realm": "mikemes",
  "enabled": true,
  "ssoSessionMaxLifespan": 28800,
  "accessTokenLifespan": 300,
  "refreshTokenMaxReuse": 0,
  "revokeRefreshToken": true,
  "bruteForceProtected": true,
  "failureFactor": 5,
  "waitIncrementSeconds": 900,
  "eventsEnabled": true,
  "eventsListeners": ["jboss-logging", "http-sender"],
  "clientScopes": [{
    "name": "mikemes-claims",
    "protocol": "openid-connect",
    "attributes": { "include.in.token.scope": "true" },
    "protocolMappers": [
      {
        "name": "org_id-mapper",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-group-membership-mapper",
        "config": { "claim.name": "org_id", "attribute.name": "org_id", "full.path": "false" }
      },
      {
        "name": "roles-mapper",
        "protocol": "openid-connect",
        "protocolMapper": "oidc-usermodel-realm-role-mapper",
        "config": { "claim.name": "roles", "multivalued": "true", "jsonType.label": "String" }
      }
    ]
  }]
}
```

Secrets (client secrets, webhook token) are externalised to Docker Compose `.env` / Docker secrets — not in the exported JSON.
