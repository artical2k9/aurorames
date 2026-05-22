# Spec: Fix CaffeinePrivilegeCache Pipeline — Missing Endpoint, Auth, and Exception Handling

**Jira Epic:** MES-28  
**Status:** To Do  
**Priority:** High  
**Created:** 2026-05-23  
**Research source:** `specs/001-iam-multi-org-security-keycloak/Research_CaffeinePrivilegeCache.md`

---

## Problem Statement

`CaffeinePrivilegeCache` is the `PrivilegeCache` implementation used by every non-iam service (gateway, quality, planning, and all future domain services). It is wired automatically by `MikeMESSecurityAutoConfiguration` via `@ConditionalOnMissingBean(PrivilegeCache.class)` — any service without a `LocalPrivilegeCache` gets it.

Research during MES-5 traced the full bean-wiring and runtime-execution pipelines and found **three critical blockers** that make `CaffeinePrivilegeCache` non-functional, plus four secondary flaws. The pipeline has **no valid success path**: every runtime execution ends in a 401, 404, or unhandled exception before a privilege set is ever returned.

The defects are invisible at startup — services start successfully. They only surface when the first authenticated request is processed.

---

## Root Cause Summary

Three independent gaps in the chain from HTTP request to granted authorities:

1. The endpoint `CaffeinePrivilegeCache` calls (`GET /internal/privileges`) does not exist in `InternalController`
2. Even if it did exist, `PrivilegeRegistryClient` sends no `Authorization` header — `WebhookTokenFilter` returns 401 before the controller is reached
3. Even if auth passed, `PrivilegeRegistryException` escapes Spring Security's filter chain uncaught, producing HTTP 500

All three must be fixed together — each one alone leaves the pipeline broken.

---

## User Stories

### US1 — Non-iam services can resolve privileges via `GET /internal/privileges` (C1)

**As** a non-iam service (gateway, quality, etc.) processing an authenticated request,  
**I want** `PrivilegeRegistryClient` to successfully fetch the privilege manifest from iam-service,  
**So that** `CaffeinePrivilegeCache` can build `GrantedAuthority` sets and authorization decisions can be made.

**Acceptance criteria:**
- `InternalController` exposes `GET /internal/privileges` returning `ResponseEntity<PrivilegeManifest>`
- Implementation delegates to `PrivilegeService.getPrivilegeMap()` (already exists); converts `List<String>` → `Set<String>` per role
- The endpoint is protected by the existing `internalSecurityFilterChain` (Order 1 / `WebhookTokenFilter`) — no security bypass
- `PrivilegeManifest` record contains `Map<String, Set<String>> rolePrivileges`
- The endpoint is idempotent (GET, read-only, no side effects)

### US2 — `PrivilegeRegistryClient` authenticates its internal requests (C2)

**As** the iam-service receiving `GET /internal/privileges`,  
**I want** `PrivilegeRegistryClient` to include `Authorization: Bearer {iam.webhook.token}`,  
**So that** `WebhookTokenFilter` allows the request through to the controller.

**Acceptance criteria:**
- `MikeMESSecurityAutoConfiguration` reads `iam.webhook.token` (property name: `mikemes.security.webhook-token`) and injects it into `PrivilegeRegistryClient` at bean construction time
- `PrivilegeRegistryClient` sets it as a default header on its `RestClient`: `Authorization: Bearer {token}`
- The token is documented in `.env.example` as `IAM_WEBHOOK_TOKEN` with a generation instruction
- The token is added to all compose files that run non-iam services alongside iam-service
- No token value is hardcoded in source

### US3 — Registry failures produce named auth errors, not HTTP 500 (C3)

**As** a developer or client receiving an error when iam-service is down,  
**I want** a named `OAuth2AuthenticationException` rather than an unhandled 500,  
**So that** the failure mode is visible in logs and clients receive a meaningful error code.

**Acceptance criteria:**
- `MikeMESJwtAuthenticationConverter.resolveAuthorities()` catches `PrivilegeRegistryException` and re-throws as `OAuth2AuthenticationException` with error code `privilege_registry_unavailable`
- HTTP response is 401 (Spring Security handles `OAuth2AuthenticationException` → `BearerTokenAuthenticationEntryPoint` → 401), not 500
- The exception message includes the role name and the original registry error message
- Unit test: mock `PrivilegeCache` to throw `PrivilegeRegistryException`; assert response is 401 with error code in body, not 500

### US4 — `iam-service` properly handles PrivilegeControllerIT Wiring assertion (C1 + existing test)

**As** a developer running `PrivilegeControllerIT`,  
**I want** the existing `privilegeCache_isLocalPrivilegeCache_notCaffeineCache()` test to continue passing,  
**So that** C1's new endpoint does not accidentally displace `LocalPrivilegeCache` in iam-service's context.

**Acceptance criteria:**
- `LocalPrivilegeCache` remains the active `PrivilegeCache` in iam-service (no regression)
- `PrivilegeControllerIT.privilegeCache_isLocalPrivilegeCache_notCaffeineCache()` still passes after C1 changes

### US5 — Cache efficiency: manifest cached once per TTL, not once per role (C4)

**As** a non-iam service with 6 system roles processing its first 6 requests,  
**I want** the privilege manifest to be fetched once and cached, not once per distinct role,  
**So that** startup does not produce 6 identical HTTP round-trips to iam-service.

**Acceptance criteria:**
- Cache key changes from role name to a fixed key (e.g., `"manifest"`); value is the full `PrivilegeManifest`
- `getPrivilegesForRole(role)` performs a local map lookup on the cached manifest
- On a cold cache with 6 roles, exactly 1 call to `fetchManifest()` is made regardless of how many distinct roles appear in the first N requests
- Kafka invalidation (`onPrivilegeChange`) still works: clears the single manifest entry, forcing a fresh fetch on the next call

