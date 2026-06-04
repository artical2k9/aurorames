package com.mes.inventory.bom.repository;

import com.mes.inventory.bom.domain.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {

    List<BomLine> findAllByBomId(UUID bomId);

    List<BomLine> findAllByBomIdAndFindNumber(UUID bomId, String findNumber);

    boolean existsByBomIdAndFindNumber(UUID bomId, String findNumber);

    /**
     * Recursive BOM explosion via CTE. Returns flat rows ordered by depth.
     * Columns: [0] component_item_id (text), [1] parent_item_id (text),
     *          [2] depth (int), [3] part_number, [4] revision, [5] description,
     *          [6] unit_of_measure, [7] counterfeit_risk_level (nullable), [8] status,
     *          [9] effectivity_method (nullable), [10] effective_from_date (text, nullable),
     *          [11] effective_to_date (text, nullable), [12] effective_from_unit (nullable),
     *          [13] effective_to_unit (nullable), [14] find_number,
     *          [15] quantity (numeric nullable), [16] make_buy_code (nullable)
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE bom_tree AS (
                SELECT
                    bl.component_item_id,
                    bom.parent_item_id,
                    1 AS depth,
                    bl.effectivity_method,
                    bl.effective_from_date,
                    bl.effective_to_date,
                    bl.effective_from_unit,
                    bl.effective_to_unit,
                    bl.find_number,
                    bl.quantity
                FROM inventory.bom_line bl
                JOIN inventory.bill_of_materials bom ON bom.id = bl.bom_id
                WHERE bl.bom_id = CAST(:rootBomId AS uuid)
                UNION ALL
                SELECT
                    bl.component_item_id,
                    bt.component_item_id,
                    bt.depth + 1,
                    bl.effectivity_method,
                    bl.effective_from_date,
                    bl.effective_to_date,
                    bl.effective_from_unit,
                    bl.effective_to_unit,
                    bl.find_number,
                    bl.quantity
                FROM bom_tree bt
                JOIN inventory.bill_of_materials bom ON bom.parent_item_id = bt.component_item_id
                JOIN inventory.bom_line bl ON bl.bom_id = bom.id
            )
            SELECT
                bt.component_item_id::text,
                bt.parent_item_id::text,
                bt.depth,
                im.part_number,
                im.revision,
                im.description,
                im.unit_of_measure,
                im.counterfeit_risk_level,
                im.status,
                bt.effectivity_method,
                bt.effective_from_date::text,
                bt.effective_to_date::text,
                bt.effective_from_unit,
                bt.effective_to_unit,
                bt.find_number,
                bt.quantity,
                im.make_buy_code
            FROM bom_tree bt
            JOIN inventory.item_master im ON im.id = bt.component_item_id
            ORDER BY bt.depth, bt.component_item_id
            """)
    List<Object[]> findExplosionRows(@Param("rootBomId") UUID rootBomId);
}
