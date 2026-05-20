# Tasks: IAM & Multi-Org Security (Keycloak)

**Branch**: `001-iam-multi-org-security-keycloak` | **Jira Epic**: MES-5
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Status**: ⏳ PENDING — Gate III (human plan approval) must be cleared before any implementation task begins.

**TDD rule (Constitution §II)**: Every `[TEST]` task MUST be written and confirmed **RED** before the corresponding `[IMPL]` task begins. Test failures MUST be logged as Jira defects before the next task starts.

**Lint rule (Constitution §II)**: `./gradlew check` (lint + unit tests) MUST pass with zero failures before every commit. Lint violations are defects — log, fix, re-run, then commit.

---

## Phase 0 — Project Structure Setup & Static Analysis Configuration

**Purpose**: Create Gradle multi-module skeletons and wire static analysis so every subsequent phase builds against a lint-enforced baseline. No business logic here.

### Structure

- [ ] T001 Create `libs/lib-common-security/` Gradle subproject with `build.gradle` (Spring Boot BOM, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, `caffeine`, `spring-kafka`)
- [ ] T002 [P] Create `services/iam-service/` Gradle subproject with `build.gradle` (`lib-common-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-web`, `keycloak-admin-client`, `flyway-core`, `spring-kafka`, `springdoc-openapi-starter-webmvc-ui`)
- [ ] T003 [P] Register both subprojects in root `settings.gradle`
- [ ] T004 [P] Create `libs/lib-common-security/src/main/java/com/mikemes/common/security/` and `test/` directory trees (empty `package-info.java` as placeholder)
- [ ] T005 [P] Create `services/iam-service/src/main/java/com/mikemes/iam/` and `test/` directory trees

### Java Static Analysis (Checkstyle + SpotBugs)

- [ ] T007 Add `checkstyle` plugin to root `build.gradle` (applies to all subprojects); create `config/checkstyle/checkstyle.xml` using Google Java Style as the base ruleset, with these project-specific rules enabled: `VisibilityModifier`, `FinalClass`, `HideUtilityClassConstructor`, `MissingJavadocMethod` (warn only on public API), `AvoidStarImport`
- [ ] T008 [P] Add `com.github.spotbugs` plugin to root `build.gradle`; create `config/spotbugs/exclude.xml` for known false-positive patterns (e.g. JPA entity field access); set `effort = 'max'`, `reportLevel = 'low'` so SpotBugs catches low-confidence issues; configure `spotbugsMain` to fail build on any bug
- [ ] T009 [P] Configure root `build.gradle` so the Gradle `check` task depends on both `checkstyleMain`, `checkstyleTest`, `spotbugsMain` — meaning `./gradlew check` runs lint + unit tests in one command; `./gradlew build` implicitly runs `check` first

### Angular Static Analysis (ESLint)

- [ ] T009a [P] Run `ng add @angular-eslint/schematics` in `frontend/angular/` to generate `.eslintrc.json` with Angular-recommended rules; add `"no-console": "error"`, `"@typescript-eslint/no-explicit-any": "error"`, `"@typescript-eslint/explicit-function-return-type": "warn"` to the config
- [ ] T009b [P] Add `"lint": "ng lint --max-warnings=0"` to `frontend/angular/package.json` scripts so `npm run lint` enforces zero-warning policy

### Verification

- [ ] T006 Verify `./gradlew check` succeeds on empty sources (no Java files yet — lint passes trivially; confirms plugin wiring is correct)
- [ ] T006a [P] Verify `npm run lint` in `frontend/angular/` passes on the generated scaffold

**Checkpoint ✅**: Both Java modules compile; `./gradlew check` runs Checkstyle + SpotBugs + unit tests in one command and passes. Angular ESLint configured and passing. Every subsequent task inherits this lint gate automatically.

---

## Phase A1 — lib-common-security: Core JWT Infrastructure

**User Stories**: US3 (request rejection), US4 (org isolation), US6 (developer onboarding)

**Delivers**: JWT validation, org_id propagation, `@RequiresPrivilege` annotation — the security gate every service depends on.

### Tests — Phase A1 ⚠️ Write first; confirm RED before any implementation

- [ ] T010 [TEST][US3] Unit test `JwtClaimsExtractorTest` in `libs/lib-common-security/src/test/.../auth/JwtClaimsExtractorTest.java`
  - `getRoles()` returns role list from `roles` claim
  - `getOrgId()` returns UUID from `org_id` claim
  - `getSub()` returns subject string
  - Missing `org_id` claim throws `MissingClaimException`
  - Missing `roles` claim returns empty list (not null)
