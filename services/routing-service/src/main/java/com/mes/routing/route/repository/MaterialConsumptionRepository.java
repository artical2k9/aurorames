package com.mes.routing.route.repository;

import com.mes.routing.route.domain.MaterialConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialConsumptionRepository extends JpaRepository<MaterialConsumption, UUID> {
    List<MaterialConsumption> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<MaterialConsumption> findByOperationIdAndId(UUID operationId, UUID id);
}
