# Tasks: Platform & System Administration (MES-6)

**Branch**: `006-platform-system-administration` | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

**Format**: `[ID] [P?] [Story?] Description — file path`
- **[P]**: Parallelizable (different files, no incomplete-task dependency)
- **[Story]**: User story label (US1–US5)
- Tests are included per Constitution §II (TDD mandate): write first, confirm FAIL, then implement

---

## PR Strategy

Five PRs targeting `Develop`. Each PR boundary is an independently testable checkpoint with a CI anchor command that must pass before the PR is raised.

| PR | Phases | Tasks | CI Anchor | Notes |
|---|---|---|---|---|
| PR 1 | Phase 1 + 2 + 3 | T001–T019 | `./gradlew :services:admin-service:check` passes | Phase 1+2 bundled: setup stubs have no tests; Phase 3 provides admin-service CI coverage anchor |
| PR 2 | Phase 4 | T020–T024 | `./gradlew :services:gateway-service:check` passes | Gateway IT tests cover new routes |
| PR 3 | Phase 5 | T025–T041 | `./gradlew :services:platform-service:check` passes | SystemConfigControllerIT + org isolation verified |
| PR 4 | Phase 6 + Phase 8 | T042–T044, T047–T052 | `docker compose ps` all healthy + `./gradlew check` (all modules) green | Phase 6 depends on PR 1 + PR 3 merged; Phase 8 compliance runs before raising PR 4 |
| PR 5 | Phase 7 | T045–T046 | `curl -sf http://localhost:9000/` succeeds | P2 optional — raise after PR 4 merges to Develop |

**Sequencing note for Phase 6–8**: Phase 7 (Portainer, P2) appears between Phase 6 and Phase 8 in task order below. Complete Phase 6 → skip to Phase 8 → raise PR 4. Implement Phase 7 separately as PR 5.

---

## Phase 1: Setup — Service Scaffolding [PR 1]

**Purpose**: Register new modules, create build files and application entry points, update static-analysis config.
No user story labels — these unblock all subsequent phases.

- [X] T001 Register `admin-service` and `platform-service` in `settings.gradle` — add `include 'services:admin-service'` and uncomment `include 'services:platform-service'`
- [X] T002 [P] Create `services/admin-service/build.gradle` with dependencies: `spring-boot-admin-starter-server`, `spring-boot-admin-starter-client` (self-monitoring), `spring-boot-starter-oauth2-client`, `spring-boot-starter-security`, `spring-boot-starter-actuator`, `spring-boot-starter-web`
- [X] T003 [P] Create `services/platform-service/build.gradle` with dependencies: `project(':libs:lib-common-security')`, `spring-boot-admin-starter-client`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `flyway-core`, `postgresql`, `spring-kafka`, `springdoc-openapi-starter-webmvc-ui`; testImplementation: `testcontainers:postgresql`, `spring-boot-starter-test`
- [X] T004 [P] Create `AdminServiceApplication.java` at `services/admin-service/src/main/java/com/mikemes/admin/AdminServiceApplication.java` (`@SpringBootApplication`)
- [X] T005 [P] Create `PlatformServiceApplication.java` at `services/platform-service/src/main/java/com/mikemes/platform/PlatformServiceApplication.java` (`@SpringBootApplication`)
- [X] T006 Add `springBootAdmin = "3.4.3"` to `[versions]` block and `spring-boot-admin = { group = "de.codecentric", name = "spring-boot-admin-starter-server", version.ref = "springBootAdmin" }` to `[libraries]` block in `gradle/libs.versions.toml`
- [X] T007 Update `sonar-project.properties` to add admin-service and platform-service source paths to `sonar.sources` and `sonar.tests` (run only after T004/T005 create `src/main/java` directories — ERR-MES-033 rule)
- [X] T008 Verify scaffold compiles: `./gradlew :services:admin-service:compileJava :services:platform-service:compileJava` — both succeed with no errors

**Checkpoint**: Both services compile. Gradle modules registered. Sonar config updated.

---

