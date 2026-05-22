# Spec: Harden IT Test JWT Pipeline — Zero Silent Failures

**Jira Epic:** MES-27  
**Status:** To Do  
**Priority:** Medium  
**Created:** 2026-05-22  
**Research sources:** `specs/001-iam-multi-org-security-keycloak/Research_JWT.md`, `Research_JWT_plan_fix.md`

---

## Problem Statement

`PrivilegeControllerIT` accumulated **11 structural design flaws** during MES-5 development. These flaws do not cause individual test failures in isolation — they cause *ambiguous* failures where the symptom surfaces several stages after the actual breakage. Nine CI iterations were consumed diagnosing these because no stage output was verified before being consumed by the next stage.

The research in MES-5 identified every step in both the pre- and post-fabrication JWT pipelines, mapped each flaw to its specific stage, and produced a 17-step zero-silent-failures redesign. None of the 11 fixes were applied before PR1 merged — they are tracked here.

---

## Structural Root Cause

Every stage in both pipelines hands off to the next by **implicit convention rather than explicit assertion**. A failure at any stage produces a symptom at a later stage with no pointer to where the chain actually broke.

**Fix strategy:**
1. **Decouple stages** — token fabrication must never depend on Keycloak container health
2. **Assert every handshake** — each stage's output is explicitly verified before being consumed as input
3. **Name every failure mode** — every `assertThat()` carries an `.as()` message identifying the stage and likely root cause

---

## User Stories

### US1 — Token fabrication is decoupled from Keycloak availability

**As** a developer running `PrivilegeControllerIT` on a machine where Keycloak fails to start,  
**I want** token fabrication to proceed independently so that all non-KC tests still run,  
**So that** a Keycloak container failure never silently nulls `adminToken` and fails unrelated assertions.

**Acceptance criteria:**
- `buildTestTokens()` is a separate `@BeforeAll @Order(1)` method with no Keycloak dependency
- `setupKeycloak()` is a separate `@BeforeAll @Order(2)` method, independently guarded with `assumeTrue(KEYCLOAK.isRunning())`
- If Keycloak fails, token-fabrication tests still run; only KC-dependent tests are skipped

### US2 — Token fabrication is self-verifying

**As** a developer whose `@BeforeAll` fails,  
**I want** the failure message to identify exactly which fabrication step broke,  
**So that** I do not trace a Nimbus NPE back through 14 steps manually.

**Acceptance criteria:**
- `buildTestTokens()` asserts: RSA key is non-null with private component
- `buildTestTokens()` asserts: Flyway V003 seeded exactly 4 `iam.*` privilege rows (JDBC)
- `buildTestTokens()` asserts: `adminToken` and `viewerToken` are non-blank after signing
- `buildTestTokens()` asserts round-trip parse: `kid == TEST_KEY_ID`, `roles` is `List<String>` containing the role, `org_id` round-trips, `exp` is in the future

### US3 — Test configuration failures are loud, not silent

**As** a developer who accidentally removes `TestJwtDecoderConfig`,  
**I want** a fast, specific failure that says "wrong decoder type",  
**So that** I do not spend CI runs debugging opaque 401s.

**Acceptance criteria:**
- `issuer-uri` in `@DynamicPropertySource` is set to `"http://test-issuer-not-used.local"` (not `""`) — if the `@Primary` override is absent, Spring attempts OIDC discovery and fails loudly
- A dedicated test `testConfiguration_primaryJwtDecoder_isRsaBackedNotAutoConfigured()` asserts the `@Primary` decoder bean is `NimbusJwtDecoder` (not the OIDC auto-configured variant)

### US4 — `listPrivileges` test distinguishes auth failure from data failure

**As** a developer whose `listPrivileges_withAdminToken_returns200WithIamModule()` returns 403,  
**I want** the test to tell me whether the issue is a missing DB migration or a SpEL version mismatch,  
**So that** I do not manually inspect DB state and Spring Security classpath separately.

**Acceptance criteria:**
- Two independent JDBC preconditions run before the HTTP call: (1) ADMIN has `iam:roles:manage` active; (2) `iam` module privileges exist
- HTTP assertions are staged: first `isNotEqualTo(401)` (decoder check), then `isEqualTo(200)` (auth check), then body `containsKey("iam")` (data check)
- Every `assertThat()` call in the test carries an `.as()` message naming the stage and the most likely root cause of failure

### US5 — `RequiresPrivilege` documents its Spring Security version dependency

