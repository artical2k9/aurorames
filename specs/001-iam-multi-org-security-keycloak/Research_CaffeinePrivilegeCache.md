# Research: CaffeinePrivilegeCache Pipeline

## Context

`CaffeinePrivilegeCache` is the `PrivilegeCache` implementation in `lib-common-security` intended for all services that are NOT `iam-service` (gateway, quality, planning, etc.). It is wired by `MikeMESSecurityAutoConfiguration` when no other `PrivilegeCache` bean is present. This document traces the full pipeline in two parts — bean wiring (how the cache is created) and runtime execution (how it retrieves and returns privileges) — and identifies every design flaw using the same step-dependency and test-plan format applied to the JWT pipeline.

**Critical finding stated upfront:** `CaffeinePrivilegeCache` calls `GET /internal/privileges` on the iam-service. **That endpoint does not exist.** This is not a latent risk — it is an active gap that causes a hard failure on every authorization attempt in any non-iam service.

---

## Part 1: Bean Wiring Pipeline — "How does CaffeinePrivilegeCache get created?"

Working backward from **"CaffeinePrivilegeCache is the active PrivilegeCache bean"** to the initial conditions.

### Step Dependency Table

| Step | Stage | Input | Processing | Expected Output | Source / Component |
|------|-------|-------|------------|-----------------|-------------------|
| 1 | **`LocalPrivilegeCache` absent from context** | Spring bean registry state when `MikeMESSecurityAutoConfiguration` processes | `@ConditionalOnMissingBean(PrivilegeCache.class)` evaluates — no `PrivilegeCache` bean found (because `LocalPrivilegeCache` is only present in iam-service, not in other services) | Condition = TRUE → `caffeinePrivilegeCache` `@Bean` method will execute | `MikeMESSecurityAutoConfiguration` |
| 2 | **`PrivilegeRegistryClient` bean creation** | `RestClient.Builder` (auto-configured) + `mikemes.security.iam-service-url` property | `@ConditionalOnMissingBean` → no existing `PrivilegeRegistryClient` → creates one; sets `baseUrl` to `iamServiceUrl` | `PrivilegeRegistryClient` bean with `restClient` pointing to `{iam-service-url}` | `MikeMESSecurityAutoConfiguration.privilegeRegistryClient()` |
| 3 | **`iam-service-url` property resolution** | Spring property `mikemes.security.iam-service-url` | Property read; falls back to `http://localhost:8085` if not set | Base URL string: e.g., `http://localhost:8085` or overridden per environment | `application.yml` / env var `IAM_SERVICE_URL` |
| 4 | **Caffeine cache construction** | `mikemes.security.privilege-cache-ttl-seconds` (default 60) | `Caffeine.newBuilder().expireAfterWrite(ttlSeconds, TimeUnit.SECONDS).build()` | `Cache<String, Set<String>>` — empty, ready to load entries on first access | `CaffeinePrivilegeCache` constructor |
| 5 | **`CaffeinePrivilegeCache` bean instantiated** | `PrivilegeRegistryClient` (Step 2) + `Cache` (Step 4) | `new CaffeinePrivilegeCache(registryClient, ttlSeconds)` | `CaffeinePrivilegeCache` bean in Spring context; `@KafkaListener` on `onPrivilegeChange()` registered | `MikeMESSecurityAutoConfiguration.caffeinePrivilegeCache()` |
| 6 | **`MikeMESJwtAuthenticationConverter` created** | `CaffeinePrivilegeCache` (as `PrivilegeCache`) | `@ConditionalOnBean(PrivilegeCache.class)` TRUE → `new MikeMESJwtAuthenticationConverter(privilegeCache)` | Converter wired with `CaffeinePrivilegeCache`; will call it for every incoming JWT | `MikeMESSecurityAutoConfiguration.mikeMESJwtAuthenticationConverter()` |
| 7 | **`SecurityFilterChain` created** | `MikeMESJwtAuthenticationConverter` | `@ConditionalOnBean(MikeMESJwtAuthenticationConverter.class)` TRUE → `http.oauth2ResourceServer(...jwt...jwtAuthenticationConverter(converter))` | JWT resource server filter chain active; every Bearer token processed through the converter | `MikeMESSecurityAutoConfiguration.mikeMESSecurityFilterChain()` |

