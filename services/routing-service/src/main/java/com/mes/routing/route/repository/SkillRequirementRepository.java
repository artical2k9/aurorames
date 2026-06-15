package com.mes.routing.route.repository;

import com.mes.routing.route.domain.SkillRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SkillRequirementRepository extends JpaRepository<SkillRequirement, UUID> {
    List<SkillRequirement> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<SkillRequirement> findByOperationIdAndId(UUID operationId, UUID id);
}
