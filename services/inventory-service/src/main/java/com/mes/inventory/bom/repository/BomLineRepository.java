package com.mes.inventory.bom.repository;

import com.mes.inventory.bom.domain.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BomLineRepository extends JpaRepository<BomLine, UUID> {

    // Use native SQL to avoid Hibernate 6.5 JPQL bug with @Audited + lazy @ManyToOne in WHERE.
    @Query(nativeQuery = true,
           value = "SELECT * FROM inventory.bom_line WHERE bom_revision_id = :bomRevisionId")
    List<BomLine> findAllByBomRevisionId(@Param("bomRevisionId") UUID bomRevisionId);

    @Query(nativeQuery = true,
           value = "SELECT * FROM inventory.bom_line "
                 + "WHERE bom_revision_id = :bomRevisionId AND find_number = :findNumber")
    List<BomLine> findAllByBomRevisionIdAndFindNumber(
            @Param("bomRevisionId") UUID bomRevisionId,
            @Param("findNumber") String findNumber);

    @Query(nativeQuery = true,
           value = "SELECT EXISTS(SELECT 1 FROM inventory.bom_line "
                 + "WHERE bom_revision_id = :bomRevisionId AND find_number = :findNumber)")
    boolean existsByBomRevisionIdAndFindNumber(
            @Param("bomRevisionId") UUID bomRevisionId,
            @Param("findNumber") String findNumber);

    /**
     * Recursive BOM explosion via CTE. Returns flat rows ordered by depth.
     * Columns: [0] component_item_id (item.id text), [1] parent_item_id (item.id text),
     *          [2] depth (int), [3] part_number, [4] revision (int),
     *          [5] description, [6] unit_of_measure, [7] counterfeit_risk_level (nullable),
     *          [8] revision_status, [9] effectivity_method (nullable),
     *          [10] effective_from_date (text, nullable), [11] effective_to_date (text, nullable),
     *          [12] effective_from_unit (nullable), [13] effective_to_unit (nullable),
     *          [14] find_number, [15] quantity (numeric nullable), [16] make_buy_code (nullable)
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE bom_tree AS (
                SELECT
                    ir.item_id             AS component_item_id,
                    parent_bom.parent_item_id AS parent_item_id,
                    1                      AS depth,
                    bl.effectivity_method,
                    bl.effective_from_date,
                    bl.effective_to_date,
                    bl.effective_from_unit,
                    bl.effective_to_unit,
                    bl.find_number,
                    bl.quantity,
                    bl.component_item_revision_id
                FROM inventory.bom_line bl
                JOIN inventory.bom_revision br ON br.id = bl.bom_revision_id
                JOIN inventory.bom parent_bom ON parent_bom.id = br.bom_id
                JOIN inventory.item_revision ir ON ir.id = bl.component_item_revision_id
                WHERE bl.bom_revision_id = CAST(:rootBomRevisionId AS uuid)
                UNION ALL
                SELECT
                    child_ir.item_id,
                    bt.component_item_id,
                    bt.depth + 1,
                    child_bl.effectivity_method,
                    child_bl.effective_from_date,
                    child_bl.effective_to_date,
                    child_bl.effective_from_unit,
                    child_bl.effective_to_unit,
                    child_bl.find_number,
                    child_bl.quantity,
                    child_bl.component_item_revision_id
                FROM bom_tree bt
                JOIN inventory.bom child_bom ON child_bom.parent_item_id = bt.component_item_id
                JOIN inventory.bom_revision child_br
                    ON child_br.bom_id = child_bom.id
                    AND child_br.revision_status = 'APPROVED'
                JOIN inventory.bom_line child_bl ON child_bl.bom_revision_id = child_br.id
                JOIN inventory.item_revision child_ir ON child_ir.id = child_bl.component_item_revision_id
            )
            SELECT
                bt.component_item_id::text,
                bt.parent_item_id::text,
                bt.depth,
                i.part_number,
                ir.revision,
                ir.description,
                ir.unit_of_measure,
                ir.counterfeit_risk_level,
                ir.revision_status,
                bt.effectivity_method,
                bt.effective_from_date::text,
                bt.effective_to_date::text,
                bt.effective_from_unit,
                bt.effective_to_unit,
                bt.find_number,
                bt.quantity,
                ir.make_buy_code
            FROM bom_tree bt
            JOIN inventory.item_revision ir ON ir.id = bt.component_item_revision_id
            JOIN inventory.item i ON i.id = bt.component_item_id
            ORDER BY bt.depth, bt.component_item_id
            """)
    List<Object[]> findExplosionRows(@Param("rootBomRevisionId") UUID rootBomRevisionId);
}
