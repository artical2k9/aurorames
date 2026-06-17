-- MES-9: Make route/operation revisions 0-based, consistent with item-master and BOM
-- (which already start at 0). The first revision of a route is now 0, not 1.
--
-- Existing rows are seed/test data only: rebase them down by one so the lowest revision
-- becomes 0. Each route is a single row whose `revision` is bumped in place on a new
-- revision (no per-row uniqueness on revision), and approval records are append-only, so a
-- uniform `- 1` shift cannot collide. The WHERE guards keep already-0 rows untouched and
-- prevent negative values.

-- New default for freshly inserted rows.
ALTER TABLE routing.route            ALTER COLUMN revision           SET DEFAULT 0;
ALTER TABLE routing.route_operation  ALTER COLUMN operation_revision SET DEFAULT 0;

-- Live tables.
UPDATE routing.route            SET revision           = revision           - 1 WHERE revision           >= 1;
UPDATE routing.route_operation  SET operation_revision = operation_revision - 1 WHERE operation_revision >= 1;

-- Envers audit history (keep snapshots consistent with the live values).
UPDATE routing.route_aud            SET revision           = revision           - 1 WHERE revision           >= 1;
UPDATE routing.route_operation_aud  SET operation_revision = operation_revision - 1 WHERE operation_revision >= 1;

-- Immutable approval records reference the route/operation revision they signed off.
UPDATE routing.approval_record  SET revision                 = revision                 - 1 WHERE revision                 >= 1;
UPDATE routing.approval_record  SET governing_route_revision = governing_route_revision - 1 WHERE governing_route_revision >= 1;
