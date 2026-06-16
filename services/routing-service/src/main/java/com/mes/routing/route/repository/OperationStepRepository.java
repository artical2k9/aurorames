package com.mes.routing.route.repository;

import com.mes.routing.route.domain.OperationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationStepRepository extends JpaRepository<OperationStep, UUID> {

    List<OperationStep> findByOperationIdOrderByStepSequenceNumberAscStepNumberAsc(UUID operationId);

    Optional<OperationStep> findByOperationIdAndId(UUID operationId, UUID id);

    boolean existsByOperationIdAndStepNumber(UUID operationId, int stepNumber);

    /** Count steps sharing a step-sequence number within an operation — derives Normal vs Parallel. */
    int countByOperationIdAndStepSequenceNumber(UUID operationId, int stepSequenceNumber);
}
