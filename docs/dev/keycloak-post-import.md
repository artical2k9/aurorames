# Keycloak post-import setup

`keycloak/mes-realm.json` exports every **confidential** client with a blank secret
(`"secret": ""`) so that no client secret is committed to the repo. On a fresh
`start-dev --import-realm`, Keycloak therefore generates a **random** secret for each
of those clients — which will **not** match the `MES_*_SECRET` env vars the services
authenticate with. A couple of post-import steps reconcile this for local/dev stacks.

Run these **once after the stack first comes up**, and again after anything that
re-imports the realm (Keycloak volume reset / container recreate). They are
idempotent — safe to re-run.

## 1. Confidential client secrets — `scripts/set-keycloak-client-secrets.ps1`

Sets each confidential client's secret in Keycloak to the value the services expect
(read from `docker/.env`). Required for the **electronic-signature** flow:
`engineering-service` (Work Instruction approval) and `routing-service` (route
approval) both re-authenticate the approver's password via a Direct Access Grant on
the **`mes-signature-verify`** client. If its secret is unset, approval fails with
**HTTP 422 `SIGNATURE_VERIFICATION_FAILED`**.

```powershell
# from the repo root, with the stack running
./scripts/set-keycloak-client-secrets.ps1
```

The script obtains an admin token (`KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` from
`docker/.env`), then for each client in its `$clientSecretEnv` map sets the secret
from the matching env var, skipping any client whose secret already matches.

**Adding a new confidential client:** export it in `mes-realm.json` with
`"secret": ""`, add its `MES_*_SECRET` to `.env` / `.env.example` / `docker/.env`,
then add one row to `$clientSecretEnv` in the script:

```powershell
$clientSecretEnv = [ordered]@{
    'mes-signature-verify' = 'MES_SIGNATURE_VERIFY_SECRET'
    # 'my-new-client'      = 'MY_NEW_CLIENT_SECRET'
}
```

## 2. Keycloak webhook shared secret (audit events)

Separately, the Keycloak `http-sender` event listener needs its shared secret set to
`KEYCLOAK_WEBHOOK_SECRET` after import (this is a realm-events setting, not a client
secret, so it is **not** handled by the script above):

> Realm Settings → Events → `http-sender` provider → **sharedSecret**

See the comment above the `keycloak` service in `docker/compose-infra.yml` and
`.env.example` for details.

## Verify the e-signature fix

After running step 1, confirm the `mes-signature-verify` Direct Access Grant succeeds
(expect HTTP 200):

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -X POST http://localhost:8080/realms/mes/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mes-signature-verify \
  -d client_secret="$MES_SIGNATURE_VERIFY_SECRET" \
  -d username=admin@test.org -d password=Admin123!
```

Then Work Instruction / route approval (password e-signature) will succeed instead of
returning 422.
