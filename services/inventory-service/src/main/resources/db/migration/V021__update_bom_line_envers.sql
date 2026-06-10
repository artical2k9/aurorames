-- MES-114: Mirror V020 column changes in bom_line_aud (required by Envers schema-validation).

ALTER TABLE inventory.bom_line_aud
    ADD COLUMN IF NOT EXISTS bom_revision_id           UUID,
    ADD COLUMN IF NOT EXISTS component_item_revision_id UUID;

UPDATE inventory.bom_line_aud
SET bom_revision_id            = bom_id,
    component_item_revision_id = component_item_id
WHERE bom_revision_id IS NULL;

ALTER TABLE inventory.bom_line_aud
    DROP COLUMN IF EXISTS bom_id,
    DROP COLUMN IF EXISTS component_item_id;
