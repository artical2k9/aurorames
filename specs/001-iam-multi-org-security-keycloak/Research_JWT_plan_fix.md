# Fix Plan: JWT Pipeline — Zero Silent Failures

## Executive Summary

Eleven design flaws were identified across the pre-fabrication and post-fabrication pipelines for `listPrivileges_withAdminToken_returns200WithIamModule()`. They share a single structural root cause: **every stage hands off to the next by implicit convention rather than explicit assertion**, so any failure anywhere in the 17-step chain surfaces as an ambiguous test result with no pointer to which stage actually broke.

The fix strategy is threefold:
1. **Decouple stages** — separate `@BeforeAll` concerns so Keycloak setup can never block JWT fabrication
2. **Assert every handshake** — each stage's output must be explicitly verified before it is consumed as input by the next stage
3. **Name every failure mode** — every `assertThat()` call carries an `.as()` message that identifies the stage and the likely root cause

---

## Development Plan — Flaw-by-Flaw Fix Table

| # | Part | Flaw | Root Cause | Fix | Code Location |
|---|------|------|-----------|-----|---------------|
| F1 | Pre | `createRealm()` is in the JWT fabrication critical path but has no bearing on token signing | `@BeforeAll` calls `createRealm()` before `buildToken()` unconditionally — a Keycloak failure aborts token fabrication | Split `setupKeycloak()` into two `@BeforeAll` methods: `buildTestTokens()` (no container dependency) `@Order(1)` and `setupKeycloak()` (guarded on Keycloak) `@Order(2)` | `PrivilegeControllerIT.java` — `@BeforeAll` |
| F2 | Pre | Guard `assumeTrue(KEYCLOAK.isRunning())` skips the entire class when Keycloak is absent, leaving `adminToken = null` | JWT fabrication needs only `TEST_RSA_KEY` — Keycloak is irrelevant, but the guard treats it as a hard dependency | Change the guard on token-building path to `assertThat(POSTGRES.isRunning())` (hard fail, not skip); change all test-method guards from `KEYCLOAK` to `POSTGRES` | `PrivilegeControllerIT.java` — `@BeforeAll` + all test methods |
| F3 | Pre | `issuer-uri = ""` silently masks a missing `@Primary` decoder — if the override is removed, context fails with an opaque `IllegalArgumentException` | Correct intent (disable OIDC discovery) is implemented via an invisible side-effect of empty string | Replace `""` with `"http://test-issuer-not-used.local"` (loud sentinel); add a dedicated test that asserts the `@Primary` bean IS a `NimbusJwtDecoder` instance | `PrivilegeControllerIT.java` — `@DynamicPropertySource` + new test method |
| F4 | Pre | Key ID `"test-key"` in the JWS header is not validated by the `@Primary` decoder — latent drift risk if decoder is changed to JWK Set URI | `NimbusJwtDecoder.withPublicKey()` ignores `kid`; the literal string is duplicated in two places with no shared constant | Extract `static final String TEST_KEY_ID = "test-key"` and use it in both `RSAKeyGenerator` and `JWSHeader.Builder`; add round-trip parse assertion checking `kid == TEST_KEY_ID` | `PrivilegeControllerIT.java` — static constant + `buildToken()` + `buildTestTokens()` assertion |
| F5 | Pre | Flyway V003 outcome is not verified — if migrations fail silently, every downstream assertion fails for opaque reasons | Flyway logs migration failure but does not halt Spring Boot context startup unless strict mode is configured | Add JDBC assertion in `buildTestTokens()` after context starts: verify exactly 4 `iam.*` privilege rows exist in the DB | `PrivilegeControllerIT.java` — `buildTestTokens()` |
| F6 | Pre | `roles` claim serialised as `List.of(role)` but Nimbus deserialises it as `net.minidev.json.JSONArray` — round-trip type is unchecked | `@SuppressWarnings("unchecked")` in `JwtClaimsExtractor.getRoles()` masks any type divergence at runtime | Add parse-back assertions in `buildTestTokens()` after token fabrication: parse `adminToken`, assert `roles` claim is a `List` and contains `"ADMIN"` | `PrivilegeControllerIT.java` — `buildTestTokens()` |
| F7 | Post | `LocalPrivilegeCache.getPrivilegesForRole()` returns empty with no exception when DB data is absent — converter silently produces zero authorities, which is indistinguishable from a wrong-role token | `resolveAuthorities()` calls `getPrivilegesForRole()` inside a `for` loop with no null/empty guard; empty set is a valid return | Add JDBC precondition check before the HTTP call: assert ADMIN role has `iam:roles:manage` with `revoked_at IS NULL` | `PrivilegeControllerIT.java` — test method body |
| F8 | Post | `@RequiresPrivilege` SpEL `{value}` substitution requires Spring Security 6.3+ — on earlier versions, `{value}` is treated as a literal string producing permanent HTTP 403 | `@PreAuthorize("hasAuthority('{value}')")` on a meta-annotation uses `AnnotationTemplateExpressionDefaults` only available from Spring Security 6.3 | Staged assertion design implicitly proves SpEL is working: P2 DB check confirms privilege exists in DB; P5 HTTP 200 assertion confirms SpEL evaluated it correctly. Add a comment to `RequiresPrivilege.java` documenting the Spring Security version requirement | `RequiresPrivilege.java` — annotation comment; `PrivilegeControllerIT.java` — staged assertions |
| F9 | Post | Auth concern (Steps 3–5) and data concern (Steps 6–7) share the same implicit V003 dependency — a migration failure breaks both with different symptoms | DB precondition check for auth and DB precondition check for data were the same single implicit dependency | Add two separate JDBC precondition checks with distinct failure messages: one for "ADMIN has `iam:roles:manage`" (auth) and one for "iam module privileges exist" (data) | `PrivilegeControllerIT.java` — test method body |
| F10 | Post | Single test conflates two independent concerns — HTTP 200 (auth) vs. body `containsKey("iam")` (data) — failure is ambiguous | Both assertions are in the same test with no intermediate stage markers | Add `.as()` message to every assertion naming the stage; sequence assertions so HTTP-status check always precedes body check — if HTTP fails, body check is never reached | `PrivilegeControllerIT.java` — test method body |
| F11 | Post | `createRealm()` does not assert HTTP 201 from Keycloak — a silent 409 or 401 leaves the realm in an unknown state | `kcAdmin.realms().create(realm)` return value is discarded | Capture `Response r = kcAdmin.realms().create(realm)` in try-with-resources; assert `r.getStatus() == 201` per ERR-MES-024 | `PrivilegeControllerIT.java` — `createRealm()` |

