package com.mes.inventory.bom.repository;

import com.mes.inventory.bom.domain.BillOfMaterials;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomRepository extends JpaRepository<BillOfMaterials, UUID> {

    Optional<BillOfMaterials> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<BillOfMaterials> findByOrgIdAndParentItemIdAndBomRevision(
            UUID orgId, UUID parentItemId, String bomRevision);

    boolean existsByOrgIdAndParentItemIdAndBomRevision(UUID orgId, UUID parentItemId, String bomRevision);

    List<BillOfMaterials> findAllByOrgIdAndParentItemId(UUID orgId, UUID parentItemId);

    /**
     * Paginated search across all BOM headers for an org, filtered by parent item
     * part number or description. Uses an implicit cross-join to ItemMaster so that
     * search terms are matched against item master fields without a @ManyToOne mapping.
     */
    @Query(value = "SELECT b FROM BillOfMaterials b, ItemMaster im "
            + "WHERE b.orgId = :orgId AND im.id = b.parentItemId AND im.orgId = :orgId "
            + "AND (:search IS NULL OR :search = '' "
            + "  OR LOWER(im.partNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "  OR LOWER(im.description) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(b) FROM BillOfMaterials b, ItemMaster im "
            + "WHERE b.orgId = :orgId AND im.id = b.parentItemId AND im.orgId = :orgId "
            + "AND (:search IS NULL OR :search = '' "
            + "  OR LOWER(im.partNumber) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "  OR LOWER(im.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BillOfMaterials> searchAllByOrgId(
            @Param("orgId") UUID orgId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Returns true if adding candidateId as a component of bomId would create a circular reference.
     * Walks the BOM tree downward from candidateId; returns true if it can reach bomId's parent item.
     */
    @Query(nativeQuery = true, value = """
            WITH RECURSIVE reachable AS (
                SELECT CAST(:candidateId AS uuid) AS item_id
                UNION ALL
                SELECT bl.component_item_id
                FROM inventory.bom_line bl
                JOIN inventory.bill_of_materials bom ON bom.id = bl.bom_id
                JOIN reachable r ON bom.parent_item_id = r.item_id
            )
            SELECT EXISTS (
                SELECT 1 FROM reachable r
                WHERE r.item_id = (
                    SELECT parent_item_id
                    FROM inventory.bill_of_materials
                    WHERE id = CAST(:bomId AS uuid)
                )
            )
            """)
    boolean hasAncestorCycle(@Param("bomId") UUID bomId, @Param("candidateId") UUID candidateComponentId);
}
