# Quickstart: Work Instructions (MES-10)

## Prereqs
- Docker stack running: `docker compose -f docker/compose-infra.yml up -d`
- Keycloak realm includes new `mes-signature-verify` client (re-import realm or add manually; set secret in `.env`)
- engineering-service rebuilt with V007+ migrations applied (check startup log: `Successfully applied N migration(s)`)

## Smoke test (via gateway, port 8082 — ERR-MES-067)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mes/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mes-frontend \
  -d username=admin@test.org -d password='Admin123!' | jq -r .access_token)

# create
WI=$(curl -s -X POST http://localhost:8082/api/v1/work-instructions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Install bracket","description":"Demo"}' | jq -r .id)

# add a step
curl -s -X POST http://localhost:8082/api/v1/work-instructions/$WI/steps \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"stepNumber":10,"title":"Deburr","bodyHtml":"<p>Deburr edges</p>"}'

# submit + approve with e-signature
curl -s -X POST http://localhost:8082/api/v1/work-instructions/$WI/submit -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://localhost:8082/api/v1/work-instructions/$WI/approve \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"password":"Admin123!","meaning":"APPROVED"}'
```

Expected: approve returns 200 with `revisionStatus: "APPROVED"` and a `signature` object (name, signedAt, meaning). Wrong password → 422 `SIGNATURE_VERIFICATION_FAILED`, status stays PENDING_APPROVAL.

## UI
Engineering > Work Instructions → list → New, author steps, upload media, Submit, Approve (password dialog). Revision history table mirrors Item Master detail.

## Tests
`./gradlew :services:engineering-service:check` — ITs extend BaseIntegrationTest directly (ERR-MES-080); KC re-auth path tested against Testcontainers KC.