### US6 — `@Component` removed from auto-configured beans (C6)

**As** a developer reading `CaffeinePrivilegeCache` or `PrivilegeRegistryClient`,  
**I want** no `@Component` annotation on either class,  
**So that** it is clear these beans are created exclusively by `MikeMESSecurityAutoConfiguration` and cannot be accidentally double-registered.

**Acceptance criteria:**
- `@Component` removed from `CaffeinePrivilegeCache`
- `@Component` removed from `PrivilegeRegistryClient`
- All existing unit tests still pass (bean construction is tested directly, not via component scan)

### US7 — End-to-end integration test validates the full privilege-resolution path (C7)

**As** a developer introducing a change to `CaffeinePrivilegeCache` or `PrivilegeRegistryClient`,  
**I want** a failing integration test if the full pipeline breaks,  
**So that** C1-type regressions (missing endpoint, wrong auth) are caught before they reach CI.

**Acceptance criteria:**
- Integration test starts a real `iam-service` Testcontainer (or uses a running instance via `TEST_IAM_SERVICE_URL`) alongside a real Postgres container
- Test sends a JWT request to a service using `CaffeinePrivilegeCache` and asserts HTTP 200 (not 401/404/500)
- Test verifies that `getPrivilegesForRole("ADMIN")` returns a non-empty set containing at least one `iam:*` privilege
- On a cache miss, exactly 1 HTTP call to `GET /internal/privileges` is made

---

## Fix Table

| # | Phase | User Story | Fix | Files |
|---|-------|-----------|-----|-------|
| C1 | 1 — Blocker | US1, US4 | Implement `GET /internal/privileges` in `InternalController` | `InternalController.java`, `PrivilegeManifest.java` (if new) |
| C2 | 1 — Blocker | US2 | Inject + send `iam.webhook.token` in `PrivilegeRegistryClient` | `PrivilegeRegistryClient.java`, `MikeMESSecurityAutoConfiguration.java`, `.env.example`, compose files |
| C3 | 1 — Blocker | US3 | Catch `PrivilegeRegistryException` → `OAuth2AuthenticationException` in converter | `MikeMESJwtAuthenticationConverter.java` |
| C4 | 2 — Quality | US5 | Cache full manifest under single key; extract per-role on read | `CaffeinePrivilegeCache.java` |
| C6 | 2 — Quality | US6 | Remove `@Component` from `CaffeinePrivilegeCache` and `PrivilegeRegistryClient` | Both classes |
| C7 | 2 — Quality | US7 | Add end-to-end integration test | New IT test file |
| C5 | 2 — Quality | — | Evaluate retry strategy (C1+C2 eliminate the current failure modes; C5 deferred to separate decision) | `PrivilegeRegistryClient.java` |

---

## Delivery Sequence

**Phase 1 — Blockers (C1 + C2 + C3, single PR)**

C1 alone: still returns 401 (no auth header).  
C2 alone: still returns 404 (no endpoint).  
Neither is testable end-to-end without C3 (exception escapes filter chain).  
All three must ship together.

**Phase 2 — Quality (C4 + C6 + C7, separate PR)**

No blocking dependency on Phase 1 being deployed, but Phase 1 must be merged first so the end-to-end test in C7 has a working pipeline to validate against.

---

## Scope

| File | Change |
|------|--------|
| `services/iam-service/.../InternalController.java` | C1 — add `GET /internal/privileges` |
| `services/iam-service/.../PrivilegeManifest.java` | C1 — create record if not already in iam-service; or reuse from lib-common-security |
| `libs/lib-common-security/.../PrivilegeRegistryClient.java` | C2 — default auth header; C6 — remove `@Component` |
| `libs/lib-common-security/.../CaffeinePrivilegeCache.java` | C4 — manifest-level cache; C6 — remove `@Component` |
| `libs/lib-common-security/.../MikeMESSecurityAutoConfiguration.java` | C2 — read + inject webhook token |
| `libs/lib-common-security/.../MikeMESJwtAuthenticationConverter.java` | C3 — catch + re-throw as `OAuth2AuthenticationException` |
| New IT test (location TBD in plan) | C7 — end-to-end path test |
| `.env.example` | C2 — document `IAM_WEBHOOK_TOKEN` |
| `docker/compose-infra.yml` (and any test compose) | C2 — add `IAM_WEBHOOK_TOKEN` env var to non-iam services |

## Out of Scope

- Changes to how iam-service itself resolves privileges (still uses `LocalPrivilegeCache`)
- Changes to `WebhookTokenFilter` or `internalSecurityFilterChain` auth model
- Retry strategy redesign (C5) — deferred; tracked as a note in the Phase 2 PR

---

## Definition of Done

- Phase 1: `GET /internal/privileges` returns 200 with correct manifest; `PrivilegeRegistryClient` sends webhook token; `MikeMESJwtAuthenticationConverter` converts registry failure to 401 with named error code
- Phase 2: manifest cached as single entry; `@Component` removed; end-to-end IT test passes
- `./gradlew check` passes with zero failures across all affected modules
- `PrivilegeControllerIT.privilegeCache_isLocalPrivilegeCache_notCaffeineCache()` still passes (no regression in iam-service)
- `.env.example` and compose files document `IAM_WEBHOOK_TOKEN`
- PR targets `Develop`, includes Deployment Steps (env var propagation instructions), passes SonarCloud quality gate
