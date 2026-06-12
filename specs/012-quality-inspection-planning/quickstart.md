# Quickstart: Quality Inspection Planning (MES-12)

## Prereqs
- `settings.gradle` includes `services:quality-service`; stack rebuilt: `docker compose -f docker/compose-infra.yml up -d --build quality-service gateway-service`
- Startup log: Flyway `Successfully applied 3 migration(s)`; privilege manifest registered

## Smoke test (via gateway, port 8082 — ERR-MES-067)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mes/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mes-frontend \
  -d username=admin@test.org -d password='Admin123!' | jq -r .access_token)

# pick an existing item master id
ITEM=$(curl -s "http://localhost:8082/api/v1/item-master?size=1" -H "Authorization: Bearer $TOKEN" | jq -r '.content[0].id')

# create plan
PLAN=$(curl -s -X POST http://localhost:8082/api/v1/inspection-plans \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"itemId\":\"$ITEM\",\"name\":\"Machined housing control plan\"}" | jq -r .id)

# SPECIFIC characteristic
curl -s -X POST http://localhost:8082/api/v1/inspection-plans/$PLAN/characteristics \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"characteristicNumber":10,"name":"Bore dia","source":"DESIGN","characteristicType":"SPECIFIC",
       "unitOfMeasure":"mm","sampleSizeRule":"ALL","recordingBasis":"PER_PIECE",
       "nominalValue":25.4,"lowerLimit":25.38,"upperLimit":25.42}'

# COMMON characteristic
curl -s -X POST http://localhost:8082/api/v1/inspection-plans/$PLAN/characteristics \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"characteristicNumber":20,"name":"CoC present","source":"IN_PROCESS","characteristicType":"COMMON",
       "sampleSizeRule":"ALL","recordingBasis":"PER_LOT","expectedBoolean":true}'

# submit + approve, then consumer check
curl -s -X POST http://localhost:8082/api/v1/inspection-plans/$PLAN/submit -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8082/api/v1/inspection-plans/$PLAN/approve -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8082/api/v1/inspection-plans/by-item/$ITEM/status -H "Authorization: Bearer $TOKEN"
```

Expected: status endpoint returns `{ "exists": true, "approved": true, "latestApprovedRevision": 0 }`.

## UI
Quality > Inspection Plans → list → detail with revision selector/history and characteristics grid; type-specific forms per characteristic type; Submit/Approve/Reject/Create Revision buttons mirror BOM authoring.

## Tests
`./gradlew :services:quality-service:check` — ExpressionValidator unit tests (refs, cycles, grammar); lifecycle + consumer-contract ITs extending BaseIntegrationTest (ERR-MES-080).
