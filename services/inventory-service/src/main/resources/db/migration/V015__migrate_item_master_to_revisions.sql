-- MES-114: Migrate item_master rows into item (identity) + item_revision (versioned).
--
-- Strategy:
-- 1. One item row per (org_id, part_number) pair — earliest record determines identity metadata.
-- 2. Every item_master row becomes an item_revision with revision = ROW_NUMBER - 1 (0-based).
-- 3. item_revision.id is set to item_master.id so that bom_line.component_item_id FKs
--    continue to resolve after V020 migrates bom_line to use component_item_revision_id.
-- 4. All migrated revisions get revision_status = 'APPROVED' (they are production-live).
-- 5. approved_by / approved_at are back-filled from modified_by / modified_at.

INSERT INTO inventory.item (id, org_id, part_number, created_by, created_at)
SELECT
    gen_random_uuid(),
    org_id,
    part_number,
    (array_agg(created_by ORDER BY created_at ASC))[1],
    MIN(created_at)
FROM inventory.item_master
GROUP BY org_id, part_number;

INSERT INTO inventory.item_revision (
    id,
    item_id,
    revision,
    revision_status,
    description,
    unit_of_measure,
    cage_code,
    classification,
    make_buy_code,
    traceability_method,
    shelf_life_controlled,
    shelf_life_days,
    step_part_ref,
    counterfeit_risk_level,
    approved_suppliers,
    verification_required,
    custom_fields,
    approved_by,
    approved_at,
    created_by,
    created_at,
    modified_by,
    modified_at
)
SELECT
    im.id,
    i.id,
    (ROW_NUMBER() OVER (PARTITION BY im.org_id, im.part_number ORDER BY im.created_at ASC) - 1)::INTEGER,
    'APPROVED',
    im.description,
    im.unit_of_measure,
    im.cage_code,
    im.classification,
    im.make_buy_code,
    im.traceability_method,
    im.shelf_life_controlled,
    im.shelf_life_days,
    im.step_part_ref,
    im.counterfeit_risk_level,
    im.approved_suppliers,
    im.verification_required,
    im.custom_fields,
    im.modified_by,
    im.modified_at,
    im.created_by,
    im.created_at,
    im.modified_by,
    im.modified_at
FROM inventory.item_master im
JOIN inventory.item i ON i.org_id = im.org_id AND i.part_number = im.part_number;