---

## Proposed `@BeforeAll` Architecture

The critical structural change is splitting the single `setupKeycloak()` method into three ordered `@BeforeAll` methods so that each stage can fail independently without blocking unrelated stages.

```
@BeforeAll @Order(1) buildTestTokens()
  ├── Assert: TEST_RSA_KEY is non-null and has private component     [F4 handshake]
  ├── Assert: Flyway V003 seeded exactly 4 iam.* privileges          [F5 handshake]
  ├── adminToken = buildToken("ADMIN")
  ├── viewerToken = buildToken("VIEWER")
  ├── Assert: adminToken is not blank                                [F1/F2 handshake]
  └── Assert: parse adminToken back — kid, roles, org_id, exp       [F4/F6 handshake]

@BeforeAll @Order(2) setupKeycloak()
  ├── assumeTrue(KEYCLOAK.isRunning())     ← isolated to Keycloak setup only
  └── createRealm(kcAdmin)
      └── Assert: response.getStatus() == 201                       [F11 handshake]

No other @BeforeAll method — buildTestTokens() is self-contained.
```

---

## Key Code Sections

### 1. Static Constants

```java
// Extract key ID as a shared constant — used in both RSAKeyGenerator and JWSHeader.Builder
// so they can never silently drift apart (F4)
static final String TEST_KEY_ID = "test-key";

static final RSAKey TEST_RSA_KEY;
static {
    try {
        TEST_RSA_KEY = new RSAKeyGenerator(2048).keyID(TEST_KEY_ID).generate();
    } catch (JOSEException e) {
        throw new RuntimeException("Failed to generate RSA test key", e);
    }
}
```

