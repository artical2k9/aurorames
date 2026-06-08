package com.mes.inventory.itemmaster.repository;

import com.mes.inventory.itemmaster.domain.Classification;
import com.mes.inventory.itemmaster.domain.CounterfeitRiskLevel;
import com.mes.inventory.itemmaster.domain.ItemMaster;
import com.mes.inventory.itemmaster.domain.ItemStatus;
import com.mes.inventory.itemmaster.domain.MakeBuyCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItemMasterRepository extends JpaRepository<ItemMaster, UUID> {

    boolean existsByOrgIdAndId(UUID orgId, UUID id);

    boolean existsByOrgIdAndPartNumberAndRevision(UUID orgId, String partNumber, String revision);

    Optional<ItemMaster> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ItemMaster> findByOrgIdAndPartNumberAndRevision(UUID orgId, String partNumber, String revision);

    Page<ItemMaster> findAllByOrgId(UUID orgId, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndStatus(UUID orgId, ItemStatus status, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndClassification(UUID orgId, Classification classification, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndStatusAndClassification(UUID orgId, ItemStatus status,
                                                               Classification classification, Pageable pageable);

    @Query("SELECT im FROM ItemMaster im WHERE im.orgId = :orgId" +
           " AND (:status IS NULL OR im.status = :status)" +
           " AND (:classification IS NULL OR im.classification = :classification)" +
           " AND (:riskLevel IS NULL OR im.counterfeitRiskLevel = :riskLevel)" +
           " AND (:makeBuyCode IS NULL OR im.makeBuyCode = :makeBuyCode)" +
           " AND (:searchPattern IS NULL OR LOWER(im.partNumber) LIKE :searchPattern" +
           "   OR LOWER(im.description) LIKE :searchPattern)")
    Page<ItemMaster> findAllFiltered(
            @Param("orgId") UUID orgId,
            @Param("status") ItemStatus status,
            @Param("classification") Classification classification,
            @Param("riskLevel") CounterfeitRiskLevel riskLevel,
            @Param("makeBuyCode") MakeBuyCode makeBuyCode,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);
}
