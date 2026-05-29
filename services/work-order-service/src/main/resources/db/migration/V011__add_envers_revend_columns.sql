-- Hibernate 6.6 changed the default Envers audit strategy to ValidityAuditStrategy,
-- which requires revend (FK to revinfo.rev) and revend_tstmp (epoch ms) on every _aud table.
ALTER TABLE work_order.item_master_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES work_order.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE work_order.bill_of_materials_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES work_order.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE work_order.bom_line_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES work_order.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE work_order.engineering_change_order_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES work_order.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE work_order.udf_field_definition_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES work_order.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;
