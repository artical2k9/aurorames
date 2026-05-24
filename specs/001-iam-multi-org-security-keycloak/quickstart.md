# Quickstart: IAM & Multi-Org Security (Keycloak)

**Branch**: `001-iam-multi-org-security-keycloak`

---

## Prerequisites

| Tool | Required version |
|------|-----------------|
| Java (Temurin) | 21 |
| Gradle | 8.x (wrapper in repo) |
| Docker + Docker Compose | 24+ |
| Keycloak | 25+ (pulled via Docker image `quay.io/keycloak/keycloak:25`) |
| PostgreSQL | 16 (pulled via Docker) |
| Apache Kafka | 3.7+ KRaft (pulled via Docker) |

---

## 1. Start infrastructure

```bash
# From repo root
docker compose -f docker/compose-infra.yml up -d
# Starts: postgres, kafka, keycloak
```

Keycloak will be available at `http://localhost:8080`. Wait ~30 s for startup.

---

## 2. Import the Keycloak realm

```bash
docker exec -i mikemes-keycloak \
  /opt/keycloak/bin/kc.sh import \
  --file /opt/keycloak/data/import/mikemes-realm.json \
  --override false
```

The realm export is at `keycloak/mikemes-realm.json` in the repo root (no secrets — secrets loaded from environment).

---

## 3. Set required environment variables

Copy `.env.example` to `.env` and populate:

```env
# Keycloak
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<set locally>
KEYCLOAK_WEBHOOK_SECRET=<random 40-char hex>

# IAM service client credentials (created in realm import)
IAM_SERVICE_CLIENT_SECRET=<from Keycloak clients → iam-service-m2m → credentials>

# PostgreSQL (iam schema)
IAM_DB_URL=jdbc:postgresql://localhost:5432/mikemes
IAM_DB_USER=iam_user
IAM_DB_PASSWORD=<set locally>
```

---

## 4. Build lib-common-security

```bash
./gradlew :libs:lib-common-security:build
```

This publishes the library to the local Maven repo used by other services.

---

## 5. Run iam-service

```bash
./gradlew :services:iam-service:bootRun
```

Service starts on port `8085`. Flyway migrations run automatically (schema `iam`, including default role seeds).

On startup, `iam-service` registers its own IAM privileges (`iam:users:create`, `iam:users:view`, `iam:roles:manage`, `iam:esig:sign`) into its own privilege registry.

---

## 6. Obtain a test token

```bash
# Using the pre-seeded test ADMIN user (dev environment only)
curl -s -X POST http://localhost:8080/realms/mikemes/protocol/openid-connect/token \
  -d "grant_type=password&client_id=mes-frontend&username=admin@test.org&password=admin123" \
  | jq -r .access_token
```

Decode the JWT at jwt.io to verify `roles` and `org_id` claims are present.

---

## 7. Call the API

```bash
export TOKEN=<token from step 6>

# List roles
curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/roles

# List all registered privileges (grouped by module)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/privileges

# Create a custom role
curl -X POST http://localhost:8085/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"SENIOR_INSPECTOR","description":"Senior inspector with sign-off rights"}'
```

---

## 8. Run tests

```bash
# Unit tests only
./gradlew :libs:lib-common-security:test :services:iam-service:test

# Integration tests (Testcontainers — starts real Keycloak + PostgreSQL + Kafka)
./gradlew :services:iam-service:integrationTest
# Expect ~60-90 s for first run (container pull); subsequent runs use Docker layer cache
```

---

## Securing a new microservice with lib-common-security

Add to the service's `build.gradle`:

```groovy
dependencies {
    implementation project(':libs:lib-common-security')
}
```

Annotate the Spring Boot application class:

```java
@SpringBootApplication
@EnableMikeMESSecurity
public class MyServiceApplication { ... }
```

Set application properties:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/mikemes

mikemes:
  security:
    iam-service-url: http://iam-service:8085
    privilege-cache-ttl-seconds: 60
    privilege-changes-topic: iam.privilege-changes
```

Declare the service's privileges:

```java
@Configuration
public class MyServicePrivileges {
    @Bean
    public PrivilegeManifest myServicePrivileges() {
        return PrivilegeManifest.of("my-service", List.of(
            PrivilegeDefinition.of("my-service:widget:create", "Create a widget"),
            PrivilegeDefinition.of("my-service:widget:view",   "View widgets")
        ));
    }
}
```

Guard an endpoint:

```java
@RestController
public class WidgetController {

    @GetMapping("/widgets")
    @RequiresPrivilege("my-service:widget:view")
    public List<Widget> list() { ... }

    @PostMapping("/widgets")
    @RequiresPrivilege("my-service:widget:create")
    public Widget create(@RequestBody CreateWidgetRequest req) { ... }
}
```

Read the org from the request context:

```java
UUID orgId = OrganisationContextHolder.getOrgId(); // populated by lib-common-security filter
```

Write an integration test:

```java
@SpringBootTest
@AutoConfigureMockMvc
class WidgetControllerTest extends KeycloakTestSupport { // from lib-common-test

    @Test
    void createWidget_withPrivilege_returns201() throws Exception {
        String token = obtainToken("CUSTOM_ROLE_WITH_WIDGET_CREATE");
        mockMvc.perform(post("/widgets")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated());
    }

    @Test
    void createWidget_withoutPrivilege_returns403() throws Exception {
        String token = obtainToken("VIEWER"); // VIEWER has no widget:create
        mockMvc.perform(post("/widgets")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
```
