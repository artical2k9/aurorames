package com.mes.routing.route.repository;

import com.mes.routing.route.domain.WorkInstructionLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkInstructionLinkRepository extends JpaRepository<WorkInstructionLink, UUID> {
    List<WorkInstructionLink> findByOperationIdOrderByCreatedAt(UUID operationId);
    Optional<WorkInstructionLink> findByOperationIdAndId(UUID operationId, UUID id);
}
