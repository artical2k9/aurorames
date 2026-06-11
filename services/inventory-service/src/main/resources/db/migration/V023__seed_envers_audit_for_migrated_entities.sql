-- MES-114: Seed initial Envers audit entries for rows created by V015/V018 raw-SQL migrations.
-- ValidityAuditStrategy requires at least one ADD entry in the _aud table before it can
-- process any subsequent UPDATE; without this seed, every PATCH returns 500 with
-- "Cannot update previous revision for entity X_AUD (0 rows modified)".

WITH migration_rev AS (
    INSERT INTO inventory.revinfo (rev, revtstmp, actor)
    VALUES (nextval('inventory.revinfo_seq'), EXTRACT(EPOCH FROM NOW())::bigint * 1000, 'migration')
    RETURNING rev
),
seed_item_aud AS (
    INSERT INTO inventory.item_aud (id, rev, revtype, org_id, part_number, created_by, created_at, revend, revend_tstmp)
    SELECT i.id, mr.rev, 0, i.org_id, i.part_number, i.created_by, i.created_at, NULL, NULL
    FROM inventory.item i
    CROSS JOIN migration_rev mr
    RETURNING 1
),
seed_item_revision_aud AS (
    INSERT INTO inventory.item_revision_aud (
        id, rev, revtype,
        item_id, revision, revision_status,
        description, unit_of_measure, cage_code, classification, make_buy_code,
        traceability_method, shelf_life_controlled, shelf_life_days, step_part_ref,
        counterfeit_risk_level, approved_suppliers, verification_required, custom_fields,
        submitted_by, submitted_at, approved_by, approved_at,
        rejected_by, rejected_at, rejection_reason,
        created_by, created_at, modified_by, modified_at,
        revend, revend_tstmp
    )
    SELECT
        ir.id, mr.rev, 0,
        ir.item_id, ir.revision, ir.revision_status,
        ir.description, ir.unit_of_measure, ir.cage_code, ir.classification, ir.make_buy_code,
        ir.traceability_method, ir.shelf_life_controlled, ir.shelf_life_days, ir.step_part_ref,
        ir.counterfeit_risk_level, ir.approved_suppliers, ir.verification_required, ir.custom_fields,
        ir.submitted_by, ir.submitted_at, ir.approved_by, ir.approved_at,
        ir.rejected_by, ir.rejected_at, ir.rejection_reason,
        ir.created_by, ir.created_at, ir.modified_by, ir.modified_at,
        NULL, NULL
    FROM inventory.item_revision ir
    CROSS JOIN migration_rev mr
    RETURNING 1
),
seed_bom_aud AS (
    INSERT INTO inventory.bom_aud (id, rev, revtype, org_id, parent_item_id, created_by, created_at, revend, revend_tstmp)
    SELECT b.id, mr.rev, 0, b.org_id, b.parent_item_id, b.created_by, b.created_at, NULL, NULL
    FROM inventory.bom b
    CROSS JOIN migration_rev mr
    RETURNING 1
)
INSERT INTO inventory.bom_revision_aud (
    id, rev, revtype,
    bom_id, revision, revision_status,
    description, eco_id, reason_for_revision, production_line, bom_type, effectivity_type,
    custom_fields,
    submitted_by, submitted_at, approved_by, approved_at,
    rejected_by, rejected_at, rejection_reason,
    created_by, created_at, modified_by, modified_at,
    revend, revend_tstmp
)
SELECT
    br.id, mr.rev, 0,
    br.bom_id, br.revision, br.revision_status,
    br.description, br.eco_id, br.reason_for_revision, br.production_line, br.bom_type, br.effectivity_type,
    br.custom_fields,
    br.submitted_by, br.submitted_at, br.approved_by, br.approved_at,
    br.rejected_by, br.rejected_at, br.rejection_reason,
    br.created_by, br.created_at, br.modified_by, br.modified_at,
    NULL, NULL
FROM inventory.bom_revision br
CROSS JOIN migration_rev mr;
