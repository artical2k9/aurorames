package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.LabourCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabourCodeRepository extends JpaRepository<LabourCode, UUID> {
    List<LabourCode> findByOrgIdOrderByCode(UUID orgId);
    Optional<LabourCode> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
    boolean existsByOrgIdAndLabourPlanTypeId(UUID orgId, UUID labourPlanTypeId);
}