- [ ] T011 [TEST][US3] Unit test `OrganisationContextHolderTest` — set/get/clear round-trip; verify ThreadLocal does not leak between threads
- [ ] T012 [TEST][US3] Unit test `MikeMESJwtAuthenticationConverterTest`
  - Converter with mocked `PrivilegeCache` returns `JwtAuthenticationToken` with union of `PrivilegeGrantedAuthority` objects
  - Issuer mismatch in JWT claims → `OAuth2AuthenticationException` (HTTP 401)
  - Empty roles claim → empty authority set (no NPE)
- [ ] T013 [TEST][US3] Unit test `RequiresPrivilegeAnnotationTest` — verify `@RequiresPrivilege("x:y:z")` resolves to `@PreAuthorize("hasAuthority('x:y:z')")`

### Implementation — Phase A1

- [ ] T014 [IMPL][US3] Implement `JwtClaimsExtractor.java` in `.../auth/`
- [ ] T015 [IMPL][US3][P] Implement `OrganisationContextHolder.java` in `.../auth/`
- [ ] T016 [IMPL][US3] Implement `MikeMESJwtAuthenticationConverter.java` in `.../auth/` — depends on T014 and `PrivilegeCache` (stub/interface only at this stage)
- [ ] T017 [IMPL][US6] Implement `RequiresPrivilege.java` meta-annotation in `.../annotation/`
- [ ] T018 [IMPL][US6] Implement `EnableMikeMESSecurity.java` annotation and `MikeMESSecurityAutoConfiguration.java` in `.../config/` — registers converter into `SecurityFilterChain`; marks actuator `/health` and `/info` as public
- [ ] T019 Verify all A1 unit tests pass GREEN
- [ ] T020 Commit: `feat(lib-common-security): A1 core JWT infrastructure`

**Checkpoint ✅**: JWT parsing, org_id propagation, and `@RequiresPrivilege` annotation all unit-tested and green.

---

## Phase A2 — lib-common-security: Privilege Cache & Registry Client

**User Stories**: US2 (privilege enforcement), US3 (403 for missing privilege), US6 (developer onboarding)

**Delivers**: In-process privilege cache with Kafka invalidation; module manifest registration at startup.

### Tests — Phase A2 ⚠️ Write first; confirm RED

- [ ] T030 [TEST][US2] Unit test `PrivilegeCacheTest` in `.../privilege/PrivilegeCacheTest.java`
  - Cache hit returns correct privilege set for a role name
  - Cache miss triggers `PrivilegeRegistryClient` fetch (verify with Mockito)
  - TTL expiry (set to 1 s in test) triggers re-fetch on next access
  - Kafka `iam.privilege-changes` event with `roleName` invalidates only that role's cache entry
  - Unknown role name returns empty set (no exception)
- [ ] T031 [TEST][US2] Unit test `PrivilegeRegistryClientTest`
  - `registerManifest()` sends `POST /privileges/register` with correct body
  - `getPrivilegeMap()` parses `GET /roles/privilege-map` JSON response into `Map<String, Set<String>>`
  - HTTP 503 from iam-service throws retryable `IamServiceUnavailableException`
- [ ] T032 [TEST][US2] Unit test `PrivilegeManifestValidationTest`
  - Privilege key `quality:inspection:sign-off` passes validation
  - Key `Quality:Inspection:SignOff` fails (uppercase)
  - Key `quality:inspection` fails (only two segments)
  - Key `quality:inspection:sign-off:extra` fails (four segments)
- [ ] T033 [TEST][US2] Unit test `PrivilegeGrantedAuthorityTest` — `getAuthority()` returns the privilege key string exactly; equals/hashCode consistent with privilege key

### Implementation — Phase A2

