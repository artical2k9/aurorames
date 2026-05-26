package com.mes.workorder.itemmaster.repository;

import com.mes.workorder.itemmaster.domain.Classification;
import com.mes.workorder.itemmaster.domain.ItemMaster;
import com.mes.workorder.itemmaster.domain.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ItemMasterRepository extends JpaRepository<ItemMaster, UUID> {

    boolean existsByOrgIdAndPartNumberAndRevision(UUID orgId, String partNumber, String revision);

    Optional<ItemMaster> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ItemMaster> findByOrgIdAndPartNumberAndRevision(UUID orgId, String partNumber, String revision);

    Page<ItemMaster> findAllByOrgId(UUID orgId, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndStatus(UUID orgId, ItemStatus status, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndClassification(UUID orgId, Classification classification, Pageable pageable);

    Page<ItemMaster> findAllByOrgIdAndStatusAndClassification(UUID orgId, ItemStatus status,
                                                               Classification classification, Pageable pageable);
}
