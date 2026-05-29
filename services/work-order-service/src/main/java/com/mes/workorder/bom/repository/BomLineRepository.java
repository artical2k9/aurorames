package com.mes.workorder.bom.repository;

import com.mes.workorder.bom.domain.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {

    List<BomLine> findAllByBomId(UUID bomId);

    boolean existsByBomIdAndFindNumber(UUID bomId, String findNumber);

    /**
     * Recursive BOM explosion via CTE. Returns flat rows ordered by depth.
     * Columns: [0] component_item_id (text), [1] parent_item_id (text),
     *          [2] depth (int), [3] part_number, [4] revision, [5] description,
     *          [6] unit_of_measure, [7] counterfeit_risk_level (nullable), [8] status
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE bom_tree AS (
                SELECT
                    bl.component_item_id,
                    bom.parent_item_id,
                    1 AS depth
                FROM work_order.bom_line bl
                JOIN work_order.bill_of_materials bom ON bom.id = bl.bom_id
                WHERE bl.bom_id = CAST(:rootBomId AS uuid)
                UNION ALL
                SELECT
                    bl.component_item_id,
                    bt.component_item_id,
                    bt.depth + 1
                FROM bom_tree bt
                JOIN work_order.bill_of_materials bom ON bom.parent_item_id = bt.component_item_id
                JOIN work_order.bom_line bl ON bl.bom_id = bom.id
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
                im.status
            FROM bom_tree bt
            JOIN work_order.item_master im ON im.id = bt.component_item_id
            ORDER BY bt.depth, bt.component_item_id
            """)
    List<Object[]> findExplosionRows(@Param("rootBomId") UUID rootBomId);
}
