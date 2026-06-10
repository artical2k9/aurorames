package com.mes.inventory.itemmaster.repository;

import com.mes.inventory.itemmaster.domain.ItemRevision;
import com.mes.inventory.itemmaster.domain.RevisionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRevisionRepository extends JpaRepository<ItemRevision, UUID> {

    Optional<ItemRevision> findByItemIdAndRevisionStatus(UUID itemId, RevisionStatus status);

    List<ItemRevision> findAllByItemId(UUID itemId);

    List<ItemRevision> findByItemIdOrderByRevisionAsc(UUID itemId);

    @Query("SELECT MAX(ir.revision) FROM ItemRevision ir WHERE ir.item.id = :itemId")
    Optional<Integer> findMaxRevisionByItemId(@Param("itemId") UUID itemId);

    boolean existsByItemIdAndRevisionStatus(UUID itemId, RevisionStatus status);

    // Returns the display revision for each item: APPROVED preferred, then PENDING_APPROVAL, then DRAFT.
    // hasDraft is true when any DRAFT revision exists for the item.
    @Query(value = """
            SELECT ir.*,
                   EXISTS (
                       SELECT 1 FROM inventory.item_revision d
                       WHERE d.item_id = ir.item_id
                         AND d.revision_status = 'DRAFT'
                   ) AS has_draft
            FROM inventory.item_revision ir
            WHERE ir.id IN (
                SELECT DISTINCT ON (item_id) id
                FROM inventory.item_revision
                ORDER BY item_id,
                         CASE revision_status
                             WHEN 'APPROVED'         THEN 1
                             WHEN 'PENDING_APPROVAL' THEN 2
                             WHEN 'DRAFT'            THEN 3
                         END
            )
            """,
            countQuery = "SELECT COUNT(DISTINCT item_id) FROM inventory.item_revision",
            nativeQuery = true)
    Page<Object[]> findDisplayRevisions(Pageable pageable);

    @Query(value = """
            SELECT ir.*,
                   EXISTS (
                       SELECT 1 FROM inventory.item_revision d
                       WHERE d.item_id = ir.item_id
                         AND d.revision_status = 'DRAFT'
                   ) AS has_draft
            FROM inventory.item_revision ir
            WHERE ir.id IN (
                SELECT DISTINCT ON (item_id) id
                FROM inventory.item_revision
                WHERE revision_status = :#{#status.name()}
                ORDER BY item_id,
                         CASE revision_status
                             WHEN 'APPROVED'         THEN 1
                             WHEN 'PENDING_APPROVAL' THEN 2
                             WHEN 'DRAFT'            THEN 3
                         END
            )
            """,
            countQuery = "SELECT COUNT(DISTINCT item_id) FROM inventory.item_revision "
                    + "WHERE revision_status = :#{#status.name()}",
            nativeQuery = true)
    Page<Object[]> findDisplayRevisionsByStatus(@Param("status") RevisionStatus status, Pageable pageable);
}
