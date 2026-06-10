package com.mes.inventory.bom.repository;

import com.mes.inventory.bom.domain.Bom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID> {

    Optional<Bom> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<Bom> findByOrgIdAndParentItemId(UUID orgId, UUID parentItemId);

    boolean existsByOrgIdAndParentItemId(UUID orgId, UUID parentItemId);

    /**
     * Returns true if adding candidateItemRevisionId as a component of bomId's parent item
     * would create a circular reference. Walks the BOM tree downward from the candidate item
     * through approved BOM revisions; returns true if it can reach bomId's parent item.
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE reachable AS (
                SELECT ir.item_id AS item_id
                FROM inventory.item_revision ir
                WHERE ir.id = CAST(:candidateItemRevisionId AS uuid)
                UNION ALL
                SELECT comp_ir.item_id
                FROM reachable r
                JOIN inventory.bom b_child ON b_child.parent_item_id = r.item_id
                JOIN inventory.bom_revision br_child
                    ON br_child.bom_id = b_child.id
                    AND br_child.revision_status = 'APPROVED'
                JOIN inventory.bom_line bl ON bl.bom_revision_id = br_child.id
                JOIN inventory.item_revision comp_ir ON comp_ir.id = bl.component_item_revision_id
            )
            SELECT EXISTS (
                SELECT 1 FROM reachable r
                WHERE r.item_id = (
                    SELECT parent_item_id FROM inventory.bom WHERE id = CAST(:bomId AS uuid)
                )
            )
            """)
    boolean hasAncestorCycle(
            @Param("bomId") UUID bomId,
            @Param("candidateItemRevisionId") UUID candidateItemRevisionId);
}