## Phase 2: Foundational — Shared Prerequisites [PR 1 continued]

**Purpose**: Wire SBA client into existing services, seed platform privileges, document env vars.
**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T009 Add `de.codecentric:spring-boot-admin-starter-client` dependency to `services/iam-service/build.gradle`
- [X] T010 [P] Add SBA client registration block to `services/iam-service/src/main/resources/application.yml`: `spring.boot.admin.client.url: ${ADMIN_SERVICE_URL:http://admin-service:8888}` with `instance.prefer-ip: true`
- [X] T011 Add `de.codecentric:spring-boot-admin-starter-client` dependency to `services/gateway-service/build.gradle`
- [X] T012 [P] Add SBA client registration block to `services/gateway-service/src/main/resources/application.yml`: `spring.boot.admin.client.url: ${ADMIN_SERVICE_URL:http://admin-service:8888}` with `instance.prefer-ip: true`
- [X] T013 Create `services/iam-service/src/main/resources/db/migration/V005__seed_platform_module_privileges.sql` — insert `platform:config:manage` and `platform:config:read` privileges into `iam.privilege`; assign both to ADMIN role via `iam.role_privilege_assignment` using INSERT … ON CONFLICT DO NOTHING
- [X] T014 Add to `.env.example`: `ADMIN_SERVICE_CLIENT_SECRET` (Keycloak client secret for admin-service), `ADMIN_SERVICE_URL=http://admin-service:8888`, `PLATFORM_DB_URL`, `PLATFORM_DB_USER`, `PLATFORM_DB_PASSWORD`, `PLATFORM_SERVICE_URL=http://platform-service:8090` — each with a brief description

**Checkpoint**: iam-service and gateway-service build with SBA client on classpath. V005 migration file in place. Env vars documented.

---

## Phase 3: User Story 1 — Spring Boot Admin Observability (Priority: P1) 🎯 MVP [PR 1 continued]

**Goal**: `admin-service` runs Spring Boot Admin Server at `:8888`, protected by Keycloak OIDC. All registered services (iam-service, gateway-service, platform-service) appear in the SBA UI.

**Independent Test**: Start `admin-service` + `iam-service`. Open `http://localhost:8888`, log in via Keycloak. `iam-service` appears with `UP` status and actuator endpoints visible in the UI.

### Tests for User Story 1 ⚠️ Write first — confirm FAIL before implementing T016

- [X] T015 [US1] Create `AdminServiceApplicationTest.java` at `services/admin-service/src/test/java/com/mikemes/admin/AdminServiceApplicationTest.java` — `@SpringBootTest` context load test; fails until SecurityConfig is wired

### Implementation for User Story 1

- [X] T016 [US1] Create `SecurityConfig.java` at `services/admin-service/src/main/java/com/mikemes/admin/config/SecurityConfig.java` — configure Keycloak OIDC login (`oauth2Login()`); permit `/actuator/health` and `/login/**` without auth; require authentication for all other requests including SBA UI and `/instances/**`
- [X] T017 [P] [US1] Create `services/admin-service/src/main/resources/application.yml` — set `server.port: 8888`; configure `spring.boot.admin.server.enabled: true`; configure `spring.security.oauth2.client.registration.keycloak` (client-id: admin-service, auth-code flow, scopes: openid/profile/email) and `provider.keycloak.issuer-uri: ${KEYCLOAK_ISSUER_URI}`; configure SBA client self-registration: `spring.boot.admin.client.url: ${ADMIN_SERVICE_URL:http://admin-service:8888}`; `management.endpoints.web.exposure.include: health,info,metrics,loggers`
- [X] T018 [US1] Add `admin-service` Keycloak client entry to `keycloak/mikemes-realm.json` — confidential client, authorization-code grant, redirect URI `http://localhost:8888/login/oauth2/code/keycloak` and `http://admin-service:8888/login/oauth2/code/keycloak`
- [X] T019 [US1] Run `./gradlew :services:admin-service:check` — `AdminServiceApplicationTest` passes; zero lint violations

