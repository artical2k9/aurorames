# Research: JWT Pipeline Design — `listPrivileges_withAdminToken_returns200WithIamModule`

## Context

This test has been consistently failing CI quality checks. Root-cause analysis across multiple rounds revealed no single defect, indicating a structural design problem in the test rather than a simple implementation bug. This document is in two parts:

1. **Pre-Fabrication Pipeline** — every step that must complete before `buildToken("ADMIN")` can return a signed JWT string (traced backward from "JWT Fabrication Succeeded" to the initial JVM class-load event).
2. **Post-Fabrication Pipeline** — every step from that signed JWT string through to the final HTTP response assertion.

---

## Part 1: Pre-Fabrication Pipeline — "What must happen before the JWT exists?"

Working backward from **"JWT Fabrication Succeeded"** (Step 14 output = `adminToken` is a non-blank signed string) to the first event in test execution.

### Step Dependency Table

| Step | Stage | Input | Processing | Expected Output | Source / Component |
|------|-------|-------|------------|-----------------|-------------------|
| 1 | **JVM Class Loading — RSA Key Generation** | JVM class-load event | `new RSAKeyGenerator(2048).keyID("test-key").generate()` in static initialiser | `static final TEST_RSA_KEY` — non-null `RSAKey` with both private and public components | Static initialiser block in `PrivilegeControllerIT` |
| 2 | **Docker Availability Check** | Docker daemon socket on the host | `@Testcontainers(disabledWithoutDocker = true)` probes Docker daemon | Docker accessible → test class proceeds; Docker absent → entire class disabled (not failed) | Testcontainers JUnit 5 extension |
| 3 | **PostgreSQL Container Startup** | Docker daemon + `postgres:16` image | Testcontainers pulls and starts PostgreSQL; configures db=`mikemes`, user=`iam_user`, password=`secret` | `POSTGRES.isRunning() == true`; JDBC URL available via `POSTGRES.getJdbcUrl()` | `@Container PostgreSQLContainer` |
| 4 | **Keycloak Container Startup** | Docker daemon + `dasniko/keycloak-containers` default image | Testcontainers pulls and starts Keycloak; admin credentials available | `KEYCLOAK.isRunning() == true`; `KEYCLOAK.getAuthServerUrl()` non-null | `@Container KeycloakContainer` |
| 5 | **Spring Property Override** | Container URLs from Steps 3 + 4 | `@DynamicPropertySource` registers `spring.datasource.url`, `keycloak.admin.server-url`, and sets `spring.security.oauth2.resourceserver.jwt.issuer-uri` to `""` | Spring property registry overridden; datasource points to POSTGRES container; issuer-uri is empty string (disabling OIDC discovery) | `PrivilegeControllerIT.props()` |
| 6 | **Spring Boot Context Startup** | Overridden Spring properties | Spring Boot initialises all beans: `@Import(TestJwtDecoderConfig.class)` registers `@Primary NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey())`; `LocalPrivilegeCache` bean created; `MikeMESJwtAuthenticationConverter` wired; `@EnableMethodSecurity` activates SpEL for `@RequiresPrivilege` | Full `ApplicationContext` loaded; `@Primary` decoder uses the local RSA public key from Step 1 | `@SpringBootTest` + `TestJwtDecoderConfig` |
| 7 | **Flyway Migration Execution** | PostgreSQL container (running) + migration scripts on classpath | Flyway applies V001→V004 in order: schema creation; SYSTEM org + 6 default roles; 4 `iam.*` privileges + ADMIN assignments; Envers audit tables | `iam.role` contains `ADMIN` (system role, org `00000000-…-0001`); `iam.privilege` has 4 `iam.*` rows; `iam.role_privilege` links all 4 to ADMIN | Flyway on Spring Boot startup |
| 8 | **`@BeforeAll` Guard Check** | `KEYCLOAK.isRunning()` boolean | `assumeTrue(KEYCLOAK.isRunning(), "Docker not available")` | Keycloak running → `@BeforeAll` proceeds; Keycloak not running → `TestAbortedException` — entire class skipped, `adminToken` stays `null` | `PrivilegeControllerIT.setupKeycloak()` |
| 9 | **Keycloak Admin Client Construction** | `KEYCLOAK.getAuthServerUrl()`, admin username + password from container | `KeycloakBuilder.builder().serverUrl(...).realm("master").clientId("admin-cli").username(...).password(...).build()` | `Keycloak kcAdmin` — authenticated admin client connected to Keycloak master realm | `KeycloakBuilder` (Keycloak admin client library) |
| 10 | **Realm Creation** | `kcAdmin` + realm name `"mikemes-test"` | `createRealm(kcAdmin)` → constructs `RealmRepresentation`; calls `kcAdmin.realms().create(realm)` (HTTP POST to Keycloak) | HTTP 201 — realm `"mikemes-test"` created in Keycloak | `PrivilegeControllerIT.createRealm()` |
| 11 | **JWT Claims Set Construction** | `SYSTEM_ORG_ID.toString()`, role string `"ADMIN"`, `System.currentTimeMillis() + 3_600_000L` | `JWTClaimsSet.Builder().subject("test-user").claim("org_id", orgId).claim("roles", List.of(role)).expirationTime(new Date(exp)).build()` | `JWTClaimsSet` with 4 claims: `sub`, `org_id` (String), `roles` (List), `exp` | `buildToken()` |
| 12 | **JWS Header Construction** | `JWSAlgorithm.RS256`, key ID literal `"test-key"` | `new JWSHeader.Builder(RS256).keyID("test-key").build()` | `JWSHeader` specifying algorithm RS256 and `kid = "test-key"` | `buildToken()` |
| 13 | **JWT Assembly + RSA Signing** | `JWSHeader` (Step 12) + `JWTClaimsSet` (Step 11) + `TEST_RSA_KEY` private component (Step 1) | `new SignedJWT(header, claims)` → `jwt.sign(new RSASSASigner(TEST_RSA_KEY))` | Signed `SignedJWT` object — header, payload, and RS256 signature | `buildToken()` |
| 14 | **JWT Compact Serialisation** | Signed `SignedJWT` object (Step 13) | `jwt.serialize()` → Base64url-encode header + payload + signature separated by `.` | JWT compact string `eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0.eyJzdWIi…` assigned to `adminToken` | `buildToken()` |