### 2. `@DynamicPropertySource` — issuer-uri sentinel (F3)

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // Non-empty sentinel: disables OIDC discovery without hiding a missing @Primary decoder.
    // If TestJwtDecoderConfig is ever removed, Spring will attempt to resolve this URL
    // and fail loudly rather than silently accepting an empty string.
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                 () -> "http://test-issuer-not-used.local");
    registry.add("keycloak.admin.server-url", KEYCLOAK::getAuthServerUrl);
    registry.add("keycloak.admin.realm",      () -> TEST_REALM);
    registry.add("keycloak.admin.username",   KEYCLOAK::getAdminUsername);
    registry.add("keycloak.admin.password",   KEYCLOAK::getAdminPassword);
}
```

### 3. `buildTestTokens()` — decoupled, self-verifying (F1, F2, F4, F5, F6)

```java
@BeforeAll
@Order(1)
static void buildTestTokens(@Autowired JdbcTemplate jdbc) throws Exception {
    // Handshake 1: RSA key was generated in static initialiser with private component
    assertThat(TEST_RSA_KEY)
        .as("TEST_RSA_KEY must have been generated in the static initialiser")
        .isNotNull();
    assertThat(TEST_RSA_KEY.isPrivate())
        .as("TEST_RSA_KEY must include the private component required for JWT signing")
        .isTrue();

    // Handshake 2: Flyway V003 applied — iam.* privileges are present in DB
    // This must be confirmed before any HTTP call that exercises authorization.
    Integer iamPrivCount = jdbc.queryForObject(
        "SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'iam'", Integer.class);
    assertThat(iamPrivCount)
        .as("Flyway V003 must have seeded exactly 4 iam.* privileges — if 0, migration did not apply")
        .isEqualTo(4);

    // Build tokens — no Docker or Keycloak dependency, only TEST_RSA_KEY is needed
    adminToken  = buildToken("ADMIN");
    viewerToken = buildToken("VIEWER");

    // Handshake 3: tokens are non-blank signed strings
    assertThat(adminToken)
        .as("adminToken must be a non-blank JWT string — buildToken() must not have thrown")
        .isNotBlank();
    assertThat(viewerToken)
        .as("viewerToken must be a non-blank JWT string — buildToken() must not have thrown")
        .isNotBlank();

    // Handshake 4: round-trip parse — kid, roles claim type, org_id, expiry
    SignedJWT parsed = SignedJWT.parse(adminToken);
    assertThat(parsed.getHeader().getKeyID())
        .as("JWT kid header must match TEST_KEY_ID constant — prevents silent key-ID drift")
        .isEqualTo(TEST_KEY_ID);
    Object roles = parsed.getJWTClaimsSet().getClaim("roles");
    assertThat(roles)
        .as("roles claim must deserialise as a List (not null or unexpected type)")
        .isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<String> roleList = (List<String>) roles;
    assertThat(roleList)
        .as("roles claim must contain the fabricated role 'ADMIN'")
        .contains("ADMIN");
    assertThat(parsed.getJWTClaimsSet().getStringClaim("org_id"))
        .as("org_id claim must round-trip as the SYSTEM_ORG_ID string")
        .isEqualTo(SYSTEM_ORG_ID.toString());
    assertThat(parsed.getJWTClaimsSet().getExpirationTime())
        .as("JWT expiry must be in the future — token must be valid when tests run")
        .isAfter(new Date());
}
```

### 4. `setupKeycloak()` — isolated, with realm creation handshake (F1, F2, F11)

```java
@BeforeAll
@Order(2)
static void setupKeycloak() {
    // This method is guarded independently — a Keycloak failure never prevents
    // buildTestTokens() from running since @Order(1) guarantees it runs first.
    assumeTrue(KEYCLOAK.isRunning(),
        "Keycloak container not running — skipping Keycloak realm setup. " +
        "Token fabrication (buildTestTokens) is unaffected.");

    Keycloak kcAdmin = KeycloakBuilder.builder()
            .serverUrl(KEYCLOAK.getAuthServerUrl())
            .realm("master")
            .clientId("admin-cli")
            .username(KEYCLOAK.getAdminUsername())
            .password(KEYCLOAK.getAdminPassword())
            .build();
    try {
        createRealm(kcAdmin);
    } finally {
        kcAdmin.close();
    }
}
```

### 5. `createRealm()` — with HTTP status handshake (F11, ERR-MES-024)

```java
static void createRealm(Keycloak kcAdmin) {
    RealmRepresentation realm = new RealmRepresentation();
    realm.setRealm(TEST_REALM);
    realm.setEnabled(true);
    realm.setDirectGrantFlow("direct grant");
    try (jakarta.ws.rs.core.Response r = kcAdmin.realms().create(realm)) {
        assertThat(r.getStatus())
            .as("Keycloak realm creation must return HTTP 201 — " +
                "409 means realm already exists (leaked container state), " +
                "401 means wrong admin credentials")
            .isEqualTo(201);
    }
}
```

### 6. `buildToken()` — using TEST_KEY_ID constant (F4)

```java
static String buildToken(String role) {
    try {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("org_id", SYSTEM_ORG_ID.toString())
                .claim("roles", List.of(role))
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000L))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KEY_ID).build(),
                claims);
        jwt.sign(new RSASSASigner(TEST_RSA_KEY));
        return jwt.serialize();
    } catch (Exception e) {
        throw new RuntimeException("buildToken failed for role=" + role, e);
    }
}
```

### 7. Decoder verification test (F3)

```java
@Autowired
JwtDecoder jwtDecoder;