**Checkpoint**: `admin-service` starts on `:8888`; `/actuator/health` returns 200; SBA UI loads after Keycloak login; after `docker compose up -d`, `iam-service` and `gateway-service` appear as registered instances.

> **Raise PR 1 after this checkpoint** (T001–T019) | CI: `./gradlew :services:admin-service:check` passes | Target: `Develop`

---

## Phase 4: User Story 2 — Gateway Routing for admin/platform (Priority: P1) [PR 2]

**Goal**: Authenticated requests to `/api/admin/**` and `/api/platform/**` are routed via Spring Cloud Gateway with JWT enforcement. Direct service ports are not externally reachable.

**Independent Test**: `curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/platform/actuator/health` → proxied 200. Same request without JWT → 401 from gateway (never reaches platform-service).

### Tests for User Story 2 ⚠️ Write first — confirm FAIL before implementing T021

- [X] T020 [US2] Add test cases to `services/gateway-service/src/test/java/com/mikemes/gateway/integration/GatewaySecurityIT.java` — (a) `GET /api/platform/actuator/health` with valid JWT → gateway forwards (200 or 503 from WireMock stub); (b) same request without JWT → 401; (c) `GET /api/admin/actuator/health` with valid JWT → forwarded; (d) unauthenticated → 401

### Implementation for User Story 2

- [X] T021 [US2] Add admin-service route to `services/gateway-service/src/main/resources/application.yml` under `spring.cloud.gateway.routes`: id `admin-service`, uri `${ADMIN_SERVICE_URL:http://admin-service:8888}`, predicates `Path=/api/admin/**`, filters `StripPrefix=2`
- [X] T022 [P] [US2] Add platform-service route to `services/gateway-service/src/main/resources/application.yml`: id `platform-service`, uri `${PLATFORM_SERVICE_URL:http://platform-service:8090}`, predicates `Path=/api/platform/**`, filters `StripPrefix=2`
- [X] T023 [US2] Verify `services/gateway-service/src/main/java/com/mikemes/gateway/config/SecurityConfig.java` — confirm authenticated() catch-all covers new routes; add explicit matchers for `/api/admin/**` and `/api/platform/**` if needed
- [X] T024 [US2] Run `./gradlew :services:gateway-service:check` — GatewaySecurityIT passes for new route tests; no regression on existing tests

**Checkpoint**: Gateway routes `/api/admin/**` and `/api/platform/**` with JWT enforcement; GatewaySecurityIT passes; unauthenticated requests to either route receive 401 from the gateway.

> **Raise PR 2 after this checkpoint** (T020–T024) | CI: `./gradlew :services:gateway-service:check` passes | Target: `Develop`

---

## Phase 5: User Story 3 — Platform Service: Organisation Configuration (Priority: P1) [PR 3]

**Goal**: `platform-service` provides CRUD for `SystemConfiguration` scoped by `org_id`. A cross-org GET returns 404. `PUT` is idempotent (upsert).

**Independent Test**: `PUT /api/platform/config/test.key` (admin JWT, org A) → 200. `GET /api/platform/config/test.key` (org A JWT) → 200 with value. `GET /api/platform/config/test.key` (org B JWT) → 404.

### Tests for User Story 3 ⚠️ Write first — confirm FAIL before implementing T027

- [X] T025 [US3] Create `SystemConfigControllerIT.java` at `services/platform-service/src/test/java/com/mikemes/platform/integration/api/SystemConfigControllerIT.java` — Testcontainers PostgreSQL; fabricate JWTs with distinct org_ids; test: (a) PUT upsert returns 200; (b) GET own org returns value; (c) GET other org returns 404; (d) PUT without `platform:config:manage` returns 403; (e) DELETE sets `active=false`; (f) V001 migration applies cleanly at startup
- [X] T026 [P] [US3] Create `SystemConfigServiceTest.java` at `services/platform-service/src/test/java/com/mikemes/platform/unit/service/SystemConfigServiceTest.java` — unit tests: upsert creates when absent, updates when present; soft-delete sets active=false; findByKey returns empty Optional for inactive entry