**As** a developer upgrading or downgrading Spring Security,  
**I want** `RequiresPrivilege.java` to document the `{value}` template substitution version requirement,  
**So that** I do not spend CI iterations discovering that `{value}` is a literal string on Spring Security < 6.3.

**Acceptance criteria:**
- A comment on `RequiresPrivilege.java` states: "`{value}` template substitution requires Spring Security 6.3+ (`AnnotationTemplateExpressionDefaults`). On 6.0–6.2, `{value}` is a literal string and `hasAuthority('{value}')` always returns false."

### US6 — Keycloak realm creation asserts HTTP 201 (per ERR-MES-024)

**As** a developer whose tests fail because a realm was silently not created,  
**I want** `createRealm()` to assert the Admin API returned 201,  
**So that** a 409 (leaked container state) or 401 (wrong credentials) fails immediately with a named message.

**Acceptance criteria:**
- `createRealm()` in both `PrivilegeControllerIT` and `UserControllerIT` uses try-with-resources and asserts `r.getStatus() == 201`
- Failure message distinguishes 409 ("realm already exists — leaked container state") from 401 ("wrong admin credentials")
- Applies ERR-MES-024 rule consistently to both IT test classes

---

## Fix Table — All 11 Fixes (from Research_JWT_plan_fix.md)

| # | User Story | Fix | Primary File |
|---|-----------|-----|-------------|
| F1 | US1 | Split `setupKeycloak()` → `buildTestTokens()` @Order(1) + `setupKeycloak()` @Order(2) | `PrivilegeControllerIT.java` |
| F2 | US1 | Change `@BeforeAll` guard and all test-method guards from `KEYCLOAK` to `POSTGRES` | `PrivilegeControllerIT.java` |
| F3 | US3 | Replace `issuer-uri = ""` with `"http://test-issuer-not-used.local"` in `@DynamicPropertySource` | `PrivilegeControllerIT.java`, `UserControllerIT.java` |
| F4 | US2 | Extract `TEST_KEY_ID = "test-key"` constant; use in `RSAKeyGenerator` and `JWSHeader.Builder`; add round-trip parse assertion | `PrivilegeControllerIT.java` |
| F5 | US2 | Assert Flyway V003 seeded exactly 4 `iam.*` privileges via JDBC in `buildTestTokens()` | `PrivilegeControllerIT.java` |
| F6 | US2 | Assert `roles` claim round-trips as `List<String>` containing the fabricated role | `PrivilegeControllerIT.java` |
| F7 | US4 | JDBC precondition: ADMIN has `iam:roles:manage` active before HTTP call | `PrivilegeControllerIT.java` |
| F8 | US5 | Add Spring Security 6.3+ version comment to `RequiresPrivilege.java` | `RequiresPrivilege.java` |
| F9 | US4 | JDBC precondition: `iam` module privileges exist (separate from F7) | `PrivilegeControllerIT.java` |
| F10 | US4 | Add `.as()` messages to all assertions; stage ordering — 401 check before 403 check before body check | `PrivilegeControllerIT.java` |
| F11 | US6 | Assert `r.getStatus() == 201` in `createRealm()` with try-with-resources | `PrivilegeControllerIT.java`, `UserControllerIT.java` |

---

## Scope

| File | Changes |
|------|---------|
| `services/iam-service/src/test/.../PrivilegeControllerIT.java` | F1, F2, F3, F4, F5, F6, F7, F9, F10, F11 — primary target |
| `services/iam-service/src/test/.../UserControllerIT.java` | F3 (issuer-uri sentinel), F11 (realm creation 201 assertion) |
| `libs/lib-common-security/src/.../RequiresPrivilege.java` | F8 (comment only — no logic change) |

No production code changes. All changes are test code and a single annotation comment.

---

## Out of Scope

- Changes to `LocalPrivilegeCache`, `MikeMESJwtAuthenticationConverter`, or any production bean
- Changes to Flyway migrations
- Adding new IT tests beyond those described above
- `UserControllerIT` refactoring beyond F3 and F11 (its `buildTestTokens` pattern is already adequate for its test scope)

---

## Definition of Done

- All 11 fixes applied
- `./gradlew :services:iam-service:test` passes with zero failures locally and in CI
- Each assertion failure in `buildTestTokens()` and `listPrivileges_withAdminToken_returns200WithIamModule()` produces a message that names the broken stage without requiring manual pipeline tracing
- `RequiresPrivilege.java` has the version comment
- PR targets `Develop`, includes Deployment Steps section, passes SonarCloud quality gate