@Test
void testConfiguration_primaryJwtDecoder_isRsaBackedNotAutoConfigured() {
    // Verifies that TestJwtDecoderConfig @Primary bean is active.
    // If this fails, the issuer-uri sentinel in @DynamicPropertySource would cause
    // Spring to attempt OIDC discovery against "http://test-issuer-not-used.local",
    // producing an opaque connection-refused failure on every token validation.
    assertThat(jwtDecoder)
        .as("@Primary JwtDecoder must be the test RSA-key-backed NimbusJwtDecoder, " +
            "not the auto-configured OIDC decoder")
        .isInstanceOf(NimbusJwtDecoder.class);
}
```

### 8. Restructured test method with staged assertions (F7, F8, F9, F10)

```java
@Test
void listPrivileges_withAdminToken_returns200WithIamModule() {
    assumeTrue(POSTGRES.isRunning(), "PostgreSQL container not running — skipping test");

    // Stage 1 — Token handshake: confirm @BeforeAll produced a valid token
    assertThat(adminToken)
        .as("adminToken must not be null — @BeforeAll buildTestTokens() must have succeeded")
        .isNotBlank();

    // Stage 2 — Auth precondition: ADMIN role has iam:roles:manage in the DB.
    // If this count is 0, Step 4 (Role→Authority Expansion) will silently produce
    // an empty authority set, causing HTTP 403 with no diagnostic signal.
    Integer adminRolePrivCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.role_privilege rp " +
        "JOIN iam.role r ON r.id = rp.role_id " +
        "JOIN iam.privilege p ON p.id = rp.privilege_id " +
        "WHERE r.name = 'ADMIN' AND r.is_system_role = true " +
        "AND p.privilege_key = 'iam:roles:manage' AND rp.revoked_at IS NULL",
        Integer.class);
    assertThat(adminRolePrivCount)
        .as("ADMIN role must have iam:roles:manage assigned and active (V002+V003 migration) — " +
            "if 0, LocalPrivilegeCache will return an empty authority set and the API will return 403")
        .isGreaterThan(0);

    // Stage 3 — Data precondition: iam module privileges exist in the DB.
    // Separate from Stage 2 — this validates the data retrieval path, not the auth path.
    Integer iamModuleCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.privilege WHERE module_name = 'iam'",
        Integer.class);
    assertThat(iamModuleCount)
        .as("iam module privileges must exist in DB (V003 migration) — " +
            "if 0, the response body will not contain the 'iam' key even if HTTP 200 is returned")
        .isGreaterThan(0);

    // Stage 4 — HTTP call
    ResponseEntity<Map> response = get("/privileges", adminToken,
            new ParameterizedTypeReference<>() {});

    // Stage 5 — JWT decode handshake: token was accepted (not rejected as invalid)
    assertThat(response.getStatusCode())
        .as("JWT signature must be valid and accepted by NimbusJwtDecoder — " +
            "HTTP 401 means the @Primary decoder did not recognise the token signature")
        .isNotEqualTo(HttpStatus.UNAUTHORIZED);

    // Stage 6 — Authorization handshake: ADMIN authority passed @RequiresPrivilege check
    // If this fails with 403 after Stage 2 passed, the likely cause is SpEL {value}
    // substitution failing on Spring Security < 6.3 (see RequiresPrivilege.java comment).
    assertThat(response.getStatusCode())
        .as("ADMIN with iam:roles:manage must pass @RequiresPrivilege(\"iam:roles:manage\") — " +
            "HTTP 403 after Stage 2 passed indicates SpEL {value} substitution is not resolving " +
            "(requires Spring Security 6.3+)")
        .isEqualTo(HttpStatus.OK);

    // Stage 7 — Data retrieval handshake: iam module present in grouped response
    assertThat(response.getBody())
        .as("Response body must contain the 'iam' module key — " +
            "if absent after Stage 3 passed, privilegeRepository.findAll() or groupingBy failed")
        .isNotNull()
        .containsKey("iam");
}
```

### 9. `RequiresPrivilege.java` — version documentation (F8)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
// {value} template substitution requires Spring Security 6.3+ (AnnotationTemplateExpressionDefaults).
// On Spring Security 6.0–6.2, {value} is a literal string and hasAuthority('{value}') always returns
// false, causing HTTP 403 for all callers regardless of their privileges.
@PreAuthorize("hasAuthority('{value}')")
public @interface RequiresPrivilege {
    String value();
}
```