**JWT Fabrication Succeeded** = Step 14 output assigned to `static String adminToken`; value is non-blank.

---

### Test Plan — Per-Step Inputs and Expected Outputs

| Step | Precondition | Specific Test | Expected Pass Condition | Expected Failure Signal |
|------|-------------|---------------|------------------------|------------------------|
| P1 — RSA Key Generated | Nimbus JOSE+JWT on classpath; JVM security provider active | `assertThat(TEST_RSA_KEY).isNotNull()` + `assertThat(TEST_RSA_KEY.isPrivate()).isTrue()` | Non-null key with private component available | `ExceptionInInitializerError` at class load — RSA gen threw `JOSEException` |
| P2 — Docker accessible | Docker daemon running on host | `@Testcontainers(disabledWithoutDocker = true)` passthrough | Test class enabled | Class silently disabled — no "fail", no log entry unless CI is configured to surface skips |
| P3 — PostgreSQL running | Docker accessible (P2) | `assumeTrue(POSTGRES.isRunning(), "PostgreSQL container not running")` | `POSTGRES.isRunning() == true` | `TestAbortedException` — container pull or port-bind failed |
| P4 — Flyway V003 applied | PostgreSQL running (P3) | `SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'iam'` → `assertThat(count).isGreaterThan(0)` | 4 rows present | `count == 0` — V003 did not execute or rolled back silently |
| P5 — Spring context loaded with `@Primary` decoder | P1 + P3 | ApplicationContext loads without exception; verify `@Primary JwtDecoder` bean is `NimbusJwtDecoder` instance backed by `TEST_RSA_KEY.toRSAPublicKey()` | Context loads; decoder bean resolves | `IllegalStateException` (bean conflict) or `IllegalArgumentException` (empty issuer-uri fallback used instead of override) |
| P6 — `@BeforeAll` guard does not skip | P3 | Change guard to `assumeTrue(POSTGRES.isRunning())` — Keycloak is not used by `buildToken` | `@BeforeAll` proceeds to Steps 9–14 | `TestAbortedException` on `assumeTrue(POSTGRES.isRunning())` — skip is correctly attributed to the real dependency |
| P7 — Realm creation succeeded | Keycloak running (P2); `kcAdmin` connected | Assert HTTP status of `kcAdmin.realms().create(realm)` is `201` (per ERR-MES-024) | Realm "mikemes-test" present in Keycloak | `jakarta.ws.rs.WebApplicationException` 409 (realm exists) or 401 (wrong admin credentials) |
| P8 — JWT is a parseable, valid token | P1; Steps 11–14 complete | Parse `adminToken` back with Nimbus and assert claims: `sub == "test-user"`, `org_id == SYSTEM_ORG_ID.toString()`, `roles` contains `"ADMIN"`, `exp` is in the future | All 4 claim assertions pass | `ParseException` — malformed compact serialisation |