---

### Test Plan — Bean Wiring

| Step | Precondition | Specific Test | Expected Pass Condition | Expected Failure Signal |
|------|-------------|---------------|------------------------|------------------------|
| W1 — No competing PrivilegeCache | No `LocalPrivilegeCache` in scan path | `assertThat(privilegeCache).isInstanceOf(CaffeinePrivilegeCache.class)` | `CaffeinePrivilegeCache` is injected | `LocalPrivilegeCache` injected → `@ConditionalOnMissingBean` evaluated after `LocalPrivilegeCache` registered |
| W2 — `iam-service-url` is non-default | Config property set correctly | Inject `PrivilegeRegistryClient`; reflectively read `restClient.baseUrl` field OR set a known URL and verify it's used | URL matches configured value | `http://localhost:8085` (default) — wrong port in `RANDOM_PORT` test |
| W3 — `CaffeinePrivilegeCache` is NOT wired in iam-service | `LocalPrivilegeCache` present in context | `assertThat(privilegeCache).isInstanceOf(LocalPrivilegeCache.class)` — in `PrivilegeControllerIT` | `LocalPrivilegeCache` injected | `CaffeinePrivilegeCache` injected → `@ConditionalOnMissingBean` ordering failure |
| W4 — Converter is wired with the correct cache | W1 or W3 pass | Inject `MikeMESJwtAuthenticationConverter`; assert `privilegeCache` field is correct type | Matches expected implementation | Wrong implementation → wrong authorities |
| W5 — Kafka listener registered | `@KafkaListener` active | `onPrivilegeChange("ADMIN")` followed by `getPrivilegesForRole("ADMIN")` → confirm cache was invalidated | Fresh fetch happens after invalidation | Kafka not configured → listener registration failure at startup |

---

## Part 2: Runtime Execution Pipeline — "How does getPrivilegesForRole('ADMIN') return a Set?"

Starting input: `getPrivilegesForRole("ADMIN")` called from `MikeMESJwtAuthenticationConverter.resolveAuthorities()`.

### Step Dependency Table

| Step | Stage | Input | Processing | Expected Output | Source / Component |
|------|-------|-------|------------|-----------------|-------------------|
| 8 | **Caffeine cache lookup** | Role string `"ADMIN"` | `cache.get("ADMIN", loader)` — check if "ADMIN" key is present and not expired | Cache hit → return cached `Set<String>` immediately (Steps 9–17 skipped); Cache miss → execute loader function | `CaffeinePrivilegeCache.getPrivilegesForRole()` |
| 9 | **Loader invoked on cache miss** | Role string `"ADMIN"` (as loader key `k`) | Caffeine invokes `k -> registryClient.fetchManifest().getPrivilegesForRole(k)` | `registryClient.fetchManifest()` called; result piped to `.getPrivilegesForRole("ADMIN")` | Caffeine load function in `CaffeinePrivilegeCache` |
| 10 | **`fetchManifest()` — request assembly** | `RestClient` with `baseUrl = {iam-service-url}` | Build `GET {iam-service-url}/internal/privileges` request; **no `Authorization` header set** | HTTP GET request object — unauthenticated | `PrivilegeRegistryClient.fetchManifest()` |
| 11 | **HTTP request dispatched** | HTTP GET to `{iam-service-url}/internal/privileges` | `restClient.get().uri("/internal/privileges").retrieve().body(PrivilegeManifest.class)` — network call | Request arrives at iam-service Spring Security filter chain | `PrivilegeRegistryClient.fetchManifest()` |
| 12 | **iam-service security filter — `/internal/**`** | HTTP request with no `Authorization` header | `internalSecurityFilterChain` (Order 1) matches `/internal/**`; `WebhookTokenFilter` checks for `Authorization: Bearer {iam.webhook.token}` | Header absent → HTTP **401 UNAUTHORIZED** — request rejected before reaching any controller | `SecurityConfig.internalSecurityFilterChain()` + `WebhookTokenFilter` |
| 13 | **`GET /internal/privileges` — endpoint does not exist** *(if auth somehow passed)* | HTTP GET `/internal/privileges` | Spring DispatcherServlet looks for `@GetMapping("/internal/privileges")` — no such mapping in `InternalController` | HTTP **404 NOT_FOUND** — `InternalController` only has `POST /internal/keycloak-events` | `InternalController` — **endpoint is missing** |
| 14 | **Error response received by `PrivilegeRegistryClient`** | HTTP 401 or 404 response | `HttpClientErrorException` caught → `throw new PrivilegeRegistryException("Registry returned " + statusCode, e)` | `PrivilegeRegistryException` thrown — no retry (retry only for 503) | `PrivilegeRegistryClient.fetchManifest()` catch block |
| 15 | **Exception propagates through Caffeine loader** | `PrivilegeRegistryException` from loader | Caffeine's `cache.get()` loader threw RuntimeException → exception propagates uncaught | `PrivilegeRegistryException` propagates out of `getPrivilegesForRole()` | Caffeine `Cache.get()` — no exception suppression |
| 16 | **Exception propagates through `resolveAuthorities()`** | `PrivilegeRegistryException` | `for (String privilege : privilegeCache.getPrivilegesForRole(role))` throws → no try/catch in `resolveAuthorities()` | `PrivilegeRegistryException` propagates out of `resolveAuthorities()` and out of `convert()` | `MikeMESJwtAuthenticationConverter.resolveAuthorities()` |
| 17 | **Spring Security handles unhandled converter exception** | `PrivilegeRegistryException` escaping `JwtAuthenticationConverter` | `BearerTokenAuthenticationFilter` has no catch for arbitrary RuntimeException from the converter; Spring's error handling converts it | Likely HTTP **500 INTERNAL SERVER ERROR** (unhandled filter exception) or HTTP **401** depending on Spring Security version's error dispatch | Spring Security `BearerTokenAuthenticationFilter` |