---

## Final Combined Pipeline — Zero Silent Failures

The table below shows the complete end-to-end pipeline from JVM class loading through to the final HTTP assertion. For every stage, the output of that stage is explicitly verified before being consumed as input by the next stage. No handshake is implicit.

| Step | Phase | Stage | Input (from previous step) | Processing | Output | Handshake Assertion — What breaks if this step fails |
|------|-------|-------|---------------------------|------------|--------|-----------------------------------------------------|
| 1 | Pre | **JVM Class Loading — RSA Key Generation** | JVM class-load event | `new RSAKeyGenerator(2048).keyID(TEST_KEY_ID).generate()` in static initialiser | `TEST_RSA_KEY`: non-null `RSAKey` with private + public key material | `assertThat(TEST_RSA_KEY).isNotNull()` + `assertThat(TEST_RSA_KEY.isPrivate()).isTrue()` — in `buildTestTokens()`. Fails: `ExceptionInInitializerError` at class load if `JOSEException` thrown |
| 2 | Pre | **Container Startup** | Docker daemon socket | `@Testcontainers` starts `POSTGRES` and (optionally) `KEYCLOAK` containers | `POSTGRES.isRunning() == true`; JDBC URL available | `assumeTrue(POSTGRES.isRunning(), "PostgreSQL container must be running")` — in every test method. Fails: `TestAbortedException` (skip, not fail) correctly attributed to Docker/PostgreSQL |
| 3 | Pre | **Spring Property Override** | `POSTGRES.getJdbcUrl()` + sentinel `"http://test-issuer-not-used.local"` | `@DynamicPropertySource` registers `spring.datasource.url` and `issuer-uri` sentinel | Spring properties overridden; OIDC discovery aimed at a non-existent host (loud failure if `@Primary` decoder is absent) | Spring context startup completes without `IllegalArgumentException`. Fails loudly if `TestJwtDecoderConfig` is removed and Spring attempts OIDC discovery against the sentinel URL |
| 4 | Pre | **Spring Boot Context + `@Primary` Decoder Registration** | Overridden properties + `TEST_RSA_KEY.toRSAPublicKey()` | Spring Boot starts beans; `TestJwtDecoderConfig @Primary NimbusJwtDecoder.withPublicKey(rsaPublicKey)` registered; `LocalPrivilegeCache` and `MikeMESJwtAuthenticationConverter` wired | Full `ApplicationContext`; `@Primary` decoder validates tokens with the local RSA public key | `assertThat(jwtDecoder).isInstanceOf(NimbusJwtDecoder.class)` — in `testConfiguration_primaryJwtDecoder_isRsaBackedNotAutoConfigured()` test. Fails: auto-configured OIDC decoder is active; all token validations will fail with 401 |
| 5 | Pre | **Flyway Migration Execution** | PostgreSQL container + classpath V001→V004 | Flyway applies V001→V004: schema, SYSTEM org, 6 roles, 4 `iam.*` privileges + ADMIN assignments, Envers tables | `iam.privilege` has exactly 4 `iam.*` rows; `iam.role_privilege` links all 4 to ADMIN | `assertThat(iamPrivCount).as("V003 must have seeded exactly 4 iam.* privileges").isEqualTo(4)` — in `buildTestTokens()`. Fails: any subsequent DB precondition check catches V003 absence with a named message |
| 6 | Pre | **JWT Claims Construction** | `SYSTEM_ORG_ID.toString()`, `"ADMIN"`, `now + 3600s` | `JWTClaimsSet.Builder().subject("test-user").claim("org_id",...).claim("roles", List.of("ADMIN")).expirationTime(...).build()` | `JWTClaimsSet` with 4 claims: `sub`, `org_id`, `roles`, `exp` | Verified in Step 8 round-trip parse: `org_id` == `SYSTEM_ORG_ID.toString()`, `exp` is after `new Date()` |
| 7 | Pre | **JWS Header Construction** | `JWSAlgorithm.RS256`, `TEST_KEY_ID` constant | `new JWSHeader.Builder(RS256).keyID(TEST_KEY_ID).build()` | `JWSHeader(RS256, kid = TEST_KEY_ID)` | Verified in Step 8 round-trip parse: `parsed.getHeader().getKeyID() == TEST_KEY_ID`. Fails: `kid` mismatch detected immediately rather than as an opaque 401 if decoder changes |
| 8 | Pre | **JWT Signing + Compact Serialisation** | `JWSHeader` (Step 7) + `JWTClaimsSet` (Step 6) + `TEST_RSA_KEY` private key (Step 1) | `jwt.sign(new RSASSASigner(TEST_RSA_KEY))` → `jwt.serialize()` | `adminToken`: compact JWT string `eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5In0.eyJzdWIi…` | `assertThat(adminToken).isNotBlank()` + full round-trip parse: `kid`, `roles` is `List<String>` containing `"ADMIN"`, `org_id`, `exp` — all in `buildTestTokens()`. Fails: `RuntimeException("buildToken failed for role=ADMIN")` from `buildToken()` catch block |
| 9 | Pre | **Keycloak Realm Creation** *(independent — does not block Steps 1–8)* | `KEYCLOAK.isRunning()` | `assumeTrue(KEYCLOAK.isRunning())` in isolated `@BeforeAll @Order(2) setupKeycloak()`; `createRealm(kcAdmin)` | Realm `"mikemes-test"` created in Keycloak (HTTP 201) | `assertThat(r.getStatus()).as("Realm creation must return 201").isEqualTo(201)` — per ERR-MES-024. Fails loudly if Keycloak returns 409 (leaked state) or 401 (bad credentials); does NOT affect `adminToken` |
| 10 | Post | **Token Handshake at Test Entry** | `adminToken` (from Step 8) | Read `adminToken` field; verify `@BeforeAll` completed | Non-blank JWT string ready for HTTP call | `assertThat(adminToken).as("adminToken must not be null — @BeforeAll buildTestTokens() must have succeeded").isNotBlank()` — first line of test method. Fails: `@BeforeAll` threw before assigning `adminToken` |
| 11 | Post | **DB Precondition: ADMIN → `iam:roles:manage`** | `jdbcTemplate` (from Spring context, Step 4) | JDBC query: count active `role_privilege` rows linking ADMIN to `iam:roles:manage` | `count ≥ 1` | `assertThat(adminRolePrivCount).as("ADMIN role must have iam:roles:manage — if 0, LocalPrivilegeCache returns empty authorities and the API returns 403").isGreaterThan(0)` — before HTTP call. Fails: exposes Flyway/data issue before it becomes an ambiguous 403 |
| 12 | Post | **DB Precondition: `iam` module privileges exist** | `jdbcTemplate` (from Spring context, Step 4) | JDBC query: count `iam.privilege` rows with `module_name = 'iam'` | `count ≥ 1` | `assertThat(iamModuleCount).as("iam module privileges must exist — if 0, response body will not contain 'iam' key even if HTTP 200").isGreaterThan(0)` — before HTTP call, after Step 11. Fails: separately from Step 11, proving data retrieval path is broken, not the auth path |
| 13 | Post | **HTTP Request Assembly** | `adminToken` (verified in Step 10) + path `/privileges` | `bearerHeaders(adminToken)` sets `Authorization: Bearer <token>`; `restTemplate.exchange(...)` sends HTTP GET | HTTP GET `/privileges` with auth header sent to Spring Boot test server | Implicit: if `adminToken` is blank, Step 10 would have already failed. HTTP call will not proceed with a null/blank token |
| 14 | Post | **JWT Decode & Verify** | HTTP request with Bearer token | `NimbusJwtDecoder.withPublicKey(TEST_RSA_KEY.toRSAPublicKey())` validates RS256 signature | `Jwt` object with verified claims; Spring Security context populated | `assertThat(response.getStatusCode()).as("JWT signature must be accepted — HTTP 401 means the @Primary decoder rejected the token").isNotEqualTo(HttpStatus.UNAUTHORIZED)` — first assertion after HTTP call. Fails: 401 means decoder or key mismatch |
| 15 | Post | **Role → Authority Expansion** | `Jwt` with `roles = ["ADMIN"]` (decoded) | `MikeMESJwtAuthenticationConverter` extracts `org_id`; for role `"ADMIN"` calls `LocalPrivilegeCache.getPrivilegesForRole("ADMIN")` → DB lookup → set of privilege keys | `JwtAuthenticationToken` with `GrantedAuthority("iam:roles:manage")` | Step 11 proves the DB data exists. If Step 11 passed but Step 16 asserts 403, the failure message names the SpEL version issue as the cause — no ambiguity |
| 16 | Post | **Method Authorization** | `JwtAuthenticationToken` with granted authorities + `@RequiresPrivilege("iam:roles:manage")` | Spring Security SpEL: `hasAuthority('iam:roles:manage')` evaluated against token's authority set | Allow → proceed to data retrieval; Deny → HTTP 403 | `assertThat(response.getStatusCode()).as("ADMIN with iam:roles:manage must pass @RequiresPrivilege — HTTP 403 after Step 11 passed indicates SpEL {value} substitution failing on Spring Security < 6.3").isEqualTo(HttpStatus.OK)` — second assertion after HTTP call. Fails: 403 with a message that names the exact likely cause |
| 17 | Post | **Data Retrieval + Response Serialisation + Final Assertion** | Authenticated request (Step 16 allow) | `privilegeService.listByModule()` → `privilegeRepository.findAll()` → `groupingBy(moduleName)` → Jackson → HTTP 200 JSON `{"iam": [...]}` | `ResponseEntity<Map>` with body `containsKey("iam")` | `assertThat(response.getBody()).as("iam module must appear in grouped privilege response — if absent after Step 12 passed, privilegeRepository.findAll() or groupingBy is broken").isNotNull().containsKey("iam")` — final assertion. Fails: data retrieval or serialisation broken; named distinctly from auth failure |