---

### Design Flaws Identified

**Flaw 1 — Steps 9 and 10 (Keycloak admin client + realm creation) are in the JWT fabrication critical path but are irrelevant to token signing**

`buildToken("ADMIN")` needs only `TEST_RSA_KEY` (Step 1). Steps 9 and 10 are called unconditionally before `buildToken` in `@BeforeAll`. If `createRealm` fails — Keycloak connection timeout, realm already exists from a leaked container, or HTTP 409 — `@BeforeAll` throws, `adminToken` is never assigned, and every test in the class reports a `@BeforeAll` failure with a Keycloak error message that has nothing to do with JWT construction or privilege lookup.

**Flaw 2 — Step 8 guard `assumeTrue(KEYCLOAK.isRunning())` skips the wrong unit and leaves `adminToken = null`**

If Keycloak fails to start but PostgreSQL starts successfully, `assumeTrue(KEYCLOAK.isRunning())` aborts `@BeforeAll` as a `TestAbortedException`. `adminToken` and `viewerToken` remain `null`. All test methods then receive a `NullPointerException` in `bearerHeaders()` or report an uninformative skip. The correct guard for `buildToken` is `assumeTrue(POSTGRES.isRunning())` — Keycloak is not a dependency of JWT fabrication.

**Flaw 3 — Step 5: `issuer-uri` set to `""` silently masks a missing configuration**

```java
registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
```

This prevents Spring from attempting OIDC discovery against Keycloak, which is correct because the `@Primary` decoder override handles token validation. However, if `TestJwtDecoderConfig` is ever removed, Spring falls back to this empty-string `issuer-uri` and throws `IllegalArgumentException: issuerUri cannot be empty` at context startup. There is no test that verifies the `@Primary` override is actually in effect — the empty string is a silent safety net that fails loudly only when the override is absent.

**Flaw 4 — Step 12: key ID `"test-key"` is invisible to the `@Primary` decoder**

`NimbusJwtDecoder.withPublicKey(rsa)` validates the RS256 signature but ignores the `kid` claim in the JWS header. The `"test-key"` set in Step 12 is never checked. This is safe for a single-key test but creates a latent fragility: if the decoder is ever changed to use a JWK Set URI (to support key rotation), it will match tokens by `kid`. Any mismatch between the `kid` set here and a `kid` in the JWK set will cause 401 failures with no obvious connection to the key ID set in Step 12.

**Flaw 5 — Step 7: Flyway execution outcome is not verified before `@BeforeAll` proceeds**

Flyway runs as part of Spring context startup. If V003 fails or is skipped (e.g., checksum mismatch, baseline conflict), the Spring context still loads successfully — Flyway logs the failure but does not halt startup unless configured with strict `outOfOrder=false` and `validateOnMigrate=true` in error mode. By the time `@BeforeAll` runs, V003 data may be absent. No test assertion checks that Flyway completed V003 before attempting to call the API.

**Flaw 6 — Step 11: `roles` claim serialised as `List` but deserialised type is runtime-unverified**

```java
.claim("roles", List.of(role))
```

Nimbus serialises this to a JSON array and deserialises it as `net.minidev.json.JSONArray`. In `JwtClaimsExtractor.getRoles()`, the `instanceof List<?>` check passes (JSONArray implements List), and the unchecked cast to `List<String>` is suppressed with `@SuppressWarnings`. This works for String role values but would silently return wrong types if any claim element were a non-String object. There is no assertion in the test that the roles claim round-trips correctly through serialise→deserialise.

---

---

## Part 2: Post-Fabrication Pipeline — "What happens after the JWT exists?"

Starting input is the signed JWT string (`adminToken`) produced by Part 1 Step 14.

### Step Dependency Table

Each row shows the stage name, its inputs, the processing performed, and the expected output. The output of each row is the input of the next.