**Runtime pipeline result: the call never returns a `Set<String>`.** Every path ends in an exception or error response. The pipeline has no valid success path.

---

### Test Plan — Runtime Execution

| Step | Precondition | Specific Test | Expected Pass Condition | Expected Failure Signal |
|------|-------------|---------------|------------------------|------------------------|
| R1 — Cache miss triggers fetch | Cold cache, no previous call for "ADMIN" | `when(registryClient.fetchManifest()).thenReturn(manifest); cache.getPrivilegesForRole("ADMIN")` | Exactly one call to `fetchManifest()` | Zero calls — cache was pre-populated |
| R2 — Cache hit skips fetch | Call R1 first | Second call to `getPrivilegesForRole("ADMIN")` | Zero additional calls to `fetchManifest()` | Another call made — TTL expired or cache eviction bug |
| R3 — iam-service endpoint is reachable at the configured URL | `iam-service-url` points to a running iam-service | HTTP GET `{iam-service-url}/internal/privileges` with correct auth token returns 200 | 200 OK with `{"rolePrivileges": {...}}` body | **404** — endpoint not implemented; **401** — auth header not sent |
| R4 — Response deserialises to `PrivilegeManifest` | R3 passes | Assert deserialized `manifest.getPrivilegesForRole("ADMIN")` is non-empty | Non-empty Set containing expected privilege keys | Empty set — "ADMIN" key absent from response |
| R5 — `fetchManifest()` retries on 503 | WireMock stubs 503 then 200 | 503 × 2 then 200 → `fetchManifest()` returns manifest | Success after retries | `PrivilegeRegistryException("unavailable")` — no 503 retry |
| R6 — `PrivilegeRegistryException` does not propagate silently | Registry unavailable | Integration test: send JWT request with cache cold and registry down | Clear HTTP 503 or named error | HTTP 403 (empty authority set swallowed) or HTTP 500 (unhandled exception) |

---

## Design Flaws Identified

**Flaw 1 — `GET /internal/privileges` endpoint does not exist (critical blocker)**

`PrivilegeRegistryClient` calls `GET /internal/privileges`. `InternalController` in iam-service has exactly one endpoint: `POST /internal/keycloak-events`. There is no `GET /internal/privileges` mapping anywhere in the codebase. This means:

- Every service that relies on `CaffeinePrivilegeCache` (all non-iam services: gateway, quality, planning, etc.) fails immediately on the first authorization attempt
- The failure path is: 404 → `PrivilegeRegistryException` → unhandled exception in Spring Security filter → 500 or 401
- The service appears to start correctly; the defect only surfaces when the first authenticated request is processed

**Flaw 2 — `PrivilegeRegistryClient` sends no authentication header**

The `/internal/**` endpoint group is protected by `internalSecurityFilterChain` (Order 1), which uses `WebhookTokenFilter` to require a static bearer token (`iam.webhook.token`). `PrivilegeRegistryClient.fetchManifest()` constructs:

```java
restClient.get()
    .uri("/internal/privileges")
    .retrieve()
    .body(PrivilegeManifest.class);
```

No `Authorization` header. Even if the endpoint existed, the request returns **401** before reaching the controller. The client needs to set `Authorization: Bearer {iam.webhook.token}` — but that secret is on the iam-service side only and would need to be propagated to every consuming service.

**Flaw 3 — `PrivilegeRegistryException` is not caught in `MikeMESJwtAuthenticationConverter`**

```java
private Set<GrantedAuthority> resolveAuthorities(JwtClaimsExtractor extractor) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (String role : extractor.getRoles()) {
        for (String privilege : privilegeCache.getPrivilegesForRole(role)) {  // ← throws
            authorities.add(new PrivilegeGrantedAuthority(privilege));
        }
    }
    return authorities;
}
```

`PrivilegeRegistryException` is an unchecked `RuntimeException`. It propagates out of `getPrivilegesForRole()`, through the for-loop, out of `resolveAuthorities()`, and out of `convert()`. Spring Security's `BearerTokenAuthenticationFilter` does not have a general catch for RuntimeExceptions from converters — the exception escapes the filter chain entirely, causing an HTTP 500 response. The caller receives a 500 with a stack trace, not a meaningful auth error.

**Flaw 4 — Every distinct-role cache miss fetches the full manifest redundantly**

The cache key is the role name; the value is that role's privilege set:

```java
cache.get(role, k -> registryClient.fetchManifest().getPrivilegesForRole(k));
```

`fetchManifest()` fetches ALL roles and ALL privileges in one HTTP call and then discards all but the one requested role's privileges. For a service with 6 system roles (ADMIN, OPERATOR, QUALITY_INSPECTOR, PLANNER, ENGINEER, VIEWER), the first request for each role triggers a separate full manifest fetch — 6 HTTP round trips to get the same data. The manifest should be cached at the manifest level, not at the per-role level.

**Flaw 5 — Retry logic only handles HTTP 503 and blocks the filter chain thread**

```java
} catch (HttpServerErrorException.ServiceUnavailable e) {
    attempt++;
    ...
    Thread.sleep(retryDelayMs);
}
```

- Only HTTP 503 is retried; connection refused, timeout, 502, and 504 all throw immediately
- `Thread.sleep(retryDelayMs)` blocks a Tomcat worker thread (the Spring Security filter chain thread) during retries — at `maxAttempts=3` and default `retryDelayMs=100ms`, that is up to 200ms of blocked thread time per request
- HTTP 401 and 404 throw immediately — so Flaws 1 and 2 are not helped by the retry logic

**Flaw 6 — `@Component` on `CaffeinePrivilegeCache` and `PrivilegeRegistryClient` is misleading**

Both classes carry `@Component`:

```java
@Component
public class CaffeinePrivilegeCache implements PrivilegeCache { ... }

@Component
public class PrivilegeRegistryClient { ... }
```

Neither class is in a package scanned by any consuming service (`com.mikemes.common.security.*` is not within `com.mikemes.iam.*`, `com.mikemes.gateway.*`, etc.). The `@Component` annotations are therefore never activated by component scanning — both beans are created exclusively through `MikeMESSecurityAutoConfiguration`'s `@Bean` factory methods. The `@Component` annotations mislead developers into thinking the classes register themselves, obscure the bean lifecycle, and risk double-registration if a service ever explicitly adds `com.mikemes.common.security` to its component scan.

**Flaw 7 — No integration test exercises the full runtime path**