- [ ] T034 [IMPL][US2] Implement `PrivilegeDefinition.java` record in `.../privilege/`
- [ ] T035 [IMPL][US2][P] Implement `PrivilegeManifest.java` record with static factory `of(moduleName, List<PrivilegeDefinition>)` and key-format validation
- [ ] T036 [IMPL][US2][P] Implement `PrivilegeGrantedAuthority.java` in `.../privilege/`
- [ ] T037 [IMPL][US2] Implement `PrivilegeRegistryClient.java` — HTTP client using `RestClient`; reads `mikemes.security.iam-service-url` property
- [ ] T038 [IMPL][US2] Implement `PrivilegeCache.java` — Caffeine `LoadingCache<String, Set<String>>`; `@KafkaListener` on `iam.privilege-changes` topic for invalidation; TTL from `mikemes.security.privilege-cache-ttl-seconds`
- [ ] T039 [IMPL][US2] Wire `PrivilegeCache` into `MikeMESJwtAuthenticationConverter` (update A1 converter to call real cache)
- [ ] T040 [IMPL][US6] Implement `ApplicationReadyEvent` listener in `MikeMESSecurityAutoConfiguration` — iterates all `PrivilegeManifest` beans and calls `PrivilegeRegistryClient.registerManifest()`
- [ ] T041 Verify all A2 unit tests pass GREEN
- [ ] T042 Commit: `feat(lib-common-security): A2 privilege cache and registry client`

**Checkpoint ✅**: Privilege cache, Kafka invalidation, and manifest registration all unit-tested and green.

---

## Phase A3 — lib-common-security: KeycloakTestSupport

**User Story**: US6 (developer onboarding — integration test fixture)

**Delivers**: `KeycloakTestSupport` base class that gives all microservice integration tests a real Keycloak container and a working privilege map stub, so no service ever needs to mock security.

### Tests — Phase A3 (dog-food: tested by B-phase integration tests)

- [ ] T050 [TEST][US6] Write `KeycloakTestSupportSelfTest.java` in `libs/lib-common-security/src/test/` — starts `KeycloakTestSupport`, calls `obtainToken("OPERATOR")`, verifies the returned JWT is parseable with the correct `roles` and `org_id` claims
- [ ] T051 [TEST][US6] Verify `obtainToken("NONEXISTENT_ROLE")` throws `IllegalArgumentException` with a clear message

### Implementation — Phase A3

- [ ] T052 [IMPL][US6] Add `dasniko/testcontainers-keycloak` and `wiremock` to `lib-common-security` test dependencies
- [ ] T053 [IMPL][US6] Implement `KeycloakTestSupport.java` in `.../test/` — Testcontainers `KeycloakContainer`; imports `keycloak/mikemes-realm.json` test realm; `obtainToken(roleName)` helper using password grant; WireMock stub for `GET /roles/privilege-map` returning a hardcoded test privilege map
- [ ] T054 [IMPL][US6] Add `keycloak/mikemes-realm.json` test realm file to `src/test/resources/` (full realm with default roles, one test org, one test user per role, brute-force config disabled for tests)
- [ ] T055 Verify A3 self-tests pass GREEN
- [ ] T056 Commit: `feat(lib-common-security): A3 KeycloakTestSupport fixture`
- [ ] T057 Publish `lib-common-security` to local Maven repository: `./gradlew :libs:lib-common-security:publishToMavenLocal`

**Checkpoint ✅**: `lib-common-security` complete; published locally; all domain services can now import it.

---

## Phase B1 — iam-service: Database & Flyway Migrations

**User Stories**: US2 (role/privilege persistence), US3b (user management), US4 (org scoping)

**Delivers**: `iam` schema with `organisation`, `role`, `privilege`, `role_privilege` tables; Hibernate Envers audit tables; default role seeds.

### Tests — Phase B1 ⚠️ Write first; confirm RED

- [ ] T060 [TEST] Integration test `FlywayMigrationIT.java` in `services/iam-service/src/test/.../integration/`
  - Extends `KeycloakTestSupport` (for Testcontainers PostgreSQL base)
  - V001 to V004 apply cleanly to a fresh schema with no errors
  - All 6 default roles present in `role` table after V002
  - IAM module privileges present in `privilege` table after V003
  - Hibernate Envers `revinfo` table exists after V004
  - `role.org_id` and `role_privilege.org_id` columns exist with NOT NULL constraint

### Implementation — Phase B1

