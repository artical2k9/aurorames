-- Mirror V013 additions into the Envers audit table (required by schema-validation)
ALTER TABLE work_order.bill_of_materials_aud
    ADD COLUMN IF NOT EXISTS reason_for_revision VARCHAR(500),
    ADD COLUMN IF NOT EXISTS production_line      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS bom_type             VARCHAR(30),
    ADD COLUMN IF NOT EXISTS effectivity_type     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS custom_fields        JSONB;
