package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.WorkCentre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkCentreRepository extends JpaRepository<WorkCentre, UUID> {
    List<WorkCentre> findByOrgIdOrderByCode(UUID orgId);
    Optional<WorkCentre> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
}
