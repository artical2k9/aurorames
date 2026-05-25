## Summary

<!-- 1–3 bullet points: what this PR does and why -->

-

## Jira

Ref: MES-

## Governance checklist

- [ ] Commit messages follow `[type](MES-NNN): short description` format exactly
- [ ] Branch name matches pattern `NNN-<feature-name>` (e.g. `001-iam-multi-org-security-keycloak`)
- [ ] PR targets `Develop` — never `main`
- [ ] Scoped to a single ticket — no bundled unrelated changes
- [ ] No debug code, `System.out.println`, `TODO`, or commented-out code included
- [ ] Security considerations reviewed (Constitution §VII — Keycloak-only auth, no secrets in code)
- [ ] `./gradlew check` passes locally (lint + unit tests, zero failures)
- [ ] Tests written for all new/changed logic — coverage not decreased
- [ ] **Pre-retrospective verification completed:**
  - [ ] Identified relevant categories in [MES-ERR-001_Index.md](docs/governance/MES-ERR-001_Index.md) for this ticket's scope
  - [ ] Spot-checked each category — confirmed no lessons were violated in the code
  - [ ] Any violations logged to `MES-ERR-001_Agent_Error_Log.md`
- [ ] **Deployment steps documented below** (mandatory — PR cannot be merged without this section)
- [ ] **Usage Cost section completed** (run `scripts/feature-cost.ps1` and paste output below)

## Deployment Steps

<!--
MANDATORY — cannot be empty. Include steps for every change type that applies:

Infrastructure (Docker Compose):
  docker compose -f docker/compose-infra.yml restart <service>

Flyway migrations (auto-applied on service start — list files added):
  services/iam-service/src/main/resources/db/migration/V<n>__<description>.sql

Keycloak realm changes (if keycloak/mes-realm.json changed):
  Re-import: docker compose -f docker/compose-infra.yml restart keycloak
  OR apply via admin console — list specific changes made

Environment variables (if new vars added):
  Confirm added to both .env and .env.example; list vars and generation instructions

Service restart (if Spring Boot services updated):
  List services to restart and restart command

Verification:
  How to confirm the deployment succeeded
-->

### Step 1:

```bash

```

### Step 2: Verify

```bash

```

## Usage Cost

<!-- Run `.\scripts\feature-cost.ps1` from the repo root and paste the output here -->

## Test plan

<!-- How was this tested? What scenarios were covered? -->

- [ ]
