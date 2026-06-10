-- MES-114: Drop legacy tables now that all data and FKs have been migrated.
-- bill_of_materials: FK from bom_line dropped in V020; bom_revision FKs point to bom, not here.
-- item_master: FK from bill_of_materials dropped with table; bom_line FK dropped in V020.

-- V003 FK from bill_of_materials to item_master is removed by dropping bill_of_materials.
DROP TABLE IF EXISTS inventory.bill_of_materials;

DROP TABLE IF EXISTS inventory.item_master;
