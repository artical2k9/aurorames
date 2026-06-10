-- MES-114: Migrate bill_of_materials rows into bom (identity) + bom_revision (versioned).
--
-- Strategy:
-- 1. Group bill_of_materials by (org_id, parent_item_id): one bom row per group.
-- 2. Every bill_of_materials row becomes a bom_revision with revision = ROW_NUMBER - 1 (0-based).
-- 3. bom_revision.id = bill_of_materials.id so bom_line.bom_id FKs continue to resolve
--    during V020 which migrates bom_line to use bom_revision_id.
-- 4. bill_of_materials.parent_item_id = item_revision.id (preserved in V015) →
--    join item_revision to get item.id for bom.parent_item_id.
-- 5. All migrated revisions get revision_status = 'APPROVED' (they are production-live).
-- 6. approved_by / approved_at are back-filled from released_by / released_at (or modified_by / modified_at).

INSERT INTO inventory.bom (id, org_id, parent_item_id, created_by, created_at)
SELECT
    gen_random_uuid(),
    bom.org_id,
    i.id,
    (array_agg(bom.created_by ORDER BY bom.created_at ASC))[1],
    MIN(bom.created_at)
FROM inventory.bill_of_materials bom
JOIN inventory.item_revision ir ON ir.id = bom.parent_item_id
JOIN inventory.item i ON i.id = ir.item_id
GROUP BY bom.org_id, i.id;

INSERT INTO inventory.bom_revision (
    id,
    bom_id,
    revision,
    revision_status,
    description,
    eco_id,
    reason_for_revision,
    production_line,
    bom_type,
    effectivity_type,
    custom_fields,
    approved_by,
    approved_at,
    created_by,
    created_at,
    modified_by,
    modified_at
)
SELECT
    bom_orig.id,
    new_bom.id,
    (ROW_NUMBER() OVER (PARTITION BY new_bom.id ORDER BY bom_orig.created_at ASC) - 1)::INTEGER,
    'APPROVED',
    bom_orig.description,
    bom_orig.eco_id,
    bom_orig.reason_for_revision,
    bom_orig.production_line,
    bom_orig.bom_type,
    bom_orig.effectivity_type,
    bom_orig.custom_fields,
    COALESCE(bom_orig.released_by, bom_orig.modified_by),
    COALESCE(bom_orig.released_at, bom_orig.modified_at),
    bom_orig.created_by,
    bom_orig.created_at,
    bom_orig.modified_by,
    bom_orig.modified_at
FROM inventory.bill_of_materials bom_orig
JOIN inventory.item_revision ir ON ir.id = bom_orig.parent_item_id
JOIN inventory.item i ON i.id = ir.item_id
JOIN inventory.bom new_bom ON new_bom.org_id = bom_orig.org_id AND new_bom.parent_item_id = i.id;