- [ ] T061 [IMPL] Write `V001__create_iam_schema.sql` — schema `iam`; tables: `organisation`, `role`, `privilege`, `role_privilege`; all FK constraints; `org_id` NOT NULL on `role` and `role_privilege`; indexes on `org_id` columns. See `data-model.md` for full DDL.
- [ ] T062 [IMPL][P] Write `V002__seed_default_roles.sql` — insert 6 default roles (`ADMIN`, `OPERATOR`, `QUALITY_INSPECTOR`, `PLANNER`, `ENGINEER`, `VIEWER`) with `system_role = true`
- [ ] T063 [IMPL][P] Write `V003__seed_iam_module_privileges.sql` — insert IAM-owned privileges (`iam:users:create`, `iam:users:view`, `iam:roles:manage`, `iam:esig:sign`) and assign them to the ADMIN default role
- [ ] T064 [IMPL][P] Write `V004__create_envers_audit_tables.sql` — Hibernate Envers `revinfo`, `role_aud`, `role_privilege_aud`, `organisation_aud` tables
- [ ] T065 [IMPL] Implement JPA entities: `Organisation.java`, `Role.java`, `Privilege.java`, `RolePrivilegeAssignment.java` — with `@Audited` on `Role`, `RolePrivilegeAssignment`, `Organisation`; `@Column(name="org_id", nullable=false)` on tenanted entities
- [ ] T066 [IMPL][P] Implement repository interfaces: `OrganisationRepository`, `RoleRepository`, `PrivilegeRepository`, `RolePrivilegeRepository`
- [ ] T067 Verify B1 migration integration test passes GREEN
- [ ] T068 Commit: `feat(iam-service): B1 database schema and Flyway migrations`

**Checkpoint ✅**: `iam` schema creates cleanly; seeds present; Envers tables exist; org_id constraints enforced at DB level.

---

## Phase B2 — iam-service: Role Management API

**User Story**: US2 (admin defines roles, assigns privileges)

**Delivers**: Full role CRUD + privilege grant/revoke endpoints; Keycloak role mirroring; Kafka `PrivilegeChangeEvent` publication.

### Tests — Phase B2 ⚠️ Write first; confirm RED

- [ ] T070 [TEST][US2] Integration test `RoleControllerIT.java`
  - `GET /roles` with ADMIN token → 200, returns all 6 seeded default roles
  - `POST /roles` with valid `{ "name": "SENIOR_INSPECTOR" }` → 201, role created in DB and in Keycloak realm
  - `POST /roles` with duplicate name → 409
  - `POST /roles` with invalid name pattern (lowercase) → 400
  - `DELETE /roles/{id}` of a system role → 400 with message "system roles cannot be deleted"
  - `DELETE /roles/{id}` of custom role with active user assignments → 409 with user count
  - `DELETE /roles/{id}` of custom role with no assignments → 204, role removed from DB and Keycloak
  - `PUT /roles/{id}/privileges/{privilegeId}` → 204; Kafka `iam.privilege-changes` event published
  - `DELETE /roles/{id}/privileges/{privilegeId}` → 204; Kafka event published
  - All role endpoints with VIEWER token → 403
  - All role endpoints with no token → 401
- [ ] T071 [TEST][US2] Unit test `RoleServiceTest.java`
  - `createRole()` calls `KeycloakAdminClient.createRole()` then DB insert; if Keycloak call fails, DB insert is rolled back
  - `deleteRole()` checks user count before proceeding
  - `grantPrivilege()` creates `RolePrivilegeAssignment` and publishes event
  - `revokePrivilege()` sets `revoked_at` and publishes event

### Implementation — Phase B2

- [ ] T072 [IMPL][US2] Implement `KeycloakAdminClient.java` wrapping `keycloak-admin-client` SDK — methods: `createRole(realmRoleName)`, `deleteRole(realmRoleName)`, `assignRoleToUser(userId, roleName)`, `removeRoleFromUser(userId, roleName)`
- [ ] T073 [IMPL][US2] Implement `PrivilegeChangeEventPublisher.java` — `@KafkaTemplate` publish to `iam.privilege-changes` topic; event payload: `{ roleName, changeType: GRANT|REVOKE, privilegeKey, orgId }`
- [ ] T074 [IMPL][US2] Implement `RoleService.java` — role CRUD with Keycloak mirroring and compensating rollback (R-07 pattern); grant/revoke with event publication
- [ ] T075 [IMPL][US2] Implement `RoleController.java` — REST endpoints per `iam-service-api.yaml` `/roles/**`; validated request bodies; `@RequiresPrivilege("iam:roles:manage")` on mutating endpoints
- [ ] T076 Verify B2 tests pass GREEN (including Keycloak container assertions)
- [ ] T077 Commit: `feat(iam-service): B2 role management API with Keycloak mirroring`

