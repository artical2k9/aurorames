package com.mes.routing.route.repository;

import com.mes.routing.route.domain.LabourPlanLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabourPlanLineRepository extends JpaRepository<LabourPlanLine, UUID> {
    List<LabourPlanLine> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<LabourPlanLine> findByOperationIdAndId(UUID operationId, UUID id);
}
