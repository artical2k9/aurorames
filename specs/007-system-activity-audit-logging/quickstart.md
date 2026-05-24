# Quickstart: System Activity & Audit Logging (MES-7)

## Prerequisites

- Docker Compose running (includes Kafka, PostgreSQL, Keycloak from MES-6 infrastructure)
- Java 21 (auto-provisioned by Gradle toolchain — no manual install needed)
- Gradle wrapper available: `./gradlew`

---

## 1. Build the Keycloak SPI listener

The Keycloak audit SPI must be built and available before starting Keycloak:

```bash
./gradlew :libs:lib-keycloak-audit-spi:jar
```

The JAR will be at `libs/lib-keycloak-audit-spi/build/libs/lib-keycloak-audit-spi.jar`.

Docker Compose mounts this automatically via the volume binding in `compose-infra.yml` — no manual copy needed.

---

## 2. Build the shared libraries

```bash
./gradlew :libs:lib-common-audit:publishToMavenLocal
./gradlew :libs:lib-common-events:publishToMavenLocal
```

Domain services that depend on these libraries resolve them from mavenLocal.

---

## 3. Configure environment

Copy `.env.example` to `.env` and populate audit-service variables:

```env
# PostgreSQL — audit-service
AUDIT_DB_NAME=mikemes
AUDIT_DB_SCHEMA=audit
AUDIT_SERVICE_DB_USERNAME=audit_service
AUDIT_SERVICE_DB_PASSWORD=<generate with: openssl rand -base64 32>
AUDIT_FLYWAY_DB_USERNAME=audit_flyway
AUDIT_FLYWAY_DB_PASSWORD=<generate with: openssl rand -base64 32>

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
AUDIT_KAFKA_CONSUMER_GROUP=audit-service-group

# Keycloak SPI Kafka publisher (used inside Keycloak JVM)
KEYCLOAK_AUDIT_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KEYCLOAK_AUDIT_KAFKA_TOPIC=mes.audit.events

# Health: Kafka consumer lag threshold
AUDIT_HEALTH_KAFKA_LAG_THRESHOLD=1000
```

---

## 4. Start the infrastructure

```bash
docker compose -f docker/compose-infra.yml up -d
```

Keycloak will auto-discover the SPI JAR at startup. Verify:

```bash
docker compose -f docker/compose-infra.yml logs keycloak | grep "audit-listener"
# Expected: Keycloak registered the custom event listener provider
```

---

## 5. Run Flyway migrations

Migrations run automatically on audit-service startup. To run manually:

```bash
./gradlew :services:audit-service:flywayMigrate
```

Expected log output:
```
Successfully applied 2 migrations to schema "audit"
```

Verify schema:

```bash
docker exec -it $(docker ps -qf name=postgres) psql -U postgres -d mikemes -c "\dt audit.*"
```

---

## 6. Build and start audit-service

```bash
./gradlew :services:audit-service:bootRun
```

Service starts on port `8090` (direct) and is available via gateway at `http://localhost:8080/api/audit/`.

Health check:

```bash
curl -sf http://localhost:8090/actuator/health | jq .
# {"status":"UP","components":{"db":{"status":"UP"},"kafka":{"status":"UP","details":{"lag":0}}}}
```

---

## 7. Run the test suite

```bash
# Unit tests only (fast — no Docker required)
./gradlew :services:audit-service:test --tests "*Test"

# Integration tests (requires Docker for Testcontainers)
./gradlew :services:audit-service:test --tests "*IT"

# Full check (lint + unit + IT)
./gradlew :services:audit-service:check
```

For shared libraries:

```bash
./gradlew :libs:lib-common-audit:check
./gradlew :libs:lib-common-events:check
```

---

## 8. Smoke-test Kafka event ingestion

Publish a test event to the `mes.audit.events` topic:

```bash
docker exec -it $(docker ps -qf name=kafka) kafka-console-producer.sh \
  --broker-list kafka:9092 \
  --topic mes.audit.events <<EOF
{"eventId":"00000000-0000-0000-0000-000000000001","eventType":"KAFKA_EVENT","serviceSource":"smoke-test","entityType":"SmokeTest","entityId":"1","userId":"system:smoke-test","timestamp":"2026-05-24T10:00:00Z","action":"PUBLISH","payload":{},"schemaVersion":1}
EOF
```

Query to verify ingestion:

```bash
curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8090/audit/entities/SmokeTest/1/history?from=2026-05-24T00:00:00Z&to=2026-05-24T23:59:59Z" | jq .
```

---

## 9. Test tamper-evidence verification

```bash
# Run verification over last hour (should PASS on a fresh DB)
curl -sf -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://localhost:8090/audit/verify?from=$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%SZ)&to=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | jq .
# Expected: {"status":"PASS","recordsChecked":N,"violations":[],"verifiedAt":"..."}
```

---

## SonarQube / SonarCloud

The new modules must be registered in `sonar-project.properties` **after** their source directories are created:

```properties
sonar.sources=...,\
  libs/lib-common-audit/src/main/java,\
  libs/lib-common-events/src/main/java,\
  libs/lib-keycloak-audit-spi/src/main/java,\
  services/audit-service/src/main/java
sonar.tests=...,\
  libs/lib-common-audit/src/test/java,\
  libs/lib-common-events/src/test/java,\
  services/audit-service/src/test/java
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Keycloak doesn't log audit events | SPI JAR not mounted or not built | Run step 1; check compose-infra.yml volume binding |
| `audit_records` INSERT fails | DB role lacks INSERT privilege | Re-run V002 migration; check `AUDIT_SERVICE_DB_USERNAME` |
| Consumer lag health DOWN | Kafka topic backlog exceeds threshold | Check audit-service logs for consumer errors; restart if stuck |
| Tamper check always FAIL | Clock skew between services | Ensure all containers use same NTP source; restart affected services |
| Envers `_AUD` tables missing | Domain service not including `lib-common-audit` | Add `implementation project(':libs:lib-common-audit')` to domain service build.gradle |