### Implementation for User Story 3

- [X] T027 [US3] Create `services/platform-service/src/main/resources/db/migration/V001__create_platform_schema.sql` — `CREATE SCHEMA IF NOT EXISTS platform`; `CREATE TABLE platform.system_configuration` with all columns from data-model.md including `UNIQUE(org_id, config_key)`; create indexes `idx_syscfg_org_id` and `idx_syscfg_org_key`
- [X] T028 [US3] Create `services/platform-service/src/main/resources/META-INF/orm.xml` — configure `catalog` and `schema` overrides so Hibernate uses `platform` schema for all entities (mirrors iam-service orm.xml pattern with `<persistence-unit-metadata>`)
- [X] T029 [US3] Create `SystemConfiguration.java` at `services/platform-service/src/main/java/com/mikemes/platform/domain/SystemConfiguration.java` — `@Entity @Table(name="system_configuration", schema="platform")`; fields: `id`, `orgId`, `configKey`, `configValue`, `description`, `active`, `createdAt`, `updatedAt`, `createdBy`; `@PreUpdate` to set `updatedAt = now()`
- [X] T030 [US3] Create `SystemConfigurationRepository.java` at `services/platform-service/src/main/java/com/mikemes/platform/repository/SystemConfigurationRepository.java` — extend `JpaRepository<SystemConfiguration, Long>`; add `Optional<SystemConfiguration> findByOrgIdAndConfigKeyAndActiveTrue(UUID orgId, String configKey)` and `List<SystemConfiguration> findByOrgIdAndActiveTrue(UUID orgId)`
- [X] T031 [P] [US3] Create `SystemConfigDto.java` at `services/platform-service/src/main/java/com/mikemes/platform/api/dto/SystemConfigDto.java` — Java record with fields: `id`, `orgId`, `key`, `value`, `description`, `active`, `createdAt`, `updatedAt`, `createdBy`; static factory `from(SystemConfiguration entity)`
- [X] T032 [P] [US3] Create `UpsertConfigRequest.java` at `services/platform-service/src/main/java/com/mikemes/platform/api/dto/UpsertConfigRequest.java` — Java record with `@NotNull String value` and `@Size(max=1000) String description`
- [X] T033 [US3] Create `SystemConfigService.java` at `services/platform-service/src/main/java/com/mikemes/platform/service/SystemConfigService.java` — `@Service @Transactional`; `upsert(UUID orgId, String key, UpsertConfigRequest req)` — find existing active entry or create new; `findByKey(UUID orgId, String key)` returns `Optional<SystemConfigDto>`; `listAll(UUID orgId)` returns active entries; `softDelete(UUID orgId, String key)` sets active=false
- [X] T034 [US3] Create `WebhookTokenFilter.java` at `services/platform-service/src/main/java/com/mikemes/platform/filter/WebhookTokenFilter.java` — mirror of `services/iam-service/src/main/java/com/mikemes/iam/filter/WebhookTokenFilter.java`; reads `mikemes.security.webhook-token` property; rejects non-matching `Authorization: Bearer` header with 401; note in Javadoc: consolidate to lib-common-security in MES-28
- [X] T035 [US3] Create `SystemConfigController.java` at `services/platform-service/src/main/java/com/mikemes/platform/api/SystemConfigController.java` — `@RestController @RequestMapping("/api/platform/config")`; `GET /` list all (requires `platform:config:read`); `GET /{key}` get by key (requires `platform:config:read`); `PUT /{key}` upsert (requires `platform:config:manage`); `DELETE /{key}` soft-delete (requires `platform:config:manage`); extract `org_id` from JWT via `JwtClaimsExtractor`; return 404 when entry not found
- [X] T036 [US3] Create `InternalConfigController.java` at `services/platform-service/src/main/java/com/mikemes/platform/api/InternalConfigController.java` — `@RestController @RequestMapping("/internal/config")`; `GET /{key}?orgId={uuid}` returns `SystemConfigDto` or 404; protected by `WebhookTokenFilter` via `internalSecurityFilterChain`
- [X] T037 [P] [US3] Create `GlobalExceptionHandler.java` at `services/platform-service/src/main/java/com/mikemes/platform/api/GlobalExceptionHandler.java` — `@RestControllerAdvice`; handle `NoSuchElementException/EmptyResultDataAccessException` → 404; `ConstraintViolationException` → 400; `MethodArgumentNotValidException` → 400; mirror iam-service `GlobalExceptionHandler` error response structure
- [X] T038 [US3] Create `SecurityConfig.java` at `services/platform-service/src/main/java/com/mikemes/platform/config/SecurityConfig.java` — two filter chains: (1) `@Order(1) internalSecurityFilterChain` for `/internal/**` using `WebhookTokenFilter`; (2) `@Order(2) mainSecurityFilterChain` with `@EnableMikeMESSecurity` and JWT resource server for all other paths; permit `/actuator/health` unconditionally
- [X] T039 [P] [US3] Create `JpaConfig.java` at `services/platform-service/src/main/java/com/mikemes/platform/config/JpaConfig.java` — `@Configuration @EnableJpaAuditing`; set `spring.jpa.properties.hibernate.default_schema=platform` and `spring.jpa.properties.hibernate.hbm2ddl.auto=validate`
- [X] T040 [P] [US3] Create `services/platform-service/src/main/resources/application.yml` — `server.port: 8090`; datasource: `${PLATFORM_DB_URL}` / `${PLATFORM_DB_USER}` / `${PLATFORM_DB_PASSWORD}`; Flyway: `spring.flyway.schemas: platform` and `default-schema: platform`; `spring.jpa.properties.hibernate.default_schema: platform`; lib-common-security: `mikemes.security.webhook-token: ${MIKEMES_SECURITY_WEBHOOK_TOKEN}`; Keycloak: `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${KEYCLOAK_ISSUER_URI}`; SBA client: `spring.boot.admin.client.url: ${ADMIN_SERVICE_URL:http://admin-service:8888}`; `management.endpoints.web.exposure.include: health,info,metrics`
- [X] T041 [US3] Run `./gradlew :services:platform-service:check` — `SystemConfigControllerIT` and `SystemConfigServiceTest` pass; V001 migration applies in Testcontainers; zero lint violations

