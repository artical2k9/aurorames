# Quickstart: Platform & System Administration (MES-6)

**Branch**: `006-platform-system-administration`

---

## Prerequisites

- Docker Desktop running (for Testcontainers and local compose stack)
- JDK 21 (Gradle toolchain auto-provisions via `gradle/wrapper`)
- `.env` file populated from `.env.example`

---

## Local Development Setup

### 1. Start infrastructure

```bash
docker compose -f docker/compose-infra.yml up -d
```

Starts: PostgreSQL 16, Apache Kafka, Keycloak 25.

Verify:
```bash
docker compose -f docker/compose-infra.yml ps   # all services healthy
```

### 2. Run admin-service

```bash
./gradlew :services:admin-service:bootRun
```

Spring Boot Admin UI: `http://localhost:8888`

Requires env: `ADMIN_SERVICE_CLIENT_SECRET`, `KEYCLOAK_ISSUER_URI`

### 3. Run platform-service

```bash
./gradlew :services:platform-service:bootRun
```

API base: `http://localhost:8090`

Requires env: `PLATFORM_DB_URL`, `PLATFORM_DB_USER`, `PLATFORM_DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `MIKEMES_SECURITY_WEBHOOK_TOKEN`

### 4. Run gateway-service (for routed access)

```bash
./gradlew :services:gateway-service:bootRun
```

Gateway: `http://localhost:8080`

Routes:
- `http://localhost:8080/api/platform/**` → platform-service:8090
- `http://localhost:8080/api/admin/**` → admin-service:8888

### 5. Verify SBA UI

Open `http://localhost:8888` — log in via Keycloak. All registered services (iam-service, platform-service, gateway-service) should appear with `UP` status.

---

## New Environment Variables

Add to `.env` (see `.env.example` for descriptions):

| Variable | Example | Purpose |
|---|---|---|
| `ADMIN_SERVICE_CLIENT_SECRET` | `<generate>` | Keycloak client secret for admin-service OIDC |
| `ADMIN_SERVICE_URL` | `http://admin-service:8888` | SBA server URL (used by all SBA clients) |
| `PLATFORM_DB_URL` | `jdbc:postgresql://localhost:5432/mikemes?currentSchema=platform` | platform-service PostgreSQL URL |
| `PLATFORM_DB_USER` | `platform_user` | platform-service DB user |
| `PLATFORM_DB_PASSWORD` | `<generate>` | platform-service DB password |
| `PLATFORM_SERVICE_URL` | `http://platform-service:8090` | Used by gateway routing |

---

## Running Tests

```bash
# All checks (unit + integration + lint + coverage)
./gradlew :services:admin-service:check
./gradlew :services:platform-service:check

# Integration tests only
./gradlew :services:platform-service:test --tests "*.integration.*"
```

Integration tests use Testcontainers — Docker must be running.

---

## Optional: Portainer

```bash
docker compose -f docker/compose-tools.yml up -d portainer
```

Portainer UI: `http://localhost:9000`

On first start, Portainer prompts for admin password setup — complete it within 5 minutes or the container must be restarted to reset the timeout.

Verify: after login, navigate to **Home → local → Containers**. All running `mikemes-*` containers should appear in the list with their status and published ports.

---

## API Quick Reference

### platform-service (direct, port 8090)

```bash
# Get a config entry (requires JWT)
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8090/api/platform/config/test.key

# Upsert a config entry
curl -X PUT \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"value":"some-value","description":"Test entry"}' \
     http://localhost:8090/api/platform/config/test.key

# Internal read (webhook token, for service-to-service)
curl -H "Authorization: Bearer $WEBHOOK_TOKEN" \
     http://localhost:8090/internal/config/test.key
```

### Via gateway (port 8080, JWT required)

```bash
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/platform/config/test.key
```