| Step | Stage | Input | Processing | Expected Output | Source / Component |
|------|-------|-------|------------|-----------------|-------------------|
| 1 | **JWT Fabrication** | `TEST_RSA_KEY` + claims `{sub: "test-user", org_id: SYSTEM_ORG_ID, roles: ["ADMIN"]}` | Build `JWTClaimsSet`, RSA-sign with RS256 | Signed JWT string | `buildToken("ADMIN")` in `@BeforeAll` |
| 2 | **HTTP Request Assembly** | JWT string + path `/privileges` | Set `Authorization: Bearer <jwt>` header, build GET request | HTTP GET `/privileges` with auth header | `get()` helper → `bearerHeaders()` |
| 3 | **JWT Decode & Verify** | HTTP request with Bearer token | `NimbusJwtDecoder` validates RS256 signature against `TEST_RSA_KEY.toRSAPublicKey()` | `Jwt` object with verified claims intact | `TestJwtDecoderConfig` `@Primary` bean override |
| 4 | **Role → Authority Expansion** | `Jwt` with `roles=["ADMIN"]` | `MikeMESJwtAuthenticationConverter`: extract `org_id` → for each role call `LocalPrivilegeCache.getPrivilegesForRole(role)` → DB lookup | `JwtAuthenticationToken` with `GrantedAuthority("iam:roles:manage")` | `roleRepository.findByName("ADMIN")` + `rolePrivilegeRepository.findActiveByRoleId(roleId)` — **depends on V002 + V003** |
| 5 | **Method Authorization** | `JwtAuthenticationToken` with granted authorities + `@RequiresPrivilege("iam:roles:manage")` | Spring Security SpEL: `hasAuthority('iam:roles:manage')` evaluates against token authorities | Allow → proceed; Deny → HTTP 403 | `@PreAuthorize("hasAuthority('{value}')")` on meta-annotation — **SpEL `{value}` substitution must resolve** |
| 6 | **Data Retrieval** | Authenticated request (Step 5 passed) | `privilegeService.listByModule()` → `privilegeRepository.findAll()` → `Collectors.groupingBy(moduleName)` | `Map<String, List<Privilege>>` containing key `"iam"` | DB query — **depends on V003 seed data** |
| 7 | **Response Serialization** | `Map<String, List<PrivilegeResponse>>` | Jackson serializes → HTTP 200 with JSON body | `{"iam": [...]}` | Spring MVC + `PrivilegeController.toResponse()` |
| 8 | **Test Assertions** | `ResponseEntity<Map>` | AssertJ checks on status + body | Status `200 OK`, body `containsKey("iam")` | Assertions A1, A2, A3 in test method |

---

### Test Plan — Per-Step Inputs and Expected Outputs

| Step | Precondition | Specific Test | Expected Pass Condition | Expected Failure Signal |
|------|-------------|---------------|------------------------|------------------------|
| P1 — JWT Fabrication | `TEST_RSA_KEY` generated in static initialiser | `assertThat(adminToken).isNotBlank()` | Token is a non-empty base64url-encoded string | `RuntimeException` from `buildToken()` — RSA key generation failed |
| P2 — DB precondition: ADMIN role has `iam:roles:manage` | V002 + V003 Flyway migrations applied | `SELECT COUNT(*) FROM iam.role_privilege rp JOIN iam.role r ON r.id=rp.role_id JOIN iam.privilege p ON p.id=rp.privilege_id WHERE r.name='ADMIN' AND r.is_system_role=true AND p.privilege_key='iam:roles:manage' AND rp.revoked_at IS NULL` | `count > 0` | `count == 0` means V002 or V003 did not seed correctly |
| P3 — DB precondition: iam module privileges exist | V003 Flyway migration applied | `SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'iam'` | `count > 0` | `count == 0` means V003 did not run |
| P4 — JWT decode does not return 401 | Steps P1+P2 pass | Assert response status is NOT `401 UNAUTHORIZED` | HTTP status is not 401 | 401 means `NimbusJwtDecoder` rejected the token signature |
| P5 — Authorization passes, not 403 | Steps P2+P3 pass | `assertThat(response.getStatusCode()).as("ADMIN with iam:roles:manage must pass @RequiresPrivilege").isEqualTo(HttpStatus.OK)` | HTTP 200 | 403 means either Step 4 produced empty authorities (DB issue) or Step 5 SpEL resolution failed |
| P6 — Response body contains `"iam"` module | Steps P3+P5 pass | `assertThat(response.getBody()).as("iam module must be grouped in response").containsKey("iam")` | Map key `"iam"` present | Key absent means `privilegeRepository.findAll()` returned no `iam` rows |

---

### Design Flaws Identified

**Flaw 1 — Step 4: Silent authority starvation (most dangerous)**

`LocalPrivilegeCache.getPrivilegesForRole("ADMIN")` makes two DB queries:
- `roleRepository.findByName("ADMIN")` — depends on V002
- `rolePrivilegeRepository.findActiveByRoleId(roleId)` — depends on V003