**Checkpoint**: `SystemConfiguration` CRUD works end-to-end; org_id isolation enforced; V001 migration runs clean; IT tests pass; `PUT` is idempotent on repeat calls.

> **Raise PR 3 after this checkpoint** (T025–T041) | CI: `./gradlew :services:platform-service:check` passes | Target: `Develop`

---

## Phase 6: User Story 4 — Docker Compose Service Mesh (Priority: P1) [PR 4]

**Goal**: `admin-service` and `platform-service` containers are defined in `compose-infra.yml` with healthchecks. All services communicate by hostname on `mikemes-net`. SBA clients register with admin-service on startup.

**Independent Test**: `docker compose -f docker/compose-infra.yml up -d && docker compose -f docker/compose-infra.yml ps` — `admin-service` and `platform-service` show `healthy`. `iam-service` logs show SBA registration confirmation.

- [X] T042 [US4] Add `admin-service` container definition to `docker/compose-infra.yml` — image built from `services/admin-service`; ports `8888:8888`; env vars: `ADMIN_SERVICE_CLIENT_SECRET`, `ADMIN_SERVICE_URL=http://admin-service:8888`, `KEYCLOAK_ISSUER_URI`; healthcheck: `curl -sf http://localhost:8888/actuator/health`; depends_on: `keycloak`; networks: `mikemes-net`
- [X] T043 [P] [US4] Add `platform-service` container definition to `docker/compose-infra.yml` — ports `8090:8090`; env vars: `PLATFORM_DB_URL=jdbc:postgresql://postgres:5432/mikemes?currentSchema=platform`, `PLATFORM_DB_USER`, `PLATFORM_DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `MIKEMES_SECURITY_WEBHOOK_TOKEN`, `ADMIN_SERVICE_URL=http://admin-service:8888`; healthcheck: `curl -sf http://localhost:8090/actuator/health`; depends_on: `postgres`; networks: `mikemes-net`
- [X] T044 [US4] Add `ADMIN_SERVICE_URL=http://admin-service:8888` env var to `iam-service` and `gateway-service` container definitions in `docker/compose-infra.yml` — enables SBA client registration from existing services to newly-added admin-service