---

## Verification Strategy

After applying the fixes, confirm zero silent failures by introducing deliberate breakages in isolation and verifying each produces a specific, named failure:

| Test | Introduce breakage | Expected named failure |
|------|--------------------|----------------------|
| Step 1 | Swap `RSAKeyGenerator(2048)` for a constructor that throws | `ExceptionInInitializerError` at class load — not a mysterious NPE |
| Step 4 | Remove `@Import(TestJwtDecoderConfig.class)` | `testConfiguration_primaryJwtDecoder_isRsaBackedNotAutoConfigured()` fails with "must be NimbusJwtDecoder" message |
| Step 5 | Drop `V003__seed_iam_module_privileges.sql` from classpath | `buildTestTokens()` fails at "V003 must have seeded exactly 4 iam.* privileges" — before any HTTP call |
| Step 9 | Set `KEYCLOAK` container to an intentionally wrong image | `setupKeycloak()` skips with `assumeTrue` — `buildTestTokens()` and all tests are unaffected |
| Step 11 | Delete all `iam.role_privilege` rows in `@BeforeEach` | Test fails at "ADMIN role must have iam:roles:manage" — not as a 403 with no context |
| Step 12 | Delete all `iam.privilege` rows in `@BeforeEach` | Test fails at "iam module privileges must exist" — not as a 200 with a missing key |
| Step 16 | Downgrade Spring Security below 6.3 | Test fails at "ADMIN with iam:roles:manage must pass @RequiresPrivilege — HTTP 403 after Step 11 passed indicates SpEL {value} substitution failing" — named precisely |
