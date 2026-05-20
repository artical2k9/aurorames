# Research: IAM & Multi-Org Security (Keycloak)

**Branch**: `001-iam-multi-org-security-keycloak` | **Date**: 2026-05-20

---

## R-01: Spring Security privilege resolution with JWT (coarse roles → fine-grained authorities)

**Decision**: Use a custom `JwtAuthenticationConverter` that maps the `roles` JWT claim to a set of `GrantedAuthority` objects (one per resolved privilege). The converter calls a `PrivilegeCache` service that holds the role → privilege set mapping in-process. `@PreAuthorize("hasAuthority('quality:inspection:sign-off')")` (Spring Security native) evaluates against these authorities — no custom annotation processor needed.

**Rationale**: Spring Security's `JwtAuthenticationConverter` is the canonical extension point for this. The `GrantedAuthority` contract means `@PreAuthorize("hasAuthority('...')")` works natively. The authority strings are privilege keys, not role names. This means endpoint protection annotations are decoupled from role definitions entirely — a role can gain or lose a privilege without any code change.

**Alternatives considered**:
- Embed privileges in JWT: rejected — JWT bloat; stale on privilege change; examined in depth during spec review.
- Custom `@RequiresPrivilege` with AOP: viable but unnecessary. Spring Security's `hasAuthority()` with named strings achieves the same outcome with less code. `@RequiresPrivilege` becomes a thin alias.
- `spring-security-oauth2-resource-server` `OpaqueTokenIntrospector`: examined and rejected during spec review — adds per-request Keycloak round-trip latency (20–100 ms) and makes Keycloak a synchronous hot path for all 18 services.

**How to apply**: `MikeMESJwtAuthenticationConverter` implements `Converter<Jwt, AbstractAuthenticationToken>`. It:
1. Extracts `roles` claim (list of strings) from the JWT.
2. For each role, calls `PrivilegeCache.getPrivilegesForRole(role)`.
3. Returns a `JwtAuthenticationToken` with the union of all privilege strings as `GrantedAuthority` objects, plus the original JWT and the `org_id` claim set on the principal.

---

## R-02: Keycloak 25+ → audit-service event publishing

**Decision**: Use **Keycloak's built-in webhook Event Listener** (introduced KC 24+) configured to POST auth events as JSON to `audit-service`'s internal webhook endpoint (`POST /internal/keycloak-events`). `audit-service` receives the webhook payload and publishes to Kafka topic `iam.events`.

**Rationale**: Keycloak 24+ ships a native `http` event listener (`org.keycloak.events.HttpSenderEventListenerProvider`). No custom SPI JAR or Kafka client embedded in the Keycloak container. The audit-service webhook endpoint is an internal endpoint (not exposed through the gateway), authenticated by a shared secret in an `Authorization: Bearer <static-token>` header (the static token is rotated via Docker secrets — it is not user-facing).

**Alternatives considered**:
- Custom Keycloak SPI JAR with embedded Kafka client: technically correct but couples the Keycloak container to the Kafka version; requires redeployment of the Keycloak container on Kafka client upgrades. Rejected for operational simplicity.
- Keycloak → database polling (polling the `EVENT` table): fragile, high latency, not recommended by Keycloak project. Rejected.
- `keycloak-kafka` community extension (GitHub: sventorben/keycloak-kafka): not maintained for KC 25+. Rejected.

**How to apply**: Configure Keycloak realm `eventsListeners: ["jboss-logging", "http-sender"]` with webhook URL pointing to `http://audit-service:8080/internal/keycloak-events`. `audit-service` maps Keycloak event types to `IamAuditEvent` Kafka records published on `iam.events`.

---

## R-03: Privilege manifest self-registration pattern

**Decision**: Each microservice declares a `PrivilegeManifest` as a Spring `@Configuration` bean listing its owned privilege strings. `lib-common-security` provides a `PrivilegeRegistrar` `ApplicationListener<ApplicationReadyEvent>` that reads all `PrivilegeManifest` beans and POSTs them to `iam-service` via `PrivilegeRegistryClient` using an M2M token. The `iam-service` endpoint performs an **upsert** (`INSERT ... ON CONFLICT (privilege_key) DO UPDATE SET description = EXCLUDED.description`). This is idempotent — safe on every restart.

**Rationale**: Self-registration at startup means the privilege registry is always consistent with deployed services. No manual configuration step. A module developer declares privileges as a bean; the library handles registration.

**Alternatives considered**:
- Static configuration file deployed alongside iam-service: requires updating a central file every time any module adds a privilege. Rejected — tight coupling.
- Privilege strings hardcoded in lib-common-security: ties the library to application-specific domain knowledge. Rejected.
- Kafka-based registration (service publishes event, iam-service consumes): valid but adds latency and ordering complexity at startup. REST is simpler for a synchronous registration call. Rejected in favour of REST.