**Checkpoint**: All services start healthy. `docker compose ps` shows no unhealthy or exited containers. SBA UI at `:8888` shows iam-service and gateway-service registered.

> **Continue to Phase 8 (compliance) before raising PR 4** — do NOT raise PR 4 here; Phase 8 compliance tasks must complete first.  
> **Skip Phase 7** (P2 Portainer) until after PR 4 merges — see sequencing note in PR Strategy section above.

---

## Phase 7: User Story 5 — Portainer Container Management UI (Priority: P2) [PR 5 — raise after PR 4 merges]

**Goal**: Operators can manage running containers via Portainer web UI, started optionally via `docker/compose-tools.yml`.

**Independent Test**: `docker compose -f docker/compose-tools.yml up -d portainer && curl -sf http://localhost:9000/` succeeds (redirects to Portainer UI).

- [X] T045 [US5] Create `docker/compose-tools.yml` — define `portainer` service: `image: portainer/portainer-ce:latest`; ports `9000:9000` and `9443:9443`; volumes: `/var/run/docker.sock:/var/run/docker.sock` and `portainer_data:/data`; restart: `unless-stopped`; networks: `mikemes-net`; include `volumes: portainer_data:` declaration. Include comment: "Optional dev tooling — do NOT include in production deployments."
- [X] T046 [P] [US5] Add Portainer startup instructions to `specs/006-platform-system-administration/quickstart.md` — first-run admin password setup note; verify `http://localhost:9000` shows container list

**Checkpoint**: `docker compose -f docker/compose-tools.yml up -d portainer` starts Portainer; UI accessible on `:9000`; can view all containers on `mikemes-net`.

> **Raise PR 5 after this checkpoint** (T045–T046) | CI: `curl -sf http://localhost:9000/` succeeds | Target: `Develop` | P2 — optional, raise after PR 4 merges

---

## Phase 8: Compliance Verification & Defect Closure [PR 4 continued]

**Purpose**: Validate Constitution gates, org isolation, idempotency, and Keycloak RBAC before marking MES-6 done.
Mandatory per Constitution §II and §IV.

- [X] T047 Verify all Constitution Check gates in `specs/006-platform-system-administration/plan.md` — confirm Gate III is ✅ PASS (plan approved 2026-05-23); confirm Gate V ❌ FAIL justification matches DEF-001; all other gates ✅ PASS
- [X] T048 [P] Confirm `org_id` scoping on `platform.system_configuration` — verify `SystemConfigControllerIT` has an explicit test where org-B JWT cannot read org-A config key (returns 404, not 403 or the value)
- [X] T049 [P] Confirm `PUT /api/platform/config/{key}` is idempotent — verify `SystemConfigControllerIT` makes two identical PUT calls and asserts the second returns 200 with the same value (no unique-constraint error)
- [X] T050 Confirm Keycloak RBAC updated: V005 migration seeds `platform:config:manage` and `platform:config:read`; verify in `services/iam-service/src/main/resources/db/migration/V005__seed_platform_module_privileges.sql`
- [X] T051 Run full `./gradlew check` across all modules — zero test failures, zero Checkstyle violations, zero SpotBugs violations
- [X] T052 Pre-retrospective: review session work against `docs/governance/MES-ERR-001_Index.md` categories for any new errors or near-misses; log any new entries before transitioning MES-6 to Done

