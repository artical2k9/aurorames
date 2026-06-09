package com.mes.inventory.bom.repository;

import com.mes.inventory.bom.domain.BomRevision;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomRevisionRepository extends JpaRepository<BomRevision, UUID> {

    Optional<BomRevision> findByBomIdAndRevisionStatus(UUID bomId, RevisionStatus status);

    List<BomRevision> findAllByBomId(UUID bomId);

    boolean existsByBomIdAndRevisionStatus(UUID bomId, RevisionStatus status);

    @Query("SELECT MAX(br.revision) FROM BomRevision br WHERE br.bom.id = :bomId")
    Optional<Integer> findMaxRevisionByBomId(@Param("bomId") UUID bomId);

    // Summary rows for list endpoint.
    // Columns: [0] bom_id, [1] bom_revision_id, [2] revision, [3] revision_status,
    //          [4] description, [5] eco_id, [6] parent_item_id, [7] org_id,
    //          [8] has_draft, [9] bom_created_by, [10] bom_created_at,
    //          [11] part_number, [12] item_revision, [13] item_revision_status
    @Query(value = """
            SELECT
                b.id::text            AS bom_id,
                br.id::text           AS bom_revision_id,
                br.revision,
                br.revision_status,
                br.description,
                br.eco_id::text,
                b.parent_item_id::text,
                b.org_id::text,
                EXISTS (
                    SELECT 1 FROM inventory.bom_revision d
                    WHERE d.bom_id = b.id AND d.revision_status = 'DRAFT'
                ) AS has_draft,
                b.created_by          AS bom_created_by,
                b.created_at          AS bom_created_at,
                i.part_number,
                disp_ir.revision      AS item_revision,
                disp_ir.revision_status AS item_revision_status
            FROM inventory.bom b
            JOIN inventory.item i ON i.id = b.parent_item_id
            JOIN inventory.bom_revision br ON br.id = (
                SELECT DISTINCT ON (bom_id) id
                FROM inventory.bom_revision
                WHERE bom_id = b.id
                ORDER BY bom_id,
                         CASE revision_status
                             WHEN 'APPROVED'         THEN 1
                             WHEN 'PENDING_APPROVAL' THEN 2
                             WHEN 'DRAFT'            THEN 3
                         END
            )
            LEFT JOIN inventory.item_revision disp_ir ON disp_ir.id = (
                SELECT DISTINCT ON (item_id) id
                FROM inventory.item_revision
                WHERE item_id = i.id
                ORDER BY item_id,
                         CASE revision_status
                             WHEN 'APPROVED'         THEN 1
                             WHEN 'PENDING_APPROVAL' THEN 2
                             WHEN 'DRAFT'            THEN 3
                         END
            )
            WHERE b.org_id = :orgId
              AND (:search IS NULL OR :search = ''
                   OR LOWER(i.part_number) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(br.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
           countQuery = """
            SELECT COUNT(DISTINCT b.id)
            FROM inventory.bom b
            JOIN inventory.item i ON i.id = b.parent_item_id
            JOIN inventory.bom_revision br ON br.id = (
                SELECT DISTINCT ON (bom_id) id
                FROM inventory.bom_revision
                WHERE bom_id = b.id
                ORDER BY bom_id,
                         CASE revision_status
                             WHEN 'APPROVED'         THEN 1
                             WHEN 'PENDING_APPROVAL' THEN 2
                             WHEN 'DRAFT'            THEN 3
                         END
            )
            WHERE b.org_id = :orgId
              AND (:search IS NULL OR :search = ''
                   OR LOWER(i.part_number) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(br.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """,
           nativeQuery = true)
    Page<Object[]> findDisplayRevisionSummaries(
            @Param("orgId") UUID orgId,
            @Param("search") String search,
            Pageable pageable);
}
