# Dev Data Seed — Item Master & BOMs

`scripts/seed-dev-data.ps1` re-creates a small, realistic set of Item Master
records and Bills of Material so local/dev environments have usable test data
after a Postgres volume reset.

It seeds through the **gateway API** (not direct SQL), so every record passes
the same validation, workflow (draft → submit → approve) and audit rules a real
user would hit. It is **idempotent**: each part and BOM is checked with a GET
first and only created when missing, so re-running it is safe and makes no
changes once the data exists.

## Prerequisites

The infra stack must be up and healthy:

```bash
docker compose -f docker/compose-infra.yml ps   # gateway + keycloak + inventory-service healthy
```

## Run

```powershell
./scripts/seed-dev-data.ps1
```

Override any of the defaults via parameters or `SEED_*` environment variables:

| Parameter      | Env var             | Default                  |
|----------------|---------------------|--------------------------|
| `-GatewayUrl`  | `SEED_GATEWAY_URL`  | `http://localhost:8082`  |
| `-KeycloakUrl` | `SEED_KEYCLOAK_URL` | `http://localhost:8080`  |
| `-Realm`       | `SEED_REALM`        | `mes`                    |
| `-ClientId`    | `SEED_CLIENT_ID`    | `mes-frontend`           |
| `-Username`    | `SEED_USERNAME`     | `admin@test.org`         |
| `-Password`    | `SEED_PASSWORD`     | `Admin123!`              |

The defaults are the dev realm seed values committed in
`keycloak/mes-realm.json`. **Dev/test only — never point this at a production realm.**

## What it seeds

**Item Master** (all created as approved revision A):

| Part number       | Classification  | Make/Buy |
|-------------------|-----------------|----------|
| `RM-AL6061-BAR`   | RAW_MATERIAL    | BUY      |
| `PP-BOLT-M6`      | PURCHASED_PART  | BUY      |
| `FB-BRKT-1001`    | FABRICATED      | MAKE     |
| `AS-SUBASSY-2001` | ASSEMBLY        | MAKE     |
| `AS-TOPASSY-3000` | ASSEMBLY        | MAKE     |

**BOMs** (created and approved):

- `AS-SUBASSY-2001` → `FB-BRKT-1001` ×1, `PP-BOLT-M6` ×4
- `AS-TOPASSY-3000` → `AS-SUBASSY-2001` ×2, `RM-AL6061-BAR` ×1
