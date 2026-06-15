package com.mes.routing.route.repository;

import com.mes.routing.route.domain.OperationResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationResourceRepository extends JpaRepository<OperationResource, UUID> {
    List<OperationResource> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<OperationResource> findByOperationIdAndId(UUID operationId, UUID id);
}
