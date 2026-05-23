# Research: Platform & System Administration (MES-6)

**Branch**: `006-platform-system-administration`  
**Date**: 2026-05-23

---

## R1 — Spring Boot Admin compatibility with Spring Boot 3.5.0

**Decision**: Use `de.codecentric:spring-boot-admin-starter-server:3.4.x` for both server and client dependencies.

**Rationale**: Spring Boot Admin 3.4.x targets Spring Boot 3.3.x–3.5.x. The Spring Boot Admin project follows Spring Boot's minor version cadence. Version 3.4.x is the current stable release as of 2026-05.

**How to apply**: In `admin-service/build.gradle`:
```groovy
implementation "de.codecentric:spring-boot-admin-starter-server:3.4.3"
implementation "org.springframework.boot:spring-boot-starter-web"
implementation "org.springframework.boot:spring-boot-starter-security"
```
In all client services (`iam-service`, `gateway-service`, `platform-service`):
```groovy
implementation "de.codecentric:spring-boot-admin-starter-client:3.4.3"
```
Note: gateway-service is reactive (WebFlux); use the SBA client with WebFlux auto-detection. SBA 3.4.x supports both servlet and reactive clients.

**Version lock**: Define in `gradle/libs.versions.toml`:
```toml
[versions]
springBootAdmin = "3.4.3"
```

---

## R2 — SBA Server + Keycloak OIDC authentication

**Decision**: Protect SBA Server UI using Spring Security OAuth2 Login with Keycloak as the OIDC provider. Resource server JWT validation protects the SBA actuator aggregation REST API.

**Configuration**:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: admin-service
            client-secret: ${ADMIN_SERVICE_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope: openid, profile, email
        provider:
          keycloak:
            issuer-uri: ${KEYCLOAK_ISSUER_URI}
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
```

**Keycloak client setup**: Create `admin-service` client in the MikeMES realm with:
- Client authentication: ON (confidential)
- Authorization: OFF
- Valid redirect URIs: `http://admin-service:8888/login/oauth2/code/keycloak`
- Add to `keycloak/mikemes-realm.json` for reproducible import

**Security config in admin-service**: Permit `/actuator/health` (healthcheck) and `/login/**`; require authentication for everything else.

---

## R3 — SBA client registration strategy (no Eureka)

**Decision**: Direct URL registration via `spring.boot.admin.client.url`. No Eureka or service registry.

**Rationale**: Single-compose deployment; Eureka adds dependency complexity. Direct URL is sufficient and explicit.

**Config added to each client service**:
```yaml
spring:
  boot:
    admin:
      client:
        url: ${ADMIN_SERVICE_URL:http://admin-service:8888}
        instance:
          metadata:
            user.name: ${ADMIN_CLIENT_USER:}
            user.password: ${ADMIN_CLIENT_PASSWORD:}
```
`ADMIN_SERVICE_URL` defaults to Docker Compose hostname; overridable for local dev outside Docker.

**Gateway-service note**: `spring-boot-admin-starter-client` auto-detects WebFlux — add `spring-webflux` if not already on classpath. Gateway already has WebFlux via spring-cloud-gateway.

---

## R4 — platform-service scope vs iam-service overlap

**Decision**: No overlap. Clear boundary:
- **iam-service** owns: organisations, users, roles, privileges, Keycloak sync
- **platform-service** owns: per-org runtime configuration key/value store, system-wide operational parameters

**How to apply**: Platform-service never calls iam-service for org validation at runtime — it trusts the `org_id` claim in the validated JWT (same pattern as iam-service's domain controllers).

If a future need arises to validate that an `org_id` exists before storing config, it can be done via the `GET /internal/organisations/{id}` endpoint (to be added to iam-service InternalController in a future story).

---

## R5 — Privilege seeding for `platform:config:manage` and `platform:config:read`

**Decision**: Add iam-service Flyway migration `V005__seed_platform_module_privileges.sql` in this PR.

**Content**:
```sql
INSERT INTO iam.privilege (name, module, description, active)
VALUES
    ('platform:config:manage', 'platform', 'Create, update, and delete platform configuration entries', true),
    ('platform:config:read',   'platform', 'Read platform configuration entries',                         true)
ON CONFLICT (name) DO NOTHING;

INSERT INTO iam.role_privilege_assignment (role_id, privilege_id, active)
SELECT r.id, p.id, true
FROM iam.role r
JOIN iam.privilege p ON p.name IN ('platform:config:manage', 'platform:config:read')
WHERE r.name = 'ADMIN'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_privilege_assignment rpa
    WHERE rpa.role_id = r.id AND rpa.privilege_id = p.id
  );
```

**Note**: V004 in iam-service is Envers audit tables. Check current highest migration version before naming.

---

## R6 — settings.gradle module activation

**Decision**: Add both services to `settings.gradle` when their `build.gradle` files are created.

```groovy
include 'services:admin-service'
// uncomment when ready:
// include 'services:platform-service'   ← already commented; uncomment
```

Wait — exploration showed `platform-service` IS commented in settings.gradle, `admin-service` is NOT listed. Add both:
```groovy
include 'services:admin-service'    // new
include 'services:platform-service' // uncomment existing
```

---

## R7 — sonar-project.properties update (ERR-MES-033 rule)

**Decision**: Add new module paths to `sonar.sources` and `sonar.tests` ONLY after their `src/main/java` directories exist on disk.

**Paths to add when directories are created**:
```properties
sonar.sources=...,\
  services/admin-service/src/main/java,\
  services/platform-service/src/main/java

sonar.tests=...,\
  services/admin-service/src/test/java,\
  services/platform-service/src/test/java
```

Add these lines in the same commit that creates the `src/main/java` scaffold — never speculatively.

---

## R8 — Spring Boot Admin + gateway-service reactive compatibility

**Decision**: Use `spring-cloud-gateway` 2025.0.0 (already on classpath) alongside SBA client. No conflict.

**Research finding**: SBA client 3.4.x detects reactive environment and uses `WebClient` for registration. Gateway already has `spring-boot-starter-webflux` via `spring-cloud-gateway`. No additional classpath change needed.

**Gateway SecurityConfig change**: Add `/api/admin/**` and `/api/platform/**` to the authenticated route matchers (already using `authenticated()` default catch-all — verify this covers new routes).

---

## R9 — admin-service vs platform-service port selection

**Decision**: `admin-service` → port 8888, `platform-service` → port 8090.

**Existing ports**:
- 8080: gateway-service
- 8085: iam-service
- 5432: PostgreSQL
- 9092: Kafka
- 8443: Keycloak

No conflict with 8888 or 8090.