`CaffeinePrivilegeCacheTest` mocks `PrivilegeRegistryClient` entirely. `PrivilegeRegistryClientTest` uses WireMock to stub HTTP responses. No test validates the complete path:

```
Incoming JWT request
  → MikeMESJwtAuthenticationConverter
    → CaffeinePrivilegeCache.getPrivilegesForRole()
      → PrivilegeRegistryClient.fetchManifest()
        → HTTP GET /internal/privileges (real iam-service)
          → PrivilegeManifest JSON deserialized
            → Set<String> returned as GrantedAuthority set
```

The first time this end-to-end path ran in a real environment, it would fail at Flaw 1 (missing endpoint). No test caught this.

---

## Combined Pipeline — Zero Silent Failures

The table below combines the bean wiring and runtime execution stages, showing the complete chain from "Spring context starts in a non-iam service" through to "authority set returned for role ADMIN." Every handshake assertion is stated; every currently-broken stage is marked.

| Step | Phase | Stage | Input | Processing | Output | Handshake Assertion | Status |
|------|-------|-------|-------|------------|--------|---------------------|--------|
| 1 | Wiring | **No competing `PrivilegeCache` present** | Spring bean registry (no `LocalPrivilegeCache` in scan path) | `@ConditionalOnMissingBean(PrivilegeCache.class)` → TRUE | Condition satisfied → proceed to create `CaffeinePrivilegeCache` | `assertThat(privilegeCache).isInstanceOf(CaffeinePrivilegeCache.class)` — in a non-iam service IT | ✅ Works as intended |
| 2 | Wiring | **`iam-service-url` property resolved** | `mikemes.security.iam-service-url` (default `http://localhost:8085`) | Property read; `RestClient.baseUrl(url).build()` | `PrivilegeRegistryClient` pointing at iam-service | Assert configured URL is non-default and points to a real iam-service instance | ⚠️ Default `localhost:8085` is wrong in RANDOM_PORT tests |
| 3 | Wiring | **Caffeine cache initialised** | `mikemes.security.privilege-cache-ttl-seconds` (default 60) | `Caffeine.newBuilder().expireAfterWrite(ttl, SECONDS).build()` | Empty `Cache<String, Set<String>>` ready | Cache is non-null and has zero entries; TTL matches configured value | ✅ Works |
| 4 | Wiring | **`CaffeinePrivilegeCache` bean created** | `PrivilegeRegistryClient` + `Cache` | Constructor assigns both fields; `@KafkaListener` registered | `CaffeinePrivilegeCache` in Spring context | `assertThat(privilegeCache).isInstanceOf(CaffeinePrivilegeCache.class)` | ✅ Works (when `LocalPrivilegeCache` absent) |
| 5 | Wiring | **Converter wired with `CaffeinePrivilegeCache`** | `CaffeinePrivilegeCache` (as `PrivilegeCache`) | `MikeMESJwtAuthenticationConverter(privilegeCache)` | Converter ready | `assertThat(jwtAuthConverter).isNotNull()` | ✅ Works |
| 6 | Wiring | **Security filter chain active** | Converter | `mikeMESSecurityFilterChain` created; JWT resource server configured | All requests require valid JWT; Bearer tokens processed through converter | `assertThat(filterChain).isNotNull()` | ✅ Works |
| 7 | Runtime | **Cache miss — loader invoked** | Role string `"ADMIN"`, cold cache | `cache.get("ADMIN", loader)` — miss → executes loader | `registryClient.fetchManifest()` called | After first request: `verify(registryClient, times(1)).fetchManifest()` | ✅ Works in unit test (mocked registry) |
| 8 | Runtime | **HTTP request assembled — no auth header** | `RestClient` + URI `/internal/privileges` | `restClient.get().uri("/internal/privileges").retrieve()` — no header set | HTTP GET `{iam-service-url}/internal/privileges` with **no** `Authorization` | Assert request contains `Authorization: Bearer {webhook-token}` header | ❌ **BROKEN** — no auth header sent |
| 9 | Runtime | **iam-service security rejects unauthenticated request** | HTTP GET with no auth header | `WebhookTokenFilter` on `/internal/**` requires `Authorization: Bearer {token}` | **HTTP 401 UNAUTHORIZED** | `assertThat(response.statusCode()).isEqualTo(401)` in WireMock test | ❌ **BROKEN** — auth header missing; request always rejected |
| 10 | Runtime | **`GET /internal/privileges` endpoint serves manifest** *(target state — not yet implemented)* | HTTP GET `/internal/privileges` with correct auth | Controller reads all active `role_privilege` rows; builds `PrivilegeManifest`; serializes as JSON | `{"rolePrivileges": {"ADMIN": ["iam:roles:manage", ...], ...}}` | `assertThat(body.rolePrivileges()).containsKey("ADMIN")` | ❌ **BROKEN** — endpoint does not exist |
| 11 | Runtime | **JSON deserialization** *(target state)* | HTTP 200 JSON body | Jackson deserializes to `PrivilegeManifest` record | `PrivilegeManifest` with `rolePrivileges` map populated | `assertThat(manifest.getPrivilegesForRole("ADMIN")).contains("iam:roles:manage")` | ❌ **Not reachable** — Step 10 not implemented |
| 12 | Runtime | **Per-role privilege extraction** *(target state)* | `PrivilegeManifest` + role `"ADMIN"` | `manifest.getPrivilegesForRole("ADMIN")` → `rolePrivileges.getOrDefault("ADMIN", Set.of())` | `Set<String>` containing ADMIN's privilege keys | `assertThat(privilegeSet).isNotEmpty().contains("iam:roles:manage")` | ❌ **Not reachable** — Step 10 not implemented |
| 13 | Runtime | **Caffeine stores result and returns Set** *(target state)* | `Set<String>` from Step 12 | Caffeine stores in cache under key `"ADMIN"`; returns Set to `resolveAuthorities()` | Cached Set available for TTL duration; `GrantedAuthority` objects built from Set | Next request for same role returns from cache without HTTP call | ❌ **Not reachable** — Step 10 not implemented |