**Checkpoint ✅**: Roles can be created, deleted, and privilege-mapped via REST; mirrored to Keycloak; Kafka events flow on every change.

---

## Phase B3 — iam-service: Privilege Registry API

**User Story**: US2 (module privileges visible and assignable in IAM UI)

**Delivers**: Privilege self-registration endpoint; `GET /privileges` list by module; `GET /roles/privilege-map` for cache warm-up.

### Tests — Phase B3 ⚠️ Write first; confirm RED

- [ ] T080 [TEST][US2] Integration test `PrivilegeControllerIT.java`
  - `POST /privileges/register` with valid manifest → 204; calling twice (same payload) → 204; only one record in DB (idempotent)
  - `POST /privileges/register` with invalid privilege key format → 400
  - `GET /privileges` with ADMIN token → 200; privileges grouped by `moduleName`
  - `GET /roles/privilege-map` with M2M token → 200; returns complete `{ roleName: [privilegeKey, ...] }` map
  - `GET /roles/privilege-map` after granting a privilege to a role → updated map immediately reflects change
  - All `GET /privileges` calls with VIEWER token → 403
  - `POST /privileges/register` with no token → 401

### Implementation — Phase B3

- [ ] T081 [IMPL][US2] Implement `PrivilegeService.java` — `registerManifest()` upsert; `listByModule()` query; `getPrivilegeMap()` full map query
- [ ] T082 [IMPL][US2] Implement `PrivilegeController.java` — `POST /privileges/register`, `GET /privileges`, `GET /roles/privilege-map` per `iam-service-api.yaml`
- [ ] T083 Verify B3 tests pass GREEN
- [ ] T084 Commit: `feat(iam-service): B3 privilege registry API`

**Checkpoint ✅**: Privilege registry is populated by microservice self-registration; full privilege map queryable for cache warm-up.

---

## Phase B4 — iam-service: User Management API

**User Story**: US3b (admin manages users via MES IAM UI)

**Delivers**: User invite, role assignment, and deactivation endpoints; all delegating to Keycloak Admin REST API.

### Tests — Phase B4 ⚠️ Write first; confirm RED

- [ ] T090 [TEST][US3b] Integration test `UserControllerIT.java`
  - `POST /users` with ADMIN token → 201; user created in Keycloak; password-set email queued
  - `POST /users` with duplicate email → 409
  - `GET /users` with ADMIN token → 200; returns only users whose Keycloak group matches authenticated org
  - `GET /users` with OPERATOR token of Org A does not see Org B users
  - `GET /users/{id}` with valid ID → 200; returns `UserResponse`
  - `GET /users/{id}` with an ID from another org → 404
  - `PUT /users/{id}/roles` → 200; next login JWT carries updated roles
  - `POST /users/{id}/deactivate` → 204; subsequent Keycloak login attempt returns 401 for that user
  - All user endpoints with no token → 401
  - `POST /users` with VIEWER token → 403

### Implementation — Phase B4

- [ ] T091 [IMPL][US3b] Extend `KeycloakAdminClient.java` with: `createUser()`, `listUsersInGroup(orgId)`, `getUserById(id)`, `updateUserRoles()`, `deactivateUser()`, `triggerPasswordResetEmail(userId)`
- [ ] T092 [IMPL][US3b] Implement `UserService.java` — thin service delegating to `KeycloakAdminClient`; org-boundary enforcement on `getUser()` by verifying group membership
- [ ] T093 [IMPL][US3b] Implement `UserController.java` — REST endpoints per `iam-service-api.yaml` `/users/**`; `@RequiresPrivilege("iam:users:view")` on read endpoints; `@RequiresPrivilege("iam:users:create")` on mutating endpoints
- [ ] T094 Verify B4 tests pass GREEN
- [ ] T095 Commit: `feat(iam-service): B4 user management API`

**Checkpoint ✅**: Users can be invited, role-assigned, and deactivated through iam-service; org isolation enforced at API layer.

---

## Phase B5 — iam-service: Keycloak Webhook Receiver

**User Story**: US1 (auth events flow to audit-service)

**Delivers**: `POST /internal/keycloak-events` endpoint that authenticates with a static secret and publishes Keycloak auth events to Kafka `iam.events`.

### Tests — Phase B5 ⚠️ Write first; confirm RED

