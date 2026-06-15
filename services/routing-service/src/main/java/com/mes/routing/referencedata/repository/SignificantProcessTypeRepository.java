package com.mes.routing.referencedata.repository;

import com.mes.routing.referencedata.domain.SignificantProcessType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignificantProcessTypeRepository extends JpaRepository<SignificantProcessType, UUID> {
    List<SignificantProcessType> findByOrgIdOrderByCode(UUID orgId);
    Optional<SignificantProcessType> findByOrgIdAndId(UUID orgId, UUID id);
    boolean existsByOrgIdAndCode(UUID orgId, String code);
}
