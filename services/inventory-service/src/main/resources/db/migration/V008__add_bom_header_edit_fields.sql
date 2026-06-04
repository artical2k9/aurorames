-- Add BOM header edit fields for BomHeaderEditDialog
ALTER TABLE inventory.bill_of_materials
    ADD COLUMN IF NOT EXISTS reason_for_revision VARCHAR(500),
    ADD COLUMN IF NOT EXISTS production_line      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS bom_type             VARCHAR(30),
    ADD COLUMN IF NOT EXISTS effectivity_type     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS custom_fields        JSONB;