If either returns empty, **no exception is thrown**. The converter silently produces a `JwtAuthenticationToken` with zero granted authorities. Step 5 then returns 403, which is indistinguishable from a wrong-role failure. Without P2 in place, there is no way to tell whether the 403 came from:
- Missing DB data (V002/V003 issue)
- The Hibernate 6.5 implicit-join bug returning 0 rows
- A SpEL resolution failure (see Flaw 2)

**Flaw 2 — Step 5: `@RequiresPrivilege` SpEL `{value}` substitution is Spring Security version-dependent**

```java
@PreAuthorize("hasAuthority('{value}')")
public @interface RequiresPrivilege { String value(); }
```

Annotation template substitution for `@PreAuthorize` on a meta-annotation requires Spring Security **6.3+** (`AnnotationTemplateExpressionDefaults`). On Spring Security 6.0–6.2, `{value}` is treated as a literal string. The check becomes `hasAuthority('{value}')`, which never matches any authority. The result is a permanent HTTP 403 regardless of what roles the token carries — a silent misconfiguration with no error log entry.

**Flaw 3 — Steps 4 and 6 share the same implicit DB dependency**

Both the authorization check (Step 4: does ADMIN have `iam:roles:manage`?) and the data assertion (Step 6: is `"iam"` in the response body?) depend entirely on V003 having run correctly. A single migration failure breaks both assertions with different symptoms (403 vs 200-but-empty). The current test cannot distinguish between these two cases.

**Flaw 4 — Single test conflates two independent concerns**

The test name implies one concern: "admin token returns 200 with iam module." It actually exercises two separate pipelines:
1. Whether the token authorization pathway works end-to-end (Steps 3→5)
2. Whether the data retrieval pathway returns the expected module key (Steps 6→7)

These have entirely different failure modes. Without intermediate assertions they cannot be distinguished from the test output alone.

**Flaw 5 — `assumeTrue(KEYCLOAK.isRunning())` is the wrong guard for this test**

The test builds tokens using a local RSA key — it never requests a token from Keycloak. `KEYCLOAK.isRunning()` is used as a proxy for "Docker is available," but the correct guard is `assumeTrue(POSTGRES.isRunning())`. On a machine with PostgreSQL available but Keycloak failing to start, the test is silently **skipped** rather than failing — masking real failures in CI reports as skips.

---

## Proposed Redesign — Explicitly Staged Test

Applying the step-validated test plan above, the test should be restructured as:

```java
@Test
void listPrivileges_withAdminToken_returns200WithIamModule() {
    assumeTrue(POSTGRES.isRunning(), "Docker not available — PostgreSQL not running");

    // P1: JWT was fabricated successfully
    assertThat(adminToken).as("adminToken must have been built in @BeforeAll").isNotBlank();

    // P2: DB precondition — ADMIN role has iam:roles:manage assigned
    Integer adminPrivCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.role_privilege rp " +
        "JOIN iam.role r ON r.id = rp.role_id " +
        "JOIN iam.privilege p ON p.id = rp.privilege_id " +
        "WHERE r.name = 'ADMIN' AND r.is_system_role = true " +
        "AND p.privilege_key = 'iam:roles:manage' AND rp.revoked_at IS NULL",
        Integer.class);
    assertThat(adminPrivCount)
        .as("V002 + V003 must have seeded ADMIN → iam:roles:manage")
        .isGreaterThan(0);

    // P3: DB precondition — iam module privileges exist
    Integer iamPrivCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'iam'",
        Integer.class);
    assertThat(iamPrivCount)
        .as("V003 must have seeded iam.* privileges")
        .isGreaterThan(0);

    // P4 + P5: Call API — assert 200 (not 401, not 403)
    ResponseEntity<Map> response = get("/privileges", adminToken,
            new ParameterizedTypeReference<>() {});

    assertThat(response.getStatusCode())
        .as("JWT should decode — not 401")
        .isNotEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getStatusCode())
        .as("ADMIN with iam:roles:manage must pass @RequiresPrivilege — not 403")
        .isEqualTo(HttpStatus.OK);

    // P6: Assert response body contains iam module
    assertThat(response.getBody())
        .as("iam module must be grouped in response body")
        .isNotNull()
        .containsKey("iam");
}
```

This design ensures each failure message identifies the exact stage that broke, rather than requiring a developer to trace through four layers to find the root cause.
