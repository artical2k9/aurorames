package com.mes.udf.repository;

import com.mes.udf.domain.ModuleKey;
import com.mes.udf.domain.UdfFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UdfFieldDefinitionRepository extends JpaRepository<UdfFieldDefinition, UUID> {

    List<UdfFieldDefinition> findByOrgIdAndModuleKeyAndActiveTrue(UUID orgId, ModuleKey moduleKey);

    Optional<UdfFieldDefinition> findByOrgIdAndId(UUID orgId, UUID id);

    boolean existsByOrgIdAndModuleKeyAndFieldKey(UUID orgId, ModuleKey moduleKey, String fieldKey);
}
