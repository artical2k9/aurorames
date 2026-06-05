-- Hibernate 6.6 ValidityAuditStrategy requires revend (FK to revinfo.rev) and revend_tstmp
-- on every _aud table.
ALTER TABLE engineering.engineering_change_order_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES engineering.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;

ALTER TABLE engineering.udf_field_definition_aud
    ADD COLUMN IF NOT EXISTS revend      INTEGER REFERENCES engineering.revinfo (rev),
    ADD COLUMN IF NOT EXISTS revend_tstmp TIMESTAMPTZ;
