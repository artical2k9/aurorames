package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.LabourPlanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabourPlanTypeRepository extends JpaRepository<LabourPlanType, UUID> {
    List<LabourPlanType> findByOrgIdOrderByCode(UUID orgId);
    Optional<LabourPlanType> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
}