- [ ] T100 [TEST][US1] Integration test `InternalControllerIT.java`
  - Valid webhook payload + correct static secret header → 204 + Kafka `iam.events` message published
  - Valid payload + wrong secret → 401
  - Valid payload + no secret → 401
  - Unknown event type (e.g., `CLIENT_DELETE`) → 204 (accepted and ignored; log entry written)
  - Malformed JSON body → 400

### Implementation — Phase B5

- [ ] T101 [IMPL][US1] Implement `InternalController.java` — `POST /internal/keycloak-events`; static bearer token validated by comparing to `KEYCLOAK_WEBHOOK_SECRET` env var (not JWT validation)
- [ ] T102 [IMPL][US1] Implement Kafka publisher to `iam.events` topic inside `InternalController` handler
- [ ] T103 [IMPL][US1] Update `SecurityConfig.java` — `/internal/**` paths exempt from JWT filter chain; use custom `WebSecurityCustomizer` to validate webhook secret header instead
- [ ] T104 Verify B5 tests pass GREEN
- [ ] T105 Commit: `feat(iam-service): B5 Keycloak webhook receiver`

**Checkpoint ✅**: All iam-service phases complete; full integration test suite passes against real Testcontainers stack.

---

## Phase B6 — iam-service: Privilege Cache Invalidation End-to-End

**User Story**: US2 (privilege revocation propagated within 60 s)

**Delivers**: Proves the full invalidation loop: revoke → Kafka → lib-common-security cache cleared.

### Tests — Phase B6 ⚠️ Write first; confirm RED

- [ ] T110 [TEST][US2] Integration test `PrivilegeCacheInvalidationIT.java` in `services/iam-service/src/test/.../integration/privilege/`
  - Start iam-service + embedded lib-common-security privilege cache (via `@SpringBootTest`) with real Kafka Testcontainer
  - Grant privilege `iam:roles:manage` to role `VIEWER` — cache initially shows VIEWER has privilege
  - Revoke the privilege via `DELETE /roles/{id}/privileges/{privilegeId}`
  - Wait for Kafka event processing (≤ 5 s)
  - Assert that next `PrivilegeCache.getPrivilegesForRole("VIEWER")` does **not** contain `iam:roles:manage`
  - Assert a request with VIEWER token to a `@RequiresPrivilege("iam:roles:manage")` endpoint returns 403 within the 60 s TTL window

### Implementation — Phase B6

- [ ] T111 No new implementation — this test validates the integration of A2 + B2 + B3 already built
- [ ] T112 Verify B6 test passes GREEN
- [ ] T113 Commit: `test(iam-service): B6 privilege cache invalidation end-to-end`

**Checkpoint ✅**: Full privilege grant/revoke/invalidation lifecycle verified with real Kafka, real Keycloak, and real PostgreSQL.

---

## Phase C — Keycloak Realm Configuration

**User Stories**: US1 (operator login), US5 (M2M auth)

**Delivers**: Production-ready `mikemes` realm JSON and `docker/compose-infra.yml` that developers use to start their local stack.

- [ ] T120 [US1] Create `keycloak/mikemes-realm.json` — realm export with:
  - Realm settings: `mikemes`, access token TTL 300 s, refresh token rotation enabled, sliding window 8 h
  - Brute-force detection: 5 failures / 60 s lockout, unlock after 900 s (FR-016)
  - Client scopes: `org_id` group attribute mapper, `roles` realm-role mapper
  - Frontend client: `mes-frontend` (public, PKCE, redirect URIs `http://localhost:4200/*`)
  - M2M clients: `iam-service-m2m` (confidential, Client Credentials) — secret placeholder `${IAM_SERVICE_CLIENT_SECRET}`; additional client entries for each of the 18 services (see plan.md service list) with `${SERVICE_NAME_CLIENT_SECRET}` placeholders
  - http-sender event listener configured to POST to `http://iam-service:8080/internal/keycloak-events` with `Authorization: Bearer ${KEYCLOAK_WEBHOOK_SECRET}`
  - No hardcoded secrets in file
- [ ] T121 [US1][P] Create `docker/compose-infra.yml` — services: `postgres:16`, `confluentinc/cp-kafka:7.6` (KRaft mode), `quay.io/keycloak/keycloak:25`; health checks on all three; `keycloak` mounts realm JSON from `./keycloak/`
- [ ] T122 [US1][P] Create `.env.example` with all required env var names and placeholder values (no real secrets)
- [ ] T123 [US1] Manually verify realm import works: `docker compose -f docker/compose-infra.yml up -d` → import realm → obtain token using curl (quickstart.md step 6)
- [ ] T124 Commit: `feat: C Keycloak realm JSON and Docker Compose infrastructure`