---

## What Must Be Built to Complete the Pipeline

Three implementation tasks are required before `CaffeinePrivilegeCache` can function end-to-end:

### Task 1 — Implement `GET /internal/privileges` in `InternalController`

```java
@GetMapping("/privileges")
public ResponseEntity<PrivilegeManifest> getPrivilegeManifest() {
    Map<String, Set<String>> rolePrivileges = privilegeService.getPrivilegeMap()
        .entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> Set.copyOf(e.getValue())));
    return ResponseEntity.ok(new PrivilegeManifest(rolePrivileges));
}
```

`PrivilegeService.getPrivilegeMap()` already exists and returns `Map<String, List<String>>`. The only conversion needed is `List → Set`.

### Task 2 — Add the webhook token to outbound requests in `PrivilegeRegistryClient`

The static bearer token that protects `/internal/**` must be sent by `PrivilegeRegistryClient`. Options:
- Pass the webhook token as a constructor parameter (from config)
- Or expose `/internal/privileges` on a separate, unauthenticated internal port (not recommended — security regression)

```java
public PrivilegeRegistryClient(RestClient.Builder builder, String iamServiceUrl, String webhookToken) {
    this.restClient = builder
        .baseUrl(iamServiceUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + webhookToken)
        .build();
    ...
}
```

The `MikeMESSecurityAutoConfiguration` would need to inject the `iam.webhook.token` value (which must be shared across services via environment variable, not hardcoded).

### Task 3 — Catch `PrivilegeRegistryException` in `MikeMESJwtAuthenticationConverter`

```java
private Set<GrantedAuthority> resolveAuthorities(JwtClaimsExtractor extractor) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (String role : extractor.getRoles()) {
        try {
            for (String privilege : privilegeCache.getPrivilegesForRole(role)) {
                authorities.add(new PrivilegeGrantedAuthority(privilege));
            }
        } catch (PrivilegeRegistryException e) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("privilege_registry_unavailable",
                    "Cannot resolve privileges for role " + role + ": " + e.getMessage(), null), e);
        }
    }
    return authorities;
}
```

This converts the infrastructure failure into a proper `OAuth2AuthenticationException` (HTTP 401 with a named error code) rather than leaking a 500.
