package com.mes.workorder.bom.repository;

import com.mes.workorder.bom.domain.BillOfMaterials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BomRepository extends JpaRepository<BillOfMaterials, UUID> {

    Optional<BillOfMaterials> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<BillOfMaterials> findByOrgIdAndParentItemIdAndBomRevision(
            UUID orgId, UUID parentItemId, String bomRevision);

    boolean existsByOrgIdAndParentItemIdAndBomRevision(UUID orgId, UUID parentItemId, String bomRevision);

    /**
     * Returns true if adding candidateId as a component of bomId would create a circular reference.
     * Walks the BOM tree downward from candidateId; returns true if it can reach bomId's parent item.
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE reachable AS (
                SELECT CAST(:candidateId AS uuid) AS item_id
                UNION ALL
                SELECT bl.component_item_id
                FROM work_order.bom_line bl
                JOIN work_order.bill_of_materials bom ON bom.id = bl.bom_id
                JOIN reachable r ON bom.parent_item_id = r.item_id
            )
            SELECT EXISTS (
                SELECT 1 FROM reachable r
                WHERE r.item_id = (
                    SELECT parent_item_id
                    FROM work_order.bill_of_materials
                    WHERE id = CAST(:bomId AS uuid)
                )
            )
            """)
    boolean hasAncestorCycle(@Param("bomId") UUID bomId, @Param("candidateId") UUID candidateComponentId);
}
