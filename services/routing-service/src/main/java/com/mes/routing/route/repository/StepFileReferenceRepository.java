package com.mes.routing.route.repository;

import com.mes.routing.route.domain.StepFileReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StepFileReferenceRepository extends JpaRepository<StepFileReference, UUID> {
    List<StepFileReference> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<StepFileReference> findByOperationIdAndId(UUID operationId, UUID id);
}
