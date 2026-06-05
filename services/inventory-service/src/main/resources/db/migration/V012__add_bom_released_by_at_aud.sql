-- MES-8: Mirror V011 columns on the Envers audit table.
-- @Audited entities require column parity between the main table and _aud
-- (Hibernate Envers schema-validation enforces this at startup — ERR-MES-057).
ALTER TABLE inventory.bill_of_materials_aud
    ADD COLUMN IF NOT EXISTS released_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS released_at  TIMESTAMPTZ;
