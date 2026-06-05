-- MES-8: Add released_by and released_at to bill_of_materials.
-- These are set once when a BOM transitions DRAFT → RELEASED and never updated
-- thereafter. Nullable because existing and future DRAFT/OBSOLETE BOMs never
-- go through a release action.
ALTER TABLE inventory.bill_of_materials
    ADD COLUMN IF NOT EXISTS released_by  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS released_at  TIMESTAMPTZ;
