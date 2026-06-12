# Quickstart: Labour Resources & Skills (MES-11)

## Prereqs
- `settings.gradle` includes `services:labour-service`; compose stack rebuilt: `docker compose -f docker/compose-infra.yml up -d --build labour-service gateway-service`
- Startup log shows Flyway `Successfully applied 3 migration(s)` and privilege manifest registration

## Smoke test (via gateway, port 8082 — ERR-MES-067)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mes/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mes-frontend \
  -d username=admin@test.org -d password='Admin123!' | jq -r .access_token)

# skill
SKILL=$(curl -s -X POST http://localhost:8082/api/v1/labour/skills \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"skillCode":"IPC-610","name":"IPC-A-610 Soldering","validityMonths":24}' | jq -r .id)

# employee
EMP=$(curl -s -X POST http://localhost:8082/api/v1/labour/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"employeeNumber":"E-001","firstName":"Ana","lastName":"Reyes"}' | jq -r .id)

# certification (expiry defaults to award + 24 months)
curl -s -X POST http://localhost:8082/api/v1/labour/certifications \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"employeeId\":\"$EMP\",\"skillId\":\"$SKILL\",\"awardDate\":\"2026-06-12\"}"

# qualification evaluation (the MES-10/MES-9 contract)
curl -s -X POST http://localhost:8082/api/v1/labour/qualifications/evaluate \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"employeeId\":\"$EMP\",\"skillIds\":[\"$SKILL\"]}"
```

Expected: evaluate returns `employeeActive: true` and `status: "HELD_ACTIVE"` with expiry 2028-06-12.

## UI
Labour > Employees / Skills / Certifications. Employee detail shows competency profile with state badges and training history. Certifications list has an "expiring within N days" filter.

## Tests
`./gradlew :services:labour-service:check` — boundary-date unit tests for CertificationStateCalculator; gating ITs (expired / revoked / inactive employee ⇒ not qualified).
