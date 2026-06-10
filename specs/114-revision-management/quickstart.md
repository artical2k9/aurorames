# Quickstart: MES-114 — Local Development Notes

## Prerequisite: Database Migration Risk

This feature runs Flyway migrations V014–V022 that **rename and drop core tables** (`item_master`, `bill_of_materials`). Before running locally:

1. Ensure all existing integration tests pass on Develop before starting implementation.
2. Take a database snapshot: `docker exec mes-postgres pg_dump -U mes -d mes > /tmp/pre-114-backup.sql`
3. Run migrations in order and verify row counts after each data migration step.

## Verifying V015 (item_master migration)

After V015 runs:
```sql
-- Row counts must match
SELECT COUNT(*) FROM inventory.item_master;       -- original count
SELECT COUNT(*) FROM inventory.item_revision;     -- must equal original count
SELECT COUNT(*) FROM inventory.item;              -- must equal distinct (org_id, part_number) count

-- Verify revision integers
SELECT i.part_number, ir.revision, ir.revision_status
FROM inventory.item_revision ir
JOIN inventory.item i ON i.id = ir.item_id
ORDER BY i.part_number, ir.revision;
```

## Verifying V020 (bom_line FK migration)

After V020 runs:
```sql
-- No NULL FKs should exist
SELECT COUNT(*) FROM inventory.bom_line WHERE bom_revision_id IS NULL;       -- must = 0
SELECT COUNT(*) FROM inventory.bom_line WHERE component_item_revision_id IS NULL; -- must = 0

-- Old columns must be gone
SELECT column_name FROM information_schema.columns
WHERE table_schema = 'inventory' AND table_name = 'bom_line';
-- Should NOT include: bom_id, component_item_id
```

## Running the service locally

```bash
# Standard service startup — migrations apply automatically on start
./gradlew :services:inventory-service:bootRun

# Run integration tests (uses Testcontainers — starts its own Postgres)
./gradlew :services:inventory-service:check
```

## Testing the revision workflow manually

```bash
# 1. Create a new item (returns revision=0, revisionStatus=DRAFT)
curl -X POST http://localhost:8082/api/v1/item-master \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"partNumber":"TEST-REV-001","description":"Test bracket","unitOfMeasure":"EA","classification":"MECHANICAL","makeBuyCode":"BUY","traceabilityMethod":"LOT"}'

# 2. Submit for approval
curl -X POST http://localhost:8082/api/v1/item-master/{id}/submit \
  -H "Authorization: Bearer $TOKEN"

# 3. Approve (requires item-master:revisions:approve privilege — use SYSTEM_ADMIN token)
curl -X POST http://localhost:8082/api/v1/item-master/{id}/approve \
  -H "Authorization: Bearer $SYSADMIN_TOKEN"

# 4. Edit approved item — creates new DRAFT at revision=1
curl -X PATCH http://localhost:8082/api/v1/item-master/{id} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"description":"Updated description"}'
# Response should show revision=1, revisionStatus=DRAFT

# 5. Cancel draft
curl -X DELETE http://localhost:8082/api/v1/item-master/{id}/draft \
  -H "Authorization: Bearer $TOKEN"
# Response 204 — item reverts to revision=0, APPROVED
```

## Frontend dev server

The UDF column picker and BOM browser fixes from MES-113 require a clean `ng serve` restart when files are edited by non-interactive tools (chokidar on Windows may miss changes):

```bash
# Kill existing ng serve, then restart
npx ng serve --port 4200
```
