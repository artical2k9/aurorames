# Quickstart: work-order-service (Item Master & BOM)

## Prerequisites

- Docker Desktop running (Testcontainers needs a Docker daemon)
- Java 21 (Eclipse Temurin)
- The full `docker/compose-infra.yml` stack running (Postgres, Kafka, Keycloak)

## Running the infra stack

```bash
docker compose -f docker/compose-infra.yml up -d postgres kafka keycloak
```

Wait for Keycloak to be healthy:
```bash
docker compose -f docker/compose-infra.yml ps
```

## First-time setup

```powershell
# Publish shared libs to mavenLocal (required before building work-order-service)
./gradlew :libs:lib-common-security:publishToMavenLocal
./gradlew :libs:lib-common-audit:publishToMavenLocal
./gradlew :libs:mes-udf-lib:publishToMavenLocal
```

## Build & test

```powershell
# Full check (Checkstyle + SpotBugs + unit tests + integration tests)
./gradlew :services:work-order-service:check

# Unit tests only (fast, no Docker required)
./gradlew :services:work-order-service:test --tests "com.mes.workorder.*.unit.*"

# Integration tests (requires Docker for Testcontainers)
./gradlew :services:work-order-service:test --tests "com.mes.workorder.*.integration.*"
```

## Running the service locally

Set these environment variables (or create a `.env.local` file):

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mes?currentSchema=work_order
SPRING_DATASOURCE_USERNAME=mes
SPRING_DATASOURCE_PASSWORD=mes
KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/mes
IAM_SERVICE_URL=http://localhost:8085
MES_SECURITY_WEBHOOK_TOKEN=dev-webhook-token
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_KAFKA_CONSUMER_GROUP_ID=work-order-service
```

```powershell
./gradlew :services:work-order-service:bootRun
```

Service starts on port **8095** by default. Swagger UI: http://localhost:8095/swagger-ui.html

## API smoke test

Get a token from Keycloak (system-admin user):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mes/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=mes-frontend&username=admin@mes.local&password=admin' \
  | jq -r '.access_token')
```

Create an item master record:
```bash
curl -s -X POST http://localhost:8095/item-master \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "partNumber": "BRKT-001",
    "revision": "A",
    "description": "Aluminium mounting bracket",
    "unitOfMeasure": "EA",
    "classification": "FABRICATED",
    "makeBuyCode": "MAKE",
    "traceabilityMethod": "SERIAL",
    "shelfLifeControlled": false
  }' | jq .
```

## Flyway migrations location

```
services/work-order-service/src/main/resources/db/migration/
├── V001__create_work_order_schema.sql
├── V002__create_item_master.sql
├── V003__create_bom_tables.sql
├── V004__create_eco_tables.sql
├── V005__create_udf_field_definition.sql
├── V006__add_envers_tables.sql
└── V007__seed_item_master_privileges.sql
```

## Adding the service to local Docker Compose

`docker/compose-local-override.yml` will include `work-order-service:local` once the first Docker image is built by CI. Until then, run directly via `bootRun`.

Add to `docker/compose-infra.yml` gateway routes once the service is deployed:
```yaml
# In gateway-service environment:
WORK_ORDER_SERVICE_URL: http://work-order-service:8095
```
