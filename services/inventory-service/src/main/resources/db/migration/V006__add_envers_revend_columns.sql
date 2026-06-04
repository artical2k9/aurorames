-- Hibernate Envers ValidityAuditStrategy requires revend and revend_tstmp on every _aud table.
ALTER TABLE inventory.item_master_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES inventory.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE inventory.bill_of_materials_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES inventory.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE inventory.bom_line_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES inventory.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;