> **Raise PR 4 after this checkpoint** (T042–T044, T047–T052) | CI: `docker compose ps` all healthy + `./gradlew check` (all modules) green | Target: `Develop`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user story phases
- **Phase 3 (US1)**: Depends on Phase 2
- **Phase 4 (US2)**: Depends on Phase 2 (can run in parallel with Phase 3)
- **Phase 5 (US3)**: Depends on Phase 2 (can run in parallel with Phases 3 and 4)
- **Phase 6 (US4)**: Depends on Phase 3 AND Phase 5 (needs admin-service + platform-service containers to exist)
- **Phase 7 (US5)**: Depends on Phase 6 (needs `mikemes-net` network and Docker Compose established) — P2 priority, can defer
- **Phase 8 (Compliance)**: Depends on all P1 story phases (3, 4, 5, 6)

### User Story Dependencies

- **US1 (SBA)**: Independent after Phase 2 — no dependency on US2, US3
- **US2 (Gateway)**: Independent after Phase 2 — no dependency on US1, US3 (gateway IT test can mock downstream)
- **US3 (Platform Config)**: Independent after Phase 2 — no dependency on US1, US2
- **US4 (Compose)**: Depends on US1 and US3 (container definitions need the services to exist)
- **US5 (Portainer)**: Fully independent — P2, can be done any time after Phase 2

### Within Each User Story

1. Write failing tests (T015, T020, T025/T026)
2. Create entities/models before services
3. Create services before controllers
4. Create SecurityConfig before running integration tests
5. Run `./gradlew check` at each story checkpoint

### Parallel Opportunities

**Phase 1**: T002, T003, T004, T005 can all run in parallel  
**Phase 2**: T009 + T010 can run in parallel with T011 + T012  
**Phases 3, 4, 5**: All three user story phases can run in parallel after Phase 2  
**Within US3**: T025 + T026 in parallel; T031 + T032 in parallel; T037 + T039 + T040 in parallel

---

## Parallel Example: User Story 3 (Platform Config)

```
# Parallel: tests (T025, T026)
Task T025: "Create SystemConfigControllerIT.java"
Task T026: "Create SystemConfigServiceTest.java"

# Sequential: V001 migration → entity → repository → service → controllers
Task T027: "Create V001__create_platform_schema.sql"
Task T028: "Create META-INF/orm.xml"
Task T029: "Create SystemConfiguration.java entity"
Task T030: "Create SystemConfigurationRepository.java"

# Parallel: DTOs (T031, T032)
Task T031: "Create SystemConfigDto.java"
Task T032: "Create UpsertConfigRequest.java"

# Sequential: service → WebhookTokenFilter → controllers
Task T033: "Create SystemConfigService.java"
Task T034: "Create WebhookTokenFilter.java"
Task T035: "Create SystemConfigController.java"
Task T036: "Create InternalConfigController.java"

# Parallel: exception handler + config classes + application.yml (T037, T038, T039, T040)
Task T037: "Create GlobalExceptionHandler.java"
Task T038: "Create SecurityConfig.java"
Task T039: "Create JpaConfig.java"
Task T040: "Create application.yml"
```

---

## Implementation Strategy

### MVP First (P1 User Stories, Phase 1–6)

1. Complete Phase 1: Setup + Phase 2: Foundational
2. Complete Phase 3 (US1): admin-service → **VALIDATE**: SBA UI accessible, iam-service visible
3. Complete Phase 4 (US2): gateway routes → **VALIDATE**: JWT-protected routing works
4. Complete Phase 5 (US3): platform-service → **VALIDATE**: config CRUD + org isolation works
5. Complete Phase 6 (US4): compose entries → **VALIDATE**: `docker compose up` stack is healthy
6. Phase 8: Compliance verification → ready for `/speckit-taskstoissues`

### Portainer (P2)

Add Phase 7 after P1 stories validate. It is independently testable and has no blockers.

### Parallel Team Strategy

With two developers after Phase 2 completes:
- **Dev A**: Phase 3 (US1 — admin-service) then Phase 4 (US2 — gateway)
- **Dev B**: Phase 5 (US3 — platform-service) then Phase 6 (US4 — compose)
- Both converge on Phase 8 (compliance) before PR
