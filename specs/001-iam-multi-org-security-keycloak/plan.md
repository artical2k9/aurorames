# Implementation Plan: IAM & Multi-Org Security (Keycloak)

**Branch**: `001-iam-multi-org-security-keycloak` | **Date**: 2026-05-20
**Spec**: [spec.md](spec.md) | **Jira**: MES-5

---

## Summary

Implement the foundation security layer for MikeMES: Keycloak 25+ as the sole IdP, a privilege-based access control system (coarse roles in JWT, fine-grained privileges resolved server-side from a Caffeine cache backed by `iam-service`), multi-organisation tenant isolation via `org_id` JWT claim, and `lib-common-security` as the shared library that every subsequent microservice uses to secure its endpoints in one annotation. Privileges are owned by each domain module, self-registered at startup, and cached in-process with Kafka-based invalidation — JWT size is constant regardless of how many custom roles or privileges exist.

---

## Technical Context

**Language/Version**: Java 21 LTS (Eclipse Temurin)

**Primary Dependencies**:
- `spring-boot-starter-oauth2-resource-server` — JWT RS256 validation on all microservices
- `spring-boot-starter-security` — Security filter chain, `@PreAuthorize`
- `spring-cloud-starter-gateway` — External API gateway (gateway-service)
- `keycloak-admin-client` 25+ — Keycloak Admin REST API calls from iam-service
- `caffeine` 3.x — In-process privilege cache (PrivilegeCache)
- `spring-kafka` — Kafka consumer for privilege-change cache invalidation
- `springdoc-openapi-starter-webmvc-ui` — OpenAPI 3.1 doc generation
- `lib-common-security` (new — this feature) — Shared security library
- `lib-common-audit` — Hibernate Envers audit trail base classes
- `lib-common-events` — Kafka topic name constants and event schemas
- `lib-common-test` — Shared Testcontainers base configuration
- `dasniko/testcontainers-keycloak` — Real Keycloak container for integration tests

**Storage**: PostgreSQL 16, schema `iam` (owned by iam-service). Keycloak uses schema `keycloak` (managed by Keycloak itself, separate DB credentials).

**Testing**: JUnit 5 + Mockito (unit), Testcontainers with real Keycloak + PostgreSQL + Kafka (integration). No persistence mocking per Constitution §II.

**Target Platform**: Docker container, on-premises Linux, managed via Portainer. Single Keycloak instance (no HA in v1).

**Project Type**: One new microservice (`iam-service`) + one new shared library (`lib-common-security`).

**Performance Goals**:
- JWT validation (resource server, JWKS cache hit): ≤ 50 ms
- Privilege cache lookup (Caffeine in-process): ≤ 5 ms
- Privilege cache warm-up on startup (batch fetch): ≤ 2 s
- User login end-to-end (browser → Keycloak → dashboard): ≤ 3 s on local network
- Privilege revocation propagation via Kafka: ≤ 5 s; TTL fallback: ≤ 60 s

**Constraints**:
- No bespoke authentication — Keycloak only (Constitution §VII)
- JWT MUST NOT carry the privilege list — only coarse role names (design decision confirmed in spec review and R-01)
- All data in `iam` schema scoped by `org_id` (Constitution §IX)
- All data mutations produce Hibernate Envers audit trail (Constitution §V)
- Integration tests use a real Keycloak Testcontainer — no security mocking (Constitution §II)

**Scale/Scope**: 18 microservices will import `lib-common-security`. Privilege registry: ~200–400 privilege strings across all domain modules. Potentially hundreds of custom roles across multiple organisations.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — all gates green.*

