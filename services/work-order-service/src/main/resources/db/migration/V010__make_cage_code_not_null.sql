-- AUD2-C001: cage_code listed as mandatory in FR-001 but was nullable.
-- Backfill any existing nulls before enforcing the constraint.
UPDATE work_order.item_master SET cage_code = '' WHERE cage_code IS NULL;
ALTER TABLE work_order.item_master ALTER COLUMN cage_code SET NOT NULL;
