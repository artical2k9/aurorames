# Feature Specification: IAM & Multi-Org Security (Keycloak)

**Feature Branch**: `001-iam-multi-org-security-keycloak`

**Jira Epic**: [MES-5](https://artical.atlassian.net/browse/MES-5)

**Created**: 2026-05-20

**Status**: Draft

**Programme Phase**: P1 — Foundation (must complete before any domain Epic begins)

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Operator authenticates via the MES frontend (Priority: P1)

An operator opens the MikeMES Angular frontend and is redirected to the Keycloak login page. After entering valid credentials they are returned to the application with a JWT session. All subsequent API calls are made with the Bearer token. On session expiry the token is silently refreshed without requiring re-login.

**Why this priority**: No user can interact with any MES feature until authentication works. This is the outermost gate for the entire system.

**Independent Test**: A browser can reach the Keycloak login page, submit credentials, and receive a JWT that is accepted by the gateway-service health-check endpoint. Delivers a working login flow demonstrable end-to-end.

**Acceptance Scenarios**:

1. **Given** a valid Keycloak user with role OPERATOR exists, **When** the user navigates to the MES frontend, **Then** they are redirected to the Keycloak login page within 500 ms.
2. **Given** the user submits correct credentials, **When** Keycloak processes the login, **Then** the frontend receives an access token and refresh token and redirects to the MES dashboard.
3. **Given** an active session, **When** the access token is within 30 seconds of expiry, **Then** the frontend silently obtains a new access token via the refresh token without user interaction.
4. **Given** a refresh token has expired, **When** the frontend attempts a silent refresh, **Then** the user is redirected to the Keycloak login page.
5. **Given** incorrect credentials, **When** the user submits the login form, **Then** Keycloak returns an error and the frontend displays a user-friendly "Invalid credentials" message.

---

### User Story 2 — Administrator defines roles and assigns module privileges (Priority: P1)

A system administrator uses the MES IAM module to create custom roles (beyond the six defaults), and for each role assigns or revokes individual privileges drawn from a registry of capabilities published by every installed MES module. This drives what each role can and cannot do within the application — no code changes are required when the privilege set changes.

**Why this priority**: The privilege model must be in place before any domain modules are built, because each module registers its own privileges at startup. Getting this right in P1 prevents retrofitting permission checks across all services later.

**Independent Test**: A SYSTEM_ADMIN user creates a custom role `SENIOR_INSPECTOR`, assigns it the privilege `quality:inspection:sign-off` but not `quality:ncm:raise`. A user with that role can call the inspection sign-off endpoint (200) but not the NCM raise endpoint (403).

**Acceptance Scenarios**:

1. **Given** an authenticated SYSTEM_ADMIN user, **When** they POST `/iam/roles` with `{ "name": "SENIOR_INSPECTOR", "description": "..." }`, **Then** the new role is created in the privilege registry and in Keycloak, and is immediately available for assignment to users.
2. **Given** a role exists, **When** the SYSTEM_ADMIN opens the role detail screen, **Then** the UI displays all available privileges grouped by module (e.g., Work Orders, Quality, Inventory) with checkboxes indicating which are currently granted.
3. **Given** a SYSTEM_ADMIN grants privilege `quality:inspection:sign-off` to role `SENIOR_INSPECTOR`, **When** a user with that role calls the sign-off endpoint, **Then** the request succeeds (200). When a user with role `OPERATOR` (without that privilege) calls the same endpoint, HTTP 403 is returned.
4. **Given** a SYSTEM_ADMIN revokes privilege `work-orders:release` from role `PLANNER`, **When** the next user token refresh completes (privilege cache TTL ≤ 60 s), **Then** PLANNER users can no longer call the WO release endpoint.
5. **Given** the SYSTEM_ADMIN attempts to delete a role that is currently assigned to one or more users, **When** the DELETE is submitted, **Then** the system returns HTTP 409 with a message listing the count of affected users; the role is not deleted.
6. **Given** all registered module privileges are listed in the IAM UI, **When** a new module starts up and registers its privileges, **Then** those privileges appear in the UI within one Kafka event processing cycle (≤ 5 s) without requiring an IAM service restart.

---

### User Story 3 — Microservice rejects unauthenticated and unauthorised requests (Priority: P1)

Any HTTP request to a MES microservice endpoint that lacks a valid JWT, or where the authenticated user's role does not carry the required privilege for that endpoint, is rejected before any business logic executes.

**Why this priority**: Security posture of the entire system depends on every service enforcing this gate. A single unprotected service exposes all data.

**Independent Test**: Using curl, send a request without a token (expect 401) and with a token for a role that lacks the required privilege (expect 403). Independently verifiable on any single service.

**Acceptance Scenarios**:

1. **Given** a request with no Authorization header, **When** any secured endpoint is called, **Then** the service returns HTTP 401 with a JSON error body containing `error: "unauthorized"` within 50 ms.
2. **Given** a request with an expired JWT, **When** a secured endpoint is called, **Then** the service returns HTTP 401 with `error: "token_expired"`.
3. **Given** a request with a valid JWT whose role does not carry the endpoint's required privilege, **When** the endpoint is called, **Then** the service returns HTTP 403 with `error: "forbidden"` and the missing privilege name. No stack trace is included in the response.
4. **Given** a request with a valid JWT whose issuer does not match the configured Keycloak realm, **When** any endpoint is called, **Then** the service returns HTTP 401.
5. **Given** Keycloak is temporarily unavailable, **When** a request arrives with a previously-seen JWT, **Then** the service validates the JWT signature using its cached JWKS public key and resolves privileges from the local cache, processing the request normally.

---

### User Story 3 — Administrator manages users and roles via the MES IAM UI (Priority: P1)

A system administrator uses a dedicated IAM section of the MES frontend to invite users, assign them to an organisation, and grant or revoke application roles without needing direct Keycloak console access.

**Why this priority**: Customer sites will not have Keycloak admin expertise. User management must be accessible through the MES application.

**Independent Test**: Admin creates a new user, assigns them OPERATOR role and an organisation, then that user can log in and access operator-scoped endpoints.

**Acceptance Scenarios**:

1. **Given** an authenticated SYSTEM_ADMIN user, **When** they submit an invite form with email and organisation, **Then** iam-service calls the Keycloak Admin REST API to create the user and sends a welcome/set-password email.
2. **Given** a user exists in Keycloak, **When** the SYSTEM_ADMIN assigns them role QUALITY_INSPECTOR for organisation ORG-1, **Then** the user's next JWT contains `roles: ["QUALITY_INSPECTOR"]` and `org_id: "ORG-1"`.
3. **Given** a user is deactivated by a SYSTEM_ADMIN, **When** the user attempts to authenticate, **Then** Keycloak rejects the login and the user receives an "account disabled" message.
4. **Given** a role is revoked from a user, **When** the user's current session token expires and they refresh, **Then** the new token no longer carries the revoked role.

---

### User Story 4 — Multi-organisation tenant isolation (Priority: P1)

Two separate manufacturing companies (Org A and Org B) are both onboarded as organisations within the same MikeMES deployment. A user belonging to Org A cannot read, write, or list any data belonging to Org B, regardless of their role level.

**Why this priority**: Tenant isolation is a hard security and commercial requirement. Data leakage between organisations is a critical defect.

**Independent Test**: Create two organisations and a user in each. Log in as Org A user and call an endpoint; verify the response contains only Org A records. Then attempt to request an Org B record by ID; verify 403 or 404.

**Acceptance Scenarios**:

1. **Given** user Alice belongs to Org A and user Bob belongs to Org B, **When** Alice calls `GET /work-orders`, **Then** the response contains only Org A work orders.
2. **Given** Alice knows a valid Org B Work Order ID, **When** she calls `GET /work-orders/{orgBId}`, **Then** the service returns HTTP 404 (not 403, to avoid confirming resource existence).
3. **Given** a JWT is crafted with a tampered `org_id` claim, **When** any endpoint is called with that token, **Then** JWT signature validation fails and the service returns HTTP 401.
4. **Given** a new organisation is provisioned, **When** the first admin user for that org logs in, **Then** their JWT contains the correct `org_id` claim and all data operations are scoped to that organisation.

---

### User Story 5 — Service-to-service M2M authentication (Priority: P2)

The work-order-service calls the inventory-service to check stock availability during WO release. This call uses a machine-to-machine client credentials token, not the originating user's token.

**Why this priority**: Required for internal microservice communication but can follow once human-facing auth is stable.

**Independent Test**: Call the inventory-service stock endpoint using a client credentials token issued to `work-order-service` client; verify 200 response. Call with a human user token that lacks the expected audience; verify rejection.

**Acceptance Scenarios**:

1. **Given** work-order-service has a Keycloak client with `inventory:read` scope, **When** it calls the Keycloak token endpoint using Client Credentials, **Then** it receives a valid M2M JWT within 200 ms.
2. **Given** the M2M JWT is presented to inventory-service, **When** the endpoint validates the token, **Then** the `aud` claim matches `inventory-service` and the request is processed.
3. **Given** a human user's JWT is presented to an M2M-only endpoint, **When** the endpoint validates the token, **Then** the request is rejected (HTTP 403) because the `azp` claim identifies a user client, not a service client.

---

### User Story 6 — Developer onboards a new microservice using lib-common-security (Priority: P2)

A developer creating a new Spring Boot microservice adds the `lib-common-security` Gradle dependency and applies one configuration annotation. The service is immediately protected with JWT validation, role-based access, and organisation context propagation.

**Why this priority**: Developer experience reduces the risk of misconfigured security in individual services. Follows after lib-common-security is defined.

**Independent Test**: Create a minimal Spring Boot service, add lib-common-security, annotate the main class, write one @PreAuthorize-guarded endpoint, run the Testcontainers-based integration test; verify it passes without additional security boilerplate.

**Acceptance Scenarios**:

1. **Given** a new Spring Boot service adds `implementation("com.mikemes:lib-common-security")` and `@EnableMikeMESSecurity`, **When** the application starts, **Then** all actuator/health endpoints are public and all other endpoints require a valid JWT.
2. **Given** an endpoint annotated `@PreAuthorize("hasPrivilege('quality:inspection:sign-off')")`, **When** called with a user whose role carries that privilege, **Then** the request succeeds; **When** called by a user whose role does not carry that privilege, **Then** HTTP 403 is returned.
3. **Given** `OrganisationContextHolder.getOrgId()` is called within a request thread, **When** the request carries a JWT with `org_id: "ORG-42"`, **Then** the method returns `"ORG-42"` without the service needing to parse the JWT manually.
4. **Given** the lib-common-security Testcontainers fixture is used in an integration test, **When** the test starts a real Keycloak container and obtains a test JWT, **Then** the service correctly validates the token and the test does not require mocked security.

---

### Edge Cases

- **Keycloak unavailable at startup**: If Keycloak JWKS endpoint is unreachable when a service starts, the service must start successfully and reject all requests with 503 until the JWKS cache is populated.
- **Org_id missing from JWT**: If a JWT contains no `org_id` claim (e.g., a misconfigured client), the service must reject the request with HTTP 401 and log a warning.
- **Role explosion**: A user with many roles must not cause JWT size issues; the JWT carries only role names (coarse), not the expanded privilege list; privilege resolution happens server-side from the cache.
- **JWKS key rotation**: When Keycloak rotates its signing keys, services must detect the new key via the `kid` header and re-fetch JWKS without restart.
- **Organisation switching**: If a user legitimately belongs to two organisations and needs to switch context, they must log out and log in selecting the correct organisation (v1 does not support session-level org switching).
- **Brute-force login**: Keycloak's built-in brute-force detection must be enabled; after 5 failed attempts in 60 seconds the account is temporarily locked; unlock after 15 minutes or by SYSTEM_ADMIN action.
- **Leaked client secret**: Each service client secret must be stored in Docker secrets or environment variables; if a secret is rotated in Keycloak the affected service must pick it up via config reload without full redeploy.
- **Privilege name collision**: Two modules register a privilege with the same string key (e.g., both quality-service and receiving-service register `inspection:approve`). The privilege registry must namespace privileges by module prefix (e.g., `quality:inspection:approve`, `receiving:inspection:approve`) to prevent collision. Each microservice declares its module prefix in its privilege manifest.
- **Role revocation mid-session**: If a role's privileges are changed by a SYSTEM_ADMIN, users with active tokens retain the old privilege set until the privilege cache expires (≤ 60 s). Endpoints with safety-critical write consequences (electronic signature, WO release) must validate privilege freshness from the registry directly for those operations.
- **Module not yet started**: If a domain microservice has not yet registered its privileges (e.g., during initial deployment), its privileges are absent from the registry. The IAM UI must display unregistered modules as "pending" rather than hiding them.
- **Default role privilege seeding**: On first deployment the six default roles must be pre-seeded with a sensible privilege baseline (defined in the `iam-service` seed migration). The baseline is the starting point; it is fully overridable by a SYSTEM_ADMIN without code changes.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST use Keycloak 25+ as the sole identity provider; no bespoke authentication implementation is permitted (Constitution §VII).
- **FR-002**: System MUST implement OIDC Authorization Code flow with PKCE for the Angular frontend.
- **FR-003**: System MUST implement OAuth 2.0 Client Credentials flow for all service-to-service communication.
- **FR-004**: All 18 microservices MUST be configured as Spring Security OAuth2 resource servers validating RS256-signed JWTs issued by Keycloak.
- **FR-005**: JWT validation MUST verify: signature, issuer (`iss`), audience (`aud`), expiry (`exp`), and not-before (`nbf`) claims. Validation failures MUST return HTTP 401.
- **FR-006**: Every database query in every microservice MUST be scoped to the `org_id` claim extracted from the authenticated JWT. Cross-organisation queries MUST be architecturally impossible at the repository layer.
- **FR-007**: The system MUST ship with six default application roles: `SYSTEM_ADMIN`, `OPERATOR`, `QUALITY_INSPECTOR`, `PLANNER`, `ENGINEER`, `VIEWER`. These defaults are a starting baseline; a SYSTEM_ADMIN user MUST be able to create additional custom roles and modify the privilege assignments of all roles (including the defaults) through the application — no code change or Keycloak console access is required. Roles are mirrored as Keycloak realm roles and emitted as a custom `roles` JWT claim.
- **FR-008**: Access control on every secured endpoint MUST be expressed as a named privilege string (e.g., `work-orders:release`, `quality:inspection:sign-off`) declared by the owning microservice, not as a hardcoded role name. `lib-common-security` MUST provide a `@RequiresPrivilege("module:resource:action")` annotation and a Spring Security `PrivilegeGrantedAuthority` resolver that evaluates the privilege against the authenticated user's role-privilege mapping.
- **FR-009**: Service-to-service calls MUST use M2M tokens (Client Credentials grant). A human user JWT MUST NOT be propagated between microservices.
- **FR-010**: `lib-common-security` MUST provide: `JwtClaimsExtractor` (typed claim access), `OrganisationContextHolder` (thread-local org_id), `@EnableMikeMESSecurity` (autoconfiguration annotation), `@RequiresPrivilege` (privilege-based method security), `PrivilegeRegistryClient` (HTTP client used at startup to register module privileges with iam-service), and `KeycloakTestSupport` (Testcontainers Keycloak + IAM fixture that wires the privilege registry for integration tests).
- **FR-011**: Keycloak realm configuration MUST be defined as an exportable realm JSON file committed to the repository. All client secrets MUST be externalised to environment variables — no secrets in the realm export file.
- **FR-012**: Keycloak MUST be configured to publish authentication events (login, logout, login failure, token refresh) to a Kafka topic `iam.events` consumed by audit-service.
- **FR-013**: `iam-service` MUST expose REST endpoints (secured by SYSTEM_ADMIN role) for: create user, update user role assignment, deactivate user, list users by organisation. All user-level operations delegate to the Keycloak Admin REST API.
- **FR-014**: The system MUST support SSO across all MES frontend modules within a single browser session (one Keycloak session, multiple Angular route groups).
- **FR-015**: Access token TTL MUST be configurable per environment (default: 5 minutes). Refresh token rotation MUST be enabled with a sliding window TTL (default: 8 hours).
- **FR-016**: Keycloak brute-force detection MUST be enabled: account locked after 5 failed attempts within 60 seconds; unlock after 15 minutes or by SYSTEM_ADMIN action.
- **FR-017**: Each microservice MUST declare a **privilege manifest** — a static list of privilege strings it owns — using the format `{module}:{resource}:{action}` (e.g., `work-orders:order:create`, `quality:inspection:sign-off`). The manifest is registered with `iam-service` via `PrivilegeRegistryClient` on application startup. `iam-service` stores the union of all registered privileges in its `privilege_registry` table.
- **FR-018**: `iam-service` MUST expose REST endpoints (secured by SYSTEM_ADMIN role) for role-privilege management: list all available privileges (grouped by module), get privileges assigned to a role, assign a privilege to a role, revoke a privilege from a role, create a role, rename a role, delete a role (blocked if role has active user assignments).
- **FR-019**: Role-privilege assignments MUST be persisted in the `iam-service` PostgreSQL schema (`role_privilege` table). The table is the source of truth; Keycloak holds only coarse role names. Flyway manages schema versioning; the seed migration populates the default role-privilege baseline.
- **FR-020**: `lib-common-security` MUST cache the role → privilege set mapping locally (in-process, TTL default 60 s, configurable). On each request it resolves the user's roles from the JWT `roles` claim, looks up the cached privilege sets, and evaluates the union. On cache miss it fetches from `iam-service` via an internal REST call. The cache MUST be invalidated by a Kafka event published by `iam-service` whenever a role-privilege assignment changes.
- **FR-021**: The privilege cache TTL and the Kafka invalidation topic name (`iam.privilege-changes`) MUST be configurable per service via Spring application properties.

### Key Entities

- **Realm**: The top-level Keycloak namespace (`mikemes`). One realm for all organisations; multi-tenancy is via group claim, not realm isolation.
- **Organisation**: A tenant. Represented as a Keycloak group; members of the group receive `org_id` in their JWT via a group attribute mapper.
- **User**: A person with Keycloak credentials. Belongs to exactly one organisation group in v1. Assigned one or more ApplicationRoles.
- **ApplicationRole**: A named role (e.g., `QUALITY_INSPECTOR`). The six defaults ship pre-seeded; additional roles can be created by a SYSTEM_ADMIN at runtime. Stored in both Keycloak (coarse, for JWT emission) and the `iam-service` `role` table (for privilege mapping). JWT `roles` claim carries the role name(s).
- **Privilege**: A named capability string in the form `{module}:{resource}:{action}` (e.g., `quality:inspection:sign-off`). Owned and registered by the module's microservice. Stored in the `privilege_registry` table in `iam-service`. Privileges are immutable by end users — only the owning microservice defines them.
- **RolePrivilegeAssignment**: A many-to-many join between ApplicationRole and Privilege. Stored in the `role_privilege` table. Created/deleted at runtime by a SYSTEM_ADMIN via the IAM UI. Published to Kafka `iam.privilege-changes` on every change.
- **PrivilegeManifest**: A static list of privilege strings declared by a microservice and registered at startup via `PrivilegeRegistryClient`. Ensures the `privilege_registry` table always reflects the current set of capabilities across all running services.
- **OidcClient**: A Keycloak client registration. One per microservice (M2M, `confidential`) plus one for the Angular frontend (public, PKCE).
- **JWT**: Access token issued by Keycloak. Mandatory claims: `sub`, `iss`, `exp`, `org_id`, `roles`. Signed RS256. Does **not** carry privileges — privilege resolution is server-side.
- **M2MToken**: Client Credentials token for service-to-service use. Claims: `client_id`, `scope`, `aud`. No `org_id` — services propagate org context in an `X-Org-Id` request header for M2M calls that need it.
- **AuditEvent**: Keycloak event published to Kafka `iam.events` topic; consumed by audit-service and persisted to the immutable audit log.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An OPERATOR user completes login from the Angular frontend and reaches the MES dashboard in under 5 seconds on a local network.
- **SC-002**: A secured endpoint returns HTTP 401 for a missing or expired JWT in under 50 ms (no database hit).
- **SC-003**: Integration tests confirm that a user in Org A cannot retrieve any record belonging to Org B across all microservice repository layers (zero cross-org data leakage).
- **SC-004**: All 18 microservices return HTTP 401/403 for unauthenticated/unauthorised requests (verified by the shared `KeycloakTestSupport` integration test suite run in CI).
- **SC-005**: A developer follows the lib-common-security integration guide and has a new microservice secured with JWT + privilege checks in under 30 minutes.
- **SC-006**: The Keycloak realm is re-provisioned from the committed realm export JSON in a fresh Docker Compose environment in under 5 minutes.
- **SC-007**: Keycloak authentication events appear in the `iam.events` Kafka topic within 2 seconds of the triggering login or logout action.
- **SC-008**: A SYSTEM_ADMIN can create a new role, assign a module privilege to it, assign a user to that role, and verify the user can call the privileged endpoint — end-to-end in under 5 minutes using only the MES IAM UI (no Keycloak console access required).
- **SC-009**: A privilege revocation takes effect for all active sessions within the privilege cache TTL (≤ 60 s) without any service restart.
- **SC-010**: The IAM UI privilege matrix (roles × module privileges) renders all currently registered privileges across all online modules within 5 seconds of page load for a deployment with all 18 services running.

---

## Compliance References *(mandatory — see Constitution §IV)*

| Standard | Applicability | Key Requirements for This Feature |
|---|---|---|
| AS9100D §7.1.4 | Partial | Authorised personnel must have controlled access to the QMS system; RBAC roles map to authorised job functions |
| AS9100D §7.5 | No | Document control not in scope for this Epic |
| AS9102 (FAI) | No | Not applicable to IAM |
| AS9131 (NCM) | No | Not applicable to IAM |
| NIST SP 800-171 §3.1 (Access Control) | Yes | AC.1.001: limit system access to authorised users; AC.1.002: limit system access to types of transactions and functions that authorised users are permitted to execute; AC.2.006: use non-privileged accounts when accessing non-security functions |
| NIST SP 800-171 §3.5 (Identification & Authentication) | Yes | IA.1.076: identify system users and authenticate their identities; IA.1.077: use multifactor authentication (Keycloak OTP support must be available); IA.3.083: use replay-resistant authentication (JWT with exp/nbf enforces this) |
| CMMC Level 2 | Yes | All AC and IA practice families from NIST SP 800-171 apply; Keycloak audit events satisfy AU practices |
| 21 CFR Part 11 §11.10(d) | Yes | System access must be limited to authorised individuals; the RBAC + org isolation design satisfies this |
| 21 CFR Part 11 §11.10(g) | Yes | Authority checks to ensure only authorised individuals can use the system, electronically sign a record, access the system — satisfied by privilege-based enforcement; the privilege `iam:esig:sign` gates electronic signature actions |
| EU Annex 11 §12 | Yes | Security controls for computerised systems; access management records must be maintained (audit trail via Kafka → audit-service) |
| ISA-95 Part 2 | Partial | Personnel model — system must know who is performing each action; JWT `sub` provides the person identity for all ISA-95 activity records |
| ISA-95 Part 3 | No | Work order management — not in scope for this Epic |
| MTConnect | No | Machine data — not applicable |
| IPC-2591 | No | Not applicable |
| ATA Spec 2000 | No | Not applicable |
| ISO 10303 (STEP) | No | Not applicable |
| QIF ISO 23952 | No | Not applicable |
| OAGIS | No | Not applicable |

---

## Assumptions

- **Single realm**: One Keycloak realm (`mikemes`) serves all organisations. Tenant isolation is achieved via `org_id` group attribute claim, not separate realms. Realm-per-tenant adds operational complexity disproportionate to the benefit for the initial deployment scale.
- **Single organisation per user (v1)**: A user belongs to exactly one organisation. Multi-org membership (e.g., a shared quality consultant) is deferred to a future release.
- **Frontend OIDC library**: The Angular frontend will use `angular-oauth2-oidc` (or equivalent actively-maintained library) for the Authorization Code + PKCE flow. The Keycloak JS adapter is not preferred for Angular 19 Signals architecture.
- **On-premises deployment**: Keycloak runs in a single Docker container managed by Portainer. Keycloak HA (active-active cluster) is out of scope for v1; planned for production hardening.
- **Keycloak database**: Keycloak uses a dedicated PostgreSQL schema (`keycloak`) within the same PostgreSQL 16 instance used by domain services. It is logically isolated (separate schema, separate credentials).
- **Email delivery**: Keycloak sends user-invite and password-reset emails. An SMTP relay (e.g., AWS SES or on-prem mail server) must be configured before user management features are usable. SMTP config is an operational concern, not delivered by this Epic.
- **MFA**: Keycloak OTP (TOTP) must be available as a policy option but is not mandated for all users in v1. CMMC Level 2 IA.3.083 will require MFA to be enforced for privileged accounts before CMMC certification; this is flagged for the operations runbook.
- **Privilege naming convention**: The `{module}:{resource}:{action}` format is a convention enforced by code review, not by the registry. Actions are lowercase verbs: `create`, `read`, `update`, `delete`, `approve`, `sign-off`, `release`, `reject`, `view`. A naming guide must be published alongside lib-common-security.
- **Default privilege baseline**: The seed Flyway migration in iam-service defines the starting role-privilege matrix for the six default roles. This baseline is a reasonable starting point for a single-site aerospace manufacturer; customers are expected to customise it before go-live.
- **Privilege granularity**: Each module Epic owner is responsible for defining their privilege strings as part of their spec. The IAM spec does not pre-define privileges for domain modules — only for IAM itself (e.g., `iam:users:create`, `iam:roles:manage`, `iam:esig:sign`).
- **Existing Jira child issues**: None — this is the first spec in the system.

---

## Deferred Decisions *(mandatory — do not leave blank)*

Every row below was a conscious in-scope decision excluded from this version. Each has a corresponding Jira Story (label: `deferred`) under MES-5 so it appears in the backlog and cannot be forgotten during future sprint planning.

| ID | Deferred Capability | Reason for Deferral | Impact if Never Addressed | Suggested Phase | Jira |
|---|---|---|---|---|---|
| DEF-001 | Multi-organisation session switching — user switches active org without re-login | Requires session-level org context management in gateway and all services; adds state that conflicts with stateless JWT design; low frequency use case for v1 customer base | Users legitimately working across two organisations must log out and back in to switch context — friction acceptable for v1 but not for shared-service organisations (e.g., a group quality manager) | P3 (after Shop Floor & Work Orders stable) | MES-23 |
| DEF-002 | Multi-organisation membership — a single user account belongs to more than one organisation simultaneously | One-org-per-user is enforced at Keycloak group level; supporting multiple orgs requires JWT to carry multiple `org_id` values and all services to handle org selection per request | Users who genuinely work across orgs (e.g., group audit manager, shared supplier representative) require separate accounts per org — duplicates credential management overhead | P3 (depends on DEF-001) | MES-24 |
| DEF-003 | MFA enforced for all users (TOTP/FIDO2 mandatory, not optional) | Keycloak OTP is available but mandating it requires UI guidance, recovery flows, and helpdesk procedures that are out of scope for the initial deployment | CMMC Level 2 IA.3.083 requires MFA for privileged access before certification. Without enforcement, CMMC audit will flag this as a gap | Pre-CMMC certification (P5 or Post-GA) | MES-25 |
| DEF-004 | Keycloak high-availability (active-active cluster) | Single on-premises Keycloak instance sufficient for initial deployment scale; HA requires Infinispan/JGroups cluster configuration and shared external DB session store | Keycloak downtime prevents all logins across all 18 services simultaneously — a full system outage. Acceptable for development; not acceptable for production SLA | Post-GA (production hardening) | MES-26 |