**Checkpoint ✅**: Local infrastructure stack starts in one command; realm imports cleanly; a test token is obtainable.

---

## Phase D — Gateway Service: JWT Resource Server

**User Stories**: US1 (authenticated login end-to-end), US3 (centralised 401 at gateway)

**Delivers**: `gateway-service` acts as the OAuth2 resource server entry point; blocks unauthenticated and `/internal/**` requests before they reach any downstream service.

### Tests — Phase D ⚠️ Write first; confirm RED

- [ ] T130 [TEST][US1][US3] Integration test `GatewaySecurityIT.java` in `services/gateway-service/src/test/`
  - Request with valid JWT to any routed path → forwarded to downstream (200 or 404 acceptable, not 401)
  - Request with expired JWT → 401 at gateway; downstream never called
  - Request with no JWT → 401 at gateway
  - Request with JWT from wrong issuer → 401
  - Request to `/internal/**` path → 404 from gateway (never forwarded); even with a valid JWT
  - `GET /actuator/health` (public) → 200 with no JWT

### Implementation — Phase D

- [ ] T131 [IMPL][US1] Add `spring-boot-starter-oauth2-resource-server` and `spring-cloud-starter-gateway` to `services/gateway-service/build.gradle`
- [ ] T132 [IMPL][US1] Implement `SecurityConfig.java` in `gateway-service` — `@EnableWebFluxSecurity`; JWT RS256 resource server; `/actuator/**` permitAll; all other routes authenticated; `/internal/**` explicitly excluded from route table (returns 404)
- [ ] T133 [IMPL][US1] Add route definitions in `application.yml` for all 18 downstream services using Docker Compose internal DNS names
- [ ] T134 Verify D tests pass GREEN
- [ ] T135 Commit: `feat(gateway-service): D JWT resource server and route security`

**Checkpoint ✅**: All unauthenticated requests blocked at gateway; internal paths unreachable from outside; downstream services reachable with valid JWT.

---

## Phase E — Angular Auth Shell (Configuration Only)

**User Story**: US1 (operator browser login flow)

**Delivers**: Angular frontend wired for OIDC PKCE login/logout; `AuthGuard` protects application routes; `AuthInterceptor` attaches Bearer token to all API calls. Note: full Angular integration tests deferred to the frontend Epic.

- [ ] T140 [US1] Add `angular-oauth2-oidc` to `frontend/angular/package.json`
- [ ] T141 [US1] Implement `OAuthModule.forRoot()` configuration in `AppModule` (or `provideOAuthClient()` for standalone) — issuer `http://localhost:8080/realms/mikemes`, clientId `mes-frontend`, scope `openid profile email`, PKCE enabled
- [ ] T142 [US1][P] Implement `AuthGuard` — implements `CanActivate`; redirects unauthenticated users to Keycloak login
- [ ] T143 [US1][P] Implement `AuthInterceptor` — `HttpInterceptor` that reads access token from `OAuthService` and sets `Authorization: Bearer` header on all API requests
- [ ] T144 [US1] Wire `AuthGuard` to all application routes in `app.routes.ts`; login/logout buttons in shell component call `OAuthService.initLoginFlow()` / `OAuthService.logOut()`
- [ ] T145 [US1] Manual smoke test: `ng serve` → navigate to `http://localhost:4200` → redirected to Keycloak → login → returned to dashboard with JWT in `localStorage` (verified in browser DevTools Network tab)
- [ ] T146 Commit: `feat(frontend): E Angular OIDC auth shell wiring`

**Checkpoint ✅**: Browser login round-trip works end-to-end; JWT attached to API calls; logout clears session.

---

## Phase F — Cross-Cutting & Compliance Verification

**Purpose**: Validate all Constitution compliance gates (including static analysis), close any open defects, and confirm zero lint violations across the entire Epic before the branch is closed.

### Lint Gate Verification

