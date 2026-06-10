-- MES-114: Migrate bom_line foreign keys to bom_revision and item_revision.
--
-- UUID preservation means:
--   bom_revision.id = bill_of_materials.id (preserved in V018)
--   item_revision.id = item_master.id (preserved in V015)
-- so new FK columns can be populated directly from the old FK column values.

-- Step 1: Add nullable new FK columns
ALTER TABLE inventory.bom_line
    ADD COLUMN bom_revision_id          UUID,
    ADD COLUMN component_item_revision_id UUID;

-- Step 2: Populate from preserved UUIDs
UPDATE inventory.bom_line
SET bom_revision_id           = bom_id,
    component_item_revision_id = component_item_id;

-- Step 3: Make NOT NULL
ALTER TABLE inventory.bom_line
    ALTER COLUMN bom_revision_id          SET NOT NULL,
    ALTER COLUMN component_item_revision_id SET NOT NULL;

-- Step 4: Drop old FK constraints
ALTER TABLE inventory.bom_line
    DROP CONSTRAINT fk_bom_line_bom,
    DROP CONSTRAINT fk_bom_line_component;

-- Step 5: Add new FK constraints
ALTER TABLE inventory.bom_line
    ADD CONSTRAINT fk_bom_line_bom_revision
        FOREIGN KEY (bom_revision_id) REFERENCES inventory.bom_revision (id),
    ADD CONSTRAINT fk_bom_line_component_item_revision
        FOREIGN KEY (component_item_revision_id) REFERENCES inventory.item_revision (id);

-- Step 6: Drop old columns
ALTER TABLE inventory.bom_line
    DROP COLUMN bom_id,
    DROP COLUMN component_item_id;

-- Step 7: Update indexes
DROP INDEX IF EXISTS inventory.idx_bom_line_bom_id;
DROP INDEX IF EXISTS inventory.idx_bom_line_component_id;
DROP INDEX IF EXISTS inventory.idx_bom_line_bom_find;

CREATE INDEX idx_bom_line_bom_revision_id       ON inventory.bom_line (bom_revision_id);
CREATE INDEX idx_bom_line_component_revision_id ON inventory.bom_line (component_item_revision_id);
CREATE INDEX idx_bom_line_rev_find              ON inventory.bom_line (bom_revision_id, find_number);
