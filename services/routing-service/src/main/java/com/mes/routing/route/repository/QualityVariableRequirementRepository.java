package com.mes.routing.route.repository;

import com.mes.routing.route.domain.QualityVariableRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityVariableRequirementRepository extends JpaRepository<QualityVariableRequirement, UUID> {
    List<QualityVariableRequirement> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<QualityVariableRequirement> findByOperationIdAndId(UUID operationId, UUID id);
}