**How to apply**: Each service's `@Configuration` class:
```java
@Bean
public PrivilegeManifest qualityPrivileges() {
    return PrivilegeManifest.of("quality", List.of(
        Privilege.of("quality:inspection:view", "View inspection records"),
        Privilege.of("quality:inspection:sign-off", "Sign off an inspection result"),
        Privilege.of("quality:ncm:raise", "Raise a nonconformance")
    ));
}
```

---

## R-04: Angular OIDC with angular-oauth2-oidc and Spring Cloud Gateway

**Decision**: Angular frontend uses `angular-oauth2-oidc` v17+ with Authorization Code + PKCE flow. Spring Cloud Gateway acts as a pass-through relay — it validates the JWT from the `Authorization: Bearer` header on every request (resource server mode) and forwards it unchanged to downstream services. No gateway-level session cookie; pure token-based flow end to end.

**Rationale**: `angular-oauth2-oidc` is well-maintained, Angular-native, and supports PKCE natively. The gateway-as-resource-server pattern is simpler than gateway-as-OAuth2-client-with-session — it avoids server-side session storage and scales horizontally without sticky sessions. Downstream services also validate JWTs independently (defence-in-depth).

**Alternatives considered**:
- Gateway as OAuth2 client (BFF pattern) with server-side sessions and opaque tokens to the browser: provides stronger security (token never in browser) but adds server-side session storage requirement and complexity. Examined during spec debate on opaque tokens — rejected because the JWT size problem does not exist in this design, removing the primary motivation for the BFF pattern.
- Keycloak JS adapter: deprecated in KC 22+; not recommended for Angular 19. Rejected.

**How to apply**: `OAuthModule.forRoot({ resourceServer: { allowedUrls: ['/api'], sendAccessToken: true }})`. Keycloak OIDC discovery URL: `https://keycloak:8080/realms/mikemes/.well-known/openid-configuration`. Gateway route config: standard `Authorization: Bearer` header forwarding — no `TokenRelay=` filter needed (that filter is for gateway-as-client mode only).

---

## R-05: Multi-tenancy via Keycloak group attribute mapper

**Decision**: One Keycloak group per organisation (e.g., `org-{UUID}`). Each group has a custom attribute `org_id` set to the organisation UUID. A **Group Attribute Mapper** client scope maps `org_id` from the user's group into the JWT as a top-level claim. Users are members of exactly one organisation group in v1.

**Rationale**: Group attribute mappers are a built-in Keycloak feature, no custom SPI required. The `org_id` claim is extracted from the JWT by `JwtClaimsExtractor` and placed in `OrganisationContextHolder` by `MikeMESJwtAuthenticationConverter` on every request. The claim is part of the signed JWT — it cannot be tampered with without invalidating the signature.

**Alternatives considered**:
- One Keycloak realm per organisation: true isolation but catastrophic operational overhead for each new customer. Rejected at spec stage.
- `org_id` stored in a custom user attribute (not group): works for single-org users but makes multi-org membership hard to express later. Group attribute is the correct model.

---

## R-06: Privilege cache invalidation via Kafka

**Decision**: `iam-service` publishes a `PrivilegeChangeEvent` to Kafka topic `iam.privilege-changes` whenever a role-privilege assignment is created or deleted. Each microservice's `lib-common-security` `PrivilegeCache` subscribes to this topic via `@KafkaListener`. On receiving an event, the cache entry for the affected role is evicted (Caffeine `cache.invalidate(roleName)`). The next request for that role triggers a fresh fetch from `iam-service`.

**Rationale**: Caffeine provides sub-millisecond in-process lookups. Kafka invalidation ensures all instances across all services see the change within seconds, without polling. TTL (default 60 s) is the fallback if the Kafka event is delayed or a service is momentarily partitioned.

**Cache warming**: On startup, `PrivilegeCache` pre-warms by fetching all role-privilege mappings from `iam-service` in one batch call (`GET /roles/privilege-map`). This prevents a cold-start thundering herd when all 18 services restart simultaneously.

---

## R-07: Keycloak role mirroring for iam-service-managed roles

**Decision**: When an ADMIN creates a custom role via `iam-service`, the service:
1. Inserts the role into its own `role` table.
2. Calls the Keycloak Admin REST API `POST /admin/realms/mikemes/roles` to create the corresponding realm role.
3. Both operations are wrapped in a compensating transaction pattern (if Keycloak call fails, the local DB insert is rolled back via a `TransactionSynchronization` after-completion hook).

**Rationale**: The JWT `roles` claim is populated by Keycloak. For a custom role to appear in JWTs, it must exist as a Keycloak realm role. The `iam-service` database is the source of truth for privilege assignments; Keycloak is the source of truth for role names in JWTs. These two stores must stay in sync.

**Alternatives considered**:
- Keycloak as sole source of truth for roles (no local DB table): requires all role queries to hit the Keycloak Admin API, which introduces latency on privilege matrix lookups. Rejected.
- Event-driven sync (publish event, async Keycloak sync): risks temporary inconsistency where a user has a role in DB but not in Keycloak (or vice versa) and receives incorrect JWT claims. Rejected for synchronous operations.