- [ ] T150a Run `./gradlew check` across all Java subprojects touched in this Epic — `lib-common-security`, `iam-service`, `gateway-service` — with zero Checkstyle violations and zero SpotBugs findings
- [ ] T150b [P] Run `npm run lint` in `frontend/angular/` — zero ESLint errors; zero ESLint warnings (max-warnings=0 enforced)
- [ ] T150c [P] Confirm no `@SuppressWarnings` or `eslint-disable` annotations exist in the codebase without a code comment explaining the specific exemption; grep: `git grep -n "@SuppressWarnings\|eslint-disable"` and review each hit
- [ ] T150d Confirm all lint violations discovered during this Epic were logged as Jira defects and are resolved with a fix-and-retest commit — no outstanding lint defects open

### Test Gate Verification

- [ ] T150 Verify all Constitution Check gates in `plan.md` are ✅ PASS — specifically Gate III (human approval) was the only pending gate
- [ ] T151 [P] Confirm Hibernate Envers audit entries exist for: role create, role delete, privilege grant, privilege revoke, organisation create — run integration test against real DB
- [ ] T152 [P] Confirm `POST /privileges/register` is idempotent: call 3× with same manifest → exactly one record per privilege in DB; no 4xx or 5xx responses
- [ ] T153 [P] Confirm `PUT /roles/{id}/privileges/{privilegeId}` is idempotent: grant same privilege twice → second call returns 204 with no duplicate row
- [ ] T154 [P] Confirm all `role`, `role_privilege`, `organisation` queries in iam-service have `WHERE org_id = :orgId` — grep for repository methods lacking org_id filter
- [ ] T155 Run `./gradlew :libs:lib-common-security:test :services:iam-service:test :services:iam-service:integrationTest` — all tests GREEN; zero failures
- [ ] T156 Run `./gradlew :services:gateway-service:integrationTest` — all tests GREEN

### Final Sign-Off

- [ ] T157 Validate `quickstart.md` steps 1–8 work end-to-end on a clean machine (no pre-existing containers or volumes)
- [ ] T158 Confirm all test failures and lint failures during this Epic were logged as Jira defects and are resolved (Constitution §II)
- [ ] T159 Update `plan.md` Gate III status to ✅ PASS after owner signs off
- [ ] T160 Final commit and push of feature branch `001-iam-multi-org-security-keycloak`

**Checkpoint ✅**: Zero lint violations. Zero test failures. Zero open defects. All compliance gates green. Epic ready for PR review.

---

## Dependencies & Execution Order

```
Phase 0 (structure)
    └── Phase A1 (JWT infrastructure)
            └── Phase A2 (privilege cache)
                    └── Phase A3 (KeycloakTestSupport) ──┐
                                                          │
Phase C (Keycloak realm + Docker Compose) ────────────────┤
                                                          │
                                              Phase B1 (DB + Flyway)
                                                    └── Phase B2 (roles API)
                                                          ├── Phase B3 (privilege API)
                                                          ├── Phase B4 (user API)
                                                          ├── Phase B5 (webhook)
                                                          └── Phase B6 (cache invalidation E2E)
                                                                    │
                                              Phase D (gateway) ────┤
                                              Phase E (Angular) ────┘
                                                          │
                                              Phase F (compliance)
```

### Parallel opportunities within phases

- T001–T009b (Phase 0): T001 first; T002–T005, T007–T009b parallelisable; T006/T006a after plugin tasks complete
- T010–T013 (A1 tests): all parallelisable
- T014–T017 (A1 impl): T014/T015 parallel; T016 depends on T014
- T030–T033 (A2 tests): all parallelisable
- T061–T064 (B1 migrations): all parallelisable
- T065–T066 (B1 entities/repos): T065 then T066
- B2, B3, B4, B5: sequential within each phase; B2/B3/B4/B5 can proceed in parallel across team members once B1 is done
- Phase C and Phase A3 can proceed in parallel with Phase B

---

## Notes

- `[P]` = parallelisable with other `[P]` tasks in the same phase (different files, no dependency)
- `[TEST]` tasks MUST be written first and confirmed **RED** before any `[IMPL]` task in the same group
- Test failures MUST be logged as Jira defects before proceeding to the next `[IMPL]` task
- Lint failures are defects — log, fix, re-run `./gradlew check`, then commit
- `./gradlew check` = Checkstyle + SpotBugs + unit tests — MUST pass before every Java commit
- `npm run lint` (zero warnings) MUST pass before every Angular commit
- Constitution §II: no feature is "done" while any defect (test or lint) it introduced remains open
- Commit after each phase checkpoint — never end the day with uncommitted GREEN tests
- Gate III (human plan approval) blocks ALL implementation tasks — Phase 0 setup tasks are the only exception