| Gate | Principle | Status |
|---|---|---|
| Does this feature have an approved spec before this plan was created? | I — Spec-First | ✅ PASS — spec.md committed and reviewed 2026-05-20 |
| Are test tasks listed BEFORE implementation tasks for every user story? | II — TDD | ✅ PASS — tasks.md will follow strict Red-Green-Refactor ordering |
| Is there a defect-registration step for test failures in the task list? | II — TDD | ✅ PASS — Compliance Verification & Defect Closure phase included in tasks.md |
| Has a human reviewed and approved this AI-generated plan? | III — AI-Approved | ⏳ PENDING — owner sign-off required before implementation begins |
| Does the spec include a "Compliance References" section? | IV — Compliance by Design | ✅ PASS — full compliance matrix in spec.md |
| Are all affected AS / ISA / NIST standards cited and addressed? | IV — Compliance by Design | ✅ PASS — NIST 800-171 §3.1/§3.5, CMMC L2, 21 CFR Part 11 §11.10(d)/(g), EU Annex 11 §12, AS9100D §7.1.4, ISA-95 Part 2 Personnel |
| Do all data mutations produce an audit log entry? | V — Auditability | ✅ PASS — Hibernate Envers on `role`, `role_privilege`, `organisation`; Keycloak auth events → Kafka `iam.events` → audit-service |
| Do new data models map to ISA-95 Part 2 object models (where applicable)? | VI — ISA-95/ISA-88 | ✅ PASS — User → ISA-95 Part 2 Personnel; Organisation → ISA-95 Enterprise level |
| Is authentication delegated to Keycloak (no bespoke auth)? | VII — Security-First | ✅ PASS — this Epic implements the Keycloak integration; no bespoke auth code anywhere |
| Is all data scoped by `organisation_id` with no cross-org leakage? | IX — Multi-Org Isolation | ✅ PASS — `role` and `role_privilege` carry `org_id` FK; all repository queries filtered by org; org_id extracted from JWT |
| Are integration endpoints idempotent and schema-validated? | VIII — Integration Integrity | ✅ PASS — `POST /privileges/register` is upsert (idempotent); OpenAPI 3.1 contract defines all request/response schemas; Keycloak webhook payload validated before processing |
| Are shop floor timestamps from source (not synthetic)? | X — Data Accuracy | N/A — IAM feature; no shop floor data |

**One gate pending**: Gate III (human plan approval). Implementation MUST NOT begin until the project owner approves this plan.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-iam-multi-org-security-keycloak/
├── spec.md                        ✅ approved
├── jira.json                      ✅ MES-5 traceability
├── plan.md                        ✅ this file
├── research.md                    ✅ R-01 to R-07
├── data-model.md                  ✅ schema + JPA entities + Flyway migrations
├── quickstart.md                  ✅ dev setup + lib-common-security onboarding guide
├── contracts/
│   └── iam-service-api.yaml       ✅ OpenAPI 3.1 (all endpoints)
└── tasks.md                       ⏳ /speckit-tasks (next step after plan approval)
```

### Source Code Layout

```text
libs/
└── lib-common-security/
    └── src/
        ├── main/java/com/mikemes/common/security/
        │   ├── annotation/
        │   │   ├── EnableMikeMESSecurity.java          # @Import(MikeMESSecurityAutoConfiguration)
        │   │   └── RequiresPrivilege.java               # meta-annotation wrapping @PreAuthorize
        │   ├── auth/
        │   │   ├── JwtClaimsExtractor.java              # typed JWT claim access: getRoles(), getOrgId(), getSub()
        │   │   ├── OrganisationContextHolder.java       # ThreadLocal<UUID> populated per request
        │   │   └── MikeMESJwtAuthenticationConverter.java  # JWT → Set<PrivilegeGrantedAuthority>
        │   ├── privilege/
        │   │   ├── PrivilegeGrantedAuthority.java       # implements GrantedAuthority; authority = privilege key
        │   │   ├── PrivilegeCache.java                  # Caffeine cache; Kafka invalidation listener
        │   │   ├── PrivilegeRegistryClient.java         # POST /privileges/register; GET /roles/privilege-map
        │   │   ├── PrivilegeManifest.java               # record(moduleName, List<PrivilegeDefinition>)
        │   │   └── PrivilegeDefinition.java             # record(key, description)
        │   └── config/
        │       └── MikeMESSecurityAutoConfiguration.java  # registers all beans; wires converter into SecurityFilterChain
        └── test/java/com/mikemes/common/security/test/
            └── KeycloakTestSupport.java                 # Testcontainers KC + WireMock IAM stub + token issuer

