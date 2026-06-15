package com.mes.routing.route.repository;

import com.mes.routing.route.domain.ToolingRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ToolingRequirementRepository extends JpaRepository<ToolingRequirement, UUID> {
    List<ToolingRequirement> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<ToolingRequirement> findByOperationIdAndId(UUID operationId, UUID id);
}