services/
└── iam-service/
    └── src/
        ├── main/java/com/mikemes/iam/
        │   ├── IamServiceApplication.java
        │   ├── domain/
        │   │   ├── Organisation.java                    # @Entity, @Audited
        │   │   ├── Role.java                            # @Entity, @Audited
        │   │   ├── Privilege.java                       # @Entity (not Envers — operational)
        │   │   └── RolePrivilegeAssignment.java         # @Entity, @Audited; soft-delete via revoked_at
        │   ├── repository/
        │   │   ├── OrganisationRepository.java
        │   │   ├── RoleRepository.java
        │   │   ├── PrivilegeRepository.java
        │   │   └── RolePrivilegeRepository.java
        │   ├── service/
        │   │   ├── RoleService.java                     # create/delete with Keycloak mirror (R-07)
        │   │   ├── PrivilegeService.java                # upsert registry; grant/revoke; privilege-map
        │   │   └── UserService.java                     # delegates entirely to KeycloakAdminClient
        │   ├── api/
        │   │   ├── RoleController.java                  # /roles/**
        │   │   ├── PrivilegeController.java             # /privileges/**
        │   │   ├── UserController.java                  # /users/**
        │   │   └── InternalController.java              # /internal/keycloak-events (webhook receiver)
        │   ├── keycloak/
        │   │   └── KeycloakAdminClient.java             # wraps keycloak-admin-client SDK
        │   ├── kafka/
        │   │   └── PrivilegeChangeEventPublisher.java   # publishes PrivilegeChangeEvent to iam.privilege-changes
        │   ├── config/
        │   │   ├── SecurityConfig.java                  # @EnableMikeMESSecurity + /internal/** webhook token auth
        │   │   └── KeycloakAdminConfig.java
        │   └── db/migration/
        │       ├── V001__create_iam_schema.sql
        │       ├── V002__seed_default_roles.sql
        │       ├── V003__seed_iam_module_privileges.sql
        │       └── V004__create_envers_audit_tables.sql
        └── test/java/com/mikemes/iam/
            ├── unit/
            │   ├── service/RoleServiceTest.java
            │   ├── service/PrivilegeServiceTest.java
            │   └── service/UserServiceTest.java
            └── integration/
                ├── api/RoleControllerIT.java
                ├── api/PrivilegeControllerIT.java
                ├── api/UserControllerIT.java
                ├── api/InternalControllerIT.java
                └── privilege/PrivilegeCacheInvalidationIT.java

keycloak/
└── mikemes-realm.json             # Realm export (no secrets)

docker/
└── compose-infra.yml              # postgres + kafka + keycloak
```

**Structure Decision**: Two-component delivery — `lib-common-security` (shared library) + `iam-service` (microservice). The library must be built and published to the local Maven repo before any other service can use it. Frontend (`frontend/angular/`) receives auth configuration stubs only in this Epic; full Angular IAM UI is wired here but integration-tested in a later Epic.

---

## Implementation Phases

### Phase A — lib-common-security (prerequisite for all other services)

**A1 · Core JWT infrastructure**
Tests first (unit): claim extraction from JWT, `org_id` propagation to thread-local, missing-claim rejection (401), issuer mismatch rejection (401).
Implementation: `JwtClaimsExtractor`, `OrganisationContextHolder`, `MikeMESJwtAuthenticationConverter`, `MikeMESSecurityAutoConfiguration`, `@EnableMikeMESSecurity`, `@RequiresPrivilege`.

**A2 · Privilege cache & registry client**
Tests first (unit): cache hit returns privilege set; cache miss fetches from iam-service; TTL expiry triggers re-fetch; Kafka `iam.privilege-changes` event invalidates correct role entry; `PrivilegeManifest` key format validation rejects invalid patterns.
Implementation: `PrivilegeGrantedAuthority`, `PrivilegeCache` (Caffeine + Kafka listener), `PrivilegeRegistryClient`, `PrivilegeManifest`, `PrivilegeDefinition`.

**A3 · KeycloakTestSupport**
Tests first: `KeycloakTestSupport` itself is tested by the iam-service integration tests that use it (dog-food approach).
Implementation: Testcontainers KC container; real `mikemes` test realm; `obtainToken(roleName)` helper; WireMock stub for `GET /roles/privilege-map` (so lib-common-security tests run without a live iam-service).

---

### Phase B — iam-service

**B1 · Database & Flyway migrations**
Tests first (integration): V001–V004 apply cleanly to a fresh Testcontainers PostgreSQL; default roles seeded; Envers `revinfo` table exists.
Implementation: schema `iam`, all 4 migrations, JPA entities with Envers annotations, repository interfaces.

**B2 · Role management API**
Tests first (integration): `GET /roles` returns seeded defaults; `POST /roles` creates role + mirrors to Keycloak; duplicate name returns 409; `DELETE /roles/{id}` of system role returns 400; `DELETE /roles/{id}` with active user assignments returns 409; privilege grant/revoke round-trip; Kafka `PrivilegeChangeEvent` published on grant and revoke.
Implementation: `RoleService`, `RoleController`, Keycloak mirroring with compensating rollback (R-07).

**B3 · Privilege registry API**
Tests first (integration): `POST /privileges/register` is idempotent (call twice, one record); `GET /privileges` groups by module; `GET /roles/privilege-map` returns complete map; grant/revoke reflected in map immediately.
Implementation: `PrivilegeService`, `PrivilegeController`, `PrivilegeChangeEventPublisher`.

**B4 · User management API**
Tests first (integration): `POST /users` creates user in Keycloak and sends password email; `PUT /users/{id}/roles` updates Keycloak role assignments; `POST /users/{id}/deactivate` disables user in Keycloak; `GET /users` returns only users in the authenticated org.
Implementation: `UserService`, `UserController`, `KeycloakAdminClient`.

**B5 · Keycloak webhook receiver**
Tests first (integration): valid webhook payload + correct static token → 204 + Kafka event published; invalid token → 401; unknown event type → 204 (ignored, logged).
Implementation: `InternalController.POST /internal/keycloak-events`, Kafka publish to `iam.events`.

---

### Phase C — Keycloak realm configuration

**C1 · Realm export JSON**
`keycloak/mikemes-realm.json` — includes realm settings, brute-force config (5 failures / 900 s lockout), client scopes (`org_id` group attribute mapper, `roles` realm-role mapper), 1 public frontend client (PKCE), 18 confidential M2M clients (Client Credentials), http-sender event listener config. No secrets in file.

**C2 · Docker Compose infrastructure**
`docker/compose-infra.yml` with health checks; `.env.example` template; import script for realm JSON.

---

### Phase D — Gateway security

**D1 · Spring Cloud Gateway JWT resource server**
Tests first (integration): request with valid JWT → routed; expired JWT → 401 at gateway; no JWT → 401; `/internal/**` paths → blocked (404 from gateway, never forwarded).
Implementation: gateway-service `SecurityConfig` as OAuth2 resource server; route definitions; actuator endpoints public; all other paths require valid JWT.

---

### Phase E — Angular auth shell (configuration only)

**E1 · angular-oauth2-oidc wiring**
`OAuthModule.forRoot()` config; `AuthGuard`; `AuthInterceptor`; login/logout wired to shell component. No Angular integration tests in this Epic — deferred to the frontend Epic.

---

## Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| JWT payload | Coarse role names only; no privileges | Prevents JWT bloat; size constant regardless of privilege count (R-01, spec review) |
| Privilege resolution | Caffeine in-process cache + Kafka invalidation | Sub-ms lookup; propagation ≤ 60 s; zero per-request Keycloak calls |
| Keycloak → audit bridge | Built-in http-sender webhook → audit-service → Kafka | No custom SPI JAR in Keycloak container (R-02) |
| Privilege self-registration | Startup `ApplicationReadyEvent` → `POST /privileges/register` | Always consistent with deployed services; idempotent (R-03) |
| Frontend OIDC | `angular-oauth2-oidc` (not Keycloak JS adapter) | KC JS adapter deprecated KC 22+; angular-oauth2-oidc is Angular-native (R-04) |
| Multi-tenancy | One realm, `org_id` via group attribute mapper | Single realm simplicity; group mapper provides signed `org_id` claim (R-05) |
| Opaque tokens | Rejected | JWT size problem does not exist in this design; opaque tokens add per-request latency + Keycloak SPOF (spec review) |

---

## Complexity Tracking

No constitution violations. No complexity exceptions required.
